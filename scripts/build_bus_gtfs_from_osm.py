#!/usr/bin/env python3
"""Build the ASSUMED (not official) HCMC bus GTFS feed from OpenStreetMap route=bus relations. See ADR-032.

    python3 scripts/build_bus_gtfs_from_osm.py      # needs osmium and data/raw/osm/vietnam-*.osm.pbf
    python3 -m unittest discover -s scripts/tests   # tests

OSM gives stops in order, headways (`interval`) and service hours (`opening_hours`), not timetables:
trips run every `interval` inside each window, and times between stops are estimated from distance.
"""
import csv
import hashlib
import re
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path

from build_discover_seed import distance_m, slugify

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "assumed" / "hcmc-bus-osm-gtfs"
BBOX = "106.35,10.35,107.05,11.2"  # same HCMC box as the Photon geocoder (ADR-031)
FEED_VERSION = "osm-assumed-2026-11-14"
FEED_ID = "bus"  # OTP ids "bus:n123"; the Metro feed is "1" (stable whatever the load order)
LABEL = "DU LIEU GIA DINH TU OPENSTREETMAP - KHONG CHINH THUC / ASSUMED DATA FROM OPENSTREETMAP - NOT OFFICIAL"
# ASSUMED: OSM has no validity dates.
CALENDAR_START, CALENDAR_END = "20261101", "20271231"
# ASSUMED average bus speed including stops (no source found; ADR-032). Used when OSM has no `duration`.
SPEED_MPS = 15 / 3.6
MIN_LEG_SECONDS = 20
MAX_GAP_M = 50  # ways farther apart than this are not joined into one shape
MAX_STOP_OFF_SHAPE_M = 300  # a stop farther than this from the shape is dropped from the route
DAYS = ["mo", "tu", "we", "th", "fr", "sa", "su"]
STOP_ROLES = ("platform", "stop")
DEFAULT_STOP_NAME = "Trạm xe buýt"


def decode(text):
    """OPL escapes: %<hex codepoint>%."""
    return re.sub(r"%([0-9a-fA-F]+)%", lambda m: chr(int(m.group(1), 16)), text)


def parse_opl(lines):
    """nodes {id: (lat, lon, tags)}, ways {id: [node ids]}, relations [(id, tags, [(type, id, role)])]."""
    nodes, ways, rels = {}, {}, []
    for line in lines:
        fields = line.split(" ")
        if not fields[0] or fields[0][0] not in "nwr":
            continue
        kind, oid = fields[0][0], int(fields[0][1:])
        f = {x[0]: x[1:] for x in fields[1:] if x}
        tags = {}
        for kv in filter(None, f.get("T", "").split(",")):
            k, _, v = kv.partition("=")
            tags[decode(k)] = decode(v)
        if kind == "n" and f.get("x") and f.get("y"):
            nodes[oid] = (float(f["y"]), float(f["x"]), tags)
        elif kind == "w":
            ways[oid] = [int(n[1:]) for n in filter(None, f.get("N", "").split(","))]
        elif kind == "r":
            members = []
            for m in filter(None, f.get("M", "").split(",")):
                ref, _, role = m.partition("@")
                members.append((ref[0], int(ref[1:]), decode(role).strip()))
            rels.append((oid, tags, members))
    return nodes, ways, rels


def _clock(token):
    """'HH:MM' -> seconds; minutes may be 60 ('00:60' appears in OSM HCMC)."""
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", (token or "").strip())
    return (int(m.group(1)) * 60 + int(m.group(2))) * 60 if m else None


def parse_interval(value):
    """Headway in seconds; a range 'a-b' gives the midpoint (ADR-032)."""
    parts = (value or "").split("-")
    secs = [_clock(p) for p in parts]
    if not 1 <= len(parts) <= 2 or None in secs or min(secs) <= 0:
        return None
    return round(sum(secs) / len(secs))


def parse_opening_hours(value):
    """[(day flags Mo..Su, [(start, end)], [departure])] for the simple forms used by HCMC bus relations.

    Anything else (PH, sunrise, overnight, overlapping days) returns None so the route is skipped, not guessed.
    """
    rules, seen = [], [0] * 7
    for rule in filter(None, (r.strip() for r in (value or "").split(";"))):
        m = re.fullmatch(r"([A-Za-z]{2})(?:-([A-Za-z]{2}))?\s+(\S+)", rule)
        if not m or m.group(1).lower() not in DAYS or (m.group(2) and m.group(2).lower() not in DAYS):
            return None
        a = DAYS.index(m.group(1).lower())
        z = DAYS.index(m.group(2).lower()) if m.group(2) else a
        if z < a:
            return None
        days = tuple(int(a <= i <= z) for i in range(7))
        if any(d and s for d, s in zip(days, seen)):
            return None
        seen = [d or s for d, s in zip(days, seen)]
        windows, departures = [], []
        for token in m.group(3).split(","):
            ends = [_clock(t) for t in token.split("-")]
            if None in ends or len(ends) > 2:
                return None
            if len(ends) == 1:
                departures.append(ends[0])
            elif ends[0] < ends[1]:
                windows.append((ends[0], ends[1]))
            else:
                return None
        rules.append((days, windows, departures))
    return rules or None


def _dist(a, b):
    return distance_m(a[0], a[1], b[0], b[1])


def _points(line, nodes):
    points = [nodes[n][:2] for n in line]
    return [p for i, p in enumerate(points) if i == 0 or p != points[i - 1]]


def assemble_shape(way_ids, ways, nodes, start=None):
    """Join member ways into one line. None if there is a gap > MAX_GAP_M.

    First in member order, flipping each way to continue from the previous end. Many HCMC relations list ways
    out of order, so if that fails and `start` (first stop) is given, chain greedily from the way nearest to it.
    """
    segs = [s for s in ([n for n in ways.get(w, []) if n in nodes] for w in way_ids) if len(s) >= 2]
    if not segs:
        return None
    line = segs[0]
    if len(segs) > 1:
        nxt = (nodes[segs[1][0]], nodes[segs[1][-1]])
        if min(_dist(nodes[line[0]], p) for p in nxt) < min(_dist(nodes[line[-1]], p) for p in nxt):
            line = line[::-1]
    for seg in segs[1:]:
        d_start, d_end = _dist(nodes[line[-1]], nodes[seg[0]]), _dist(nodes[line[-1]], nodes[seg[-1]])
        if min(d_start, d_end) > MAX_GAP_M:
            return _greedy(segs, nodes, start) if start else None
        line = line + (seg if d_start <= d_end else seg[::-1])
    return _points(line, nodes)


def _greedy(segs, nodes, start):
    rest = list(segs)
    first = min(rest, key=lambda s: min(_dist(start, nodes[s[0]]), _dist(start, nodes[s[-1]])))
    rest.remove(first)
    line = first if _dist(start, nodes[first[0]]) <= _dist(start, nodes[first[-1]]) else first[::-1]
    while rest:
        tail = nodes[line[-1]]
        d, seg, flip = min(((min(_dist(tail, nodes[s[0]]), _dist(tail, nodes[s[-1]])), i, _dist(tail, nodes[s[-1]]) < _dist(tail, nodes[s[0]]))
                            for i, s in enumerate(rest)), key=lambda x: x[0])
        if d > MAX_GAP_M:
            return None
        nxt = rest.pop(seg)
        line = line + (nxt[::-1] if flip else nxt)
    return _points(line, nodes)


def stop_offsets(stops, shape, duration):
    """Seconds from the first stop, from distance along the shape (stops already in shape order, see order_stops).

    Speed is SPEED_MPS unless OSM gives the trip `duration`. Every leg takes at least MIN_LEG_SECONDS.
    """
    cum = [0.0]
    for a, b in zip(shape, shape[1:]):
        cum.append(cum[-1] + _dist(a, b))
    at, j = [], 0
    for s in stops:
        j = min(range(j, len(shape)), key=lambda i: _dist(s, shape[i]))
        at.append(cum[j])
    total = at[-1] - at[0]
    scale = duration / total if duration and total else 1 / SPEED_MPS
    offsets = [0]
    for a in at[1:]:
        offsets.append(max(round((a - at[0]) * scale), offsets[-1] + MIN_LEG_SECONDS))
    return offsets


def order_stops(stops, nodes, shape):
    """(shape, stops in travel order), or (None, []) when the order cannot be checked.

    Stop order in many HCMC relations is wrong (route 19 listed order: 63 km of straight lines for a ~25 km route),
    so stops are sorted by their position along the shape and stops > MAX_STOP_OFF_SHAPE_M away are dropped.
    Without a shape the listed order is mostly wrong (measured 14-11-2026), so the route is skipped, not guessed.
    """
    if not shape:
        return None, []
    near = [(min(range(len(shape)), key=lambda i: _dist(nodes[n][:2], shape[i])), n) for n in stops]
    if near[0][0] > near[-1][0]:  # shape drawn from the other end
        shape = shape[::-1]
        near = [(len(shape) - 1 - i, n) for i, n in near]
    kept = [n for i, n in sorted((i, n) for i, n in near if _dist(nodes[n][:2], shape[i]) <= MAX_STOP_OFF_SHAPE_M)]
    kept = [n for k, n in enumerate(kept) if k == 0 or n != kept[k - 1]]
    return (shape, kept) if len(kept) >= 2 else (None, [])


def hms(seconds):
    return f"{seconds // 3600:02d}:{seconds % 3600 // 60:02d}:{seconds % 60:02d}"


def build(nodes, ways, rels):
    """(feed {file name: rows, '_stats': Counter}, skipped [(relation id, ref, reason)])."""
    routes, agencies, stops, services, shapes = {}, {}, {}, set(), []
    trips, stop_times, frequencies, skipped, stats = [], [], [], [], Counter()
    for rid, tags, members in sorted(rels, key=lambda r: r[0]):
        if tags.get("route") != "bus":
            continue
        ref = tags.get("ref", "").strip()
        listed = [i for t, i, role in members if t == "n" and role.startswith(STOP_ROLES)]
        listed = [n for k, n in enumerate(listed) if k == 0 or n != listed[k - 1]]
        present = [n for n in listed if n in nodes]
        hours = parse_opening_hours(tags.get("opening_hours"))
        interval = parse_interval(tags.get("interval"))
        reason = ("too_few_stops" if len(present) < 2 else "no_opening_hours" if hours is None
                  else "no_interval" if interval is None and any(w for _, w, _ in hours) else None)
        if reason:
            skipped.append((rid, ref, reason))
            continue
        shape = assemble_shape([i for t, i, role in members if t == "w" and not role.startswith(STOP_ROLES)],
                               ways, nodes, nodes[present[0]][:2])
        if shape is None:
            skipped.append((rid, ref, "no_shape"))
            continue
        shape, present = order_stops(present, nodes, shape)
        if not present:
            skipped.append((rid, ref, "stops_off_shape"))
            continue
        stats["stops_dropped_outside_extract_or_off_shape"] += len(listed) - len(present)

        network = tags.get("network", "").strip()
        route_id = f"{slugify(network)}-{slugify(ref)}" if ref and network else f"r{rid}"
        operator = tags.get("operator") or network or "Không rõ"
        agency_id = slugify(operator)
        agencies.setdefault(agency_id, [agency_id, operator, f"https://www.openstreetmap.org/relation/{rid}",
                                        "Asia/Ho_Chi_Minh", "vi"])
        colour = tags.get("colour", "").lstrip("#")
        route = routes.setdefault(route_id, [route_id, agency_id, ref or tags.get("name", f"r{rid}"),
                                             tags.get("name", ""), LABEL, 3, ""])
        if not route[6] and re.fullmatch(r"[0-9A-Fa-f]{6}", colour):
            route[6] = colour.upper()

        for n in present:
            lat, lon, ntags = nodes[n]
            stops.setdefault(n, [f"n{n}", ntags.get("name", "").strip() or DEFAULT_STOP_NAME, f"{lat:.7f}", f"{lon:.7f}"])
        shape_id = f"r{rid}"
        shapes += [[shape_id, f"{lat:.7f}", f"{lon:.7f}", k] for k, (lat, lon) in enumerate(shape, start=1)]
        offsets = stop_offsets([nodes[n][:2] for n in present], shape, _clock(tags.get("duration")))
        headsign = tags.get("to") or stops[present[-1]][1]

        def add_trip(trip_id, service, start):
            trips.append([route_id, service, trip_id, headsign, "", shape_id])
            for seq, (n, off) in enumerate(zip(present, offsets), start=1):
                stop_times.append([trip_id, hms(start + off), hms(start + off), f"n{n}", seq])

        for days, windows, departures in hours:
            service = "D" + "".join(map(str, days))
            services.add((service, days))
            if windows:
                trip_id = f"r{rid}-{service}"
                add_trip(trip_id, service, 0)
                # exact_times=1 as for the Metro feed (ADR-020): fixed departures every headway from the window start.
                frequencies += [[trip_id, hms(a), hms(z + 60), interval, 1] for a, z in windows]
            for t in departures:
                add_trip(f"r{rid}-{service}-{t // 3600:02d}{t % 3600 // 60:02d}", service, t)
        stats["relations_used"] += 1

    feed = {
        "agency.txt": sorted(agencies.values()),
        "routes.txt": sorted(routes.values()),
        "stops.txt": sorted(stops.values()),
        "calendar.txt": [[s, *d, CALENDAR_START, CALENDAR_END] for s, d in sorted(services)],
        "trips.txt": trips, "stop_times.txt": stop_times, "frequencies.txt": frequencies, "shapes.txt": shapes,
        "feed_info.txt": [[f"GoWander ({LABEL})", "https://github.com/LeGiaBaoDev/GoWander", "vi",
                           CALENDAR_START, CALENDAR_END, FEED_VERSION, FEED_ID]],
        "_stats": stats,
    }
    return feed, skipped


HEADERS = {
    "agency.txt": ["agency_id", "agency_name", "agency_url", "agency_timezone", "agency_lang"],
    "routes.txt": ["route_id", "agency_id", "route_short_name", "route_long_name", "route_desc", "route_type", "route_color"],
    "stops.txt": ["stop_id", "stop_name", "stop_lat", "stop_lon"],
    "calendar.txt": ["service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
                     "start_date", "end_date"],
    "trips.txt": ["route_id", "service_id", "trip_id", "trip_headsign", "direction_id", "shape_id"],
    "stop_times.txt": ["trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"],
    "frequencies.txt": ["trip_id", "start_time", "end_time", "headway_secs", "exact_times"],
    "shapes.txt": ["shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence"],
    "feed_info.txt": ["feed_publisher_name", "feed_publisher_url", "feed_lang", "feed_start_date", "feed_end_date",
                      "feed_version", "feed_id"],
}


def check(feed):
    """Referential integrity and increasing times; raises AssertionError."""
    def ids(name, col=0):
        return {r[col] for r in feed[name]}
    stop_ids, trip_ids, route_ids = ids("stops.txt"), ids("trips.txt", 2), ids("routes.txt")
    assert all(r[3] in stop_ids and r[0] in trip_ids for r in feed["stop_times.txt"])
    assert all(r[0] in route_ids and r[1] in ids("calendar.txt") for r in feed["trips.txt"])
    assert all(r[1] in ids("agency.txt") for r in feed["routes.txt"])
    assert all(r[0] in trip_ids and r[3] > 0 for r in feed["frequencies.txt"])
    assert ids("trips.txt", 5) <= ids("shapes.txt")
    last = {}
    for r in feed["stop_times.txt"]:
        assert r[0] not in last or r[1] > last[r[0]], r
        last[r[0]] = r[1]
    for _, _, lat, lon in feed["stops.txt"]:
        assert 10.0 < float(lat) < 11.6 and 106.0 < float(lon) < 107.6


def main():
    osm_files = sorted((ROOT / "data" / "raw" / "osm").glob("vietnam-*.osm.pbf"))
    if not osm_files:
        sys.exit("Thiếu data/raw/osm/vietnam-*.osm.pbf — chạy infrastructure/otp/scripts/fetch-osm.sh")
    osm_file = osm_files[-1]
    with tempfile.TemporaryDirectory() as tmp:
        area, bus = Path(tmp) / "area.osm.pbf", Path(tmp) / "bus.osm.pbf"
        run = lambda *a: subprocess.run(["osmium", *a], check=True, capture_output=True, text=True)
        run("extract", "-b", BBOX, "--strategy", "complete_ways", str(osm_file), "-o", str(area))
        run("tags-filter", str(area), "r/route=bus", "-o", str(bus))  # keeps member nodes and ways' nodes
        opl = run("cat", str(bus), "-f", "opl").stdout.splitlines()
    feed, skipped = build(*parse_opl(opl))
    check(feed)
    stats = feed.pop("_stats")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, header in HEADERS.items():
        with open(OUT / name, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(header)
            w.writerows(feed[name])
    (OUT / "README.txt").write_text(
        f"{LABEL}\n\nFeed GTFS xe buyt TP.HCM dung tu OpenStreetMap, phien ban {FEED_VERSION}.\n"
        "KHONG phai du lieu cua don vi van hanh. Gio chay la uoc tinh (gian cach + toc do gia dinh).\n"
        "Tao boi scripts/build_bus_gtfs_from_osm.py; khong sua tay. Nguon va gia dinh: docs/decisions/ADR-032-bus-gtfs-from-osm.md.\n"
        "(c) OpenStreetMap contributors, ODbL 1.0.\n", encoding="utf-8")
    reasons = Counter(r for _, _, r in skipped)
    manifest = [
        f"feed_version={FEED_VERSION}", f"osm_file={osm_file.name}",
        f"osm_md5={hashlib.md5(osm_file.read_bytes()).hexdigest()}", f"bbox={BBOX}",
        *(f"{name.removesuffix('.txt')}={len(feed[name])}" for name in HEADERS if name != "feed_info.txt"),
        *(f"{k}={v}" for k, v in sorted(stats.items())),
        f"relations_skipped={len(skipped)}", *(f"skipped_{k}={v}" for k, v in sorted(reasons.items())),
        *(f"skipped relation/{rid} ref={ref or '-'} reason={why}" for rid, ref, why in skipped),
    ]
    (OUT / "manifest.txt").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    print("\n".join(line for line in manifest if not line.startswith("skipped relation/")))
    print(f"written to {OUT}")


if __name__ == "__main__":
    main()
