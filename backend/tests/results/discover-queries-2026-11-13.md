# Truy vấn Discover — EXPLAIN ANALYZE, 13-11-2026

- Database: `postgis/postgis:18-3.6` (PostGIS 3.6.4) chạy **qua Rosetta** trên Mac Apple Silicon — thời gian chỉ để so sánh tương đối, không đại diện cho máy chủ.
- Dữ liệu: `data/discover/seed.sql` — 1.016 địa điểm OSM, 1.024 liên kết danh mục (`data/discover/seed.manifest.txt`).
- Role chạy: `gowander_app` (chỉ `SELECT`, `statement_timeout` 5 s). Điểm gốc: ga Bến Thành (10.770775, 106.697569).

| Truy vấn | Kế hoạch chính | Dòng | Thời gian |
|---|---|---|---|
| Gần đây 1 km, 20 kết quả | `Bitmap Index Scan on places_location_gix` (GiST) → 596 ứng viên → 520 đúng bán kính | 520 | ~64–68 ms |
| Gần đây 1 km, danh mục `food` | `place_category_links_category_idx` (540 dòng) → `places_pkey` | 279 | ~64 ms |
| Danh sách `q=ben thanh` | Seq Scan (1.016 dòng; rẻ hơn chỉ mục trigram ở cỡ này) | 4 | ~2 ms |
| Danh sách `category=museum` | `place_categories_slug_key` → `place_category_links_category_idx` → `places_pkey` | 9 | ~6 ms |

Nhận xét:

- `ST_DWithin` dùng chỉ mục GiST đúng như ADR-028; phần lớn thời gian là tính `ST_Distance` trên spheroid cho từng điểm trong bán kính (để sắp xếp), cộng chi phí giả lập amd64.
- Khi dữ liệu tăng (vài chục nghìn địa điểm), cân nhắc giới hạn bán kính mặc định nhỏ hơn hoặc sắp xếp bằng toán tử KNN `<->`. Chưa cần ở M05.
- Số địa điểm đã xuất bản theo danh mục (bao gồm danh mục phụ): museum 9, historic 18, attraction 34, worship 59, park 34, market 29, food 540, cafe 290, entertainment 11.
