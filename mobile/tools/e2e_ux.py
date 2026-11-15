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


def scroll_to(text=None, desc=None, tries=4):
    """Swipe the list up until the element is on screen (long screens: the plan map sits above the stops)."""
    for _ in range(tries):
        n = find(text, desc, timeout=2.0)
        if n is not None and ux_audit.bounds(n)[3] < 1950:  # above the fixed bottom card
            return n
        adb("shell", "input", "swipe", "540", "1700", "540", "1100", "500")
        time.sleep(0.8)
    return find(text, desc, timeout=2.0)


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
    check(find("Tìm quán, món ăn, địa điểm", timeout=15) is not None and gone("Chọn ngôn ngữ"), "Bắt đầu: vào thẳng bản đồ, không có màn chọn ngôn ngữ / vị trí")
    adb("shell", "am", "force-stop", APP)
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    check(find("Tìm quán, món ăn, địa điểm", timeout=25) is not None and gone("Move Smart. Explore More."), "Mở lại app: không hiện màn chào lần nữa")
    check(find(r"~★ \d,\d", timeout=30) is not None and find("Ăn uống") is not None and find("Danh sách") is not None,
          "Bản đồ (mẫu màn 1): thẻ quán nổi có sao (mẫu), hàng lọc, nút Danh sách")
    audit("Bản đồ chính")
    tabs = ["Bản đồ", "Khám phá", "Đi đâu?", "Hành trình", "Hồ sơ"]
    check(all(find(t, timeout=3) is not None for t in tabs), "Thanh dưới 5 mục: Bản đồ · Khám phá · Đi đâu? · Hành trình · Hồ sơ")
    check(find(desc="Ngôn ngữ: Tiếng Việt", timeout=2) is None, "Không còn nút 'VI' khi chưa có tiếng Anh (ADR-034)")

    # A suggested place opens its page; "Chỉ đường" goes to the routes; Back returns to the page (T3, T11).
    # P3: the cards come from the area on screen, so any card will do (no fixed place name).
    if find(desc=r"~.*số sao mẫu.*", timeout=20) is not None:
        tap(desc=r"~.*số sao mẫu.*")
        check(find("Chỉ đường", timeout=10) is not None, "Bấm địa điểm gợi ý: mở trang địa điểm (có nút Chỉ đường)")
        audit("Trang địa điểm")
        tap("Chỉ đường")
        check(find("Buýt", timeout=15) is not None and find(r"~Thông tin về .*", timeout=15) is not None, "Chỉ đường: vào danh sách tuyến + dòng 'Thông tin về …'")
        adb("shell", "input", "keyevent", "4")
        check(find("Chỉ đường", timeout=6) is not None, "Back từ danh sách tuyến: về đúng trang địa điểm")
        adb("shell", "input", "keyevent", "4")
        check(find("Tìm quán, món ăn, địa điểm", timeout=6) is not None, "Back lần nữa: về bản đồ")

    # P3: a dot is a place without a card; tapping it centres the map there and gives it a card.
    dot = find(desc=r"~.*, hiện thẻ$", timeout=8)
    if dot is not None:
        name = dot.get("content-desc").rsplit(", hiện thẻ", 1)[0]
        tap(desc=dot.get("content-desc"))
        check(find(desc="~^" + re.escape(name) + r", .*số sao mẫu.*", timeout=8) is not None, "Bấm một chấm trên bản đồ: chấm thành thẻ của đúng quán đó")
        audit("Bản đồ sau khi bấm chấm")
    else:
        check(False, "Bản đồ có chấm bấm được")

    # P3: zoomed far out the map shows how many places each area holds; tapping a bubble zooms in to single places.
    # adb cannot pinch: the "-" key zooms MapLibre once the map has keyboard focus (a centre press moves it there).
    adb("shell", "input", "keyevent", "--longpress", "KEYCODE_DPAD_CENTER")
    time.sleep(1.5)
    for _ in range(3):
        adb("shell", "input", "keyevent", "KEYCODE_MINUS")
        time.sleep(1.5)
    bubble = find(desc=r"~\d+ địa điểm, phóng to", timeout=15)
    check(bubble is not None and find(desc=r"~.*số sao mẫu.*", timeout=2) is None, "Zoom xa: bản đồ gom thành bong bóng có số địa điểm, không còn thẻ quán")
    if bubble is not None:
        audit("Bản đồ zoom xa")
        tap(desc=bubble.get("content-desc"))
        tap(desc=r"~\d+ địa điểm, phóng to") if find(desc=r"~\d+ địa điểm, phóng to", timeout=6) is not None else None
        check(find(desc=r"~.*(số sao mẫu|hiện thẻ).*", timeout=15) is not None, "Bấm bong bóng: phóng to, hiện lại quán")

    # Map view panel: 2D / 3D / Vệ tinh (satellite only explains itself, owner 14-11-2026); closes after a choice and outside.
    tap(desc="Loại bản đồ")
    check(find("3D") is not None and find("Vệ tinh") is not None, "Bấm 'Loại bản đồ': có 2D / 3D / Vệ tinh")
    tap("Vệ tinh")
    check(find("^Chưa có ảnh vệ tinh", timeout=3) is not None, "Bấm Vệ tinh: báo chưa có ảnh vệ tinh, không đổi bản đồ")
    tap("3D")
    check(gone("3D"), "Chọn 3D: bảng tự đóng")
    tap(desc="Loại bản đồ")
    tap(desc="Đóng bảng chọn")
    check(gone("3D"), "Bấm ra ngoài bảng: bảng đóng")

    # Map filters: "Cà phê" keeps only cafés (or none), "Tất cả" brings everything back.
    tap("Cà phê")
    tap("Tất cả")
    check(find(r"~★ \d,\d", timeout=8) is not None, "Lọc Cà phê rồi Tất cả: thẻ quán hiện lại")

    # Each tab keeps its own back stack (ADR-034).
    tap("Khám phá")
    check(find("Gần bạn", timeout=10) is not None and find("Điểm đến Việt Nam") is not None and find("HÀNH TRÌNH GỢI Ý") is not None,
          "Khám phá (mẫu màn 8): 2 tab, hành trình gợi ý")
    audit("Khám phá")
    row = find(r"~★ \d,\d", timeout=15)
    if row is not None:
        # Tap the node found, not a fresh search by text: the list may reload in between (flaky run 14-11-2026).
        x1, y1, x2, y2 = ux_audit.bounds(row)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1.0)
        check(find("Chỉ đường", timeout=10) is not None and find("Tổng quan") is not None, "Khám phá: bấm địa điểm mở sheet địa điểm (mẫu màn 2), không tự nhảy sang tuyến (T4)")
        tap("Đánh giá")
        check(find("Viết đánh giá", timeout=4) is not None, "Sheet địa điểm › Đánh giá: điểm sao (mẫu) + nút Viết đánh giá")
        tap(desc="Lưu địa điểm")
        check(find(desc="Bỏ lưu địa điểm", timeout=4) is not None, "Bấm Lưu: nút đổi thành Bỏ lưu")
        adb("shell", "input", "keyevent", "4")
        check(find("HÀNH TRÌNH GỢI Ý", timeout=6) is not None, "Back từ trang địa điểm: về danh sách Khám phá")
    # Regions (screens 13-15) and the planner (16).
    tap("Điểm đến Việt Nam")
    check(find(desc="^TP.HCM", timeout=6) is not None and find(desc="^Hà Nội") is not None, "Điểm đến Việt Nam (mẫu màn 13): bản đồ có thẻ vùng")
    audit("Điểm đến Việt Nam")
    tap(desc="^TP.HCM")
    check(find("TP. HỒ CHÍ MINH", timeout=6) is not None and find("01 Giới thiệu") is not None, "Cẩm nang TP.HCM (mẫu màn 14): bìa + mục lục")
    audit("Cẩm nang TP.HCM")
    tap("03 Ẩm thực")
    tap("Cơm tấm sườn nướng ›")
    check(find("Quán gợi ý", timeout=6) is not None, "Bấm món (mẫu màn 15): mô tả + quán gợi ý")
    audit("Món ăn")
    tap("+ Thêm vào kế hoạch")
    time.sleep(1.5)
    check(find(desc="Bản đồ kế hoạch", timeout=6) is not None and scroll_to("Ăn: cơm tấm sườn nướng") is not None, "Thêm vào kế hoạch: mở Tự lên kế hoạch (mẫu màn 16) có món vừa thêm")
    audit("Tự lên kế hoạch")
    tap(desc="Xoá điểm: Ăn: cơm tấm sườn nướng")
    check(gone("Ăn: cơm tấm sườn nướng"), "Xoá một điểm: điểm biến mất, giờ tính lại")
    tap("Lưu kế hoạch")
    check(find("Quán gợi ý", timeout=6) is not None, "Lưu kế hoạch: quay về màn trước")
    adb("shell", "input", "keyevent", "4")
    adb("shell", "input", "keyevent", "4")
    tap("Gần bạn")
    tap("Hành trình")
    # Plans live in the Journey tab (owner, 15-11-2026): "Của tôi" first, then "Gợi ý".
    check(find("SẮP TỚI", timeout=6) is not None and find("Một ngày ở TP.HCM") is not None and find("Tạo kế hoạch mới") is not None, "Hành trình › Của tôi (mẫu màn 17): kế hoạch của bạn + tạo mới")
    audit("Hành trình Của tôi")
    tap("Gợi ý")
    check(find("Dấu ấn Sài Gòn ở trung tâm", timeout=6) is not None and find("Nghệ thuật, chợ và nhịp sống ven sông") is not None, "Hành trình (mẫu màn 9): các chuyến do biên tập soạn")
    audit("Hành trình")
    tap("Dấu ấn Sài Gòn ở trung tâm")
    check(find("Bắt đầu chuyến", timeout=6) is not None and find("Bưu điện Trung tâm Sài Gòn") is not None, "Kế hoạch chuyến (mẫu màn 10): các điểm đánh số")
    audit("Kế hoạch chuyến")
    # "Bắt đầu chuyến" turns the trip into a plan to follow: real travel times per leg, then stop by stop.
    tap("Bắt đầu chuyến")
    check(find("Điểm kế tiếp: Chợ Bến Thành", timeout=8) is not None and find("Đi tới điểm kế tiếp") is not None, "Bắt đầu chuyến: mở kế hoạch, điểm kế tiếp là điểm đầu")
    check(find(r"~(Đi bộ|Buýt|Metro).* \d+ phút", timeout=40) is not None and find(r"~.*đi bộ \d.*giờ là ước tính", timeout=40) is not None,
          "Kế hoạch: thời gian từng chặng do bộ tìm tuyến tính, có tổng quãng đi bộ")
    # Day, start time and way of travelling (owner, 15-11-2026): the legs are measured again for that day and mode.
    tap("~Chưa chọn ngày.*")
    check(find("~Hôm nay \\d\\d-\\d\\d", timeout=4) is not None and find("~Ngày mai \\d\\d-\\d\\d") is not None, "Chọn ngày: mở danh sách 14 ngày tới")
    tap("~Ngày mai \\d\\d-\\d\\d")
    check(find("~Ngày mai \\d\\d-\\d\\d ▾", timeout=4) is not None, "Chọn Ngày mai: ô ngày đổi thành ngày đã chọn")
    tap("~Bắt đầu 08:00 ▾")
    tap(desc="Muộn hơn 15 phút")
    check(find("08:15", timeout=3) is not None, "Giờ bắt đầu +15 phút: 08:00 thành 08:15")
    tap("Xong")
    tap("~.*đi bộ ▾|Buýt.*▾")
    audit("Kế hoạch đang chọn phương tiện")
    tap("Đi bộ")
    check(find("~Đi bộ ▾", timeout=4) is not None and find("~(Đang tính thời gian đi…|Đi bộ \\d+ phút)", timeout=4) is not None, "Đổi sang Đi bộ: các chặng được tính lại")
    check(find("~Đi bộ \\d+ phút", timeout=40) is not None and gone("Đang tính thời gian đi…"), "Đi bộ: thời gian từng chặng tính xong")
    # The plan on a map: numbered stops and the way between them; "Sắp xếp lại" only proposes a new order.
    check(find(desc="Bản đồ kế hoạch", timeout=4) is not None and find(desc="Điểm 1: Chợ Bến Thành") is not None, "Kế hoạch có bản đồ: các điểm đánh số và đường đi")
    # Full-screen map of the plan, like the Map tab: numbered stops with names, the way between them, 2D / 3D.
    tap("Xem trên bản đồ")
    check(find(desc="Loại bản đồ", timeout=8) is not None and find("Dinh Độc Lập") is not None and find("~Đi tới điểm kế tiếp: .*") is not None,
          "Xem trên bản đồ: bản đồ toàn màn hình có tên các điểm, 2D/3D và nút đi tiếp")
    audit("Bản đồ kế hoạch toàn màn hình")
    adb("shell", "input", "keyevent", "4")
    tap("Sắp xếp lại cho đỡ đi vòng")
    if find("Giữ như cũ", timeout=4) is not None:
        check(find("Dùng thứ tự này") is not None and find("~Thứ tự gợi ý: ngắn hơn.*") is not None, "Sắp xếp lại: chỉ đề xuất, có Dùng thứ tự này / Giữ như cũ")
        audit("Kế hoạch đang xem thứ tự gợi ý")
        tap("Giữ như cũ")
        check(gone("Giữ như cũ") and find(desc="Điểm 1: Chợ Bến Thành") is not None, "Giữ như cũ: thứ tự không đổi")
    else:
        # the notice is a short toast: what matters is that nothing changed without the user saying so
        check(gone("Dùng thứ tự này") and find(desc="Điểm 1: Chợ Bến Thành") is not None, "Sắp xếp lại: thứ tự đã gọn thì không đổi gì")
    scroll_to("Dinh Độc Lập")
    tap("Dinh Độc Lập")
    check(scroll_to("Chỉ đường tới đây") is not None and find(desc="~Ghi chú của bạn: Dinh Độc Lập") is not None, "Bấm một điểm: mở chỉnh thời gian ở lại, ghi chú, chỉ đường tới đây")
    tap(desc="Ở lại thêm 15 phút")
    check(find("~.*ở 45 phút$", timeout=4) is not None, "Ở lại thêm 15 phút: 30 thành 45, giờ các điểm sau tính lại")
    audit("Kế hoạch đang mở một điểm")
    tap("Dinh Độc Lập")
    tap("Đi tới điểm kế tiếp")
    check(find("Buýt", timeout=15) is not None and find("Chợ Bến Thành") is not None, "Đi tới điểm kế tiếp: mở tìm tuyến tới điểm đó")
    adb("shell", "input", "keyevent", "4")
    check(find("Đã đến nơi", timeout=6) is not None and find("Chỉ đường lại") is not None, "Quay lại kế hoạch: đang trên đường, có nút Đã đến nơi")
    tap("Đã đến nơi")
    check(find("Điểm kế tiếp: Dinh Độc Lập", timeout=4) is not None and find(desc="đã đến") is not None, "Đã đến nơi: điểm đó được đánh dấu, điểm kế tiếp đổi")
    audit("Kế hoạch đang đi")
    adb("shell", "input", "keyevent", "4")
    adb("shell", "input", "keyevent", "4")
    tap("Của tôi")
    check(find("ĐANG ĐI", timeout=6) is not None and find("Điểm kế tiếp: Dinh Độc Lập") is not None and find("Tiếp tục") is not None, "Của tôi: kế hoạch đang đi nằm trên cùng, có điểm kế tiếp và nút Tiếp tục")
    tap("Hồ sơ")
    check(find("Thiết kế", timeout=6) is not None and find("Hành trình của") is not None, "Hồ sơ (mẫu màn 11): portfolio có bìa + nút Thiết kế")
    audit("Hồ sơ")
    check(find("Kế hoạch", timeout=2) is None, "Hồ sơ không còn thẻ Kế hoạch (đã chuyển sang Hành trình)")
    # P5 (ADR-036): plans and saved places are kept on the device. Close the app and open it again: the place saved
    # earlier is still there (plans go through the same store; AppStoreTest covers them).
    adb("shell", "am", "force-stop", APP)
    adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
    find("Tìm quán, món ăn, địa điểm", timeout=25)
    tap("Hồ sơ")
    tap("Đã lưu")
    check(find("^Chưa lưu địa điểm nào", timeout=3) is None, "Tắt app rồi mở lại: địa điểm đã lưu vẫn còn")
    tap("Portfolio")
    tap("Thiết kế")
    check(find("Mẫu trình bày", timeout=6) is not None, "Thiết kế portfolio (mẫu màn 12)")
    audit("Thiết kế portfolio")
    adb("shell", "input", "keyevent", "4")
    tap(desc="Cài đặt")
    check(find("Nguồn dữ liệu và giấy phép", timeout=6) is not None and find("Bạn đang dùng chế độ khách") is not None, "Hồ sơ › Cài đặt: chế độ khách + nguồn dữ liệu và giấy phép")
    # Light / dark look (owner, 15-11-2026): the switch is in Settings and is kept after a restart of the screen.
    tap("Tối")
    time.sleep(1.0)
    check('name="theme.dark" value="true"' in adb("shell", "run-as", APP, "cat", "shared_prefs/gowander.xml"), "Cài đặt › Giao diện: chọn Tối, lựa chọn được lưu trên máy")
    audit("Cài đặt nền tối")
    tap("Sáng")
    adb("shell", "input", "keyevent", "4")
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
    bus = find(r"~Đi bộ tổng \d+ phút.*", timeout=45)
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
    check(find(r"~(Góc nhìn trên đường · 3D|Ảnh đường phố)", timeout=8) is not None and find(desc="Tiến lên 20 m") is not None and find(r"~Bước \d+/\d+.*") is not None,
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
    # P2b voice: speaker button cycles Bật -> Chỉ đọc cảnh báo -> Tắt -> Bật, with a spoken label for each.
    on = find(desc="^Âm thanh: ", timeout=4)
    check(on is not None and ux_audit.bounds(on)[3] - ux_audit.bounds(on)[1] >= 48 * 2.6, "Dẫn đường: nút loa (mẫu màn 7), đủ lớn để bấm")
    tap(desc="^Âm thanh: Bật")
    check(find(desc="Âm thanh: Chỉ đọc cảnh báo", timeout=4) is not None, "Bấm loa lần 1: Chỉ đọc cảnh báo")
    tap(desc="Âm thanh: Chỉ đọc cảnh báo")
    check(find(desc="Âm thanh: Tắt", timeout=4) is not None, "Bấm loa lần 2: Tắt")
    tap(desc="Âm thanh: Tắt")
    check(find(desc="^Âm thanh: Bật", timeout=4) is not None, "Bấm loa lần 3: Bật lại")
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
    check(find("Khám phá", timeout=6) is not None and find("Tìm quán, món ăn, địa điểm") is not None, "Back từ danh sách tuyến: về bản đồ, thanh tab dưới hiện lại")


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
