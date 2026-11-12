#!/usr/bin/env python3
"""Chạy các route case của M02 trên OTP đang chạy (serve.sh start) và ghi PASS/FAIL.

Cách dùng (từ gốc repo):
    python3 -I infrastructure/otp/scripts/run_route_cases.py [--label nen] [--repeat 1] [--port 8088]

Đọc data/test-cases/route-cases.csv (cột expect_*), gửi truy vấn queries/plan-connection.graphql,
so kết quả với kỳ vọng, ghi infrastructure/otp/results/route-cases-<ngày>-<label>.md.
Chỉ dùng thư viện chuẩn. Thoát mã 1 nếu có case FAIL, 2 nếu không gọi được OTP.
"""
import argparse
import csv
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OTP = ROOT / "infrastructure" / "otp"
CASES = ROOT / "data" / "test-cases" / "route-cases.csv"
QUERY = (OTP / "queries" / "plan-connection.graphql").read_text(encoding="utf-8")
FEED_PREFIX = "1:"  # OTP gắn feed id "1" cho feed duy nhất; gtfsId của ga là "1:L1-01"
# Thứ trong tuần -> ngày cụ thể trong thời hạn feed (01-06 → 31-12-2026): tuần 16–22/11/2026.
WEEK_START = date(2026, 11, 16)  # thứ Hai
DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
TOLERANCE_S = 5


def post(url, variables):
    body = json.dumps({"query": QUERY, "variables": variables}).encode()
    req = urllib.request.Request(url, body, {"Content-Type": "application/json"})
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.load(resp)
        status = resp.status
    return status, data, (time.perf_counter() - t0) * 1000


def parse_dt(s):
    return datetime.fromisoformat(s)


def transit_legs(it):
    return [leg for leg in it["legs"] if leg.get("transitLeg")]


def summarize(it):
    parts = []
    for leg in it["legs"]:
        if leg.get("transitLeg"):
            parts.append(f'{leg["mode"]} {(leg["route"] or {}).get("shortName", "")} '
                         f'{leg["from"]["name"]}→{leg["to"]["name"]} {parse_dt(leg["start"]["scheduledTime"]):%H:%M:%S}')
        else:
            parts.append(f'{leg["mode"]} {round(leg["distance"] or 0)} m')
    return f'{parse_dt(it["start"]):%a %H:%M:%S}→{parse_dt(it["end"]):%H:%M:%S}: ' + " + ".join(parts)


def check(case, depart_at, status, data):
    """Trả (pass: bool, ghi chú). Chỉ kiểm những gì cột expect_* yêu cầu."""
    kind = case["expect_kind"]
    errors = data.get("errors") or []
    pc = (data.get("data") or {}).get("planConnection")
    codes = [e["code"] for e in (pc or {}).get("routingErrors", [])]
    its = [e["node"] for e in (pc or {}).get("edges", [])]
    notes = [f"HTTP {status}", f"{len(its)} hành trình"]
    if codes:
        notes.append("routingErrors=" + ",".join(codes))
    if errors:
        notes.append("errors=" + "; ".join(e.get("message", "")[:100] for e in errors))

    if kind == "invalid_input":
        return bool(errors) and not its, notes
    if kind == "outside_bounds":
        return "OUTSIDE_BOUNDS" in codes and not its, notes
    if errors:
        return False, notes
    if its:
        notes.append("đầu tiên: " + summarize(its[0]))
    if kind == "walk_only":
        return any(all(leg["mode"] == "WALK" for leg in it["legs"]) for it in its), notes
    if kind == "no_transit_same_day":
        same_day = [it for it in its
                    if any(parse_dt(leg["start"]["scheduledTime"]).date() == depart_at.date() for leg in transit_legs(it))]
        return not same_day, notes
    if kind != "transit":
        notes.append(f"expect_kind lạ: {kind}")
        return False, notes

    # Tìm hành trình đi đúng ga lên/xuống trên L1.
    board, alight = FEED_PREFIX + case["expect_board_stop"], FEED_PREFIX + case["expect_alight_stop"]
    match = None
    for it in its:
        tl = transit_legs(it)
        if tl and tl[0]["from"]["stop"] and tl[-1]["to"]["stop"] \
                and tl[0]["from"]["stop"]["gtfsId"] == board and tl[-1]["to"]["stop"]["gtfsId"] == alight:
            match = it
            break
    if not match:
        notes.append(f"không có hành trình lên {case['expect_board_stop']} xuống {case['expect_alight_stop']}")
        return False, notes
    ok = True
    tl = transit_legs(match)
    in_vehicle = round(sum(leg["duration"] for leg in tl))
    notes.append(f"trên tàu {in_vehicle} s")
    if case["expect_in_vehicle_s"] and abs(in_vehicle - int(case["expect_in_vehicle_s"])) > TOLERANCE_S:
        ok = False
        notes.append(f"kỳ vọng {case['expect_in_vehicle_s']} s")
    access = sum(leg["duration"] for leg in match["legs"][:match["legs"].index(tl[0])])
    first_dep = parse_dt(tl[0]["start"]["scheduledTime"])
    wait = round((first_dep - depart_at).total_seconds() - access)
    notes.append(f"chờ {wait} s")
    if case["expect_max_wait_s"] and wait > int(case["expect_max_wait_s"]):
        ok = False
        notes.append(f"> tối đa {case['expect_max_wait_s']} s")
    if case["expect_first_departure"] and (
            first_dep.strftime("%H:%M:%S") != case["expect_first_departure"] or first_dep.date() != depart_at.date()):
        ok = False
        notes.append(f"chuyến đầu {first_dep:%Y-%m-%d %H:%M:%S}, kỳ vọng {case['expect_first_departure']}")
    notes.append("có dữ liệu thời gian thực" if any(leg["start"]["estimated"] for leg in tl) else "theo lịch")
    if not all((leg["legGeometry"] or {}).get("points") for leg in tl):
        ok = False
        notes.append("thiếu geometry")
    return ok, notes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8088)
    ap.add_argument("--label", default="run")
    ap.add_argument("--repeat", type=int, default=1, help="số lần đo độ trễ mỗi case (sau 1 lần khởi động)")
    args = ap.parse_args()
    url = f"http://127.0.0.1:{args.port}/otp/gtfs/v1"

    serve_manifest = OTP / "manifests" / "metro-l1-assumed.serve.txt"
    meta = dict(line.split("=", 1) for line in serve_manifest.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.startswith("#"))

    rows, failed = [], 0
    for case in csv.DictReader(CASES.open(encoding="utf-8", newline="")):
        day = WEEK_START + timedelta(days=DAYS.index(case["depart_day"]))
        depart_at = datetime.fromisoformat(f"{day}T{case['depart_time']}:00+07:00")
        variables = {"fromLat": float(case["from_lat"]), "fromLon": float(case["from_lon"]),
                     "toLat": float(case["to_lat"]), "toLon": float(case["to_lon"]),
                     "departAt": depart_at.isoformat(), "first": 3}
        try:
            status, data, _ = post(url, variables)  # lần khởi động, cũng là lần dùng để kiểm
            times = [post(url, variables)[2] for _ in range(args.repeat)]
        except (urllib.error.URLError, OSError) as e:
            print(f"LỖI: không gọi được {url}: {e}. Chạy serve.sh start trước.", file=sys.stderr)
            return 2
        ok, notes = check(case, depart_at, status, data)
        failed += not ok
        p95 = sorted(times)[max(0, round(0.95 * len(times)) - 1)]
        rows.append((case["case_id"], case["description"], f"{case['depart_day'][:3]} {day:%d-%m} {case['depart_time']}",
                     case["expect_kind"], "PASS" if ok else "FAIL", "; ".join(notes),
                     statistics.median(times), p95, max(times)))
        print(f'{case["case_id"]} {"PASS" if ok else "FAIL"}  {"; ".join(notes)}')

    out_dir = OTP / "results"
    out_dir.mkdir(exist_ok=True)
    out = out_dir / f"route-cases-{date.today():%Y-%m-%d}-{args.label}.md"
    lines = [
        f"# Route cases M02 — {args.label}",
        "",
        "> Feed **giả định** (ADR-020): chỉ chứng minh công nghệ, không chứng minh TP.HCM sẵn sàng.",
        "",
        f"- Chạy lúc: {datetime.now().astimezone():%Y-%m-%d %H:%M:%S %z}",
        f"- OTP: {meta.get('otp_version', 'UNKNOWN')}, graph `{meta.get('graph_obj_sha256', 'UNKNOWN')[:16]}…` "
        f"(dựng {meta.get('graph_built_at', 'UNKNOWN')}), feed `{meta.get('gtfs_feed_version', 'UNKNOWN')}`",
        f"- Ngày dùng cho thứ trong tuần: tuần {WEEK_START:%d-%m-%Y} → {WEEK_START + timedelta(days=6):%d-%m-%Y}, giờ +07:00",
        f"- Độ trễ: đo từ client trên cùng máy, {args.repeat} lần mỗi case sau 1 lần khởi động",
        f"- Kết quả: {len(rows) - failed}/{len(rows)} PASS",
        "",
        "| Case | Mô tả | Lúc đi | Kỳ vọng | Kết quả | Chi tiết | p50 ms | p95 ms | max ms |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for r in rows:
        cells = [f"{x:.0f}" if isinstance(x, float) else str(x).replace("|", "/") for x in r]
        lines.append("| " + " | ".join(cells) + " |")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(rows) - failed}/{len(rows)} PASS → {out.relative_to(ROOT)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
