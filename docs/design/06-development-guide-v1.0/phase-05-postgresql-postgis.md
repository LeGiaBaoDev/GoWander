08. PHASE 5 — POSTGRESQL &
POSTGIS
### 8.1. Objective

Thiết lập database nền tảng và các bảng tối thiểu cho Discover.

### Step 5.1 — Create Local Database Service

Sử dụng PostgreSQL image tương thích với PostGIS.

Khóa image version.

Cấu hình:

- ​   Persistent Volume.
- ​   Database Name.
- ​   Application User.
- ​   Development Password trong .env không commit.

- ​ Healthcheck.
- ​ Internal Docker Network.

### Step 5.2 — Enable PostGIS

Chạy trong database development:

None‎
CREATE EXTENSION IF NOT EXISTS postgis;

### Step 5.3 — Configure Database Access

Backend dùng pgx và connection pool.

Không sử dụng PostgreSQL superuser cho ứng dụng.

### Step 5.4 — Build Initial Migrations

Chỉ tạo các bảng cần thiết:

- ​ place_categories.
- ​ places.
- ​ place_category_links.
- ​ place_media.
- ​ food_items.
- ​ place_foods.
- ​ historical_stories.

Conditional tables không cần tạo trước.

### Step 5.5 — Seed Pilot Data

Dữ liệu mẫu gồm địa điểm được kiểm tra, tọa độ và nội dung theo giấy phép phù hợp.

Không tạo địa điểm giả trong production dataset.

### Step 5.6 — Implement Basic Queries

- ​     Get Places.
- ​     Get Place by ID.
- ​     Search by Name.
- ​     Get Places by Category.
- ​     Nearby Places.

- ​ Get Place Details.

### Step 5.7 — Verify Spatial Queries

Sử dụng ST_DWithin và GiST index theo loại dữ liệu phù hợp.

Kiểm tra:

- ​   Radius.
- ​   Coordinates.
- ​   Pagination.
- ​   Published Status.
- ​   Query Plan.

### Exit Criteria

- ​   PostgreSQL chạy được.
- ​   PostGIS hoạt động.
- ​   Migrations chạy được.
- ​   API lấy dữ liệu thật.
- ​   Spatial Query trả kết quả đúng.

