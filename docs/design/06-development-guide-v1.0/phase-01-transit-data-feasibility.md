04. PHASE 1 — TRANSIT DATA
FEASIBILITY
### 4.1. Objective

Xác minh có thể xây dựng dữ liệu routing bus/metro/walking cho phạm vi thí điểm tại
TP.HCM.

Đây là bước bắt buộc trước khi phát triển chức năng tìm tuyến thực tế.

### Step 1.1 — Define Pilot Region

Chọn một đến hai hành lang hoặc khu vực thí điểm.

Tiêu chí:

- ​   Có điểm tham quan.
- ​   Có tuyến bus/metro phù hợp.
- ​   Có dữ liệu được sử dụng hợp lệ.
- ​   Có thể kiểm tra hành trình thủ công.
- ​   Phạm vi đủ nhỏ để test nhanh.

Lưu khu vực, giới hạn địa lý và lý do chọn trong:

docs/research/pilot-region.md

### Step 1.2 — Research Data Sources

Nghiên cứu:

- ​   Cổng dữ liệu TP.HCM.
- ​   Cơ quan vận tải và đơn vị vận hành.
- ​   Các nguồn GTFS công khai.
- ​   OpenStreetMap.

- ​ Các nguồn dữ liệu đã được cấp phép.
- ​ Điều khoản bản quyền và tái phân phối.

Không tự động xem một trang hiển thị dữ liệu là bằng chứng có quyền tải, lưu hoặc khai
thác thương mại dữ liệu đó.

### Step 1.3 — Prepare Dataset Inventory

Tạo:

data/sources/source-inventory.csv

Các cột:

    Field               Meaning

    source_nam       Tên nguồn
e

    provider         Đơn vị cung cấp

    type             GTFS, OSM, GTFS-RT...

    url              Link nguồn

    license          Giấy phép/điều kiện

    coverage         Phạm vi

    freshness        Mức cập nhật

    status           Candidate/Verified/Rejecte
d

    notes            Giới hạn và ghi chú

### Step 1.4 — Download Licensed Test Data

Khi đã xác minh được quyền sử dụng, đưa dữ liệu vào:

- ​ data/raw/transit/
- ​ data/raw/osm/

Không commit dataset lớn lên repository mặc định.

Ghi checksum và nguồn trong manifest để có thể tái tạo kết quả.

### Step 1.5 — Validate GTFS Structure


Kiểm tra các thành phần thường dùng:

- ​ agency.txt.
- ​ routes.txt.
- ​ trips.txt.
- ​ stops.txt.
- ​ stop_times.txt.
- ​ calendar.txt hoặc calendar_dates.txt theo quy định.

Kiểm tra:

- ​   Referential integrity.
- ​   Stop coordinates.
- ​   Trip/route mapping.
- ​   Service dates.
- ​   Stop times.
- ​   Feed timezone.
- ​   Duplicate IDs.
- ​   Data freshness.

Các file bắt buộc và có điều kiện phải đối chiếu với phiên bản GTFS Specification hiện hành.

### Step 1.6 — Validate Real-world Coverage

Chọn ít nhất 5–10 trường hợp hành trình đại diện.

Ví dụ nhóm test:

1. Walking-only.
2. Bus-only.
3. Metro-only.
4. Bus → Metro.
5. Metro → Walking.
6. One transfer.
7. No valid route.
8. Route outside coverage.
9. Scheduled route near service boundary.
10. Invalid origin/destination.

Các test case phải dùng tọa độ và lịch chạy thực của dataset đã chọn.

### Exit Criteria

- ​   Có nguồn dữ liệu rõ ràng.
- ​   Có thông tin license.
- ​   GTFS/OSM được kiểm tra.
- ​   Có phạm vi pilot xác định.

- ​ Có route test cases.
- ​ Không sử dụng bộ dữ liệu giả làm dữ liệu tuyến production.

Deliverable: Data Feasibility Report.

Status: NOT STARTED.

