# ADR-032 — Xe buýt từ OpenStreetMap, graph toàn TP.HCM, ô tô và xe đạp (RD-0)

- **Trạng thái:** Accepted (tạm thời, dữ liệu giả định) · **Ngày:** 14-11-2026 · **Người quyết định:** chủ dự án
- **Liên quan:** `PIPELINE.md` RD-0 (§5a) và DATA-1 (§5b), [`docs/research/transit-data-feasibility.md`](../research/transit-data-feasibility.md) §9, ADR-020 (feed Metro giả định), ADR-023 (OTP 2.10.0), ADR-027 (API), ADR-031 (khung TP.HCM)

## Bối cảnh

Ngoài hành lang Metro số 1, app chỉ ra tuyến đi bộ (ví dụ Bến Thành → Trương Đăng Quế: 85 phút). Không có GTFS xe buýt chính thức (M01 vẫn BLOCKED). OSM có 349 relation `route=bus` trong khung TP.HCM, kèm thứ tự trạm, `interval` và `opening_hours`. Chủ dự án muốn có xe buýt, ô tô và xe đạp. Xe máy để sau (RD-1).

## Quyết định

### 1. Feed xe buýt giả định dựng từ OSM

- Script `scripts/build_bus_gtfs_from_osm.py`, test ở `scripts/tests/`. Đầu vào là file Geofabrik `data/raw/osm/vietnam-*.osm.pbf` (ODbL), cắt theo khung `106.35,10.35,107.05,11.2`. Đầu ra là `data/assumed/hcmc-bus-osm-gtfs/`. Các file `.txt` của feed không commit (khoảng 6 MB, dựng lại được), chỉ commit `manifest.txt` và `README.txt`.
- Nhãn: `feed_version=osm-assumed-2026-11-14`, `feed_id=bus`, chữ "DU LIEU GIA DINH … NOT OFFICIAL" trong `feed_info` và `route_desc`. API trả `dataStatus=ASSUMED`; app ghi "giờ ước tính".
- Cách dựng:
  - **Trạm:** thành viên node có role `platform*`/`stop`. Không có tên thì đặt "Trạm xe buýt".
  - **Hình dạng tuyến:** nối các way theo thứ tự thành viên. Nếu có chỗ hở trên 50 m thì nối lại theo kiểu tham lam, bắt đầu từ way gần trạm đầu. Vẫn còn hở thì **bỏ tuyến** (`no_shape`).
  - **Thứ tự trạm:** sắp lại theo vị trí dọc hình dạng tuyến, bỏ trạm cách tuyến quá 300 m. Lý do: thứ tự khai báo trong OSM thường sai. Tuyến 19 nếu nối trạm theo thứ tự khai báo ra 63 km đường chim bay, trong khi tuyến chỉ khoảng 25 km. Đo trên tuyến không có hình dạng: độ zigzag p50 = 2,11, so với 1,31 ở tuyến có hình dạng. Vì vậy tuyến không có hình dạng bị bỏ, không đoán.
  - **Giờ chạy:** quãng đường dọc tuyến chia cho tốc độ **giả định 15 km/h**, đã gồm thời gian dừng. Không tìm được nguồn cho con số này. Nếu OSM có `duration` thì dùng `duration`. Mỗi chặng tối thiểu 20 giây.
  - **Giãn cách:** `interval` dạng khoảng (`00:10-00:20`) lấy trung điểm. `frequencies.txt` dùng `exact_times=1`, giống feed Metro (ADR-020).
  - **Ngày chạy:** từ `opening_hours` dạng đơn giản (`Mo-Su 05:00-22:00`, nhiều luật ngăn bằng `;`, giờ chạy lẻ `07:00,17:15`). Dạng khác (ngày lễ, qua đêm, ngày chồng nhau) thì bỏ tuyến. Lịch giả định 01-11-2026 đến 31-12-2027.
- Kết quả ngày 14-11-2026: dùng được 230 relation, gồm 151 tuyến, 5.001 trạm, 234 trip. Bỏ 119 relation: 96 `no_shape`, 11 `no_interval`, 10 `too_few_stops`, 2 `no_opening_hours`. Danh sách từng relation bị bỏ nằm trong `manifest.txt`. Thời gian một chuyến: p10 35, p50 83, p90 137 phút.

### 2. Một graph OTP cho toàn TP.HCM

- `build-graph.sh` nhận nhiều feed qua `GTFS_DIRS`. Graph `hcmc-assumed` gồm OSM cắt theo khung trên, feed Metro L1 (`feed_id=1`, nên id vẫn là `1:L1-01`) và feed xe buýt (`feed_id=bus`).
- Số đo (Apple M4 Pro, Docker 8,7 GiB):
  - dựng graph 37–38 giây, bộ nhớ đỉnh container khoảng 1,9–2,0 GiB, heap JVM đỉnh khoảng 1,4 GiB;
  - `graph.obj` 94 MB;
  - khi phục vụ: sẵn sàng sau khoảng 5 giây, dùng khoảng 1,2 GiB.
- Hiện phục vụ ở `127.0.0.1:8089`, song song với graph Metro cũ ở 8088. Muốn API dùng graph mới thì đặt `OTP_URL=http://127.0.0.1:8089`.

### 3. API và app

- `modes` nhận thêm `CAR` và `BICYCLE`, mỗi giá trị **đứng một mình** (không kết hợp đỗ xe rồi đi công cộng). `WALK`/`TRANSIT` giữ nguyên. Xe máy (`MOTORBIKE`) bị từ chối với lỗi `INVALID_REQUEST` cho tới RD-1.
- App có ba chip: Công cộng / Xe đạp / Ô tô. Chặng buýt hiện "Buýt <số>". Kết quả ô tô có ghi chú "chưa tính kẹt xe". Nhãn giờ ước tính chỉ hiện khi có chặng công cộng.

## Hệ quả

- Tuyến đi qua phần lớn thành phố có xe buýt. 10/10 route case cũ vẫn PASS trên graph mới.
- Bản đồ và feed cùng một nguồn OSM. Sửa OSM rồi dựng lại là cập nhật được, không phải sửa tay.

## Rủi ro

- Giờ xe buýt là **ước tính**: tốc độ 15 km/h chưa có nguồn, giờ cao điểm có thể lệch nhiều. Giãn cách lấy trung điểm.
- OSM có thể chưa cập nhật đợt điều chỉnh tuyến từ 01-07-2026. 96 relation bị bỏ vì hình dạng tuyến có chỗ hở.
- Ô tô và xe đạp dùng tốc độ theo loại đường của OTP, **không có dữ liệu giao thông**. Ví dụ ô tô 9 km giờ cao điểm ra 10 phút là quá lạc quan.
- Không làm M01 thành PASS. DATA-1 (`PIPELINE.md` §5b) là đường để thay bằng dữ liệu chính thức.
