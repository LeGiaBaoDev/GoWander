# ADR-040 — Tài khoản người dùng app

- **Trạng thái:** Accepted (hướng đi, chủ dự án 16-11-2026) · chưa triển khai · Plan chi tiết: [`docs/plans/AUTH-tai-khoan-nguoi-dung.md`](../plans/AUTH-tai-khoan-nguoi-dung.md)
- **Liên quan:** ADR-022 (đăng nhập bằng mật khẩu, OTP qua email, guest mode), ADR-037/039 (tài khoản Admin), ADR-038 (thống kê không lưu dữ liệu cá nhân).

## Bối cảnh
Portfolio, tab Hồ sơ và Feedbacks cần biết người dùng là ai. App hiện chỉ có khách; Admin đã có tài khoản riêng.

## Quyết định
1. App **không bắt đăng nhập khi mở**; Bản đồ, Khám phá, Tìm đường, gửi feedback vẫn dùng được khi là khách.
2. Đăng ký bằng **tên đăng nhập + email + số điện thoại (không bắt buộc) + mật khẩu**; đăng nhập bằng **email, số điện thoại hoặc tên đăng nhập + mật khẩu**, và **Google** (đợt đầu, nút có logo G chính thức). Email phải xác minh bằng mã 6 số. Số điện thoại chưa xác minh được cho tới khi có SMS.
3. Quên mật khẩu: **email qua SMTP** ở đợt đầu; **SMS** ở đợt mở rộng.
4. **Để sau** (chủ dự án 16-11-2026): Face ID / vân tay (mở khoá token đã lưu), SMS, đăng nhập Apple.
5. Tài khoản app **tách khỏi Admin**: bảng `app_users`, `app_sessions`, `app_codes`; API công khai không đọc được bảng Admin.
6. Phiên app: token ngẫu nhiên, server lưu băm, 60 ngày trượt, thu hồi được; không dùng JWT.
7. Feedbacks khi đã đăng nhập: tên + email **lấy từ tài khoản, chỉ đọc**, server không tin dữ liệu app gửi.
8. Có **xoá tài khoản** trong app.
9. **Lịch sử tìm kiếm**: lưu trong máy (gợi ý nhanh ở ô tìm + trang Lịch sử trong Hồ sơ, xoá được, tắt được); đồng bộ lên tài khoản là tuỳ chọn ở đợt sau.

## Hệ quả
- Vai trò database `gowander_app` được ghi vào các bảng tài khoản app (trước đây API chỉ đọc nội dung và ghi bộ đếm). Test quyền database phải chứng minh nó vẫn không chạm được bảng Admin.
- Đăng nhập Google trên iOS: khi lên App Store, Apple thường đòi thêm Đăng nhập bằng Apple (Guideline 4.8), cần tài khoản Apple Developer.
- Cần chính sách quyền riêng tư và điều khoản trước pilot.
