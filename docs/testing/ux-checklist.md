# Tiêu chí UX của GoWander và cách kiểm tra

> Áp dụng từ 14-11-2026 (chủ dự án: test không chỉ "đúng" mà phải "dễ dùng và đẹp"). Mọi màn mới hoặc màn sửa đều phải qua các mục dưới đây trước khi merge.
> Chuẩn tham chiếu: WCAG 2.2 (skill `ecc:accessibility`), kích thước tối thiểu của Material (48 dp) và Apple (44 pt).

## 1. Dễ bấm

| Mã | Tiêu chí | Kiểm tra tự động |
|---|---|---|
| T1 | Mọi phần tử bấm được ≥ 48 × 48 dp | `ux_audit.py` trên từng màn; `UxComponentTest` (`assertHeightIsAtLeast`) |
| T2 | Nút chỉ có icon phải có tên đọc được (contentDescription) | `ux_audit.py`; `UxComponentTest` |
| T3 | Hai phần tử bấm được không chồng lên nhau, kể cả logo và nút ⓘ của bản đồ | `ux_audit.py` |
| T4 | Không có gì bấm được nằm dưới thanh trạng thái | `ux_audit.py` |
| T0 | Màn phải có phần tử của app; công cụ không được "đạt" khi đếm được 0 phần tử | `ux_audit.py` (đã từng đạt giả vì sai tên gói, xem change record 14-11 mục 8) |

## 2. Bấm vào thì cái gì ẩn, và hiện lại thế nào

Không có gì được biến mất mà không có đường quay lại rõ ràng.

| Phần tử | Bấm thì ẩn gì | Hiện lại thế nào |
|---|---|---|
| Nút "Loại bản đồ" | Mở bảng 2D/3D/Vệ tinh | Bảng tự đóng sau khi chọn; bấm ra ngoài, bấm ✕ hoặc bấm lại nút cũng đóng |
| "Gợi ý gần bạn" (bản đồ chính) | Bấm tiêu đề thì thu gọn thành một dòng | Bấm lại dòng tiêu đề (▴) |
| Thẻ Chi tiết tuyến | Bấm phần đầu thẻ thì mở rộng hoặc thu gọn danh sách chặng | Bấm lại (▾/▴); thẻ không bao giờ biến mất hẳn |
| Mở màn con (Chi tiết, Dẫn đường, Xem trước) | Thanh tab dưới ẩn | Back về màn tab thì thanh tab hiện lại |
| Kéo bản đồ khi dẫn đường | Camera ngừng đi theo | Nút "Căn giữa lại" |
| Xem trước (mở từ Đường đi) | — | Back về Đường đi; "Quay lại tuyến đường" về màn dẫn đường |
| Thoát dẫn đường | Màn dẫn đường | Về Chi tiết tuyến, tuyến vẫn còn, bấm Bắt đầu lại được |
| Chọn Vệ tinh (chưa có nhà cung cấp) | Không chuyển sang bản đồ trống | Hiện ghi chú "Sắp có" |
| Nút "VI" | Mở menu Tiếng Việt / English | Chọn Tiếng Việt thì đóng; English báo "sắp có" |
| Màn chào (lần đầu) | — | Chỉ hiện ở lần mở app đầu tiên; mở lại app thì vào thẳng bản đồ |

Kiểm tra tự động: `mobile/tools/e2e_ux.py` (từng dòng ở trên là một kiểm tra); `UxComponentTest.mapViewPanel…`.

## 3. Rõ ràng và đẹp (kiểm tra bằng mắt trên ảnh chụp, mỗi lần đổi giao diện)

- Chữ đặt trên bản đồ phải có nền (tiêu đề nổi dùng nền trắng bo tròn).
- Một hành động chính cho mỗi màn, nổi bật nhất (ví dụ "Bắt đầu" màu teal đậm; "Thoát" màu đỏ).
- Lựa chọn đang chọn phải nhìn thấy được không chỉ bằng màu: viền, chữ đậm, dấu ✓.
- Bản đồ phải đóng khung đúng phần còn nhìn thấy (không bị thẻ che, không zoom ra quá xa). Lỗi đã gặp: bản đồ Chi tiết tuyến zoom ra tới Mỹ Tho vì khoảng đệm bị cộng hai lần.
- Nhãn chỉ hiện khi có nghĩa: không hiện "Nhanh nhất" khi chỉ có một tuyến ô tô.
- Các nút cùng loại đặt cùng chỗ trên mọi màn: "Loại bản đồ" luôn ở góc trên phải, ngay dưới phần trên cùng của màn.
- Chữ trên nút luôn một dòng và không bị cắt, kể cả trên iPhone SE (375 pt). `UxComponentTest.routeActionsStayOnOneLineOnSmallIphones` so chiều rộng tự nhiên của chữ với chiều rộng được cấp. Bài học: đo chiều cao không phát hiện được chữ bị cắt khi `maxLines = 1`, và cờ `didOverflowWidth` của Compose báo sai trong trường hợp này.
- Màu chữ/nền đạt WCAG AA ≥ 4,5:1 (`ContrastTest`, chạy cả Android và iOS).
- **Thử cả hai nền tảng.** Màn iPhone hẹp hơn emulator Android: lỗi xuống hàng chỉ thấy trên iOS (chủ dự án phát hiện 14-11-2026).
- Mỗi test UX mới phải được chứng minh **fail trên giao diện lỗi** trước khi tin nó đạt.
- Ảnh chụp lưu ở `docs/evidence/ux/`, xem lại trước khi merge.

## 4. Cách chạy

```
python3 -I mobile/tools/ux_audit.py --self-test
cd mobile && ./gradlew :shared:iosSimulatorArm64Test
python3 -I mobile/tools/e2e_ux.py docs/evidence/ux/ux-e2e-<ngày>.md
```

Lệnh thứ hai chạy cả test UX của component (Compose UI test, chạy trên iOS simulator). Lệnh thứ ba cần Android emulator chạy bản debug và API ở 10.0.2.2:8090 với graph toàn TP.HCM. Ghi chú: `uiautomator` không thấy phần tử đã cuộn ra ngoài màn hình; phần tử bị khung cuộn cắt nửa không bị tính T1.
