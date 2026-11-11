# ADR-022 — Đăng nhập bằng mật khẩu, ngôn ngữ, và quy mô Admin

- **Trạng thái:** Accepted · **Ngày:** 11-11-2026 · **Người quyết định:** chủ dự án
- **Liên quan:** nghiên cứu nền ở [`docs/research/ux-wireframe-language-solo-admin.md`](../research/ux-wireframe-language-solo-admin.md); ADR-006 (OIDC-based Authentication) trong tài liệu kiến trúc.

## Quyết định

### 1. Đăng nhập (áp dụng cho cả người dùng và Admin)

- Đăng nhập bằng **email, tên đăng nhập hoặc số điện thoại, kèm mật khẩu**.
- **Giữ song song nút "Đăng nhập với Google"**, hiển thị kèm biểu tượng Google.
- **Quên mật khẩu:** gửi mã OTP qua email, dùng SMTP của Google; nhập đúng OTP thì đặt mật khẩu mới.
- Guest mode vẫn giữ nguyên: Map và Discover dùng được không cần đăng nhập.

### 2. Ngôn ngữ

- Bản đầu: **tiếng Việt và tiếng Anh**.
- Phase sau: thêm **tiếng Trung và tiếng Hàn**. Không dự định thêm ngôn ngữ nào khác.

### 3. Admin

- Chỉ có **1–2 quản trị viên**; không dựng hệ thống phân quyền nhiều vai trò ở giai đoạn này.
- Hai vai trò: **Owner** (toàn quyền) và **Editor** (nội dung và kiểm duyệt).
- Năm vai trò trong tài liệu thiết kế được để dành cho khi mở rộng.

### 4. Wireframe

- Toàn bộ danh sách cải thiện UX đã liệt kê ngày 11-11-2026 được đưa vào wireframe (lối tắt điểm đến, bảng kéo ba nấc, chuyến kế tiếp ước tính, lọc nhanh, nút đổi ngôn ngữ, gợi ý khi gõ, khởi hành lúc / đến trước lúc, nhãn so sánh tuyến, chuyến cuối, hướng tàu, nhắc ga xuống, lưu tuyến, báo sai theo chặng, huy hiệu thời gian đi metro, nút Chỉ đường dính đáy, lưu trên máy trước khi đăng nhập, nút Hữu ích, màn hình giải thích quyền vị trí, trạng thái mất mạng, các cải thiện Admin).

## Điều này thay đổi gì so với tài liệu thiết kế

- Tài liệu kiến trúc và database hiện mô tả đăng nhập chỉ qua OIDC, không lưu mật khẩu. Quyết định này **thêm** đăng nhập bằng mật khẩu; OIDC với Google vẫn còn.
- Chưa sửa các file trong `docs/design/` theo quyết định này.

## Việc phải làm khi triển khai (chưa làm)

- Lưu mật khẩu dạng băm bằng thuật toán chuyên dụng; không bao giờ lưu hay ghi log mật khẩu gốc.
- Giới hạn số lần đăng nhập sai và khoá tạm; thông báo lỗi không tiết lộ sai tên hay sai mật khẩu.
- OTP: có hạn dùng, giới hạn số lần nhập và số lần gửi lại; trả lời giống nhau dù email có tồn tại hay không.
- Thêm nơi lưu thông tin đăng nhập bằng mật khẩu, và quy định email, tên đăng nhập, số điện thoại là duy nhất. Tài liệu database hiện chưa bắt buộc điều này.
- Vẫn giữ kiểm tra quyền ở server, nhật ký thao tác và các bảng `roles`, `user_roles` để mở rộng sau.

## Rủi ro và điểm chưa kiểm chứng

- **Số điện thoại chưa được xác minh**, vì OTP chỉ gửi qua email. Dùng số điện thoại làm tên đăng nhập khi chưa xác minh có thể bị người khác đăng ký trước.
- **SMTP của Google có giới hạn số thư mỗi ngày** và cần mật khẩu ứng dụng. Con số giới hạn chưa được kiểm chứng; đủ cho pilot, có thể không đủ khi nhiều người dùng.
- **Tài khoản Admin dùng mật khẩu** rủi ro hơn chỉ dùng Google. Khuyến nghị bật xác thực hai lớp cho Owner; chủ dự án chưa chốt.
- Các ngưỡng trong wireframe (khoá 15 phút, OTP 10 phút, 5 lần nhập sai, gửi lại sau 60 giây) là số đề xuất, chưa chốt.
- Lựa chọn ngôn ngữ dựa trên số liệu khách quốc tế cả nước, không riêng TP.HCM.
