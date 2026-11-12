# ADR-028 — PostgreSQL/PostGIS cho Discover: image, quyền, migration, nguồn dữ liệu

- **Trạng thái:** Accepted cho M05 · **Ngày:** 13-11-2026
- **Liên quan:** `PIPELINE.md` mục 9 (M05), `docs/design/05-database-design-v1.2/03-discover-tables.md`, `docs/design/06-development-guide-v1.0/phase-05-postgresql-postgis.md`, ADR-020 (OSM), ADR-022 (ngôn ngữ), ADR-027 (API Go)

## Quyết định

### 1. Phiên bản

| Thành phần | Chọn | Ghi chú |
|---|---|---|
| Database | `postgis/postgis:18-3.6` (PostgreSQL 18, PostGIS 3.6), khoá theo digest `sha256:4a43e3c3e5d55b9208240fc71db543b79c638766280c625a425bd0a2390989c4` | Image chính thức của PostGIS. **Chỉ có bản amd64**: trên Mac Apple Silicon chạy qua Rosetta (chậm hơn, đủ cho dev). Máy chủ Linux amd64 chạy trực tiếp, cùng image. Không chọn `18-3.7` vì mới phát hành 09-11-2026 |
| Driver Go | `github.com/jackc/pgx/v5` v5.13.0 + `pgxpool` | |
| Migration | `github.com/golang-migrate/migrate/v4` v4.20.1 (thư viện, driver pgx v5, nguồn `iofs`) | File SQL đánh số `NNNN_name.up.sql` / `.down.sql`, nhúng vào binary bằng `embed` |
| Truy vấn | SQL viết tay thành hằng số có tên trong Go | Chưa dùng sqlc: 5 truy vấn, chưa đáng thêm công cụ sinh code. Xem lại khi quá khoảng 15 truy vấn |

### 2. Ba vai trò, app không bao giờ là superuser

| Role | Dùng cho | Quyền |
|---|---|---|
| `postgres` (superuser của image) | **Chỉ** script khởi tạo chạy một lần khi tạo volume (`infrastructure/postgres/init/`) | `CREATE EXTENSION postgis`, tạo hai role bên dưới, tạo schema |
| `gowander_owner` | Chạy migration và nạp seed | Sở hữu schema `gowander`; không phải superuser, không `CREATEDB`/`CREATEROLE` |
| `gowander_app` | API Go lúc chạy | `USAGE` trên schema; **chỉ `SELECT`** trên bảng Discover (M05 chỉ đọc). Quyền ghi thêm ở M07 khi có Admin |

- Bảng nằm trong schema `gowander`; PostGIS nằm ở `public`. `search_path` của hai role là `gowander, public`.
- **Không** dùng `ALTER DEFAULT PRIVILEGES`: mỗi migration tự `GRANT SELECT` cho đúng bảng API công khai được đọc, để bảng Admin/người dùng sau này (M07) không tự nhiên đọc được qua role app. Test migration kiểm tra danh sách quyền của `gowander_app` (sửa sau security review 13-11-2026).
- Mật khẩu ở `.env` (không commit); `.env.example` chỉ có giá trị giữ chỗ.

### 3. Bảng của M05

Đúng 7 bảng PIPELINE liệt kê: `place_categories`, `places`, `place_category_links`, `place_media`, `food_items`, `place_foods`, `historical_stories`, theo thiết kế `03-discover-tables.md`, cộng:

- `places.source` (`OSM` hoặc `CURATED`), `places.source_ref` (ví dụ `node/123456`), `places.license`, `places.attribution`: mỗi địa điểm truy được về nguồn và giấy phép.
- `places.name_en`: tên tiếng Anh nếu nguồn có (ADR-022 cần VI + EN). Bản dịch nội dung dài chưa có thiết kế; quyết định sau.
- Không tạo `place_details`, `story_sources`, `trip_ideas`, `trip_idea_places` (thiết kế ghi "Conditional", chưa có màn hình cần).

### 4. Nguồn dữ liệu (chủ dự án chọn A + B, 13-11-2026)

- **A — OpenStreetMap:** script `scripts/build_discover_seed.py` lọc POI có tên trong bán kính 800 m quanh 14 ga Metro số 1 từ file OSM đã tải (`data/raw/osm/`), đổi tag OSM sang danh mục GoWander, xuất seed SQL. Giấy phép ODbL 1.0, ghi "© OpenStreetMap contributors". Chỉ có tên, loại, toạ độ, địa chỉ nếu có; không có mô tả, không có ảnh.
- **B — nhập tay có nguồn:** `data/curated/places.csv`, do chủ dự án điền. Mỗi dòng bắt buộc có nguồn (URL) và giấy phép của phần mô tả; script từ chối dòng thiếu. Agent không tự viết mô tả không có nguồn.
- `food_items`, `place_foods`, `historical_stories`, `place_media`: **không có dữ liệu seed** vì chưa có nguồn. Test dùng dữ liệu riêng, gắn nhãn `FIXTURE`, chỉ nạp vào database test.

### 5. API (thêm vào `openapi.yaml`)

- `GET /api/v1/place-categories`
- `GET /api/v1/places?category=&q=&limit=&cursor=`
- `GET /api/v1/places/nearby?lat=&lon=&radiusM=&category=&limit=`
- `GET /api/v1/places/{id}`
- Chỉ trả địa điểm `PUBLISHED`. Lỗi dùng cùng dạng của ADR-027; thêm `PLACE_NOT_FOUND` (404) và `DATA_STORE_UNAVAILABLE` (503).
- `/health/ready` kiểm tra cả database và OTP.

### 6. Seed (sau database review 13-11-2026)

- Seed là SQL idempotent, nạp bằng role owner trong một transaction: chạy lại không đổi dòng nào nếu dữ liệu không đổi (`IS DISTINCT FROM`).
- Liên kết danh mục đi theo nguồn: liên kết không còn đúng bị xoá khi nạp lại.
- Địa điểm biến mất khỏi nguồn được chuyển sang `HIDDEN`, không xoá (giữ id). Seed không đổi `status` của địa điểm còn trong nguồn, nên địa điểm bị ẩn tay vẫn ẩn.
- Từ khoá tìm kiếm tối thiểu 2 ký tự (1 ký tự không dùng được chỉ mục trigram).

### 7. Để sau

- Trigger cập nhật `updated_at` và chặn vòng lặp cha–con của `place_categories`: làm cùng quyền ghi ở M07.
- Hai biến thể truy vấn danh sách (có/không có `q`) khi bảng vượt khoảng 10.000 dòng, để planner dùng chỉ mục tốt hơn.

## Hệ quả

- Migration chạy được bằng role thường; chỉ việc tạo extension cần superuser, và nó nằm ở script khởi tạo hạ tầng, không ở migration.
- Tái tạo dữ liệu A bằng script; dữ liệu B nằm trong Git dạng CSV, review được.

## Rủi ro

- Image amd64 chạy qua Rosetta trên Mac: chậm hơn; không dùng để đo hiệu năng cho máy chủ.
- POI của OSM ở TP.HCM không đều (nhiều quán ăn, ít tên tiếng Anh, ít mô tả); Discover sẽ "mỏng" cho tới khi có dữ liệu B.
- golang-migrate đánh dấu database "dirty" nếu migration lỗi giữa chừng; cách sửa ghi trong README database.
