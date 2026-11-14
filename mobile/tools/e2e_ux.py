#!/usr/bin/env python3
"""UX journey on the Android emulator: plan -> car route -> detail -> navigation -> steps -> preview, with the
"what disappears and how does it come back" checks of docs/testing/ux-checklist.md and ux_audit.py on every screen.

    python3 -I mobile/tools/e2e_ux.py docs/evidence/ux/report.md

Needs: emulator running the debug app, the Go API on 10.0.2.2:8090 with the whole-HCMC graph. Uses a fake GPS fix
at Bến Thành. Exit 1 if a check fails. Stdlib only; waits for text instead of sleeping fixed times.
"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ux_audit  # noqa: E402

ADB = ux_audit.ADB
APP = ux_audit.PACKAGE
results = []  # (ok, check)
report = []


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def nodes():
    return list(ux_audit.dump().iter("node"))


def find(text=None, desc=None, timeout=12.0):
    """First node whose text (or content-desc) matches: exact, '^x' = starts with, '~x' = full regex. None on timeout."""
    want = text if text is not None else desc
    end = time.time() + timeout
    while True:
        for n in nodes():
            value = n.get("text") if text is not None else n.get("content-desc")
            if value and ((want.startswith("^") and value.startswith(want[1:])) or (want.startswith("~") and re.fullmatch(want[1:], value)) or value == want):
                return n
        if time.time() > end:
            return None
        time.sleep(0.8)


def tap(text=None, desc=None, timeout=12.0):
    n = find(text, desc, timeout)
    if n is None:
        raise RuntimeError(f"not found: {text or desc}")
    x1, y1, x2, y2 = ux_audit.bounds(n)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.0)


def check(ok, what):
    results.append((bool(ok), what))
    print(("PASS " if ok else "FAIL ") + what)


def gone(text=None, desc=None):
    return find(text, desc, timeout=2.0) is None


def audit(screen):
    density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1])
    problems, n = ux_audit.audit(ux_audit.dump(), density, round(24 * density / 160))
    report.append(f"### {screen}\n\n{n} phần tử bấm được · " + ("**ĐẠT**" if not problems else f"**{len(problems)} vấn đề**") + "\n")
    report.extend(f"- {c} `{w}`: {d}" for c, w, d in problems)
    report.append("")
    check(not problems, f"[{screen}] T1–T4: kích thước, nhãn, chồng lấn, thanh trạng thái")


def journey():
    adb("emu", "geo", "fix", "106.6980", "10.7726")
    adb("shell", "am", "force-stop", APP)
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    check(find("Bạn muốn đến đâu?", timeout=25) is not None, "Mở app: thấy ô tìm kiếm trên bản đồ")
    audit("Bản đồ chính")

    # Map view panel: opens, closes after a choice, closes on an outside tap.
    tap(desc="Loại bản đồ")
    check(find("3D") is not None, "Bấm 'Loại bản đồ': hiện 2D / 3D / Vệ tinh")
    tap("3D")
    check(gone("3D"), "Chọn 3D: bảng tự đóng")
    tap(desc="Loại bản đồ")
    tap(desc="Đóng bảng chọn")
    check(gone("3D"), "Bấm ra ngoài bảng: bảng đóng")
    tap(desc="Loại bản đồ")
    tap("2D")

    # Nearby sheet: collapses, and its title row brings it back.
    if find(desc="Thu gọn gợi ý", timeout=6) is not None:
        tap(desc="Thu gọn gợi ý")
        check(find(desc="Hiện gợi ý gần bạn") is not None, "Thu gọn 'Gợi ý gần bạn': còn dòng tiêu đề để mở lại")
        tap(desc="Hiện gợi ý gần bạn")
        check(find(desc="Thu gọn gợi ý") is not None, "Bấm tiêu đề: gợi ý hiện lại")

    # Plan tab: destination by search, car mode.
    tap("Tìm tuyến")
    check(find("Công cộng") is not None and find("Đi bộ") is not None, "Tab Tìm tuyến: thấy 4 tab phương tiện")
    audit("Tìm tuyến (M3)")
    tap("Điểm đến")
    tap("Gõ tên ga hoặc địa điểm (không cần dấu)")
    adb("shell", "input", "text", "truong")
    time.sleep(1.5)
    adb("shell", "input", "text", "%sdang%sque")
    tap("Trương Đăng Quế", timeout=15)
    tap("Ô tô")
    first = find(r"~\d+ phút", timeout=20)
    check(first is not None, "Chọn tab Ô tô: có kết quả ngay, không phải bấm thêm")
    audit("Kết quả")
    check(find("Nhanh nhất", timeout=2) is None, "Ô tô: không hiện chip/nhãn 'Nhanh nhất / Ít đi bộ' vô nghĩa")

    # Route detail: full-screen map, the sheet expands and collapses.
    tap(first.get("text"))
    check(find("Bắt đầu") is not None, "Mở chi tiết: thấy nút Bắt đầu")
    check(gone("Discover"), "Chi tiết tuyến: thanh tab dưới ẩn (màn con)")
    audit("Chi tiết tuyến")
    tap(desc="Mở rộng")
    check(find(desc="Thu gọn") is not None, "Bấm phần đầu thẻ: thẻ mở rộng, nhãn đổi thành 'Thu gọn'")
    tap(desc="Thu gọn")
    tap("Xe đạp")
    check(find(r"~Đạp xe \d+ phút", timeout=20) is not None and find("Bắt đầu") is not None,
          "Đổi sang Xe đạp ngay trong Chi tiết tuyến: có tuyến xe đạp, vẫn ở màn chi tiết")
    tap("Ô tô")
    check(find(r"~Lái xe \d+ phút", timeout=20) is not None, "Đổi lại Ô tô trong Chi tiết tuyến")

    # Navigation: banner, pause on pan + recenter, steps, preview, back stack.
    tap("Bắt đầu")
    check(find("Thoát") is not None and find(r"~(Bắt đầu|Rẽ|Chếch|Đi thẳng|Tiếp tục).*", timeout=8) is not None, "Bắt đầu: băng chỉ hướng + nút Thoát")
    audit("Dẫn đường")
    adb("shell", "input", "swipe", "540", "1100", "300", "900", "300")
    check(find("Căn giữa lại", timeout=6) is not None, "Kéo bản đồ khi dẫn đường: hiện 'Căn giữa lại'")
    tap("Căn giữa lại")
    check(gone("Căn giữa lại"), "Bấm 'Căn giữa lại': nút biến mất, bản đồ đi theo lại")
    tap("Đường đi")
    step = find(r"~(Rẽ|Chếch).*")
    check(step is not None, "Đường đi: danh sách các bước rẽ")
    audit("Đường đi")
    tap(step.get("text"))
    check(find("‹ Quay lại tuyến đường") is not None, "Chạm một bước: mở Xem trước")
    audit("Xem trước tuyến")
    before = find(r"~Bước \d+/\d+").get("text")
    tap("Sau ›")
    after = find(r"~Bước \d+/\d+").get("text")
    check(before != after, f"Nút Sau: {before} -> {after}")
    card = find(r"~Bước \d+/\d+")
    y = (ux_audit.bounds(card)[1] + ux_audit.bounds(card)[3]) // 2
    adb("shell", "input", "swipe", "900", str(y), "200", str(y), "250")
    time.sleep(1.5)
    swiped = find(r"~Bước \d+/\d+").get("text")
    check(swiped != after, f"Vuốt trái trên thẻ: {after} -> {swiped}")
    adb("shell", "input", "keyevent", "4")
    # The steps screen: its title, and no "Thoát" (that would mean we jumped straight back to navigation).
    check(find("Đường đi", timeout=4) is not None and gone("Thoát"), "Back trong Xem trước (mở từ Đường đi): về Đường đi")
    tap(find(r"~(Rẽ|Chếch).*").get("text"))
    tap("‹ Quay lại tuyến đường")
    check(find("Thoát", timeout=4) is not None, "'Quay lại tuyến đường': về màn dẫn đường")
    tap("Thoát")
    check(find("Bắt đầu") is not None, "Thoát: về chi tiết tuyến, tuyến vẫn còn")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")
    check(find("Discover", timeout=6) is not None, "Back về màn tab: thanh tab dưới hiện lại")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "ux-report.md"
    try:
        journey()
    except RuntimeError as e:
        check(False, f"hành trình dừng: {e}")
    passed = sum(ok for ok, _ in results)
    lines = [f"# UX E2E (Android emulator) — {time.strftime('%Y-%m-%d %H:%M')}", "",
             f"**{passed}/{len(results)} kiểm tra đạt.** Tiêu chí: `docs/testing/ux-checklist.md`.", "",
             "## Hành trình", ""] + [f"- {'✅' if ok else '❌'} {what}" for ok, what in results] + ["", "## Kiểm tra từng màn (ux_audit.py)", ""] + report
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{passed}/{len(results)} -> {out}")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
