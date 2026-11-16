#!/usr/bin/env python3
"""Build the guide seed SQL (M07 slice 3): the owner's dishes and stories, today bundled in the app
(mobile/shared/.../content/GuideHcm.kt, copied from docs/content/cam-nang-tp-hcm.md), moved to the server.

Output: data/content/guide-hcm.sql (idempotent upserts; rows edited in the Admin are left alone).
Load with infrastructure/postgres/db.sh seed.

    python3 scripts/build_guide_seed.py
    python3 scripts/build_guide_seed.py --self-test
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "mobile" / "shared" / "src" / "commonMain" / "kotlin" / "com" / "gowander" / "content" / "GuideHcm.kt"
OUT = ROOT / "data" / "content" / "guide-hcm.sql"
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')


def const(source, name):
    m = re.search(rf'const val {name} = "((?:[^"\\]|\\.)*)"', source)
    if not m:
        raise SystemExit(f"{name} not found in {GUIDE.name}")
    return unescape(m.group(1))


def unescape(s):
    return s.replace('\\"', '"').replace("\\$", "$").replace("\\\\", "\\")


def calls(source, list_name, ctor):
    """String literals of each `ctor(` call inside `val list_name = listOf(` ... the matching `)`."""
    start = source.index(f"val {list_name} = listOf(")
    depth, i = 0, source.index("(", start)
    for j in range(i, len(source)):
        depth += {"(": 1, ")": -1}.get(source[j], 0)
        if depth == 0:
            body = source[i + 1:j]
            break
    parts = re.split(rf"\b{ctor}\(", body)[1:]
    return [[unescape(s) for s in STRING.findall(p)] for p in parts]


def dishes(source):
    out = []
    for i, s in enumerate(calls(source, "GUIDE_DISHES", "Dish")):
        slug, name, summary, description, *shop = s
        if len(shop) % 2:
            raise SystemExit(f"{slug}: shops must be name/address pairs")
        shops = "\n".join(f"{shop[k]} | {shop[k + 1]}" for k in range(0, len(shop), 2))
        out.append(dict(slug=slug, name=name, summary=summary, description=description, shops=shops,
                        sources=const(source, "GUIDE_FOOD_SOURCE"), sort_order=(i + 1) * 10))
    return out


def stories(source):
    out = []
    for i, s in enumerate(calls(source, "GUIDE_STORIES", "Story")):
        slug, title, summary, *paragraphs, try_this, sources = s
        out.append(dict(slug=slug, title=title, summary=summary, content="\n\n".join(paragraphs), try_this=try_this,
                        sources=sources, sort_order=(i + 1) * 10))
    return out


def sql(v):
    return "NULL" if v is None else (str(v) if isinstance(v, int) else "'" + v.replace("'", "''") + "'")


def seed_sql(ds, ss, header):
    out = [f"-- {line}" for line in header] + ["BEGIN;"]
    for d in ds:
        out.append(
            "INSERT INTO food_items (name, slug, summary, description, shops, sources, sort_order, status) VALUES ("
            + ", ".join(sql(d[k]) for k in ("name", "slug", "summary", "description", "shops", "sources", "sort_order"))
            + ", 'PUBLISHED')\n  ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, summary = EXCLUDED.summary, description = EXCLUDED.description,"
            " shops = EXCLUDED.shops, sources = EXCLUDED.sources, sort_order = EXCLUDED.sort_order, updated_at = now()"
            "\n  WHERE food_items.edited_at IS NULL;")
    for s in ss:
        out.append(
            "INSERT INTO historical_stories (slug, title, summary, content, try_this, sources, sort_order, language_code, status, published_at) VALUES ("
            + ", ".join(sql(s[k]) for k in ("slug", "title", "summary", "content", "try_this", "sources", "sort_order"))
            + ", 'vi', 'PUBLISHED', now())\n  ON CONFLICT (slug) DO UPDATE SET title = EXCLUDED.title, summary = EXCLUDED.summary, content = EXCLUDED.content,"
            " try_this = EXCLUDED.try_this, sources = EXCLUDED.sources, sort_order = EXCLUDED.sort_order, updated_at = now()"
            "\n  WHERE historical_stories.edited_at IS NULL;")
    out += ["COMMIT;", ""]
    return "\n".join(out)


def self_test():
    src = '''const val GUIDE_FOOD_SOURCE = "Nguồn \\"A\\""
val GUIDE_DISHES = listOf(
    Dish("d-1", "Phở", "Tóm tắt", "Mô tả (dài)", listOf(Shop("Quán O'Brien", "1 Lê Lợi"), Shop("Quán 2", "2 Hai Bà Trưng"))),
)
val GUIDE_STORIES = listOf(
    Story("s-1", "Tiêu đề", "Tóm tắt", listOf("Đoạn 1.", "Đoạn 2."), "Thử", "Nguồn B"),
)
val OTHER = listOf(Dish("x", "y", "z", "w", listOf()))'''
    ds, ss = dishes(src), stories(src)
    assert ds == [dict(slug="d-1", name="Phở", summary="Tóm tắt", description="Mô tả (dài)",
                       shops="Quán O'Brien | 1 Lê Lợi\nQuán 2 | 2 Hai Bà Trưng", sources='Nguồn "A"', sort_order=10)], ds
    assert ss[0]["content"] == "Đoạn 1.\n\nĐoạn 2." and ss[0]["try_this"] == "Thử" and ss[0]["sources"] == "Nguồn B"
    out = seed_sql(ds, ss, ["t"])
    assert "'Quán O''Brien | 1 Lê Lợi" in out and out.count("edited_at IS NULL") == 2 and out.endswith("COMMIT;\n")
    real = GUIDE.read_text(encoding="utf-8")
    assert len(dishes(real)) == 6 and len(stories(real)) == 3, "the app guide changed: check the parser"
    print("self-test ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    source = GUIDE.read_text(encoding="utf-8")
    ds, ss = dishes(source), stories(source)
    header = ["GoWander guide seed (M07 slice 3). Generated by scripts/build_guide_seed.py — do not edit by hand.",
              f"Source: {GUIDE.relative_to(ROOT)} (owner's content, docs/content/cam-nang-tp-hcm.md). Rows edited in the Admin are kept."]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(seed_sql(ds, ss, header), encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}: {len(ds)} dishes, {len(ss)} stories")


if __name__ == "__main__":
    main()
