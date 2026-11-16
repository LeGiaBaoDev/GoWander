# Cloudflare R2 cho ảnh GoWander (ADR-035)

Chủ dự án làm các bước 1–5 trên trang Cloudflare; agent làm bước 6 (adapter R2 trong code) khi có thông tin.
**Không dán Secret Access Key vào chat, issue hay commit.** Chỉ ghi vào file `.env` trên máy chạy API.

## 1. Tài khoản và gói R2

1. Đăng nhập <https://dash.cloudflare.com> (tạo tài khoản nếu chưa có).
2. Menu trái: **Storage & databases › R2 Object Storage** › **Purchase R2 / Get started**. Cloudflare yêu cầu thêm thẻ thanh toán, nhưng gói miễn phí đủ cho pilot: 10 GB lưu trữ, 1 triệu thao tác ghi và 10 triệu thao tác đọc mỗi tháng, **không tính phí băng thông tải ra**. Mỗi ảnh GoWander là 2 file JPEG (480 px và 1600 px), tổng khoảng 100–400 KB.

## 2. Tạo bucket

1. **R2 Object Storage › Create bucket**.
2. Tên: `gowander-media` (chữ thường, không dấu).
3. Location: **Automatic**, hoặc gợi ý **Asia-Pacific (APAC)** cho gần người dùng Việt Nam.
4. Default storage class: **Standard**. Bấm **Create bucket**.

## 3. Cho phép đọc công khai (để app và Admin hiện ảnh)

Ảnh đã bỏ EXIF/GPS và có ghi công, nên được phép đọc công khai; **chỉ API được ghi**.

- **Nên dùng (production):** bucket › **Settings › Custom Domains › Connect Domain**, nhập ví dụ `media.<tên-miền-của-bạn>`. Tên miền phải đang dùng DNS của Cloudflare (thêm site vào Cloudflare trước nếu chưa). Cloudflare tự tạo bản ghi DNS và chứng chỉ https; có cache CDN.
- **Chỉ để thử:** bucket › **Settings › Public Development URL (r2.dev) › Enable**. Địa chỉ dạng `https://pub-xxxx.r2.dev`, bị giới hạn tốc độ, Cloudflare ghi rõ không dùng cho production.

CORS: **không cần** (trình duyệt và app chỉ tải ảnh bằng GET thường; việc tải lên đi qua API, không đi thẳng từ trình duyệt vào R2).

## 4. Tạo khoá API chỉ cho bucket này

1. **R2 Object Storage › Manage R2 API Tokens** (hoặc **API › Manage API tokens** trong trang R2) › **Create API token** (loại *User API token* hoặc *Account API token* đều được; account token không mất khi người tạo rời tổ chức).
2. Token name: `gowander-api-media`.
3. Permissions: **Object Read & Write**.
4. Specify bucket(s): **Apply to specific buckets only** › `gowander-media`.
5. TTL: để trống (không hết hạn) hoặc chọn ngày rồi nhớ đổi khoá trước hạn.
6. Bấm **Create API Token**. Trang kết quả hiện **một lần duy nhất**:
   - Access Key ID
   - Secret Access Key
   - Endpoint S3: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`

## 5. Ghi vào `.env` (không đưa vào Git)

```
R2_ACCOUNT_ID=<ACCOUNT_ID trong endpoint>
R2_ACCESS_KEY_ID=<Access Key ID>
R2_SECRET_ACCESS_KEY=<Secret Access Key>
R2_BUCKET=gowander-media
MEDIA_BASE_URL=https://media.<tên-miền-của-bạn>
```

Sau đó báo agent: "đã có R2, tên miền là media.…" (chỉ tên miền, không gửi khoá).

## 6. Việc của agent sau khi có R2 (đã làm 16-11-2026, bucket `gowander-media`, địa chỉ r2.dev)

- Viết adapter `media.R2` (S3 API, chữ ký SigV4) thay cho `media.Local` khi `R2_BUCKET` có giá trị; test với bucket thật (tải lên, đọc qua tên miền, xoá).
- Bỏ chặn "Admin https khi chưa có R2" trong `config.go`.
- Thêm tên miền ảnh vào `PHOTO_HOSTS` của app (`GoWanderApi.kt`) để app tải ảnh từ đó; kiểm tra Android và iOS.
- Chép ảnh đang ở `backend/var/media` (nếu có ảnh thật) lên bucket.
- Cập nhật ADR-035, `backend/README.md`, change record.

## Khi lộ khoá

Vào **Manage R2 API Tokens**, xoá token cũ (**Roll** hoặc **Delete**), tạo token mới như bước 4, sửa `.env`, khởi động lại API.

## Kiểm tra lại bucket bất cứ lúc nào

```
cd backend
set -a && . ../.env && set +a
R2_PUBLIC_URL=$MEDIA_BASE_URL go test -tags r2 -run TestR2Live -count=1 -v ./internal/media/
```

Test tải 2 file thử lên, đọc lại qua địa chỉ công khai rồi xoá.
