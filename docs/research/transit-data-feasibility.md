# M01 — Khả thi dữ liệu transit cho TP.HCM

> **Ngày:** 11-11-2026, cập nhật 14-11-2026 (mục 9: xe buýt từ OSM) · **Trạng thái M01:** **BLOCKED** (phần GTFS bus/metro chính thức) · **Người thực hiện:** Claude Code
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

## 9. Cập nhật 14-11-2026 — xe buýt có trong OSM, đủ dựng GTFS giả định

Chủ dự án gửi ảnh chụp các gợi ý nguồn (bài nhóm Flutter Vietnam: Mapbox/OpenStreetMap, BusMap; câu trả lời AI của Google: Vietmap Routing, BusMap, timbus-api) và yêu cầu xem lại cho M01, vì app hiện chỉ ra tuyến đi bộ ngoài Metro số 1.

### 9.1. Đo trực tiếp trên file OSM của dự án

- **Phương pháp:** 7 truy vấn web (tiếng Việt và tiếng Anh), chỉ đọc kết quả tìm kiếm, chưa đọc trực tiếp trang nào; đo OSM bằng osmium trên máy.

- **File:** `data/raw/osm/vietnam-261111.osm.pbf` (Geofabrik, ODbL), cắt theo khung TP.HCM mở rộng `106.35,10.35,107.05,11.2` bằng osmium 1.19.1. Chỉ đọc, không sửa file gốc.
- **Lệnh** (chạy trong thư mục scratch, kết quả không commit):

  ```
  osmium extract -b 106.35,10.35,107.05,11.2 vietnam-261111.osm.pbf -o hcmc.osm.pbf
  osmium tags-filter hcmc.osm.pbf r/route=bus -o bus.osm.pbf -R
  osmium tags-filter hcmc.osm.pbf n/highway=bus_stop -o stops.osm.pbf -R
  ```

| Chỉ số | Kết quả |
|---|---|
| Relation `route=bus` (mỗi chiều một relation) | **349**, khoảng 174 giá trị `ref` khác nhau |
| `network` | 315 "Xe buýt Thành phố Hồ Chí Minh", 17 "Xe buýt Bình Dương", 16 "Xe buýt Đồng Nai" |
| `public_transport:version=2` (trạm xếp theo thứ tự trong relation) | 246 / 349; 346 relation có thành viên `stop`/`platform` |
| `interval` (giãn cách) | 326 / 349, phổ biến `00:10-00:20`, `00:10-00:15`, `00:12-00:20` |
| `opening_hours` (giờ hoạt động) | 348 / 349, phổ biến `Mo-Su 05:00-22:00`, `Mo-Su 04:45-22:00` |
| `duration` (thời gian một chuyến) | chỉ 4 / 349 |
| `operator` | 349 / 349 |
| Lần sửa gần nhất của relation | 343 trong năm 2026, 5 năm 2025, 1 năm 2024 |
| Node `highway=bus_stop` | **6.388**. Thành phố công bố 6.241 vị trí điểm dừng (nguồn 9.4, số 3) |

Dữ liệu này được cộng đồng thêm theo đợt ("add bus routes for HCMC", ví dụ changeset 149037423; nguồn 9.4, số 9).

### 9.2. Kết luận

- **Dựng được GTFS xe buýt giả định từ OSM** (ODbL, ghi "© OpenStreetMap contributors"): `stops` từ trạm, `routes`/`trips` từ relation, `frequencies.txt` từ `interval` + `opening_hours`, thời gian giữa trạm **ước tính** theo khoảng cách và tốc độ giả định. Đây cùng loại với feed giả định Metro (ADR-020): phải có nhãn `ASSUMED`, app ghi "giờ ước tính", **không làm M01 thành PASS**.
- Thiếu: lịch từng chuyến, thời gian chạy thật giữa các trạm, giá vé theo tuyến, ngày lễ, thay đổi tuyến chưa được cập nhật lên OSM.
- **Chưa kiểm chứng:** số hiệu và lộ trình tuyến trong OSM có khớp đợt điều chỉnh tuyến từ 01-07-2026 (134 tuyến miễn phí vé) hay không; chất lượng hình học relation (đứt đoạn, sai chiều) chưa kiểm tra.
- Công cụ có sẵn: `osm2gtfs` (cần nguồn lịch riêng, issue cho thấy chưa hỗ trợ `interval`), `o2g`/`osmtogtfs` (có chế độ dữ liệu giả + `frequencies.txt`). Hướng ưu tiên: **script Python riêng** trong `scripts/` giống `build_metro_l1_assumed_gtfs.py`, dùng osmium đã cài, để kiểm soát nhãn, nguồn từng giá trị và test; chỉ dùng thư viện ngoài nếu ADR ghi lý do.

### 9.3. Đánh giá từng nguồn

| Nguồn | Dữ liệu | Giấy phép / điều kiện | Kết luận |
|---|---|---|---|
| **OpenStreetMap** (Geofabrik) | Tuyến, trạm, giãn cách, giờ hoạt động | ODbL 1.0, ghi công | ✅ **Dùng** cho GTFS xe buýt giả định |
| Mapbox | Bản đồ nền dựa trên OSM; không có lịch buýt riêng cho TP.HCM | Thương mại, theo lượt | ➖ Không cần thêm |
| BusMap (Phenikaa MaaS JSC) | Tuyến, trạm, vị trí xe; hợp tác theo dự án (VinBus, TopCV) | Độc quyền; không có API công khai | ⏸ Có thể liên hệ hỏi giá sau pilot. **Không scrape** |
| Trung tâm Quản lý giao thông công cộng (`buyttphcm.com.vn`, app Go!Bus) | Tuyến, giờ, giá, danh sách trạm hai chiều | Bản quyền của Trung tâm; không có GTFS công khai | ⏸ Nguồn chính thức nên xin bằng văn bản trước M10 |
| Cổng dữ liệu mở TP.HCM | Chưa thấy bộ dữ liệu xe buýt | Điều khoản riêng (mục 8, số 4) | ⏸ Theo dõi; Quyết định 3974/QĐ-UBND yêu cầu số hoá dữ liệu chuyến |
| timbus-api | Không tìm thấy repo này. Timbus là app của Transerco **Hà Nội** | Không rõ | ❌ Sai thành phố |
| GTFS Hà Nội trên TUMI (hanoibusmap, hanoibusgtk) | Hà Nội | TUMI ghi giấy phép có thể đã cũ | ❌ Sai thành phố; chỉ để tham khảo cấu trúc |
| Vietmap Routing | Tìm đường ô tô / xe máy | Tính phí, giá phải liên hệ | ⏸ Xe máy và ô tô sau pilot (ADR-031) |

### 9.4. Nguồn của mục 9 (đọc ngày 14-11-2026)

1. File OSM của dự án: [Geofabrik — Vietnam](https://download.geofabrik.de/asia/vietnam-latest.osm.pbf), bản `vietnam-261111`
2. [Lao Động — TP.HCM miễn phí xe buýt](https://news.laodong.vn/xa-hoi/tphcm-mien-phi-xe-buyt-mot-mui-ten-nhieu-dich-den-1681144.ldo) — Quyết định 3974/QĐ-UBND, số hoá dữ liệu chuyến
3. Số liệu 37 depot/bến, 6.241 vị trí điểm dừng, 1.030 nhà chờ: lấy từ tóm tắt kết quả tìm kiếm cho các bài 2 và 4–5; **chưa đọc trực tiếp bài gốc** để xác định bài nào
4. [VTC News — miễn phí từ 01-07-2026](https://vtcnews.vn/xe-buyt-san-sang-don-nguoi-dan-di-lai-mien-phi-khap-tp-hcm-tu-hom-nay-ar1026510.html)
5. [Nông nghiệp & Môi trường — 134 tuyến miễn phí](https://nongnghiepmoitruong.vn/tu-1-7-tphcm-mien-phi-xe-buyt-tren-134-tuyen-d817668.html)
6. [Doanh nghiệp Hội nhập — 17 tuyến buýt gom metro dùng xe điện](https://doanhnghiephoinhap.vn/tp-ho-chi-minh-dua-vao-hoat-dong-17-tuyen-buyt-gom-metro-su-dung-xe-dien-phuc-vu-ket-noi-cac-ga-metro-90224.html)
7. [Lao Động — 5 tuyến mới khu vực Bình Dương cũ](https://news.laodong.vn/xa-hoi/tphcm-sap-mo-them-5-tuyen-xe-buyt-moi-tai-khu-vuc-binh-duong-cu-1723119.ldo) — tuyến thay đổi liên tục, OSM có thể chậm
8. [Hướng dẫn app Go!Bus (svhtt.hochiminhcity.gov.vn)](https://svhtt.hochiminhcity.gov.vn/documents/10184/344330/hdsd+gobus.docx/e69e6b61-38b2-45a9-a594-3edb4489b9be) — app tra cứu chính thức
9. [OSM node 2091954745 — Cầu Tham Lương](https://www.openstreetmap.org/node/2091954745) — ví dụ trạm thuộc 10 relation, đợt sửa "add bus routes for HCMC"
10. [grote/osm2gtfs](https://github.com/grote/osm2gtfs) và [issue #146 về `interval`/frequencies](https://www.github.com/grote/osm2gtfs/issues/146)
11. [o2g trên PyPI](https://pypi.org/project/o2g/) và [osmtogtfs trên PyPI](https://pypi.org/project/osmtogtfs)
12. [TUMI — hanoibusmap](https://hub.tumidata.org/dataset/_hanoibusmap_hanoi) và [TUMI — hanoibusgtk](https://hub.tumidata.org/dataset/hanoibusgtk_hanoi)
13. [Hanoi Times — Timbus/Transerco](https://hanoitimes.vn/searching-for-bus-similar-to-grab-and-uber-8014.html)
14. [Thị trường Tài chính Tiền tệ — Phenikaa MaaS chuyển đổi số giao thông](https://thitruongtaichinhtiente.vn/phenikaa-maas-thuc-day-hoat-dong-chuyen-doi-so-trong-linh-vuc-giao-thong-37021.html) và [Phenikaa đầu tư 1,5 triệu USD vào BusMap](https://thitruongtaichinhtiente.vn/print/35973.html)
15. [BusMap](https://busmap.vn/)
