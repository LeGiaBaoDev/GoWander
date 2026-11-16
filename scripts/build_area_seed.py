#!/usr/bin/env python3
"""Build the ward seed SQL (ADR-038): wards/communes of Ho Chi Minh City (commune level after the 7-2025 merger)
from the downloaded OpenStreetMap extract. Usage counters are grouped by these areas.

Output: data/areas/hcmc-areas.sql (replaces admin_areas; load with infrastructure/postgres/db.sh seed).

    python3 scripts/build_area_seed.py              # needs osmium and data/raw/osm/vietnam-*.osm.pbf
    python3 scripts/build_area_seed.py --self-test
"""
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "areas" / "hcmc-areas.sql"
CITY = "Thành phố Hồ Chí Minh"  # admin_level 4 since the 7-2025 merger (with Bình Dương and Bà Rịa - Vũng Tàu)


def bbox(geometry):
    pts = [p for poly in rings(geometry) for ring in poly for p in ring]
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


def rings(geometry):
    return geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]


def overlaps(a, b):
    return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]


def relation_code(unique_id):
    """osmium --add-unique-id=type_id on areas: a<k>, k = 2*way_id or 2*relation_id+1. Boundaries are relations."""
    k = int(unique_id[1:])
    return f"r{(k - 1) // 2}" if k % 2 else f"w{k // 2}"


def rounded(geometry):
    """4 decimals (~11 m) keeps the file small; enough to tell which ward a search starts in. Neighbours share
    vertices, so they round the same way. Repeated points after rounding are dropped (rings stay closed)."""
    def ring_of(ring):
        out = []
        for x, y in ring:
            p = [round(x, 4), round(y, 4)]
            if not out or out[-1] != p:
                out.append(p)
        return out
    return {"type": "MultiPolygon", "coordinates": [[ring_of(r) for r in poly] for poly in rings(geometry)]}


def inside(point, geometry):
    """Even-odd ray casting over every ring (holes included)."""
    x, y, hit = point[0], point[1], False
    for poly in rings(geometry):
        for ring in poly:
            for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
                if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                    hit = not hit
    return hit


def in_city(ward, city):
    """Most of the ward's vertices inside the city. Vertices on the shared border can land either way; the interior
    ones decide. About 25 evenly spaced vertices are enough and keep pure-Python ray casting fast."""
    pts = [p for poly in rings(ward) for ring in poly for p in ring]
    pts = pts[::max(1, len(pts) // 25)]
    return sum(inside(p, city) for p in pts) * 2 > len(pts)


def sql(value):
    return "'" + value.replace("'", "''") + "'"


def areas_sql(wards, header):
    out = [f"-- {line}" for line in header] + ["BEGIN;", "DELETE FROM admin_areas;"]
    for code, name, geometry in wards:
        geo = json.dumps(rounded(geometry), separators=(",", ":"))
        # ST_MakeValid: rounding can make a sliver ring touch itself; CollectionExtract keeps only the polygons.
        out.append(f"INSERT INTO admin_areas (code, name, geom) VALUES ({sql(code)}, {sql(name)}, "
                   f"ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON({sql(geo)}), 4326)), 3)));")
    out += ["COMMIT;", ""]
    return "\n".join(out)


def load(osm_file):
    with tempfile.TemporaryDirectory() as tmp:
        run = lambda *a: subprocess.run(["osmium", *a], check=True, capture_output=True, text=True)
        b, l = f"{tmp}/b.osm.pbf", f"{tmp}/l.osm.pbf"
        run("tags-filter", "-O", "-o", b, str(osm_file), "r/boundary=administrative")
        run("tags-filter", "-O", "-o", l, b, "r/admin_level=4,6")
        out = run("export", "-f", "geojsonseq", "--geometry-types=polygon", "--add-unique-id=type_id", "-o", "-", l).stdout
    features = [json.loads(line.lstrip("\x1e")) for line in out.splitlines() if line.strip()]
    city = next(f["geometry"] for f in features if f["properties"].get("admin_level") == "4" and f["properties"].get("name") == CITY)
    city = rounded(city)  # fewer vertices to cast rays against
    box = bbox(city)
    wards = sorted((relation_code(f["id"]), f["properties"]["name"], f["geometry"]) for f in features
                   if f["properties"].get("admin_level") == "6" and f["properties"].get("name")
                   and overlaps(bbox(f["geometry"]), box) and in_city(f["geometry"], city))
    return wards


def self_test():
    sq = {"type": "Polygon", "coordinates": [[[0, 0], [2, 0], [2, 2], [0, 2], [0, 0]]]}
    assert bbox(sq) == (0, 0, 2, 2)
    assert overlaps((0, 0, 2, 2), (1, 1, 3, 3)) and not overlaps((0, 0, 1, 1), (2, 2, 3, 3))
    assert relation_code("a85") == "r42" and relation_code("a84") == "w42"
    assert rounded({"type": "Polygon", "coordinates": [[[106.1234567, 10.7654321]]]})["coordinates"] == [[[[106.1235, 10.7654]]]]
    assert rounded({"type": "Polygon", "coordinates": [[[1.00001, 1], [1.00002, 1], [2, 2]]]})["coordinates"] == [[[[1.0, 1], [2, 2]]]]
    assert inside((1, 1), sq) and not inside((3, 1), sq)
    holed = {"type": "Polygon", "coordinates": sq["coordinates"] + [[[0.5, 0.5], [1.5, 0.5], [1.5, 1.5], [0.5, 1.5], [0.5, 0.5]]]}
    assert not inside((1, 1), holed) and inside((0.2, 0.2), holed)
    big = {"type": "Polygon", "coordinates": [[[-1, -1], [3, -1], [3, 3], [-1, 3], [-1, -1]]]}
    far = {"type": "Polygon", "coordinates": [[[5, 5], [6, 5], [6, 6], [5, 5]]]}
    assert in_city(sq, big) and not in_city(far, big)
    out = areas_sql([("r1", "Phường Bến Thành", sq), ("r2", "Xã O'Mon", sq)], ["t"])
    assert out.startswith("-- t\nBEGIN;\nDELETE FROM admin_areas;") and out.endswith("COMMIT;\n") and "'Xã O''Mon'" in out
    assert out.count("INSERT INTO admin_areas") == 2
    print("self-test ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    osm_files = sorted((ROOT / "data" / "raw" / "osm").glob("vietnam-*.osm.pbf"))
    if not osm_files:
        sys.exit("Thiếu data/raw/osm/vietnam-*.osm.pbf — chạy infrastructure/otp/scripts/fetch-osm.sh")
    osm_file = osm_files[-1]
    wards = load(osm_file)
    md5 = hashlib.md5(osm_file.read_bytes()).hexdigest()
    header = ["GoWander wards of Ho Chi Minh City (ADR-038). Generated by scripts/build_area_seed.py — do not edit by hand.",
              f"OSM: {osm_file.name} (md5 {md5}), © OpenStreetMap contributors, ODbL-1.0. Wards: {len(wards)}."]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(areas_sql(wards, header), encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}: {len(wards)} wards, {OUT.stat().st_size // 1024} KiB")


if __name__ == "__main__":
    main()
