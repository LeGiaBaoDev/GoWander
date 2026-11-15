# ADR-035 — Lưu ảnh: Cloudflare R2, phát qua CDN của Cloudflare

- **Trạng thái:** Accepted (chưa triển khai) · **Ngày:** 15-11-2026
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
