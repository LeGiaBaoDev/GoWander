# Google sign-in — chạy và gỡ lỗi

> Dùng cho app (ADR-040, AUTH-1…AUTH-3). Tài khoản Admin không dùng Google từ ADR-039.

## Cấu hình đang có

| Việc | Ở đâu |
|---|---|
| Project Google Cloud | **GoWander**, Audience **External · Testing** (tối đa 100 người thử; mỗi người phải nằm trong **Test users**) |
| 3 OAuth client | Web (`GoWander server`), Android (package `com.gowander.app` + SHA-1 debug **và** release), iOS (bundle `com.legiabao.gowander.dev`) |
| Server | `.env`: `GOOGLE_CLIENT_ID` (Web), `GOOGLE_ANDROID_CLIENT_ID`, `GOOGLE_IOS_CLIENT_ID` — server chấp nhận token cấp cho **bất kỳ client nào trong ba** |
| Android app | `BuildConfig.GOOGLE_WEB_CLIENT_ID` đọc `GOOGLE_CLIENT_ID` từ `.env` lúc build (Gradle). Thiếu biến ⇒ nút Google ẩn |
| iOS app | `Info.plist`: `GIDClientID` (client iOS) + `CFBundleURLTypes` với scheme `com.googleusercontent.apps.<id>` (client ID đảo ngược) |

Không có client secret ở đâu cả: app chỉ gửi **ID token**, server kiểm chữ ký với JWKS của Google.

## Kiểm nhanh (không cần app)

```bash
# 1. Đường dẫn sống và Google bật (token rác phải trả 401, không phải 503)
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8090/api/v1/auth/google \
  -H 'Content-Type: application/json' -d '{"idToken":"a.b.c"}'

# 2. Khoá công khai của Google lấy được
curl -s -o /dev/null -w '%{http_code}\n' https://www.googleapis.com/oauth2/v3/certs
```

Log của API ghi rõ lý do khi từ chối (không ghi token):

```bash
grep "google token refused" backend/var/api.log | tail -3
```

## Lỗi hay gặp

| Hiện ra | Nguyên nhân | Sửa |
|---|---|---|
| `google token invalid: audience …expected one of […]` | Token cấp cho một client **không nằm trong `.env`** (thường: project khác, hoặc client vừa tạo chưa thêm biến) | Thêm client ID đó vào `.env` (`GOOGLE_*_CLIENT_ID`), khởi động lại API |
| `google token invalid: issuer` | Token không do Google cấp | Kiểm app đang dùng SDK/Credential Manager thật, không phải token tự tạo |
| `google token invalid: expired` | Đồng hồ máy lệch, hoặc token cũ được dùng lại | Đồng bộ giờ máy; bấm lại |
| `google token invalid: email not verified` | Tài khoản Google chưa xác minh email | Dùng tài khoản khác |
| App báo **"Chưa đăng nhập được bằng Google…"** | Máy ảo chưa có tài khoản Google, hoặc Google không mở được bảng chọn | Android: Settings › Passwords & accounts › Add account › Google (AVD phải là image **Google Play**); iOS: Settings › Sign in to your iPhone |
| Trang Google báo **Access blocked** | Gmail chưa nằm trong **Test users** (Audience đang Testing) | Thêm Gmail đó vào Test users |
| Android: **DEVELOPER_ERROR / 10** | SHA-1 hoặc package không khớp client Android | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` rồi so SHA-1; bản release phải thêm SHA-1 khoá release |
| iOS: bấm Google không mở gì | Thiếu URL scheme, hoặc `.onOpenURL` không gọi `GIDSignIn.handle` | Kiểm `CFBundleURLTypes` trong `Info.plist` và `iOSApp.swift` |
| `409 LINK_REQUIRED` | Email Google đã thuộc một tài khoản **mật khẩu** | Đăng nhập bằng mật khẩu → Hồ sơ › tài khoản › Google › **Liên kết** (A8) |

## Đăng nhập Google tạo tài khoản thế nào

1. Lần đầu (email chưa có ai dùng): tạo `app_users` với `email_verified_at = now()`, `google_sub`, **không mật khẩu**; app mở **A13 Hoàn tất hồ sơ** để chọn tên đăng nhập / thêm số / đặt mật khẩu.
2. Lần sau: vào đúng tài khoản theo `google_sub`.
3. Email đã có tài khoản mật khẩu: **không tự gộp**, trả `LINK_REQUIRED`.

## Việc phải làm trước khi phát hành cho người ngoài

- **Publish** OAuth consent screen: cần trang chủ, **chính sách quyền riêng tư**, logo; Testing chỉ cho 100 người thử.
- Thêm **SHA-1 khoá release** vào client Android.
- iOS: cân nhắc **Đăng nhập bằng Apple** (App Store Guideline 4.8) — AUTH-7.
