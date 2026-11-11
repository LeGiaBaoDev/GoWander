# M01 — Khả thi dữ liệu transit cho TP.HCM

> **Ngày:** 11-11-2026 · **Trạng thái M01:** **BLOCKED** (phần GTFS bus/metro) · **Người thực hiện:** Claude Code
> **Danh mục nguồn:** [`data/sources/source-inventory.csv`](../../data/sources/source-inventory.csv)

## 1. Kết luận

- **Chưa tìm thấy bộ GTFS nào dùng được hợp pháp cho xe buýt hoặc metro TP.HCM.** Không có nguồn chính thức, và nguồn bên thứ ba duy nhất tìm được vừa thiếu lịch chạy vừa không có giấy phép.
- **Dữ liệu bản đồ đường (OSM) có sẵn và dùng được:** bản trích Việt Nam của Geofabrik, giấy phép ODbL.
- Theo cổng kiểm tra của M01 trong `PIPELINE.md`, không có dữ liệu transit hợp lệ thì phải **dừng phần tìm tuyến production** và chọn hướng đi khác. Các hướng nằm ở mục 5.
- Chưa tải bộ dữ liệu nào về `data/raw/`, chưa kiểm tra chất lượng GTFS và chưa viết test case hành trình, vì chưa có GTFS để làm.

## 2. Phương pháp và giới hạn

- Tìm kiếm web bằng tiếng Việt và tiếng Anh (9 truy vấn), đọc trực tiếp 6 trang, gọi API/tệp công khai của 4 dịch vụ. Thực hiện ngày 11-11-2026.
- Skill `ecc:deep-research` yêu cầu công cụ firecrawl hoặc exa; phiên này không có, nên dùng công cụ tìm kiếm và đọc trang có sẵn theo cùng quy trình.
- **Những gì không đọc được:**
  - `hub.tumidata.org` (máy chủ từ chối kết nối).
  - Trang tìm kiếm của `mobilitydatabase.org` (HTTP 403); thay bằng tệp danh mục CSV của họ.
  - File PDF biểu đồ chạy tàu của HURC1 (tải thất bại).
  - 9 trong 10 trang danh sách của cổng dữ liệu mở TP.HCM, và ô tìm kiếm của cổng.
  - Trang chi tiết tuyến của `buyttphcm.com.vn` (nội dung nạp bằng JavaScript).
- Chưa liên hệ cơ quan hay đơn vị vận hành nào. "Không tìm thấy" ở đây nghĩa là không thấy công bố công khai, không có nghĩa là dữ liệu không tồn tại.
- Chưa nghiên cứu nguồn map tiles và giấy phép của chúng.

## 3. Kết quả theo từng loại dữ liệu

### 3.1. GTFS xe buýt — không có nguồn dùng được

| Nguồn | Kết quả | Vì sao không dùng được |
|---|---|---|
| Trung tâm Quản lý giao thông công cộng (`buyttphcm.com.vn`) | Trang tra cứu tuyến chính thức; chân trang ghi "Bản quyền © Trung tâm Quản lý giao thông công cộng Thành phố Hồ Chí Minh" | Không có điều khoản dữ liệu mở, trang API hay tải dữ liệu. Cần văn bản cho phép |
| Cổng dữ liệu mở TP.HCM | 110 bộ dữ liệu, không có chủ đề giao thông và không có cơ quan giao thông trong danh sách đơn vị công bố | Không thấy dữ liệu bus. Điều khoản của cổng cấm sao chép, phân phối khi chưa có văn bản đồng ý |
| Enlightened Data (bên thứ ba) | `stops.txt` 4.693 trạm, `feed_info.txt`, một File Geodatabase gồm trạm và tuyến | Thiếu `routes`, `trips`, `stop_times`, `calendar`; thu thập bằng web scraping; repo không có giấy phép; `feed_end_date` 2025-10-04 đã hết hạn |
| Mobility Database | Tệp danh mục không có dòng nào của Việt Nam | Không có feed |
| Transitland | "No agencies match 'Ho Chi Minh'" | Không có feed |
| BusMap, MultiGo | Ứng dụng tra cứu tuyến | Độc quyền, không có dữ liệu tải công khai |

Bối cảnh: báo Tiền Phong (2026) nêu mạng xe buýt TP.HCM có khoảng 180 tuyến. Con số này chỉ có một nguồn, chưa đối chiếu.

### 3.2. GTFS metro — không có

- Không tìm thấy feed GTFS hay tệp lịch chạy dạng máy đọc được cho Metro số 1.
- Thông tin về tuyến, lấy từ báo chí và chưa đối chiếu với đơn vị vận hành:
  - Khai trương ngày 22-12-2024, chạy Bến Thành – Suối Tiên.
  - Dài khoảng 19,7 km, 14 ga, trong đó 3 ga ngầm (Bến Thành, Nhà hát Thành phố, Ba Son).
  - Đơn vị vận hành: Công ty TNHH MTV Đường sắt đô thị số 1 (HURC1).
  - Số lượt tàu thường ngày được nêu là 251 trong một bài về dịp Tết 2026.
- **Giờ chạy và giãn cách giữa các nguồn không khớp nhau** (5h–22h hay tới 23h30; giãn cách 8–12 phút hay 4–10 phút). Một bài nói biểu đồ chạy tàu 2026 áp dụng từ 02-01-2026 và chia ba nhóm ngày, nhưng chưa tìm được chi tiết.
- Có một file PDF biểu đồ chạy tàu của HURC1 đăng trên máy chủ của Saigon Times; chưa đọc được.

### 3.3. OSM — dùng được

- Nguồn: `https://download.geofabrik.de/asia/vietnam-latest.osm.pbf`.
- Kiểm tra ngày 11-11-2026: file ngày 10-11-2026, 332.453.587 byte, cập nhật hằng ngày.
- Giấy phép: ODbL 1.0 theo công bố của Geofabrik cho mọi dữ liệu trên máy chủ tải về. Phải ghi công OpenStreetMap.
- Geofabrik không có bản trích riêng cho TP.HCM; cần cắt từ file cả nước theo khung khu vực pilot.
- Chưa tải file và chưa kiểm tra chất lượng dữ liệu đường đi bộ trong khu vực pilot.

### 3.4. GTFS Realtime và map tiles

- GTFS Realtime: không tìm thấy nguồn nào. Mọi thời gian hiển thị, nếu có, phải ghi là theo lịch.
- Map tiles: chưa nghiên cứu. Cần làm trước M03.

## 4. Đề xuất khu vực pilot

Đây là đề xuất để chủ dự án chọn, dựa trên những gì đã biết về Metro số 1; chưa kiểm tra bằng dữ liệu.

| Phương án | Phạm vi | Ưu điểm | Nhược điểm |
|---|---|---|---|
| **A. Hành lang Metro số 1 (đề xuất)** | 14 ga từ Bến Thành tới Suối Tiên, cộng vùng đi bộ quanh mỗi ga | Một tuyến, lịch đơn giản, dễ kiểm tra tay; đi qua trung tâm có nhiều điểm tham quan; không phụ thuộc dữ liệu xe buýt | Chỉ có metro + đi bộ, chưa thể hiện được việc kết hợp bus |
| B. Lõi trung tâm (khu Bến Thành – Nhà hát – Ba Son) | Khu vực quanh 3 ga ngầm và các điểm tham quan lân cận | Mật độ điểm tham quan cao, phù hợp Discover | Hành trình ngắn, phần lớn là đi bộ; ít giá trị cho tìm tuyến |
| C. Trung tâm + Chợ Lớn bằng xe buýt | Một vài tuyến bus nối Quận 1 với Quận 5 | Thể hiện được bus | Phụ thuộc hoàn toàn vào dữ liệu bus chưa có |

## 5. Các hướng gỡ chặn

| Hướng | Nội dung | Ai làm | Kết quả có thể tuyên bố |
|---|---|---|---|
| **1. Xin dữ liệu và quyền sử dụng** | Gửi văn bản tới Trung tâm Quản lý giao thông công cộng (bus) và HURC1 (metro), hỏi dữ liệu tuyến/trạm/lịch và điều kiện sử dụng | Chủ dự án (Claude Code soạn thư) | Dữ liệu chính thức, đúng luật |
| **2. Thu hẹp còn Metro số 1 + đi bộ** | Tự dựng một GTFS nhỏ cho 14 ga từ lịch chạy do HURC1 công bố, kết hợp OSM | Claude Code dựng; chủ dự án xác nhận quyền dùng lịch công bố | Tìm tuyến thật cho một hành lang, ghi rõ là dữ liệu tự biên soạn từ thông báo công khai |
| 3. Thử kỹ thuật OTP bằng dữ liệu khác | Chạy OTP với GTFS Hà Nội (nếu giấy phép cho phép) hoặc GTFS giả có gắn nhãn | Claude Code | Chỉ chứng minh kỹ thuật, **không** tính là sẵn sàng cho TP.HCM |
| 4. Làm việc không bị chặn | M03 (bản đồ KMP) và M05 (database + Discover) không phụ thuộc dữ liệu transit | Claude Code | Tiến độ ở các phần khác |

**Khuyến nghị:** làm hướng 1 và hướng 2 song song, đồng thời tiếp tục hướng 4. Hướng 2 cần hai điều trước khi bắt đầu: đọc được lịch chạy chính thức hiện hành của HURC1, và chủ dự án chấp nhận rủi ro về quyền sử dụng lịch công bố hoặc có xác nhận của HURC1.

## 6. Tiêu chí gỡ chặn M01

M01 chuyển sang PASS khi có đủ:

1. Một bộ GTFS cho ít nhất một hành lang pilot, có nguồn và quyền sử dụng được ghi lại.
2. Bộ đó qua kiểm tra cấu trúc và tính toàn vẹn (trạm, tuyến, chuyến, lịch, múi giờ).
3. 5–10 test case hành trình lấy từ chính bộ dữ liệu đó.
4. Một ADR ghi quyết định về nguồn dữ liệu (số ADR-020, vì tài liệu kiến trúc đã dùng ADR-001 đến ADR-019).

## 7. Quyết định của chủ dự án (11-11-2026)

- **Ghi nhận chỗ kẹt, không chờ dữ liệu chính thức.** Việc xin dữ liệu và quyền sử dụng từ Trung tâm Quản lý giao thông công cộng và HURC1 được hoãn lại, chưa huỷ.
- **Đi tiếp bằng dữ liệu giả định có cơ sở:** tự biên soạn GTFS từ thông tin công khai có nguồn (ga, tuyến, giờ chạy, giãn cách), mỗi giá trị ghi rõ lấy từ đâu và giá trị nào là giả định.
- **Hệ quả đối với các cổng kiểm tra** (theo `PIPELINE.md` mục 0, mục 5 và mục 6):
  - M01 **vẫn là BLOCKED** về dữ liệu chính thức. Dữ liệu giả định không làm M01 thành PASS.
  - M02 được làm như một **PoC kỹ thuật**; kết quả không được gọi là sẵn sàng cho TP.HCM.
  - Bộ GTFS giả định phải mang nhãn rõ ràng trong tên feed, `feed_info.txt` và tài liệu. Ứng dụng không được hiển thị nó như lịch chạy chính thức.
  - Trước pilot với người dùng thật (M10), phải thay bằng dữ liệu có quyền sử dụng hoặc có xác nhận của đơn vị vận hành.
- **Chưa chốt:** phạm vi khu vực pilot (xem mục 4). ADR-020 sẽ được viết khi phạm vi được chốt.

## 8. Nguồn

1. [Enlightened Data — TransformVietnam](https://enlighteneddata.github.io/EDprofile/TransformVietnam.html) — mô tả bộ dữ liệu nháp cho TP.HCM
2. [EnlightenedData/EDprofile — thư mục data](https://github.com/EnlightenedData/EDprofile/tree/master/data) — các tệp thực tế trong repo
3. [buyttphcm.com.vn](http://buyttphcm.com.vn/) — trang xe buýt của Trung tâm Quản lý giao thông công cộng
4. [Cổng dữ liệu mở TP.HCM](https://opendata.hochiminhcity.gov.vn/) và [Điều khoản sử dụng](https://opendata.hochiminhcity.gov.vn/dieu-khoan-su-dung)
5. [Transitland — tìm "Ho Chi Minh"](https://www.transit.land/operators?search=Ho%20Chi%20Minh)
6. [Mobility Database — tệp danh mục](https://storage.googleapis.com/storage/v1/b/mdb-csv/o/sources.csv?alt=media)
7. [Geofabrik — Vietnam](https://download.geofabrik.de/asia/vietnam-latest.osm.pbf) và [thông tin kỹ thuật, giấy phép](https://download.geofabrik.de/technical.html)
8. [TUMI data hub — GTFS Hanoi](https://hub.tumidata.org/dataset/gtfs-hanoi) — chưa đọc được
9. [The Investor — khai trương Metro số 1](https://theinvestor.vn/hcmcs-first-metro-line-officially-operational-after-nearly-2-decades-d13873.html)
10. [Luật Minh Khuê — lịch chạy tàu Tết 2026](https://luatminhkhue.vn/lich-chay-tau-metro-tp-hcm-tet-2026-va-gia-ve-chi-tiet.aspx)
11. [VOH — Metro số 1 dịp Tết Bính Ngọ 2026](https://voh.com.vn/tin-tp-ho-chi-minh/metro-so-1-tphcm-mien-phi-ve-trong-dip-tet-binh-ngo-2026-645490.html)
12. [Saigon Times CDN — HURC1.pdf](https://cdn.thesaigontimes.vn/wp-content/uploads/2025/12/HURC1.pdf) — chưa đọc được
13. [Tiền Phong số 107/2026](https://tienphong.vn/epaper/2026/TP-107/5/) — số tuyến xe buýt
14. [elpwc/VietnamRailwayData](https://github.com/elpwc/VietnamRailwayData) — chỉ biết qua kết quả tìm kiếm
15. [BusMap](https://busmap.vn/) và [MultiGo](https://multigo.golabs.vn/en)
