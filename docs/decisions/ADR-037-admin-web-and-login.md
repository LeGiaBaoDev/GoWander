# ADR-037 — Trang Admin: Go server-rendered + htmx; đăng nhập Google (OIDC) với danh sách email được cấp vai trò

- **Trạng thái:** Accepted · **Ngày:** 16-11-2026
- **Liên quan:** PIPELINE §11 (M07), `docs/design/04-technical-architecture-v2.1/02-mobile-admin-backend-architecture.md` §5, `03-auth-routing-database.md` §7, `docs/design/05-database-design-v1.2/02-identity-tables.md`, ADR-027 (API Go), ADR-028 (Discover)

## Bối cảnh

M07 cần trang quản trị nội bộ: quản lý nội dung (địa điểm, món, câu chuyện), trạng thái dữ liệu giao thông, xử lý phản hồi và vài bảng số liệu. Thiết kế để ngỏ framework ("không chọn chỉ vì phổ biến"), tiêu chí: form và bảng, kết nối API có kiểu, xác thực, hiển thị theo quyền, dễ bảo trì, triển khai đơn giản. Dự án do một người làm, backend là Go, app là Kotlin; chưa có Node trong dự án.

## Quyết định (chủ dự án chọn 16-11-2026)

1. **Giao diện Admin dựng ở server Go hiện có**: `html/template` (nhúng vào binary bằng `embed`), **htmx** cho tương tác nhỏ (lọc, phân trang, xuất bản/ẩn không tải lại trang), CSS tự viết gọn. Đường dẫn `/admin/…` trong cùng binary API; không thêm dịch vụ, không Node.
2. **Đăng nhập Google qua OIDC**, Authorization Code + PKCE, server giữ phiên: cookie `HttpOnly`, `Secure`, `SameSite=Lax`; phiên lưu băm (SHA-256) trong database, có hạn. Kiểm tra chữ ký, issuer, audience, `email_verified` của ID token.
3. **Chỉ email đã được cấp vai trò mới vào được.** Vai trò theo thiết kế: CONTENT_EDITOR, MODERATOR, OPERATIONS_ADMIN, BUSINESS_ADMIN, SYSTEM_ADMIN, lưu ở `roles` / `user_roles`. Người SYSTEM_ADMIN đầu tiên lấy từ biến môi trường (`ADMIN_BOOTSTRAP_EMAIL`, chỉ dùng khi chưa có ai có vai trò); sau đó cấp vai trò trong trang Admin (chỉ SYSTEM_ADMIN).
4. **Quyền được kiểm ở server cho từng request** (middleware theo vai trò cho mỗi route), không dựa vào việc ẩn nút. Mọi thao tác ghi đi qua cùng một chỗ ghi `admin_audit_logs` (người làm, hành động, đối tượng, thời điểm, request ID; trước/sau dạng JSON, không chứa thông tin liên lạc riêng tư).
5. **Form có token CSRF** (gắn với phiên); htmx gửi kèm qua header.
6. **Thư viện mới**: `github.com/coreos/go-oidc/v3` và `golang.org/x/oauth2` (xác minh ID token và JWKS là phần dễ sai, không tự viết). htmx là một file JS tĩnh có ghim phiên bản, nhúng vào binary (không tải từ CDN lúc chạy).

## Không chọn

- **React + Vite SPA**: bảng/form/biểu đồ phong phú hơn, nhưng thêm Node/npm, một bản build, giữ client đồng bộ OpenAPI; với phạm vi CRUD và vài bảng số liệu của M07 thì chưa đáng. Vì API Go là nơi kiểm quyền, sau này có thể thay giao diện mà không đổi luật phân quyền.
- **Next.js**: thêm một server Node phải chạy và giám sát; SSR không cần cho trang nội bộ.
- **Keycloak / Zitadel tự chạy**: linh hoạt (2FA, nhóm) nhưng thêm một dịch vụ cần sao lưu và vá. Xem lại khi có nhiều người vận hành hoặc cần 2FA bắt buộc.
- **Tài khoản + mật khẩu riêng**: phải tự làm băm mật khẩu, đặt lại, khoá tài khoản, 2FA.

## Hệ quả

- Chủ dự án tạo một **OAuth client** trên Google Cloud (loại Web, redirect `…/admin/auth/callback`) và đặt `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `ADMIN_BOOTSTRAP_EMAIL` trong `.env` (không commit). Bí mật này chỉ server đọc.
- Bảng `users` của Admin là bảng người dùng chung trong thiết kế (sau này app cũng dùng khi có tài khoản, COM-1); email là dữ liệu riêng tư, không hiện ở nơi không cần.
- Biểu đồ trong bảng số liệu sẽ là SVG dựng ở server hoặc một thư viện JS nhỏ ghim phiên bản; quyết định ở lát làm bảng số liệu.
- Kiểm thử: test handler cho từng route Admin chứng minh không có vai trò thì 403 ở server; test tích hợp cho ghi nhật ký.

## Nội dung do người dùng gửi lên (bổ sung 16-11-2026, chủ dự án: "một số thứ ở client là do các client khác cung cấp, thì cũng phải nghĩ tới")

- Nội dung người dùng gửi (báo sai thông tin; sau này đánh giá, ảnh cộng đồng, portfolio công khai, báo vi phạm) đi qua **một khung kiểm duyệt chung**: hàng chờ, trạng thái (`NEW`, `IN_REVIEW`, `RESOLVED`, `REJECTED` cho báo cáo; `PENDING`, `APPROVED`, `HIDDEN` cho nội dung hiển thị), người xử lý, thời điểm, ghi vào `admin_audit_logs`.
- **App chỉ hiển thị nội dung người dùng đã được duyệt** (giống quy tắc chỉ hiện địa điểm `PUBLISHED`). Số sao chỉ tính từ đánh giá đã duyệt.
- **MODERATOR** chỉ thấy và xử lý hàng chờ, không sửa nội dung biên tập; CONTENT_EDITOR sửa nội dung biên tập, không xử lý người dùng. Không vai trò nào thấy email hay số điện thoại của người gửi trong danh sách; chỉ SYSTEM_ADMIN xem được khi cần, và việc xem được ghi nhật ký.
- Gửi báo cáo không cần tài khoản nhưng **giới hạn tần suất** theo thiết bị/IP. Đánh giá, ảnh, portfolio cần tài khoản (COM-1), nên làm sau M07; ảnh cần R2 (ADR-035) và phải bỏ vị trí trong EXIF trước khi hiện.
- M07 làm khung này qua "Báo sai thông tin"; COM-1 dùng lại cho đánh giá và ảnh.

