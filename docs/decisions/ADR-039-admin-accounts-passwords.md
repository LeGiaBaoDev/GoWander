# ADR-039 — Admin đăng nhập bằng tài khoản riêng (mã tài khoản + mật khẩu), quên mật khẩu qua email Gmail

- **Trạng thái:** Accepted · **Ngày:** 16-11-2026
- **Thay thế:** ADR-037 §2 (đăng nhập Google OIDC) và §3 (email bootstrap). Các phần khác của ADR-037 giữ nguyên: Go server-rendered, phiên ở server, CSRF, vai trò kiểm ở server, nhật ký chỉ thêm.
- **Liên quan:** ADR-037, `docs/design/03-product-ux-v4.0/05-admin-web.md`

## Bối cảnh

Chủ dự án, 16-11-2026: "không dùng google cho admin, sẽ có cung cấp tài khoản riêng, mã tk, mật khẩu, quên mật khẩu mới dùng đến smtp"; mã tài khoản "baole nhưng không đc trùng"; SMTP "là gmail của google". Hiện chỉ có một quản trị viên (chủ dự án), đã đăng nhập bằng Google trước đó.

## Quyết định

1. **Tài khoản do quản trị cấp**, không có trang tự đăng ký. Mã tài khoản do người cấp tự đặt (ví dụ `baole`): 3–40 ký tự `a-z 0-9 . _ -`, lưu chữ thường, **không trùng** (không phân biệt hoa thường; ràng buộc UNIQUE trong database).
2. **Mật khẩu** băm bằng **argon2id** (`golang.org/x/crypto/argon2`; tham số OWASP: 19 MiB, 2 vòng, 1 luồng; muối 16 byte ngẫu nhiên). Dài 12–128 ký tự, không trùng mã tài khoản. Không bao giờ ghi mật khẩu hay mã băm vào log.
3. **Đăng nhập sai:** luôn cùng một thông báo ("Sai mã tài khoản hoặc mật khẩu"), kể cả khi mã không tồn tại (vẫn băm thử một lần để thời gian trả lời như nhau). Sai **5 lần liên tiếp → khoá 15 phút**.
4. **Tài khoản đầu tiên / cấp lại mật khẩu** bằng lệnh chạy trên server: `go run ./cmd/admin create-account` và `reset-password`. Lệnh in **mật khẩu tạm** một lần; lần đăng nhập đầu phải đổi mật khẩu trước khi mở trang khác. Tài khoản của chủ dự án được gắn vào user hiện có (giữ nguyên vai trò và nhật ký).
5. **Quên mật khẩu:** nhập mã tài khoản → email tới địa chỉ đã lưu của tài khoản một link dùng **một lần, hết hạn sau 30 phút** (database chỉ giữ SHA-256 của mã trong link). Trang luôn trả lời giống nhau dù mã có tồn tại hay không; email gửi nền để thời gian trả lời không lộ điều đó. Mỗi tài khoản nhận tối đa một email mỗi 2 phút. Đặt lại thành công thì đăng xuất mọi phiên của tài khoản.
6. **SMTP là Gmail:** `smtp.gmail.com:587` STARTTLS, đăng nhập bằng **mật khẩu ứng dụng** của tài khoản Gmail (cần bật xác minh 2 bước). Biến môi trường `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` (bí mật), `SMTP_FROM`. Thiếu cấu hình thì trang "Quên mật khẩu" báo chưa bật; link đặt lại không bao giờ được in ra log. Test dùng Mailpit chạy local.
7. **Bỏ Google:** xoá code OIDC và hai thư viện `go-oidc`, `oauth2`; bỏ `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `ADMIN_BOOTSTRAP_EMAIL`; bỏ bảng `user_auth_identities`. Bỏ luôn code cấp/thu hồi vai trò theo email (đang không dùng từ khi gỡ trang vai trò); cấp tài khoản cho người khác sẽ làm cùng trang "Tài khoản" khi có phân quyền.
8. **Form đăng nhập, quên mật khẩu, đặt lại** (chưa có phiên nên chưa có CSRF token) từ chối request có header `Origin` khác máy chủ; cookie phiên vẫn `HttpOnly`, `SameSite=Lax`, `Secure` khi https.

## Không chọn

- **Giữ Google OIDC:** chủ dự án không muốn phụ thuộc tài khoản Google cho người vận hành.
- **bcrypt:** đủ dùng nhưng giới hạn 72 byte và không tốn bộ nhớ; argon2id là khuyến nghị hiện tại của OWASP.
- **Link đặt mật khẩu cho tài khoản đầu tiên qua email:** cần SMTP chạy được trước khi có ai đăng nhập; lệnh trên server không phụ thuộc mạng.
- **2FA (TOTP):** nên có khi có nhiều quản trị viên hoặc trước khi mở Admin ra Internet; để lát sau.

## Hệ quả

- Thêm `golang.org/x/crypto`. Bớt `go-oidc`, `oauth2`.
- Chủ dự án tạo **mật khẩu ứng dụng Gmail** và đặt các biến `SMTP_*` trong `.env` (không commit).
- Chưa có giới hạn số lần theo IP; khoá theo tài khoản chặn đoán mật khẩu cho một tài khoản. Thêm giới hạn theo IP khi Admin mở ra Internet (M09/M10).
