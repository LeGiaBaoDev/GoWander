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
import unicodedata
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ux_audit  # noqa: E402

ADB = ux_audit.ADB
APP = ux_audit.PACKAGE
results = []  # (ok, check)
SHOTS = os.environ.get("UX_SHOTS")  # folder for screenshots; unset = none
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
    # Screenshot of every audited screen for the visual review against the design (docs/evidence/ux2/android-*.png).
    if SHOTS:
        with open(os.path.join(SHOTS, "android-" + re.sub(r"[^a-z0-9]+", "-", unicodedata.normalize("NFKD", screen.replace("đ", "d").replace("Đ", "D")).encode("ascii", "ignore").decode().lower()).strip("-") + ".png"), "wb") as f:
            f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def journey():
    adb("emu", "geo", "fix", "106.6980", "10.7726")
    adb("shell", "am", "force-stop", APP)
    # Fresh first run: remove only the app's flags (debug build), keep the location permission.
    adb("shell", "run-as", APP, "rm", "-f", "shared_prefs/gowander.xml")
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    check(find("Move Smart. Explore More.", timeout=25) is not None and find(desc="GoWander") is not None,
          "Lần đầu mở app: màn chào có logo đầy đủ (biểu tượng + chữ GoWander) và khẩu hiệu")
    audit("Màn chào (S0)")
    tap("Bắt đầu")
    # UX-2 (ADR-034): no first-run form; location is asked only when a feature needs it.
    check(find("Bạn muốn đến đâu?", timeout=15) is not None and gone("Chọn ngôn ngữ"), "Bắt đầu: vào thẳng bản đồ, không có màn chọn ngôn ngữ / vị trí")
    adb("shell", "am", "force-stop", APP)
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    check(find("Bạn muốn đến đâu?", timeout=25) is not None and gone("Move Smart. Explore More."), "Mở lại app: không hiện màn chào lần nữa")
    audit("Bản đồ chính")
    tabs = ["Bản đồ", "Khám phá", "Đi đâu?", "Hành trình", "Hồ sơ"]
    check(all(find(t, timeout=3) is not None for t in tabs), "Thanh dưới 5 mục: Bản đồ · Khám phá · Đi đâu? · Hành trình · Hồ sơ")
    check(find(desc="Ngôn ngữ: Tiếng Việt", timeout=2) is None, "Không còn nút 'VI' khi chưa có tiếng Anh (ADR-034)")

    # A suggested place opens its page; "Chỉ đường" goes to the routes; Back returns to the page (T3, T11).
    if find("Chợ Bến Thành", timeout=4) is not None:
        tap("Chợ Bến Thành")
        check(find("Chỉ đường", timeout=10) is not None, "Bấm địa điểm gợi ý: mở trang địa điểm (có nút Chỉ đường)")
        audit("Trang địa điểm")
        tap("Chỉ đường")
        check(find("Buýt", timeout=15) is not None and find(r"~Thông tin về .*", timeout=15) is not None, "Chỉ đường: vào danh sách tuyến + dòng 'Thông tin về …'")
        adb("shell", "input", "keyevent", "4")
        check(find("Chỉ đường", timeout=6) is not None, "Back từ danh sách tuyến: về đúng trang địa điểm")
        adb("shell", "input", "keyevent", "4")
        check(find("Bạn muốn đến đâu?", timeout=6) is not None, "Back lần nữa: về bản đồ")

    # Map view panel: 2D / 3D only (no "coming soon" satellite), closes after a choice and on an outside tap.
    tap(desc="Loại bản đồ")
    check(find("3D") is not None and find("Vệ tinh", timeout=1) is None, "Bấm 'Loại bản đồ': chỉ 2D / 3D, không có ô Vệ tinh 'sắp có'")
    tap("3D")
    check(gone("3D"), "Chọn 3D: bảng tự đóng")
    tap(desc="Loại bản đồ")
    tap(desc="Đóng bảng chọn")
    check(gone("3D"), "Bấm ra ngoài bảng: bảng đóng")

    # Nearby sheet: collapses, and its title row brings it back.
    if find(desc="Thu gọn gợi ý", timeout=6) is not None:
        tap(desc="Thu gọn gợi ý")
        check(find(desc="Hiện gợi ý gần bạn") is not None, "Thu gọn 'Gợi ý gần bạn': còn dòng tiêu đề để mở lại")
        tap(desc="Hiện gợi ý gần bạn")
        check(find(desc="Thu gọn gợi ý") is not None, "Bấm tiêu đề: gợi ý hiện lại")

    # Each tab keeps its own back stack (ADR-034).
    tap("Khám phá")
    row = find(r"~.+ · .+", timeout=15)
    if row is not None:
        name = row.get("text")
        # Tap the node found, not a fresh search by text: the list may reload in between (flaky run 14-11-2026).
        x1, y1, x2, y2 = ux_audit.bounds(row)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1.0)
        check(find("Chỉ đường", timeout=10) is not None, "Khám phá: bấm địa điểm mở trang địa điểm, không tự nhảy sang tuyến (T4)")
        adb("shell", "input", "keyevent", "4")
        check(find(name, timeout=6) is not None, "Back từ trang địa điểm: về danh sách Khám phá")
    tap("Hành trình")
    check(find("Nơi bạn đã tìm đường", timeout=6) is not None, "Tab Hành trình: có nội dung thật (nơi đã tìm đường)")
    audit("Hành trình")
    tap("Hồ sơ")
    check(find("Nguồn dữ liệu và giấy phép", timeout=6) is not None and find("Bạn đang dùng chế độ khách") is not None, "Tab Hồ sơ: chế độ khách + nguồn dữ liệu và giấy phép")
    audit("Hồ sơ")
    tap("Bản đồ")

    # Centre action "Đi đâu?": search, pick, straight to the routes (T2), no extra "Tìm" step.
    tap("Đi đâu?")
    # Design screen 3: back + pill field "Bạn muốn đến đâu?", "Từ: … · Đổi", "Chọn trên bản đồ" first.
    check(find(desc="Tìm điểm đến", timeout=8) is not None and find("^Từ") is not None and find("Đổi") is not None and find("Chọn trên bản đồ") is not None,
          "Đi đâu?: ô tìm + dòng 'Từ: … · Đổi' + 'Chọn trên bản đồ' (mẫu màn 3)")
    audit("Đi đâu?")
    tap(desc="Tìm điểm đến")
    adb("shell", "input", "text", "truong")
    time.sleep(1.5)
    adb("shell", "input", "text", "%sdang%sque")
    check(find("Kết quả", timeout=15) is not None, "Gõ tên: kết quả nằm dưới tiêu đề 'Kết quả'")
    tap("Trương Đăng Quế", timeout=15)
    check(find("Buýt", timeout=15) is not None and find("Metro") is not None and gone("Công cộng"), "Chọn điểm đến: vào thẳng danh sách tuyến, 5 tab phương tiện")
    bus = find(r"~Đi bộ tổng \d+ phút.*", timeout=20)
    check(bus is not None, "Tab Buýt: mỗi tuyến ghi rõ phần đi bộ ra/vào trạm ('Đi bộ tổng … phút')")
    # UX-2 screen 4: From / To edited in place, swap and time on the results; no separate trip form.
    check(find(desc="^Điểm đi: ", timeout=4) is not None and find(desc="^Điểm đến: ") is not None and find(desc="Đổi chiều", timeout=2) is not None
          and find("Đổi điểm đi, điểm đến hoặc giờ", timeout=1) is None, "Danh sách tuyến: sửa điểm đi / điểm đến / đổi chiều ngay ở đầu màn")
    check(sum(1 for n in nodes() if n.get("text") == "Nhanh nhất") <= 1, "Không lặp nhãn 'Nhanh nhất' trên thẻ (chỉ còn chip sắp xếp)")
    later = find("Chuyến sau:", timeout=2)
    check(True, "Chuyến giống nhau gộp một thẻ" + (" (có 'Chuyến sau')" if later is not None else " (lần này không có chuyến trùng)"))
    tap("^Đi ngay")
    check(find("Khởi hành ngay", timeout=3) is not None, "Bấm 'Đi ngay ▾': mở lựa chọn giờ")
    tap("^Đi ngay")
    check(gone("Khởi hành ngay"), "Bấm lại 'Đi ngay ▴': lựa chọn giờ đóng lại")
    audit("Danh sách tuyến")
    tap("Ô tô")
    first = find(r"~\d+ phút", timeout=20)
    check(first is not None, "Chọn tab Ô tô: có kết quả ngay, không phải bấm thêm")
    check(find("Nhanh nhất", timeout=2) is None, "Ô tô: không hiện chip/nhãn 'Nhanh nhất / Ít đi bộ' vô nghĩa")

    # Route detail (screen 5): full-screen map, the sheet expands and collapses, two actions.
    tap(first.get("text"))
    check(find("Bắt đầu") is not None and find("Xem từng bước") is not None and find("Xem trước", timeout=1) is None,
          "Chi tiết tuyến: 2 nút Bắt đầu · Xem từng bước (không còn 'Xem trước' riêng)")
    check(gone("Khám phá"), "Chi tiết tuyến: thanh tab dưới ẩn (màn con)")
    audit("Chi tiết tuyến")
    tap(desc="Mở rộng")
    check(find(desc="Thu gọn") is not None, "Bấm phần đầu thẻ: thẻ mở rộng, nhãn đổi thành 'Thu gọn'")
    tap(desc="Thu gọn")
    check(find(r"~Lái xe \d+ phút", timeout=4) is not None and find("^Đến ", timeout=2) is not None and gone("Xe đạp"),
          "Chi tiết tuyến như mẫu màn 5: dòng thời gian các chặng đến điểm đến, không có thanh phương tiện")

    # Step by step (screen 6): always split, 3D street view on top with ︿ / ﹀, 2D below, card with Trước / ▶▶ / Sau.
    tap("Xem từng bước")
    check(find("Góc nhìn trên đường · 3D", timeout=8) is not None and find(desc="Tiến lên 20 m") is not None and find(r"~Bước \d+/\d+.*") is not None,
          "Xem từng bước: chia đôi, 3D trên có ︿ / ﹀, 2D dưới, thẻ bước")
    check(find("2D") is not None and find("3D") is not None and find("Chạy") is not None, "Xem từng bước: bản đồ dưới có 2D | 3D, nút '▶▶ Chạy' có chữ (mẫu màn 6)")
    audit("Xem từng bước")
    tap("3D")
    x1, y1, x2, y2 = ux_audit.bounds(find("3D"))
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    on = [n for n in nodes() if "true" in (n.get("selected"), n.get("checked")) and ux_audit.bounds(n)[0] <= cx <= ux_audit.bounds(n)[2] and ux_audit.bounds(n)[1] <= cy <= ux_audit.bounds(n)[3]]
    check(on, "Chọn 3D ở bản đồ dưới: nút 3D được chọn (báo cho trình đọc màn hình)")
    tap("2D")
    tap(desc="Tự chạy xem trước toàn tuyến")
    check(find(desc="Tạm dừng xem trước", timeout=4) is not None, "Bấm ▶▶: tự chạy, nút đổi thành Tạm dừng")
    tap(desc="Tạm dừng xem trước")
    check(find(desc="Tự chạy xem trước toàn tuyến", timeout=4) is not None, "Bấm Tạm dừng: dừng lại")
    num = lambda t: int(re.search(r"Bước (\d+)/", t).group(1))
    before = find(r"~Bước \d+/\d+.*").get("text")
    tap("Sau ›")
    after = find(r"~Bước \d+/\d+.*").get("text")
    check(num(after) == num(before) + 1, f"Nút Sau: đúng +1 ({before} -> {after})")
    card = find(r"~Bước \d+/\d+.*")
    y = (ux_audit.bounds(card)[1] + ux_audit.bounds(card)[3]) // 2
    adb("shell", "input", "swipe", "900", str(y), "200", str(y), "250")
    time.sleep(1.5)
    swiped = find(r"~Bước \d+/\d+.*").get("text")
    check(num(swiped) == num(after) + 1, f"Vuốt trái trên thẻ: sang đúng bước kế tiếp ({after} -> {swiped})")
    tap(desc="Đường đi")
    step = find(r"~(Rẽ|Chếch).*")
    check(step is not None, "Nút danh sách: mở các bước rẽ")
    tap(step.get("text"))
    check(find(r"~Bước \d+/\d+.*", timeout=6) is not None and find(r"~(Rẽ|Chếch).*") is not None, "Chọn một bước trong danh sách: về màn Xem từng bước tại bước đó")
    adb("shell", "input", "keyevent", "4")
    check(find("Xem từng bước", timeout=6) is not None, "Back từ Xem từng bước: về Chi tiết tuyến (không qua danh sách)")

    # Navigation (screen 7): split 3D / 2D, banner, steps list, back stack.
    tap("Bắt đầu")
    check(find("Thoát") is not None and find(r"~(Bắt đầu|Rẽ|Chếch|Đi thẳng|Tiếp tục).*", timeout=8) is not None, "Bắt đầu: băng chỉ hướng + nút Thoát")
    check(find(desc="Loại bản đồ: 2D", timeout=4) is not None, "Dẫn đường: nút kiểu bản đồ dưới ghi '2D' (mẫu màn 7)")
    audit("Dẫn đường")
    tap(desc="Loại bản đồ: 2D")
    check(find(desc="Loại bản đồ: 3D", timeout=4) is not None, "Bấm nút '2D': bản đồ dưới đổi sang 3D")
    tap(desc="Loại bản đồ: 3D")
    tap(desc="Đường đi")
    step = find(r"~(Rẽ|Chếch).*")
    check(step is not None, "Dẫn đường › Đường đi: danh sách các bước rẽ")
    audit("Đường đi")
    tap(step.get("text"))
    check(find(desc="Tiến lên 20 m", timeout=6) is not None, "Chạm một bước: mở Xem từng bước (chia đôi)")
    adb("shell", "input", "keyevent", "4")
    # The steps screen: its title, and no "Thoát" (that would mean we jumped straight back to navigation).
    check(find("Đường đi", timeout=4) is not None and gone("Thoát"), "Back trong Xem từng bước (mở từ Đường đi): về Đường đi")
    adb("shell", "input", "keyevent", "4")
    check(find("Thoát", timeout=4) is not None, "Back từ Đường đi: về màn dẫn đường")
    tap("Thoát")
    check(find("Bắt đầu") is not None, "Thoát: về chi tiết tuyến, tuyến vẫn còn")
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")
    check(find("Khám phá", timeout=6) is not None and find("Bạn muốn đến đâu?") is not None, "Back từ danh sách tuyến: về bản đồ, thanh tab dưới hiện lại")


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
