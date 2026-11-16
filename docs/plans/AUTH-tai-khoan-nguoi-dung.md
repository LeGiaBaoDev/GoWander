# AUTH — Tài khoản người dùng app, đăng nhập Google, Face ID, lịch sử tìm kiếm

> **Trạng thái:** KẾ HOẠCH ĐÃ CHỐT HƯỚNG (chủ dự án, 16-11-2026), chưa code. Quyết định: [ADR-040](../decisions/ADR-040-app-accounts.md).
> **Dành cho:** bất kỳ agent nào làm tiếp. Đọc hết file này, `AGENTS.md`, `PIPELINE.md` trước khi bắt đầu.
> **Nhánh đề xuất:** `feat/app-accounts` tạo từ `develop` **sau khi** `feat/admin-rbac` (M07) được gộp. Chủ dự án tạo nhánh; agent không chạy lệnh Git ghi (AGENTS.md §1.6).

---

## 0. Lời chủ dự án (nguyên văn, để không hiểu sai)

- "người dùng cái portfolio cũng cần login mà, và vẫn chưa ở tab profile tôi nghĩ cùng cần login vẫn chỗ, mà chúng ta vẫn chưa làm á và khi đó thì thông tin feedback sẽ lấy theo tên người dùng + email không cần nhập cái đó, readonly thôi"
- "email, + sđt, quên mật khẩu phase 1 dùng smtp phase mở rộng sẽ có sms, và có thể đăng nhập faceid với ios chẳng hạn"
- "đăng nhập google ngay nha và nhớ có icon google … và dễ nhìn nha, input sẽ màu trắng"
- "đối với ứng dụng chúng ta ban đầu ko bắt đăng nhập ngay nha"
- "ghi thêm kế hoạch về lưu lịch sử tìm kiếm để hiện input nhanh trên chỗ tìm địa điểm và có nơi lịch sử để xem"
- Chốt phạm vi đợt đầu (16-11-2026): "đăng nhập apple phase sau nha, đăng ký email / sđt + mật khẩu + tên đăng nhập thôi với google còn mấy cái kia để sau nha" → đợt đầu chỉ có **email, số điện thoại, tên đăng nhập + mật khẩu, và Google**. Face ID, SMS, Apple để sau.
- Mẫu giao diện chủ dự án gửi (16-11-2026): màn đăng nhập "bear." (nền sáng, nút bo tròn lớn, ô nhập có biểu tượng) và "deeps BEERCAFE" (khối màu đầu trang, ô nhập trắng, nút đen to, nút "Continue with Google" trắng có logo G nhiều màu và mũi tên). **Chỉ lấy tinh thần bố cục, không sao chép**; dùng màu, chữ, logo của GoWander (Design Kit v4, Be Vietnam Pro).

## 1. Hiện trạng (16-11-2026)

| Phần | Đang có | Chưa có |
|---|---|---|
| Admin | Tài khoản riêng (`admin_credentials`, argon2id, `admin_sessions`, quên mật khẩu qua Gmail SMTP, ADR-039) | — |
| App | Khách dùng mọi thứ. Hồ sơ/Portfolio là dữ liệu mẫu; Kế hoạch và Đã lưu chỉ trong máy (`home/AppStore.kt`, lưu qua `Prefs`) | Tài khoản, đăng nhập, phiên, Google, Face ID |
| Feedbacks | Khách nhập tên + email tuỳ chọn (`feedback/FeedbackScreen.kt`, `POST /api/v1/feedbacks`, bảng `feedback_reports`) | Lấy tên/email từ tài khoản |
| Tìm địa điểm | `SearchScreen` (App.kt) gọi `places` + `geocode`; không nhớ gì | Lịch sử tìm kiếm |
| Database | `users`, `user_contacts`, `roles`, `user_roles` (0002) đang dùng cho **Admin** | Bảng tài khoản app |
| SMTP | Gmail app password trong `.env` (`SMTP_*`), `admin.SMTP` gửi thư | Mẫu thư cho người dùng app |

## 2. Phạm vi

**Làm (đợt đầu):** đăng ký bằng **tên hiển thị + tên đăng nhập + email + số điện thoại (không bắt buộc) + mật khẩu**; đăng nhập bằng **email, số điện thoại hoặc tên đăng nhập + mật khẩu**; **đăng nhập Google** (Android + iOS); xác minh email bằng mã; quên mật khẩu qua **email (SMTP)**; hồ sơ; đăng xuất; xoá tài khoản; Feedbacks lấy tên + email từ tài khoản (chỉ đọc); **lịch sử tìm kiếm** (trong máy); Admin xem người dùng app.

**Để sau (agent không tự làm khi chưa được giao):** **Face ID / vân tay (AUTH-4)**, **SMS (AUTH-5)**, **đăng nhập Apple (AUTH-7)**, Facebook, đồng bộ Kế hoạch/Đã lưu/Portfolio lên server (AUTH-6), cộng đồng, điểm thưởng, thanh toán.

**Nguyên tắc không đổi:** app **không bắt đăng nhập khi mở**; Bản đồ, Khám phá, Tìm đường, Feedbacks (khách) vẫn dùng được không cần tài khoản (ADR-022).

## 3. Quyết định thiết kế

### 3.1 Tách tài khoản app khỏi Admin
- Bảng riêng `app_users`, `app_sessions`, `app_codes` (không dùng `users`/`user_contacts` của Admin). Lý do: vai trò database `gowander_app` (API công khai) phải được ghi vào bảng tài khoản app, nhưng **không bao giờ** được đọc email/mật khẩu Admin. Tài khoản Admin (`baole`) không đăng nhập app được và ngược lại.
- Cập nhật `backend/internal/platform/database/migrate_integration_test.go`: thêm bảng mới vào danh sách, kiểm tra `gowander_app` có đúng quyền trên bảng app và **không** có quyền trên bảng Admin.

### 3.2 Định danh đăng nhập
- **Email** (chuẩn hoá: trim + lower) — bắt buộc khi đăng ký bằng mật khẩu, phải xác minh bằng mã trước khi dùng các chức năng cần tài khoản.
- **Số điện thoại** (tuỳ chọn khi đăng ký, chuẩn hoá E.164, mặc định +84: `0912 345 678` → `+84912345678`) — dùng được làm tên đăng nhập. **Chưa xác minh được ở đợt 1** (chưa có SMS): lưu `phone_verified_at = NULL`.
  - Rủi ro (ADR-022): người khác có thể đăng ký trước số của mình. Giảm thiểu: đăng nhập bằng số điện thoại **chỉ khi tài khoản đã xác minh email**; màn Hồ sơ ghi "Số điện thoại chưa xác minh"; khi có SMS (AUTH-5), số chưa xác minh bị giải phóng nếu chủ thật xác minh.
- **Tên đăng nhập** (bắt buộc khi đăng ký bằng mật khẩu, chủ dự án 16-11-2026): 3–30 ký tự `a-z 0-9 . _`, bắt đầu bằng chữ, không phân biệt hoa thường (lưu chữ thường), duy nhất; không trùng dạng email/số điện thoại. Tài khoản tạo bằng Google được gợi ý tên đăng nhập từ email, đổi được trong Cài đặt.
- Ô nhập trên màn đăng nhập: một ô "Email, số điện thoại hoặc tên đăng nhập"; server nhận dạng (có `@` → email; chỉ chữ số, `+`, khoảng trắng → số điện thoại; còn lại → tên đăng nhập).

### 3.3 Mật khẩu, khoá, mã
- argon2id, **dùng lại** `backend/internal/admin/password.go` (đưa phần băm/so sánh ra gói chung `backend/internal/platform/passwords` hoặc `internal/auth`; Admin gọi lại gói đó, test Admin phải còn đạt).
- Mật khẩu 8–128 ký tự; không chặn ký tự đặc biệt; từ chối 1000 mật khẩu phổ biến nhất (danh sách nhỏ nhúng trong code).
- Sai 5 lần → khoá 15 phút (theo tài khoản) + giới hạn theo IP (tái dùng `feedback.Limiter` hoặc tách ra `httpx`).
- **Mã 6 số** (xác minh email, quên mật khẩu): hạn **10 phút**, tối đa **5 lần nhập**, gửi lại sau **60 giây**, tối đa **5 mã/giờ** mỗi email; lưu **băm SHA-256**; dùng xong là hỏng.
- Lời báo lỗi không lộ tài khoản có tồn tại hay không ("Nếu email này có tài khoản, mã đã được gửi.").

### 3.4 Phiên đăng nhập (app di động)
- Token ngẫu nhiên 32 byte (base64url), gửi qua header `Authorization: Bearer <token>`. Server chỉ lưu **SHA-256** của token (`app_sessions.token_hash`), giống `admin_sessions`.
- Hạn **60 ngày trượt** (mỗi lần dùng gia hạn, tối đa 1 lần/giờ để đỡ ghi); đăng xuất = thu hồi ngay; đổi mật khẩu = thu hồi mọi phiên khác; "Đăng xuất mọi thiết bị" trong Cài đặt.
- Không dùng JWT cho phiên app (cần thu hồi tức thì; đơn giản hơn).
- App cất token: **iOS Keychain** (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`), **Android Keystore** (EncryptedSharedPreferences hoặc DataStore + khoá Keystore). Không lưu trong `Prefs` thường, không ghi log.

### 3.5 Đăng nhập Google (làm ngay, AUTH-3)
- App lấy **Google ID token**:
  - Android: Credential Manager (`androidx.credentials` + `com.google.android.libraries.identity.googleid`), nút "Tiếp tục với Google".
  - iOS: GoogleSignIn SDK (Swift Package) gọi từ `iosApp` qua cầu nối nhỏ, trả ID token cho Kotlin.
- Server `POST /api/v1/auth/google {idToken}`: kiểm tra chữ ký RS256 với khoá công khai Google (JWKS `https://www.googleapis.com/oauth2/v3/certs`, cache theo `Cache-Control`), `iss` ∈ {`accounts.google.com`, `https://accounts.google.com`}, `aud` = **Web client ID** của server, `exp` còn hạn, `email_verified = true`. Tự viết bằng thư viện chuẩn Go (`crypto/rsa`, `encoding/json`) hoặc thêm 1 thư viện nhỏ nếu ADR cho phép; có test với khoá giả.
- Liên kết tài khoản: `google_sub` là duy nhất. Nếu email Google trùng email của tài khoản mật khẩu **đã xác minh** → **không tự gộp** (nguyên tắc thiết kế 3.2 của dự án); báo "Email này đã có tài khoản. Đăng nhập bằng mật khẩu rồi bật 'Liên kết Google' trong Cài đặt." Tài khoản chỉ có Google thì `password_hash = NULL`, có thể đặt mật khẩu sau.
- **Biểu tượng Google**: dùng logo "G" nhiều màu chính thức theo [Google Sign-In branding guidelines](https://developers.google.com/identity/branding-guidelines) (nút nền trắng, viền xám nhạt, chữ "Tiếp tục với Google"/"Đăng nhập bằng Google"). Tải SVG chính thức, không vẽ lại.
- **Việc chủ dự án phải làm** (agent hướng dẫn, không tự làm): tạo Google Cloud project → OAuth consent screen (tên GoWander, email hỗ trợ, logo, link chính sách) → 3 OAuth client: **Web** (lấy client ID làm `GOOGLE_WEB_CLIENT_ID` cho server và cho Android), **Android** (package `com.gowander.app`, SHA-1 của khoá debug và release), **iOS** (bundle `com.legiabao.gowander.dev`, lấy client ID + reversed client ID làm URL scheme). Ghi vào `.env` và cấu hình app; không dán secret vào chat.
- **Lưu ý Apple (App Store Review Guideline 4.8):** app có đăng nhập Google trên iOS thường phải có thêm **Đăng nhập bằng Apple** khi phát hành App Store. Cần tài khoản Apple Developer trả phí. Ghi thành AUTH-7, chủ dự án quyết khi chuẩn bị lên App Store; không chặn pilot qua TestFlight nội bộ.

### 3.6 Face ID / vân tay (AUTH-4)
- Không phải phương thức đăng nhập mới ở server: là **mở khoá token đã lưu**.
- Sau lần đăng nhập đầu, app hỏi "Dùng Face ID để đăng nhập nhanh lần sau?". Nếu đồng ý: token được lưu thêm một bản trong **Keychain với `SecAccessControl` `.biometryCurrentSet`** (iOS, `LocalAuthentication`) / **Keystore key yêu cầu `BiometricPrompt`** (Android, `androidx.biometric`).
- Khi phiên hết hạn hoặc người dùng chọn "Khoá app": màn đăng nhập có nút "Đăng nhập bằng Face ID" → hệ thống hỏi sinh trắc → lấy token → gọi `GET /api/v1/me` để kiểm tra còn hiệu lực.
- Đổi vân tay/khuôn mặt trên máy → bản lưu hỏng, phải đăng nhập lại bằng mật khẩu (đúng ý `.biometryCurrentSet`).
- `NSFaceIDUsageDescription` trong `Info.plist`: "GoWander dùng Face ID để bạn đăng nhập nhanh."

### 3.7 SMS (đợt mở rộng, AUTH-5)
- Xác minh số điện thoại và quên mật khẩu bằng SMS. Ở Việt Nam cần **đăng ký brandname** với nhà mạng qua nhà cung cấp (ví dụ eSMS, SpeedSMS, Viettel; quốc tế: Twilio). Chi phí theo tin; thời gian đăng ký brandname vài tuần. Thiết kế interface `Sender` (email, sms) ngay từ AUTH-1 để AUTH-5 chỉ thêm một bản cài đặt.

### 3.8 Xoá tài khoản
- Có trong Cài đặt (bắt buộc với App Store khi app có tài khoản; luật bảo vệ dữ liệu cá nhân VN 2026). Xác nhận bằng mật khẩu hoặc Google/Face ID. Xoá ngay: `app_users` xoá mềm (status `DELETED`, xoá email/phone/tên/băm mật khẩu ngay), xoá phiên, xoá lịch sử tìm kiếm trên server; feedback cũ giữ nội dung nhưng gỡ liên kết người dùng (`app_user_id = NULL`, tên → "Tài khoản đã xoá").

## 4. Database (migration `0014_app_accounts`)

```sql
CREATE TABLE app_users (
  id                UUID PRIMARY KEY DEFAULT uuidv7(),
  display_name      VARCHAR(100) NOT NULL CHECK (btrim(display_name) <> ''),
  username          VARCHAR(30) UNIQUE CHECK (username ~ '^[a-z][a-z0-9._]{2,29}$'), -- tên đăng nhập, chữ thường
  email             VARCHAR(254) UNIQUE,            -- lower(btrim()), NULL only for deleted accounts
  email_verified_at TIMESTAMPTZ,
  phone             VARCHAR(16) UNIQUE,             -- E.164, optional
  phone_verified_at TIMESTAMPTZ,                    -- NULL until SMS (AUTH-5)
  password_hash     TEXT,                           -- argon2id; NULL for Google-only accounts
  google_sub        VARCHAR(255) UNIQUE,
  status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
  failed_logins     INT NOT NULL DEFAULT 0,
  locked_until      TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at     TIMESTAMPTZ,
  CHECK (email IS NULL OR email = lower(btrim(email))),
  CHECK (status = 'DELETED' OR email IS NOT NULL OR google_sub IS NOT NULL)
);
CREATE TABLE app_sessions (
  token_hash   BYTEA PRIMARY KEY,                   -- sha256(token)
  user_id      UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  device_label VARCHAR(100) NOT NULL DEFAULT '',    -- "iPhone", "Android" (no device id)
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX app_sessions_user_idx ON app_sessions (user_id);
CREATE TABLE app_codes (
  id         UUID PRIMARY KEY DEFAULT uuidv7(),
  user_id    UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  purpose    VARCHAR(20) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
  code_hash  BYTEA NOT NULL,
  attempts   INT NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX app_codes_user_idx ON app_codes (user_id, purpose, created_at DESC);
ALTER TABLE feedback_reports ADD COLUMN app_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL;

GRANT SELECT, INSERT, UPDATE ON app_users, app_sessions, app_codes TO gowander_app;
GRANT DELETE ON app_sessions, app_codes TO gowander_app;
GRANT INSERT (app_user_id) ON feedback_reports TO gowander_app;
GRANT SELECT (id, display_name, email, email_verified_at, phone, status, created_at, last_login_at) ON app_users TO gowander_admin;
GRANT UPDATE (status, updated_at) ON app_users TO gowander_admin;
```

Lịch sử tìm kiếm đồng bộ (chỉ AUTH-6, xem §7): bảng `app_search_history` — chưa tạo ở 0014.

## 5. API (thêm vào `backend/api/openapi/openapi.yaml`)

Gói mới `backend/internal/account` (store + handler), mount trong `cmd/api/main.go` với pool công khai. Mọi lỗi dùng `httpx.WriteError` (mã + câu tiếng Việt).

| Method & path | Body | Trả | Ghi chú |
|---|---|---|---|
| `POST /api/v1/auth/register` | `{name, username, email, phone?, password}` | `201 {session, user}` + gửi mã email | email/phone trùng → `409 ACCOUNT_EXISTS` (câu chung chung) |
| `POST /api/v1/auth/verify-email` | `{code}` (cần Bearer) | `200 {user}` | sai → `400 CODE_INVALID`, quá 5 lần → mã hỏng |
| `POST /api/v1/auth/resend-code` | `{purpose}` | `202` | giới hạn 60 s / 5 giờ |
| `POST /api/v1/auth/login` | `{identifier, password, device?}` | `200 {session, user}` | sai → `401 LOGIN_FAILED`; khoá → `423 ACCOUNT_LOCKED` |
| `POST /api/v1/auth/google` | `{idToken, device?}` | `200 {session, user, created}` | trùng email chưa liên kết → `409 LINK_REQUIRED` |
| `POST /api/v1/auth/logout` | — (Bearer) | `204` | thu hồi phiên hiện tại |
| `POST /api/v1/auth/logout-all` | — | `204` | |
| `POST /api/v1/auth/forgot` | `{email}` | `202` (luôn) | gửi mã RESET_PASSWORD nếu có tài khoản |
| `POST /api/v1/auth/reset` | `{email, code, newPassword}` | `200` | thu hồi mọi phiên |
| `GET /api/v1/auth/username-available?u=` | — | `200 {available}` | gợi ý khi gõ; giới hạn tần suất |
| `GET /api/v1/me` | — | `200 {user}` | `{id, name, username, email, emailVerified, phone, phoneVerified, hasPassword, google}` |
| `PATCH /api/v1/me` | `{name?, username?, phone?}` | `200 {user}` | đổi email: AUTH-6 |
| `POST /api/v1/me/password` | `{current?, new}` | `204` | |
| `POST /api/v1/me/google` | `{idToken}` | `200` | liên kết Google |
| `DELETE /api/v1/me` | `{password? / idToken?}` | `204` | §3.8 |

- Middleware `account.Optional` đọc Bearer (nếu có) và gắn user vào context; `account.Required` trả `401 AUTH_REQUIRED`.
- `POST /api/v1/feedbacks`: nếu có phiên hợp lệ và email đã xác minh → **bỏ qua** `senderName/senderEmail` gửi lên, dùng tên + email tài khoản, ghi `app_user_id`. Khách: như hiện nay.
- Thư email: tái dùng `admin.SMTP` (đưa phần gửi ra gói chung `internal/platform/mail`), mẫu tiếng Việt: "Mã xác minh GoWander của bạn: 123456 (hết hạn sau 10 phút)". Không ghi mã vào log.

## 6. App (KMP)

### 6.1 Thành phần
- `account/AccountStore` (StateFlow): `Guest | SignedIn(user)`; đọc token từ `SecureTokenStore` (expect/actual: Keychain / Keystore) lúc mở app; gọi `GET /me` ở nền; lỗi 401 → về Guest, xoá token.
- `GoWanderApi`: thêm header `Authorization` khi có token (một chỗ trong `call`), các hàm auth ở §5.
- `GoogleSignIn` expect/actual: Android Credential Manager; iOS gọi Swift qua protocol đăng ký từ `iosApp` (giống cách `MainViewController` nhận dịch vụ iOS).
- `BiometricUnlock` expect/actual (AUTH-4).

### 6.2 Màn hình (vẽ trên canvas UX-2 trước, chủ dự án duyệt rồi mới code)
Canvas: https://claude.ai/artifact/XtZuZY6CkCk98ZPVd3dSp4 — thêm hàng "Tài khoản".
**Bản dựng để duyệt (16-11-2026):** [`docs/design/ux2-auth/A1-A12-tai-khoan.html`](../../docs/design/ux2-auth/A1-A12-tai-khoan.html) — 12 màn A1–A12, khung 390 × 844, logo thật (`logo_full.png`), ô nhập trắng, nút Google có logo G chính thức, kèm ghi chú hành vi/lỗi/tiếp cận dưới từng màn. Phiên làm bản này không có công cụ artifact nên **chưa đăng lên canvas**; mở file bằng Chrome để duyệt, hoặc dán vào canvas khi cần. Đợt đầu chỉ vẽ email / số điện thoại / tên đăng nhập + mật khẩu và Google.
Phong cách theo mẫu chủ dự án gửi nhưng bằng màu GoWander:
- Nền trắng hoặc khối màu teal `#047A7D` ở đầu trang với logo GoWander; phần form là thẻ trắng bo 24 dp.
- **Ô nhập nền trắng**, viền `#E5F2F1`, bo 16 dp, cao ≥ 52 dp, biểu tượng bên trái (thư, điện thoại, khoá), nút con mắt ở ô mật khẩu; chữ ≥ 16 sp; nhãn phía trên ô (không chỉ placeholder).
- Nút chính teal đầy, cao 56 dp, bo tròn; nút **"Tiếp tục với Google"** nền trắng, viền xám nhạt, logo G nhiều màu bên trái, mũi tên bên phải (như mẫu deeps).
- Dòng nhỏ dưới cùng: "Tiếp tục nghĩa là bạn đồng ý với Điều khoản và Chính sách quyền riêng tư" (link).
- Tương phản ≥ 4.5:1, vùng bấm ≥ 48 dp, đọc được bằng TalkBack/VoiceOver, hỗ trợ chữ lớn.

| # | Màn | Nội dung |
|---|---|---|
| A1 | Hồ sơ (khách) | Thẻ "Đăng nhập để lưu portfolio, kế hoạch và gửi feedback nhanh hơn" + nút Đăng nhập / Tạo tài khoản; "Đã lưu" (trong máy) vẫn hiện; Cài đặt, Lịch sử tìm kiếm |
| A2 | Chào mừng | Logo, 1 câu giới thiệu, "Tiếp tục với Google", "Đăng nhập bằng email / số điện thoại", "Tạo tài khoản", "Để sau" |
| A3 | Đăng nhập | Ô "Email, số điện thoại hoặc tên đăng nhập", "Mật khẩu", "Quên mật khẩu?", nút Đăng nhập, "Tiếp tục với Google" (Face ID: AUTH-4, chưa vẽ ở đợt đầu) |
| A4 | Tạo tài khoản | Tên hiển thị, Tên đăng nhập (báo ngay còn trống hay đã có), Email, Số điện thoại (không bắt buộc), Mật khẩu (+ gợi ý độ mạnh), đồng ý điều khoản, nút Tạo tài khoản, Google |
| A5 | Nhập mã | 6 ô số, đếm ngược gửi lại 60 s, "Đổi email" |
| A6 | Quên mật khẩu | Nhập email → mã → mật khẩu mới |
| A7 | Hồ sơ (đã đăng nhập) | Avatar chữ cái, tên, Portfolio, Kế hoạch, Đã lưu, Lịch sử tìm kiếm, Cài đặt |
| A8 | Cài đặt tài khoản | Tên, tên đăng nhập, email (đã xác minh), số điện thoại (chưa xác minh), đổi mật khẩu, liên kết Google, đăng xuất, đăng xuất mọi thiết bị, xoá tài khoản (Face ID bật/tắt: AUTH-4) |
| A9 | Xoá tài khoản | Giải thích mất gì, xác nhận |
| A10 | Feedbacks (đã đăng nhập) | Khối người gửi **chỉ đọc**: avatar, tên, email, nhãn "Từ hồ sơ đã đăng nhập"; bỏ 2 ô tên/email |
| A11 | Lịch sử tìm kiếm | Danh sách theo ngày, xoá từng mục, "Xoá tất cả", công tắc "Lưu lịch sử" |
| A12 | Ô tìm địa điểm có gợi ý nhanh | Khi ô trống: "Gần đây" (5 mục) kèm nút xoá từng mục |

### 6.3 Chỗ cần đăng nhập (không chặn khi mở app)
- Sửa / chia sẻ Portfolio → mở A2 với câu "Đăng nhập để tạo portfolio".
- Feedbacks: khách vẫn gửi; có dòng "Đăng nhập để khỏi nhập tên và email".
- Kế hoạch, Đã lưu: vẫn trong máy (đồng bộ AUTH-6).

## 7. Lịch sử tìm kiếm

- **Đợt 1 (AUTH-2, chỉ trong máy, cả khách và người đã đăng nhập):** lưu 30 mục gần nhất trong `Prefs` (đã có `Prefs.ios.kt`/Android): `{kind: QUERY|PLACE|ADDRESS, label, placeId?, lat?, lon?, at}`. Ghi khi người dùng **chọn** một kết quả (không ghi mỗi lần gõ). Trùng thì đưa lên đầu.
- Ô tìm (`SearchScreen`, `FindPlace`): khi ô trống và được chạm → hiện "Gần đây" (5 mục, biểu tượng đồng hồ, nút ✕ từng mục) trước danh sách gợi ý; chọn là đi luôn như chọn kết quả.
- Hồ sơ › **Lịch sử tìm kiếm** (A11): toàn bộ, nhóm theo Hôm nay / Hôm qua / Trước đó, xoá từng mục, "Xoá tất cả", công tắc "Lưu lịch sử tìm kiếm" (tắt thì xoá và ngừng lưu).
- Quyền riêng tư: lịch sử **không gửi lên server** ở đợt 1, không dùng cho thống kê; toạ độ chỉ nằm trong máy.
- **Đợt 2 (AUTH-6, tuỳ chọn):** người đã đăng nhập bật "Đồng bộ lịch sử" → bảng `app_search_history(user_id, kind, label, place_id, created_at)` (không lưu toạ độ địa chỉ tự do), giữ 90 ngày, xoá cùng tài khoản.

## 8. Admin (AUTH-3)
- Trang "Người dùng app": tìm theo tên/email/số, cột ngày tạo, đăng nhập cuối, số feedback, trạng thái; tạm khoá/mở khoá (ghi `admin_audit_logs`, chỉ quản trị hệ thống). Không xem mật khẩu, không đăng nhập thay người dùng.
- Chi tiết feedback: nếu có `app_user_id` → "Tài khoản đã đăng nhập · email đã xác minh" + "Đã đóng góp: N feedbacks" (đúng ảnh thiết kế Feedbacks v2); email hiện ngay cho quản trị hệ thống (vẫn ghi nhật ký khi xem).

## 9. Các phase và điều kiện đạt

| Phase | Việc | Đạt khi |
|---|---|---|
| **AUTH-0** | ADR-040 (đã viết, trạng thái Proposed → chủ dự án chốt), vẽ A1–A12 trên canvas | Chủ dự án duyệt canvas — **bản dựng 12 màn đã có (16-11-2026, `docs/design/ux2-auth/A1-A12-tai-khoan.html`), chờ duyệt** |
| **AUTH-1** | Migration 0014, gói `account` (đăng ký, tên đăng nhập, mã, đăng nhập bằng email/số/tên đăng nhập, phiên, quên mật khẩu, me, xoá, **kiểm tra Google ID token**), tách gói mật khẩu + mail dùng chung với Admin, feedback lấy tên/email từ phiên, OpenAPI | **XONG 16-11-2026** trên `feat/app-accounts`: `go vet`, `go test -race ./...`, `go test -tags integration -p 1 ./...` đạt (gồm quyền vai trò DB, khoá 5 lần, mã hết hạn/hết lượt, xoá tài khoản, feedback gỡ người gửi), database dev phiên bản 14, OpenAPI 0.6.0. **Rà soát `ecc:security-reviewer` chưa chạy** (phiên làm việc không có ECC): đã tự rà tay và ghi ở mục 39 của `docs/changes/change-16-11-2026.md` |
| **AUTH-2** | App: `SecureTokenStore`, `AccountStore`, màn A1–A10, **nút Google trên Android và iOS**, Portfolio cần đăng nhập, lịch sử tìm kiếm trong máy (A11, A12) | `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`, `xcodebuild`, ảnh chụp **Android và iOS** từng màn, `mobile/tools/e2e_ux.py` không lỗi |
| **AUTH-3** | Admin trang Người dùng app; chi tiết feedback hiện tài khoản; liên kết/huỷ liên kết Google trong Cài đặt app | Chrome máy tính + điện thoại; đăng nhập Google thật trên emulator + simulator |
| **AUTH-4** (để sau) | Face ID / vân tay | Thử trên simulator (Face ID giả lập: Features › Face ID › Enrolled) và emulator (vân tay giả lập) |
| **AUTH-5** (để sau) | SMS: xác minh số, quên mật khẩu bằng SMS | Có nhà cung cấp + brandname |
| **AUTH-6** | Đồng bộ Kế hoạch, Đã lưu, Portfolio, lịch sử (tuỳ chọn) lên tài khoản; đổi email | — |
| **AUTH-7** (để sau) | Đăng nhập bằng Apple (trước khi lên App Store) | Tài khoản Apple Developer |

Đăng nhập Google nằm ngay trong AUTH-1 (server) và AUTH-2 (app).

### Trạng thái cấu hình Google (16-11-2026, đã xong)
- Project Google Cloud mới **GoWander** (chủ dự án tạo lại từ đầu; client Web của Admin cũ bỏ đi). Google Auth Platform: **External**, **Testing** (tối đa 100 người thử, phải thêm Gmail vào **Test users**), Branding đã lưu (chưa có logo, chưa có trang chủ/chính sách → chưa Publish được; cần trước khi ra mắt).
- 3 OAuth client: **Web** `GoWander server`, **Android** (package `com.gowander.app`, SHA-1 khoá debug `27:7A:BC:E9:26:7A:8E:91:D7:E5:7D:13:70:E0:C2:25:65:F4:51:5D`), **iOS** (bundle `com.legiabao.gowander.dev`).
- `.env` (không vào Git): `GOOGLE_CLIENT_ID` = Web client ID (server dùng làm `aud`; Android truyền làm `serverClientId`), `GOOGLE_ANDROID_CLIENT_ID`, `GOOGLE_IOS_CLIENT_ID` (iOS: client ID + reversed client ID làm URL scheme trong `Info.plist`). **Không dùng client secret**: dòng `GOOGLE_CLIENT_SECRET` cũ đã xoá; secret của client Web mới bị dán vào chat ngày 16-11 → chủ dự án được khuyên Reset secret trong console và không lưu ở đâu.
- Còn cho chủ dự án: thêm Test users; kiểm tra Data Access có `openid`, `email`, `profile`; khi phát hành bản release Android thêm SHA-1 khoá release vào client Android.

## 10. Rủi ro
- Số điện thoại chưa xác minh (§3.2). Gmail SMTP giới hạn số thư/ngày (đủ cho pilot; khi lớn chuyển dịch vụ thư). Đăng nhập Google cần cấu hình đúng SHA-1/bundle, sai là lỗi khó hiểu: ghi rõ trong runbook. Apple 4.8 (§3.5). Token trong máy bị lộ nếu máy đã root/jailbreak: chấp nhận, có "đăng xuất mọi thiết bị". Luật dữ liệu cá nhân VN: cần màn đồng ý + chính sách quyền riêng tư trước pilot.

## 11. Ghi chú bàn giao cho agent khác
- Đọc `AGENTS.md` (không chạy lệnh Git ghi; mỗi thay đổi một mục trong `docs/changes/change-DD-MM-YYYY.md` tiếng Việt; đưa chủ dự án lệnh commit, không có dòng Co-Authored-By).
- Giao diện: **vẽ trên canvas trước**, kiểm tra **cả Android và iOS**, chụp màn hình làm bằng chứng; máy ảo Android hiện có thể mất internet (Cold Boot nếu cần).
- Không in hay dán secret (`.env`: DB, SMTP, R2, Google). Email chủ dự án là riêng tư.
- Code mẫu nên đọc trước: `backend/internal/admin/password.go`, `mail.go`, `store.go` (đăng nhập Admin, khoá, mã đặt lại); `backend/internal/feedback` (multipart, giới hạn IP); `mobile/shared/.../feedback/FeedbackScreen.kt` (form, ô nhập); `api/GoWanderApi.kt` (`call`, lỗi `ApiError`).
- Lệnh kiểm tra: xem `AGENTS.md` và phần "Bằng chứng" các mục gần nhất trong `docs/changes/change-16-11-2026.md`.
