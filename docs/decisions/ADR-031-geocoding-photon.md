# ADR-031 — Tìm địa chỉ (geocoding): Photon tự chạy, Vietmap là hướng nâng cấp

- **Trạng thái:** Accepted cho M06b · **Ngày:** 14-11-2026
- **Liên quan:** ADR-027 (API Go), ADR-028 (quy tắc nguồn dữ liệu), `infrastructure/photon/README.md`, `backend/internal/geocode/`

## Bối cảnh

Chủ dự án gõ địa chỉ thường ("770 tỉnh lộ 15…", "128 Trương Đăng Quế…") thì ô tìm kiếm không ra gì: app chỉ tìm tên địa điểm/ga trong database Discover. Cần geocoding.

## Quyết định (chủ dự án chọn 14-11-2026: "dùng cái free, sau đó đề xuất Vietmap")

- **Photon 0.7.4** (Apache 2.0) tự chạy trên máy/máy chủ, index Việt Nam dựng sẵn từ OSM (graphhopper.com, 21-07-2025, MD5 kiểm tra), chỉ lắng nghe `127.0.0.1:2322`. Jar ghim SHA-256.
- API Go: `GET /api/v1/geocode?q=&lat=&lon=&limit=` (q 2–200 ký tự UTF-8, limit 1–10), trả `results[]` + `source` (© OpenStreetMap contributors, ODbL); lỗi `GEOCODER_UNAVAILABLE` (503). Kết quả giới hạn trong khung TP.HCM (`bbox`), ưu tiên gần vị trí người dùng.
- Photon **không** nằm trong `/health/ready`: chỉ là chức năng phụ; khi dừng, ga/địa điểm và tìm tuyến vẫn chạy.
- Log không ghi chuỗi người dùng gõ (có thể là địa chỉ nhà).
- App (M2): mục "Địa chỉ" dưới ga/địa điểm, kèm dòng ghi nguồn OSM; chọn địa chỉ → điểm đến. Khi người dùng gõ số nhà mà kết quả không có số nhà, kết quả được gắn `approximate` và app ghi "Vị trí gần đúng: không tìm thấy số nhà" (chủ dự án đồng ý 14-11-2026).
- Chọn trên bản đồ (M4): `GET /api/v1/geocode/reverse?lat=&lon=` (Photon `/reverse`, tối đa 3) cùng địa điểm/ga gần (API nearby, 150 m) được gợi ý khi ghim dừng; người dùng chọn một nơi có thật hoặc giữ "Đúng điểm ghim" (chủ dự án yêu cầu 14-11-2026: chỉ ghim thì không biết có chính xác không).

## Không chọn

- Nominatim/Photon công cộng: chính sách cấm dùng cho tìm-khi-gõ, giới hạn 1 request/giây.
- Google Geocoding: 10.000 request/tháng miễn phí rồi 5 USD/1.000 (giá tra 14-11-2026), Places Autocomplete tính riêng; điều khoản Google không cho hiển thị kết quả trên bản đồ không phải Google (GoWander dùng OpenFreeMap).

## Hệ quả và giới hạn đã thấy khi thử thật

- OSM thiếu **số nhà** ở Việt Nam: "770 tỉnh lộ 15, ấp Bến Mương, An Nhơn Tây" → ra đường Tỉnh lộ 15 và mốc trong ấp Bến Mương, không có số 770; "128 Trương Đăng Quế" → ra đường Trương Đăng Quế (Gò Vấp), không có số 128.
- Tên phường mới sau sáp nhập 2025 (ví dụ "Hạnh Thông") có thể chưa có trong index tháng 7-2025.
- Nhiều địa chỉ nằm ngoài vùng dữ liệu tuyến (hành lang Metro số 1) → tìm tuyến báo ngoài vùng hỗ trợ; đó là giới hạn dữ liệu tuyến (M01), không phải geocoding.

## Hướng nâng cấp: Vietmap (sau pilot)

- Lý do: dữ liệu địa chỉ Việt Nam đầy đủ hơn, hỗ trợ cả định dạng địa chỉ trước/sau sáp nhập (theo tài liệu Autocomplete v4 của Vietmap); bắt buộc gọi từ server, khớp kiến trúc hiện tại.
- Giá: Vietmap không công bố; xin báo giá qua maps.info@vietmap.vn hoặc Zalo OA "Vietmap Solutions". Hỏi: giá mỗi 1.000 request geocode/autocomplete, hạn mức miễn phí, quyền cache, được hiển thị trên bản đồ OpenFreeMap không.
- Cách chuyển: thêm adapter Vietmap cho interface `geocode.Searcher` và chọn bằng cấu hình; app không phải sửa.
- Khi nào: khi log pilot cho thấy nhiều lượt tìm có số nhà mà Photon không trả kết quả có số nhà.
