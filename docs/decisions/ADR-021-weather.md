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

## Dự kiến làm sau: điều chỉnh thời gian đi bộ khi mưa (bổ sung 11-11-2026)

Chủ dự án đồng ý đưa việc này vào kế hoạch. Nó thay thế một phần quy tắc "thời tiết không làm thay đổi nội dung tuyến" ở trên: thứ tự tuyến vẫn không đổi, nhưng thời gian đoạn đi bộ được điều chỉnh.

- **Cách làm:** khi dự báo có mưa tại thời điểm khởi hành, Go API gửi yêu cầu tìm tuyến tới OTP với tốc độ đi bộ thấp hơn bình thường.
- **Chỉ áp dụng cho đoạn đi bộ.** Thời gian trên metro không đổi. Xe buýt chậm vì mưa chỉ xử lý được khi có dữ liệu thời gian thực, hiện chưa có.
- **Hiển thị:** tuyến đã điều chỉnh phải ghi rõ "đã tính thêm thời gian vì có thể mưa (ước tính)", và ở chi tiết tuyến hiện cả thời gian thường lẫn thời gian đã điều chỉnh.
- **Khi không có dữ liệu thời tiết:** không điều chỉnh, không ghi chú.
- **Điều kiện bắt đầu:** sau khi M02 chạy được, vì cần OTP thật để thử.
- **Chưa xác định:**
  - Hệ số giảm tốc độ đi bộ. Con số 15–20% từng nêu chỉ là ví dụ, không có nguồn. Số "7 phút (thường 6 phút)" trong wireframe là số mẫu.
  - Ngưỡng dự báo mưa nào thì kích hoạt.
  - Cách đặt tốc độ đi bộ theo từng yêu cầu trong phiên bản OTP sẽ dùng: chưa kiểm tra.

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
