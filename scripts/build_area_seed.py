#!/usr/bin/env python3
"""Build the area seed SQL (ADR-038, amended 16-11-2026): Vietnam's provinces and centrally-run cities
(admin_level 4) from the downloaded OpenStreetMap extract. Usage counters are grouped by these areas.

A province whose own boundary ring is broken in OSM (Khánh Hòa in the 2026-11 extract: osmium cannot build it) is
rebuilt as the union of its "subarea" communes, which are all mapped.

OSM is midway through the 7-2025 merger: some old provinces are still mapped next to the new ones that contain them
(e.g. Hòa Bình inside Phú Thọ), and a few are tagged "... cũ". Those tagged "cũ" are dropped here, old provinces lying
inside a larger one are deleted when the seed loads, and the counter still picks the largest area containing a point.

Output: data/areas/vn-provinces.sql (replaces admin_areas) and data/areas/hcm-wards.sql (replaces city_wards: the 168
wards/communes of Ho Chi Minh City, the subareas of its OSM relation, for the Admin's "Khách đi đâu" map; owner
16-11-2026). Load both with infrastructure/postgres/db.sh seed.

    python3 scripts/build_area_seed.py              # needs osmium and data/raw/osm/vietnam-*.osm.pbf
    python3 scripts/build_area_seed.py --self-test
"""
import hashlib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "areas" / "vn-provinces.sql"
WARDS_OUT = ROOT / "data" / "areas" / "hcm-wards.sql"
HCMC = "Thành phố Hồ Chí Minh"
OLD_SUFFIX = " cũ"  # OSM keeps some pre-merger provinces under this name


def rings(geometry):
    return geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]


def relation_code(unique_id):
    """osmium --add-unique-id=type_id on areas: a<k>, k = 2*way_id or 2*relation_id+1. Boundaries are relations."""
    k = int(unique_id[1:])
    return f"r{(k - 1) // 2}" if k % 2 else f"w{k // 2}"


def rounded(geometry, digits=3):
    """3 decimals (~110 m) keeps the file small; plenty to tell which province a search starts in. Neighbours share
    vertices, so they round the same way. Repeated points after rounding are dropped (rings stay closed)."""
    def ring_of(ring):
        out = []
        for x, y in ring:
            p = [round(x, digits), round(y, digits)]
            if not out or out[-1] != p:
                out.append(p)
        return out
    return {"type": "MultiPolygon", "coordinates": [[ring_of(r) for r in poly] for poly in rings(geometry)]}


def is_province(tags):
    name = tags.get("name", "")
    return tags.get("admin_level") == "4" and name.startswith(("Tỉnh ", "Thành phố ")) and not name.endswith(OLD_SUFFIX)


def sql(value):
    return "'" + value.replace("'", "''") + "'"


def areas_sql(areas, header, unions=()):
    """unions: (code, name, [geometry, ...]) inserted as the union of the parts."""
    out = [f"-- {line}" for line in header] + ["BEGIN;", "DELETE FROM admin_areas;"]
    valid = "ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON({}), 4326)), 3)"
    for code, name, parts in unions:
        values = ", ".join("(" + valid.format(sql(json.dumps(rounded(g), separators=(",", ":")))) + ")" for g in parts)
        out.append(f"INSERT INTO admin_areas (code, name, geom) SELECT {sql(code)}, {sql(name)}, "
                   f"ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(g)), 3)) FROM (VALUES {values}) AS v(g);")
    for code, name, geometry in areas:
        geo = json.dumps(rounded(geometry), separators=(",", ":"))
        # ST_MakeValid: rounding can make a sliver ring touch itself; CollectionExtract keeps only the polygons.
        out.append(f"INSERT INTO admin_areas (code, name, geom) VALUES ({sql(code)}, {sql(name)}, "
                   f"ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON({sql(geo)}), 4326)), 3)));")
    # Pre-merger provinces still mapped inside their successor (Hòa Bình in Phú Thọ, ...) would only confuse the filter.
    out += ["DELETE FROM admin_areas a WHERE EXISTS (SELECT 1 FROM admin_areas b WHERE b.code <> a.code",
            "  AND ST_Area(b.geom) > ST_Area(a.geom) AND ST_Contains(b.geom, ST_PointOnSurface(a.geom)));", "COMMIT;", ""]
    return "\n".join(out)


def wards_sql(wards, header):
    """wards: (code, name, geometry). 4 decimals (~11 m): wards are small and drawn on a city map."""
    out = [f"-- {line}" for line in header] + ["BEGIN;", "DELETE FROM city_wards;"]
    for code, name, geometry in wards:
        geo = json.dumps(rounded(geometry, 4), separators=(",", ":"))
        out.append(f"INSERT INTO city_wards (code, name, geom) VALUES ({sql(code)}, {sql(name)}, "
                   f"ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON({sql(geo)}), 4326)), 3)));")
    return "\n".join(out + ["COMMIT;", ""])


def opl_relations(text):
    """id -> (tags, subarea relation ids) from osmium's OPL output."""
    out = {}
    for line in text.splitlines():
        rid, _, rest = line.partition(" ")
        tags = re.search(r" T(\S*)", " " + rest)
        members = re.search(r" M(\S*)", " " + rest)
        tagmap = dict(kv.split("=", 1) for kv in (tags.group(1).split(",") if tags and tags.group(1) else []) if "=" in kv)
        tagmap = {k: opl_unescape(v) for k, v in tagmap.items()}
        subs = [m.split("@")[0][1:] for m in (members.group(1).split(",") if members and members.group(1) else []) if m.startswith("r") and m.endswith("@subarea")]
        out[rid[1:]] = (tagmap, subs)
    return out


def opl_unescape(v):
    """OPL writes special characters as %<hex>%."""
    return re.sub(r"%([0-9a-f]+)%", lambda m: chr(int(m.group(1), 16)), v)


def load(osm_file):
    with tempfile.TemporaryDirectory() as tmp:
        run = lambda *a: subprocess.run(["osmium", *a], check=True, capture_output=True, text=True)
        b, l = f"{tmp}/b.osm.pbf", f"{tmp}/l.osm.pbf"
        run("tags-filter", "-O", "-o", b, str(osm_file), "r/boundary=administrative")
        run("tags-filter", "-O", "-o", l, b, "r/admin_level=4,6")
        out = run("export", "-f", "geojsonseq", "--geometry-types=polygon", "--add-unique-id=type_id", "-o", "-", l).stdout
        opl = run("tags-filter", "-R", "-f", "opl", "-o", "-", l, "r/admin_level=4").stdout
    features = [json.loads(line.lstrip("\x1e")) for line in out.splitlines() if line.strip()]
    built = {relation_code(f["id"])[1:]: f for f in features}
    relations = opl_relations(opl)
    provinces = sorted((relation_code(f["id"]), f["properties"]["name"], f["geometry"]) for f in features if is_province(f["properties"]))
    unions = []
    for rid, (tags, subs) in sorted(relations.items()):
        if is_province(tags) and rid not in built:
            parts = [built[s]["geometry"] for s in subs if s in built]
            if not parts:
                sys.exit(f"{tags.get('name')} (r{rid}): no boundary and no mapped subareas")
            print(f"{tags['name']}: boundary broken in OSM, built from {len(parts)}/{len(subs)} subareas")
            unions.append((f"r{rid}", tags["name"], parts))
    wards = []
    for rid, (tags, subs) in relations.items():
        if tags.get("name") == HCMC:
            wards = sorted((f"r{s}", built[s]["properties"]["name"], built[s]["geometry"]) for s in subs if s in built)
            if len(wards) < len(subs):
                print(f"{HCMC}: {len(subs) - len(wards)} of {len(subs)} wards have no buildable boundary")
    return provinces, unions, wards


def self_test():
    sq = {"type": "Polygon", "coordinates": [[[0, 0], [2, 0], [2, 2], [0, 2], [0, 0]]]}
    assert relation_code("a85") == "r42" and relation_code("a84") == "w42"
    assert rounded({"type": "Polygon", "coordinates": [[[106.1234567, 10.7654321]]]})["coordinates"] == [[[[106.123, 10.765]]]]
    assert rounded({"type": "Polygon", "coordinates": [[[1.0001, 1], [1.0002, 1], [2, 2]]]})["coordinates"] == [[[[1.0, 1], [2, 2]]]]
    assert is_province({"admin_level": "4", "name": "Thành phố Hồ Chí Minh"}) and is_province({"admin_level": "4", "name": "Tỉnh Gia Lai"})
    assert not is_province({"admin_level": "4", "name": "Tỉnh An Giang cũ"}) and not is_province({"admin_level": "6", "name": "Tỉnh X"})
    out = areas_sql([("r1", "Thành phố Hồ Chí Minh", sq), ("r2", "Tỉnh O'Lâm", sq)], ["t"])
    assert out.startswith("-- t\nBEGIN;\nDELETE FROM admin_areas;") and out.endswith("COMMIT;\n") and "'Tỉnh O''Lâm'" in out
    assert out.count("INSERT INTO admin_areas") == 2 and out.index("ST_PointOnSurface") < out.index("COMMIT;")
    u = areas_sql([], ["t"], [("r9", "Tỉnh Khánh Hòa", [sq, sq])])
    assert "ST_UnaryUnion(ST_Collect(g))" in u and u.count("ST_GeomFromGeoJSON") == 2 and "'Tỉnh Khánh Hòa'" in u
    w = wards_sql([("r5", "Phường Bến Thành", {"type": "Polygon", "coordinates": [[[106.69871, 10.77251], [106.7, 10.77], [106.69871, 10.77251]]]})], ["t"])
    assert w.startswith("-- t\nBEGIN;\nDELETE FROM city_wards;") and "106.6987" in w and "'Phường Bến Thành'" in w and w.endswith("COMMIT;\n")
    rel = opl_relations("r42 v1 dV c0 t2026-01-01T00:00:00Z i0 u Tadmin_level=4,name=T%1ec9%nh%20%Kh%e1%nh Mw1@outer,r7@subarea,r8@subarea,n1@label")
    assert rel["42"] == ({"admin_level": "4", "name": "Tỉnh Khánh"}, ["7", "8"]), rel
    print("self-test ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    osm_files = sorted((ROOT / "data" / "raw" / "osm").glob("vietnam-*.osm.pbf"))
    if not osm_files:
        sys.exit("Thiếu data/raw/osm/vietnam-*.osm.pbf — chạy infrastructure/otp/scripts/fetch-osm.sh")
    osm_file = osm_files[-1]
    areas, unions, wards = load(osm_file)
    md5 = hashlib.md5(osm_file.read_bytes()).hexdigest()
    header = ["GoWander provinces of Vietnam (ADR-038). Generated by scripts/build_area_seed.py — do not edit by hand.",
              f"OSM: {osm_file.name} (md5 {md5}), © OpenStreetMap contributors, ODbL-1.0. Areas: {len(areas) + len(unions)} ({len(unions)} rebuilt from subareas)."]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(areas_sql(areas, header, unions), encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}: {len(areas) + len(unions)} areas, {OUT.stat().st_size // 1024} KiB")
    if not wards:
        sys.exit(f"{HCMC}: no wards found")
    WARDS_OUT.write_text(wards_sql(wards, ["GoWander wards and communes of Ho Chi Minh City (Admin map). Generated by scripts/build_area_seed.py — do not edit by hand.",
        f"OSM: {osm_file.name} (md5 {md5}), © OpenStreetMap contributors, ODbL-1.0. Wards: {len(wards)}."]), encoding="utf-8")
    print(f"{WARDS_OUT.relative_to(ROOT)}: {len(wards)} wards, {WARDS_OUT.stat().st_size // 1024} KiB")


if __name__ == "__main__":
    main()
