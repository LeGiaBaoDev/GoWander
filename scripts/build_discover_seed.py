#!/usr/bin/env python3
"""Build the Discover seed SQL (ADR-028 §4) from two sources:

A. OpenStreetMap: named POIs within RADIUS_M of the 14 Metro L1 stations, cut from the downloaded Vietnam extract.
B. data/curated/places.csv: places entered by hand. Every row must carry a source URL, a licence and an attribution;
   rows without them are rejected (no unsourced descriptions).

Output: data/discover/seed.sql (idempotent upserts, load with infrastructure/postgres/db.sh seed) and seed.manifest.txt.

    python3 scripts/build_discover_seed.py              # needs osmium and data/raw/osm/vietnam-*.osm.pbf
    python3 scripts/build_discover_seed.py --self-test
"""
import csv
import hashlib
import json
import math
import re
import subprocess
import sys
import tempfile
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STOPS = ROOT / "data" / "assumed" / "hcmc-metro-l1-gtfs" / "stops.txt"
CURATED = ROOT / "data" / "curated" / "places.csv"
OUT_DIR = ROOT / "data" / "discover"
RADIUS_M = 800
BBOX = "106.675,10.750,106.835,10.900"  # same corridor as infrastructure/otp (stations + ~2 km)
OSM_LICENSE = "ODbL-1.0"
OSM_ATTRIBUTION = "© OpenStreetMap contributors"

# slug, name (vi), name (en), sort order. Order is also the priority for the primary category.
CATEGORIES = [
    ("museum", "Bảo tàng", "Museums", 10),
    ("historic", "Di tích lịch sử", "Historic sites", 20),
    ("attraction", "Điểm tham quan", "Attractions", 30),
    ("worship", "Nơi thờ tự", "Places of worship", 40),
    ("park", "Công viên", "Parks", 50),
    ("market", "Chợ và mua sắm", "Markets and shopping", 60),
    ("food", "Ăn uống", "Food", 70),
    ("cafe", "Cà phê", "Cafés", 80),
    ("entertainment", "Văn hoá và giải trí", "Culture and entertainment", 90),
]
CATEGORY_SLUGS = [c[0] for c in CATEGORIES]

# OSM tag -> category slug. "historic": any value.
TAG_RULES = {
    ("tourism", "museum"): "museum", ("tourism", "gallery"): "museum", ("amenity", "arts_centre"): "museum",
    ("historic", "*"): "historic",
    ("tourism", "attraction"): "attraction", ("tourism", "viewpoint"): "attraction", ("tourism", "artwork"): "attraction",
    ("amenity", "place_of_worship"): "worship",
    ("leisure", "park"): "park", ("leisure", "garden"): "park",
    ("amenity", "marketplace"): "market", ("shop", "mall"): "market", ("shop", "department_store"): "market",
    ("amenity", "restaurant"): "food", ("amenity", "fast_food"): "food", ("amenity", "food_court"): "food",
    ("amenity", "cafe"): "cafe",
    ("amenity", "theatre"): "entertainment", ("amenity", "cinema"): "entertainment",
}
OSMIUM_FILTER = ["nwr/historic"] + sorted({f"nwr/{k}={v}" for (k, v) in TAG_RULES if v != "*"})


def categories_for(tags):
    found = {slug for (k, v), slug in TAG_RULES.items() if k in tags and (v == "*" or tags[k] == v)}
    return [s for s in CATEGORY_SLUGS if s in found]  # priority order; first = primary


def ascii_fold(text):
    text = text.replace("đ", "d").replace("Đ", "D")
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()


def slugify(text):
    return re.sub(r"[^a-z0-9]+", "-", ascii_fold(text).lower()).strip("-")[:180] or "place"


def distance_m(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(a))


def representative_point(geometry):
    """Point as-is; for lines and polygons the mean of the first ring's vertices (fine for building-sized POIs)."""
    coords = geometry["coordinates"]
    if geometry["type"] == "Point":
        return coords[1], coords[0]
    while isinstance(coords[0][0], list):  # Polygon / MultiPolygon -> first (outer) ring
        coords = coords[0]
    ring = coords[:-1] if len(coords) > 1 and coords[0] == coords[-1] else coords
    return sum(c[1] for c in ring) / len(ring), sum(c[0] for c in ring) / len(ring)


def osm_ref(export_id):
    """osmium --add-unique-id=type_id: n123/w123/r123, or a<k> for areas (k = 2*way_id or 2*relation_id+1)."""
    kind, num = export_id[0], int(export_id[1:])
    if kind == "a":
        return f"way/{num // 2}" if num % 2 == 0 else f"relation/{(num - 1) // 2}"
    return {"n": "node", "w": "way", "r": "relation"}[kind] + f"/{num}"


def address(tags):
    street = " ".join(x for x in (tags.get("addr:housenumber"), tags.get("addr:street")) if x)
    parts = [street] + [tags.get(k) for k in ("addr:subdistrict", "addr:district", "addr:city")]
    return ", ".join(p for p in parts if p) or tags.get("addr:full")


def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return repr(value)
    return "'" + str(value).replace("'", "''") + "'"


def load_stations():
    with STOPS.open(encoding="utf-8") as f:
        return [(float(r["stop_lat"]), float(r["stop_lon"])) for r in csv.DictReader(f)]


def osm_places(osm_file, stations):
    with tempfile.TemporaryDirectory() as tmp:
        corridor, poi, geo = (Path(tmp) / n for n in ("corridor.osm.pbf", "poi.osm.pbf", "poi.geojsonseq"))
        run = lambda *a: subprocess.run(["osmium", *a], check=True, capture_output=True, text=True)
        run("extract", "-b", BBOX, "--strategy", "complete_ways", str(osm_file), "-o", str(corridor))
        run("tags-filter", str(corridor), *OSMIUM_FILTER, "-o", str(poi))
        run("export", str(poi), "-f", "geojsonseq", "--add-unique-id=type_id", "-o", str(geo))
        # Not splitlines(): it also splits on \x1e, the GeoJSONSeq record separator.
        lines = [l for l in geo.read_text(encoding="utf-8").split("\n") if l.strip("\x1e \r")]
    places, skipped = {}, {"no_name": 0, "far": 0, "no_category": 0, "duplicate": 0}
    for line in lines:
        feature = json.loads(line.lstrip("\x1e"))
        tags = feature["properties"]
        name = (tags.get("name") or "").strip()
        if not name:
            skipped["no_name"] += 1
            continue
        lat, lon = representative_point(feature["geometry"])
        if min(distance_m(lat, lon, a, b) for a, b in stations) > RADIUS_M:
            skipped["far"] += 1
            continue
        cats = categories_for(tags)
        if not cats:
            skipped["no_category"] += 1
            continue
        ref = osm_ref(feature["id"])
        if ref in places:  # a closed way is exported twice (line and area); keep the area, which comes later
            skipped["duplicate"] += 1
        places[ref] = ({
            "name": name[:200], "name_en": (tags.get("name:en") or "").strip()[:200] or None,
            "slug": f"{slugify(name)}-{ref.replace('/', '-')}", "short_description": None,
            "lat": round(lat, 7), "lon": round(lon, 7), "address": address(tags), "categories": cats,
            "source": "OSM", "source_ref": ref, "source_url": f"https://www.openstreetmap.org/{ref}",
            "license": OSM_LICENSE, "attribution": OSM_ATTRIBUTION,
        })
    return list(places.values()), skipped


def curated_places():
    """Rows of data/curated/places.csv; any invalid row stops the build with its line number."""
    required = ["name", "lat", "lon", "category", "source_url", "license", "attribution"]
    places, errors = [], []
    with CURATED.open(encoding="utf-8") as f:
        for n, row in enumerate(csv.DictReader(f), start=2):
            row = {k: (v or "").strip() for k, v in row.items()}
            missing = [k for k in required if not row.get(k)]
            if missing:
                errors.append(f"dòng {n}: thiếu {', '.join(missing)}")
                continue
            try:
                lat, lon = float(row["lat"]), float(row["lon"])
            except ValueError:
                errors.append(f"dòng {n}: lat/lon không phải số")
                continue
            if not (10.3 <= lat <= 11.2 and 106.3 <= lon <= 107.1):
                errors.append(f"dòng {n}: toạ độ ngoài TP.HCM")
            if row["category"] not in CATEGORY_SLUGS:
                errors.append(f"dòng {n}: category phải là một trong {CATEGORY_SLUGS}")
            if not row["source_url"].startswith(("https://", "http://")):
                errors.append(f"dòng {n}: source_url phải là URL")
            if len(row.get("short_description", "")) > 500:
                errors.append(f"dòng {n}: short_description quá 500 ký tự")
            places.append({
                "name": row["name"], "name_en": row.get("name_en") or None, "slug": slugify(row["name"]),
                "short_description": row.get("short_description") or None, "lat": lat, "lon": lon,
                "address": row.get("address") or None, "categories": [row["category"]],
                "source": "CURATED", "source_ref": f"curated/{slugify(row['name'])}", "source_url": row["source_url"],
                "license": row["license"], "attribution": row["attribution"],
            })
    if errors:
        sys.exit(f"{CURATED.relative_to(ROOT)} không hợp lệ:\n  " + "\n  ".join(errors))
    return places


def seed_sql(places, header):
    out = [f"-- {line}" for line in header] + ["BEGIN;", ""]
    for slug, name, name_en, order in CATEGORIES:
        out.append(f"INSERT INTO place_categories (name, name_en, slug, sort_order) VALUES ({sql(name)}, {sql(name_en)}, {sql(slug)}, {order})"
                   " ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, name_en = EXCLUDED.name_en, sort_order = EXCLUDED.sort_order;")
    out.append("")
    for p in places:
        cols = "name, name_en, slug, short_description, location, address, primary_category_id, status, source, source_ref, source_url, license, attribution"
        vals = ", ".join([
            sql(p["name"]), sql(p["name_en"]), sql(p["slug"]), sql(p["short_description"]),
            f"ST_SetSRID(ST_MakePoint({p['lon']}, {p['lat']}), 4326)::geography", sql(p["address"]),
            f"(SELECT id FROM place_categories WHERE slug = {sql(p['categories'][0])})", "'PUBLISHED'",
            sql(p["source"]), sql(p["source_ref"]), sql(p["source_url"]), sql(p["license"]), sql(p["attribution"]),
        ])
        # status is not updated: a place hidden by an editor stays hidden. Rows only change when data changed.
        synced = [c for c in cols.split(", ") if c not in ("source", "source_ref", "status")]
        updates = ", ".join(f"{c} = EXCLUDED.{c}" for c in synced)
        changed = " OR ".join(f"places.{c} IS DISTINCT FROM EXCLUDED.{c}" for c in synced)
        out.append(f"INSERT INTO places ({cols}) VALUES ({vals}) ON CONFLICT (source, source_ref) DO UPDATE SET {updates}, updated_at = now() WHERE {changed};")
        this = f"(SELECT id FROM places WHERE source = {sql(p['source'])} AND source_ref = {sql(p['source_ref'])})"
        slugs = ", ".join(sql(s) for s in p["categories"])
        # Categories follow the source: drop links to categories the place no longer has.
        out.append(f"DELETE FROM place_category_links WHERE place_id = {this} AND category_id NOT IN (SELECT id FROM place_categories WHERE slug IN ({slugs}));")
        out.append(f"INSERT INTO place_category_links (place_id, category_id) SELECT {this}, id FROM place_categories WHERE slug IN ({slugs}) ON CONFLICT DO NOTHING;")
    # Places that disappeared from their source are hidden, not deleted (keeps ids stable for links and reviews).
    for source in ("OSM", "CURATED"):
        refs = ", ".join(sql(p["source_ref"]) for p in places if p["source"] == source) or "''"
        out.append(f"UPDATE places SET status = 'HIDDEN', updated_at = now() WHERE source = {sql(source)} AND status = 'PUBLISHED'"
                   f" AND source_ref NOT IN ({refs});")
    out += ["", "COMMIT;", ""]
    return "\n".join(out)


def self_test():
    assert slugify("Chợ Bến Thành") == "cho-ben-thanh"
    assert slugify("Đường Đồng Khởi!!") == "duong-dong-khoi"
    assert slugify("???") == "place"
    assert categories_for({"amenity": "place_of_worship", "historic": "church"}) == ["historic", "worship"]
    assert categories_for({"amenity": "cafe", "shop": "bakery"}) == ["cafe"]
    assert categories_for({"amenity": "bank"}) == []
    assert representative_point({"type": "Point", "coordinates": [106.7, 10.77]}) == (10.77, 106.7)
    lat, lon = representative_point({"type": "Polygon", "coordinates": [[[0, 0], [2, 0], [2, 2], [0, 2], [0, 0]]]})
    assert (lat, lon) == (1.0, 1.0)
    assert abs(distance_m(10.770775, 106.697569, 10.775511, 106.702262) - 735) < 5  # RC-04 straight line
    assert address({"addr:housenumber": "12", "addr:street": "Lê Lợi", "addr:city": "TP.HCM"}) == "12 Lê Lợi, TP.HCM"
    assert address({}) is None
    assert osm_ref("n42") == "node/42" and osm_ref("a84") == "way/42" and osm_ref("a85") == "relation/42"
    assert sql("O'Brien") == "'O''Brien'" and sql(None) == "NULL"
    print("self-test ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    osm_files = sorted((ROOT / "data" / "raw" / "osm").glob("vietnam-*.osm.pbf"))
    if not osm_files:
        sys.exit("Thiếu data/raw/osm/vietnam-*.osm.pbf — chạy infrastructure/otp/scripts/fetch-osm.sh")
    osm_file = osm_files[-1]
    stations = load_stations()
    osm, skipped = osm_places(osm_file, stations)
    curated = curated_places()
    places = sorted(osm, key=lambda p: p["source_ref"]) + curated
    osm_md5 = hashlib.md5(osm_file.read_bytes()).hexdigest()
    built = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    header = [
        "GoWander Discover seed (ADR-028). Generated by scripts/build_discover_seed.py — do not edit by hand.",
        f"OSM: {osm_file.name} (md5 {osm_md5}), {OSM_ATTRIBUTION}, {OSM_LICENSE}. Curated: {CURATED.relative_to(ROOT)}.",
    ]
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "seed.sql").write_text(seed_sql(places, header), encoding="utf-8")
    per_cat = {s: sum(p["categories"][0] == s for p in places) for s in CATEGORY_SLUGS}
    manifest = [
        f"built_at={built}", f"osm_file={osm_file.name}", f"osm_md5={osm_md5}", f"radius_m={RADIUS_M}", f"bbox={BBOX}",
        f"stations={len(stations)}", f"osm_places={len(osm)}", f"curated_places={len(curated)}",
        f"osm_with_name_en={sum(1 for p in osm if p['name_en'])}",
        *(f"skipped_{k}={v}" for k, v in skipped.items()), *(f"primary_{k}={v}" for k, v in per_cat.items()),
    ]
    (OUT_DIR / "seed.manifest.txt").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    print("\n".join(manifest))


if __name__ == "__main__":
    main()
