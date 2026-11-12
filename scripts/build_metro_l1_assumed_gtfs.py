#!/usr/bin/env python3
"""Build the ASSUMED (not official) GTFS feed for HCMC Metro Line 1. See docs/decisions/ADR-020-transit-data.md."""
import csv
import math
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "data" / "assumed" / "hcmc-metro-l1-gtfs"
FEED_VERSION = "assumed-2026-11-12"
LABEL = "DU LIEU GIA DINH - KHONG CHINH THUC / ASSUMED DATA - NOT OFFICIAL"

# Order, names and coordinates: OpenStreetMap route relation 11919223 (ODbL), osm_base 2026-11-11.
STATIONS = [
    ("L1-01", "Bến Thành", 10.770775, 106.697569, 11586280308),
    ("L1-02", "Nhà Hát Thành Phố", 10.775511, 106.702262, 11586280310),
    ("L1-03", "Ba Son", 10.781987, 106.708464, 11586280313),
    ("L1-04", "Công viên Văn Thánh", 10.796045, 106.715521, 11586280314),
    ("L1-05", "Tân Cảng", 10.798581, 106.723324, 11586280316),
    ("L1-06", "Thảo Điền", 10.800471, 106.733654, 11586280320),
    ("L1-07", "An Phú", 10.802084, 106.742253, 11586280322),
    ("L1-08", "Rạch Chiếc", 10.808538, 106.755280, 9733886226),
    ("L1-09", "Phước Long", 10.821417, 106.758197, 11586280324),
    ("L1-10", "Bình Thái", 10.832660, 106.763920, 11586280326),
    ("L1-11", "Thủ Đức", 10.846403, 106.771666, 11586280328),
    ("L1-12", "Khu Công Nghệ Cao", 10.858974, 106.788839, 11586280331),
    ("L1-13", "Đại học Quốc gia", 10.866286, 106.801209, 11586280333),
    ("L1-14", "Bến xe Suối Tiên", 10.879569, 106.814170, 11586280335),
]

# Reported: one run takes 29 min 30 s including a 30 s stop at each station (pre-opening trial figure).
END_TO_END_SECONDS = 29 * 60 + 30
DWELL_SECONDS = 30

# Reported for the timetable in force from 2026-06-01: first train 05:00; last departure 22:00 Mon-Thu,
# 23:00 Fri-Sun; trains per day in both directions 228 / 236 / 230; headway 6-7 min peak, 8-15 min off-peak.
# ASSUMED: the peak windows, the exact headways, an even split between directions, and the end date.
PEAK_WINDOWS = [(6 * 3600, 8 * 3600 + 1800), (16 * 3600 + 1800, 19 * 3600)]
PEAK_HEADWAY = 390
SERVICES = [
    # service_id, weekday flags Mon..Sun, last departure, trains/day both directions, has peaks
    ("MON_THU", (1, 1, 1, 1, 0, 0, 0), 22 * 3600, 228, True),
    ("FRI", (0, 0, 0, 0, 1, 0, 0), 23 * 3600, 236, True),
    ("SAT_SUN", (0, 0, 0, 0, 0, 1, 1), 23 * 3600, 230, False),
]
FIRST_DEPARTURE = 5 * 3600
CALENDAR_START, CALENDAR_END = "20260601", "20261231"


def hms(seconds):
    return f"{seconds // 3600:02d}:{seconds % 3600 // 60:02d}:{seconds % 60:02d}"


def distance_m(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[2], a[3], b[2], b[3]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def stop_offsets(stations):
    """(arrival, departure) seconds from trip start; run time is split in proportion to straight-line distance."""
    legs = [distance_m(a, b) for a, b in zip(stations, stations[1:])]
    run_total = END_TO_END_SECONDS - DWELL_SECONDS * (len(stations) - 2)
    offsets, clock = [(0, 0)], 0
    for i, leg in enumerate(legs):
        clock += round(run_total * leg / sum(legs))
        last = i == len(legs) - 1
        offsets.append((clock, clock if last else clock + DWELL_SECONDS))
        clock = offsets[-1][1]
    return offsets


def windows(last_departure, per_direction, has_peaks):
    """Frequency windows (start, end, headway). Off-peak headway is derived to hit the reported trip count."""
    end = last_departure + 60  # include the last departure itself
    if not has_peaks:
        return [(FIRST_DEPARTURE, end, round((end - FIRST_DEPARTURE) / per_direction))]
    cuts = sorted({FIRST_DEPARTURE, end, *[t for w in PEAK_WINDOWS for t in w]})
    spans = [(a, b, (a, b) in PEAK_WINDOWS) for a, b in zip(cuts, cuts[1:])]
    peak_trips = sum(math.ceil((b - a) / PEAK_HEADWAY) for a, b, peak in spans if peak)
    offpeak_seconds = sum(b - a for a, b, peak in spans if not peak)
    offpeak_headway = round(offpeak_seconds / (per_direction - peak_trips))
    return [(a, b, PEAK_HEADWAY if peak else offpeak_headway) for a, b, peak in spans]


def write(name, header, rows):
    with open(OUT / name, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    return len(rows)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    write("agency.txt", ["agency_id", "agency_name", "agency_url", "agency_timezone", "agency_lang"],
          [["HURC1", "Công ty TNHH MTV Đường sắt đô thị số 1 (HURC1)",
            "https://www.openstreetmap.org/relation/11919223", "Asia/Ho_Chi_Minh", "vi"]])
    write("feed_info.txt",
          ["feed_publisher_name", "feed_publisher_url", "feed_lang", "feed_start_date", "feed_end_date", "feed_version"],
          [[f"GoWander ({LABEL})", "https://github.com/LeGiaBaoDev/GoWander", "vi",
            CALENDAR_START, CALENDAR_END, FEED_VERSION]])
    write("routes.txt",
          ["route_id", "agency_id", "route_short_name", "route_long_name", "route_desc", "route_type", "route_color"],
          [["L1", "HURC1", "L1", "Bến Thành - Suối Tiên", LABEL, 1, "0079B8"]])
    write("stops.txt", ["stop_id", "stop_code", "stop_name", "stop_lat", "stop_lon", "location_type"],
          [[sid, sid, name, f"{lat:.6f}", f"{lon:.6f}", 0] for sid, name, lat, lon, _ in STATIONS])
    write("calendar.txt",
          ["service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
           "start_date", "end_date"],
          [[sid, *days, CALENDAR_START, CALENDAR_END] for sid, days, *_ in SERVICES])

    trips, stop_times, frequencies, summary = [], [], [], []
    for direction, stations in ((0, STATIONS), (1, STATIONS[::-1])):
        offsets = stop_offsets(stations)
        for sid, _days, last_departure, both_directions, has_peaks in SERVICES:
            trip_id = f"L1-{sid}-{direction}"
            trips.append(["L1", sid, trip_id, stations[-1][1], direction])
            for seq, (station, (arr, dep)) in enumerate(zip(stations, offsets), start=1):
                stop_times.append([trip_id, hms(arr), hms(dep), station[0], seq])
            count = 0
            for start, end, headway in windows(last_departure, both_directions // 2, has_peaks):
                # exact_times=1: trains leave at start, start+headway, ... (a fixed timetable). With 0 ("about every
                # N s") OTP 2.10.0 plans a pessimistic boarding time and shows made-up departures 1 min apart
                # (route cases RC-01/RC-03, 12-11-2026).
                frequencies.append([trip_id, hms(start), hms(end), headway, 1])
                count += math.ceil((end - start) / headway)
            summary.append((trip_id, count, both_directions // 2))
    write("trips.txt", ["route_id", "service_id", "trip_id", "trip_headsign", "direction_id"], trips)
    write("stop_times.txt", ["trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"], stop_times)
    write("frequencies.txt", ["trip_id", "start_time", "end_time", "headway_secs", "exact_times"], frequencies)
    (OUT / "README.txt").write_text(
        f"{LABEL}\n\n"
        "Feed GTFS tu bien soan cho Metro so 1 TP.HCM (Ben Thanh - Suoi Tien), phien ban "
        f"{FEED_VERSION}.\nKHONG phai du lieu cua don vi van hanh. Khong hien thi nhu lich chay chinh thuc.\n"
        "Tao boi scripts/build_metro_l1_assumed_gtfs.py; khong sua tay cac file .txt.\n"
        "Nguon va gia dinh: docs/decisions/ADR-020-transit-data.md.\n"
        "Toa do va ten ga: (c) OpenStreetMap contributors, ODbL 1.0 (relation 11919223).\n",
        encoding="utf-8")

    # Self-checks: references resolve, times increase, run time matches, trip counts near the reported totals.
    stop_ids, trip_ids = {s[0] for s in STATIONS}, {t[2] for t in trips}
    assert all(r[3] in stop_ids and r[0] in trip_ids for r in stop_times)
    assert all(r[0] in trip_ids for r in frequencies)
    for trip_id in trip_ids:
        rows = [r for r in stop_times if r[0] == trip_id]
        times = [t for r in rows for t in (r[1], r[2])]
        assert times == sorted(times) and len(rows) == len(STATIONS)
        assert abs(sum(int(x) * m for x, m in zip(rows[-1][1].split(":"), (3600, 60, 1))) - END_TO_END_SECONDS) <= 7
    for trip_id, count, target in sorted(summary):
        assert abs(count - target) <= 2, (trip_id, count, target)
        print(f"{trip_id}: {count} trips/day generated, {target} reported")
    print(f"stops={len(STATIONS)} trips={len(trips)} stop_times={len(stop_times)} frequencies={len(frequencies)}")
    print(f"written to {OUT}")


if __name__ == "__main__":
    sys.exit(main())
