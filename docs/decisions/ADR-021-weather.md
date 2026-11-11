# ADR-021 — Hiển thị thời tiết trong phần di chuyển

- **Trạng thái:** Accepted (phạm vi), nhà cung cấp dữ liệu **chưa chọn** · **Ngày:** 11-11-2026 · **Người quyết định:** chủ dự án
- **Liên quan:** `OVERVIEW.md` mục 2 và mục 4, wireframe mobile A1, A3, A4

## Bối cảnh

GoWander gợi ý hành trình có đoạn đi bộ và thời gian chờ ngoài trời. Ở TP.HCM, mưa ảnh hưởng trực tiếp tới việc chọn tuyến. Các tài liệu thiết kế trước đây chưa nhắc tới thời tiết.

## Quyết định

1. **GoWander có hiển thị thời tiết, ở mức nhẹ, gắn với việc di chuyển.** Không làm màn hình dự báo thời tiết riêng.
2. **Nơi hiển thị:**
   - Màn hình Map (A1): một dòng thời tiết hiện tại và khả năng mưa sắp tới.
   - Kết quả tuyến (A3): cảnh báo khi có khả năng mưa và tuyến có đoạn đi bộ.
   - Chi tiết tuyến (A4): ghi chú thời tiết tại thời điểm khởi hành.
3. **Luồng dữ liệu:** Mobile → Go API → nhà cung cấp thời tiết. Mobile không gọi thẳng nhà cung cấp, giống quy tắc đã áp dụng cho OpenTripPlanner.
4. **Lưu đệm ở backend theo khu vực**, làm mới khoảng 15 phút một lần, để số lượt gọi không phụ thuộc số người dùng. Ước tính cho khu pilot: 100–300 lượt mỗi ngày (chưa đo).
5. **Quy tắc hiển thị:**
   - Luôn ghi là dự báo, kèm thời điểm cập nhật và tên nguồn.
   - Khi không lấy được dữ liệu: ẩn phần thời tiết, không hiện số cũ hay số đoán.
   - Thời tiết không làm thay đổi thứ tự hay nội dung tuyến do OTP trả về; nó chỉ là thông tin kèm theo.
   - Cảnh báo dùng chữ và biểu tượng, không chỉ dùng màu.

## Chưa quyết định

- **Nhà cung cấp.** Ứng viên: Open-Meteo, OpenWeatherMap, MET Norway. Thông tin hiện có lấy từ trang tổng hợp, chưa đọc điều khoản chính thức:
  - Open-Meteo: bản miễn phí chỉ cho mục đích phi thương mại; giá bản thương mại chưa xác minh.
  - OpenWeatherMap: bản miễn phí khoảng 1.000 lượt mỗi ngày; điều khoản dùng cho production chưa xác minh.
  - MET Norway: điều khoản chưa xác minh.
- **Bản phát hành.** Đề xuất đưa vào Core MVP vì chi phí thấp; chủ dự án chưa chốt.
- **Ngưỡng cảnh báo mưa** (xác suất và khoảng thời gian nào thì hiện cảnh báo).

## Hệ quả

- Thêm một phụ thuộc bên ngoài. Go API cần một adapter thời tiết có timeout, lưu đệm và xử lý lỗi; lỗi thời tiết không được làm hỏng việc tìm tuyến.
- GoWander định hướng thương mại, nên trước khi thu tiền phải dùng nguồn cho phép sử dụng thương mại.
- Phải ghi công nhà cung cấp theo điều khoản của họ.
- Chưa cần bảng mới trong database; dữ liệu thời tiết chỉ nằm trong bộ nhớ đệm.

## Điều kiện xem lại

- Khi chọn nhà cung cấp: đọc điều khoản và bảng giá chính thức, ghi vào ADR này.
- Khi GoWander bắt đầu có doanh thu, hoặc khi mở rộng ra ngoài khu pilot.
