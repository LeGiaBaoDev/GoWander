# ADR-035 — Lưu ảnh: Cloudflare R2, phát qua CDN của Cloudflare

- **Trạng thái:** Accepted · **Ngày:** 15-11-2026 · nhận ảnh trong Admin đã làm 16-11-2026, lưu trên máy cho môi trường dev; adapter R2 chưa làm (chờ tài khoản R2)
- **Liên quan:** ADR-028 (Discover, bảng `place_media`), ADR-033 (ảnh đường phố), `PIPELINE.md` §5c (bảng dữ liệu mẫu: ảnh)

## Bối cảnh

App cần ảnh thật cho quán, món ăn, cẩm nang vùng và portfolio. Hiện bảng `place_media` trống (0 dòng, kiểm 15-11-2026); app vẽ ô màu mẫu. App bản đồ hiển thị rất nhiều ảnh nhỏ, nên khoản phí chính là lưu lượng tải ảnh ra.

## Quyết định (chủ dự án chốt 15-11-2026: "chốt cloudflare")

- **Kho ảnh:** Cloudflare R2 (tương thích S3). Bucket riêng tư; app chỉ đọc qua tên miền CDN của Cloudflare.
- **Database chỉ lưu đường dẫn:** `place_media.storage_key` là khoá trong bucket, không lưu URL đầy đủ và không lưu ảnh trong PostgreSQL. API trả URL ghép từ một biến cấu hình (tên miền CDN).
- **Cỡ ảnh:** server tạo sẵn vài cỡ cố định lúc nhận ảnh (ví dụ ảnh thu nhỏ cho thẻ và danh sách, ảnh vừa cho trang địa điểm), không biến đổi ảnh theo yêu cầu.
- **Bí mật:** khoá truy cập R2 chỉ nằm trong `.env` / kho bí mật của máy chủ, không vào Git (AGENTS.md quy tắc 5).
- **Ghi công:** mỗi ảnh giữ `source_credit` và `license_info` như schema hiện có; app hiện ghi công cạnh ảnh.

## Không chọn

- **Cloudinary:** tiện vì tự cắt, nén, đổi định dạng qua URL, nhưng tính phí theo lưu lượng và số lần biến đổi, khó đoán khi lượt xem tăng. Xem lại nếu sau này cần biến đổi ảnh phức tạp.
- **Lưu ảnh trong PostgreSQL hoặc trên đĩa máy chủ API:** làm phình bản sao lưu (M08) và không có CDN.

## Hệ quả và việc chưa làm

- **Giá chưa tra lại tại ngày quyết định** (lý do chọn R2 là không tính phí tải ra, theo hiểu biết chung): phải kiểm bảng giá R2 và hạn mức miễn phí trước khi triển khai.
- Cần quy trình nhận ảnh (ai tải lên, kiểm duyệt, giấy phép từng ảnh): thuộc CONTENT / M07 Admin, có `ecc:security-reviewer` xem (tải file lên là đầu vào từ người dùng).
- Thẻ nổi trên bản đồ giữ ô biểu tượng cho tới khi có ảnh thật của địa điểm (chủ dự án, 15-11-2026: đã thử ảnh đường phố làm ảnh thu nhỏ rồi bỏ).

## Triển khai đợt 1 (16-11-2026)

- `backend/internal/media`: nhận ảnh JPEG/PNG/WebP ≤ 10 MB, kiểm cỡ trước khi giải mã (≤ 12.000 px mỗi cạnh, ≤ 40 triệu điểm ảnh), giải mã để chắc là ảnh, mã hoá lại JPEG ở 480 px và 1600 px (bỏ toàn bộ metadata, kể cả GPS trong EXIF), đặt tên theo SHA-256 của file gốc (`img/<32 hex>-480.jpg`). Interface `Storage`; bản `Local` lưu vào `MEDIA_DIR` (mặc định `backend/var/media`, không vào Git) và phát ở `/media/` với `nosniff`, cache 1 năm.
- **Chỉ cho môi trường dev:** cấu hình từ chối khởi động khi `ADMIN_BASE_URL` là https mà chưa có R2 (đúng quyết định "không lưu ảnh trên máy chủ API"). Khi có R2: viết adapter `Storage` cho R2 (S3 API), đặt `MEDIA_BASE_URL` là tên miền CDN; database, Admin và API không đổi.
- Admin: ảnh địa điểm (nhiều ảnh, một ảnh bìa, xoá), ảnh món ăn và câu chuyện (một ảnh); bắt buộc ghi công và chọn giấy phép (Ảnh của GoWander, Chủ địa điểm cho phép, CC BY 4.0, CC BY-SA 4.0, CC0); file chỉ bị xoá khi không còn mục nào dùng.
- API công khai trả `url` (1600 px), `thumbUrl` (480 px), ghi công và giấy phép.

## Đợt 2 (16-11-2026): app hiện ảnh thật

- `MEDIA_BASE_URL` mặc định `/media` (đường dẫn tương đối): app ghép với địa chỉ API nó đang dùng (emulator, simulator, Tailscale đều đúng), Admin dùng cùng đường dẫn. Khi có R2: đặt địa chỉ https của bucket.
- App chỉ tải ảnh GoWander từ `/media/` trên chính API, hoặc từ tên miền R2 ghi cứng trong `PHOTO_HOSTS` (đang rỗng); ảnh khác vẫn chỉ từ Mapillary. Ảnh được giải mã trên luồng nền, giữ 40 ảnh gần nhất; lỗi thì hiện ô mẫu như trước.
- Hướng dẫn tạo R2 cho chủ dự án: `docs/runbooks/cloudflare-r2.md`.

## Đợt 3 (16-11-2026): R2 chạy thật

- `media.R2` (`backend/internal/media/r2.go`): PUT/DELETE vào bucket qua S3 API, ký AWS SigV4 (region `auto`) bằng thư viện chuẩn, không thêm SDK; chỉ nhận tên file do `media` tạo; xoá file đã mất không lỗi; lỗi trả mã HTTP và mã lỗi S3, không có khoá. Cấu hình `R2_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`; có R2 thì `MEDIA_BASE_URL` phải là https và Admin được chạy trên https; không có R2 thì vẫn lưu trên máy như trước.
- Đang dùng bucket `gowander-media`, địa chỉ công khai r2.dev (giới hạn tốc độ, không cache CDN): đổi sang tên miền riêng trước khi ra mắt (sửa `MEDIA_BASE_URL` và `PHOTO_HOSTS` của app).
