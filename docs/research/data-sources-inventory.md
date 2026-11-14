# DATA-1 — Danh mục nguồn dữ liệu và cách ghép thành dữ liệu GoWander

> **Ngày:** 14-11-2026 · **Giai đoạn:** DATA-1 (`PIPELINE.md` §5b) · **Người thực hiện:** Claude Code
> **Skill:** `ecc:deep-research`. Máy không có công cụ firecrawl/exa mà skill yêu cầu, nên tôi dùng công cụ tìm kiếm web có sẵn theo cùng quy trình: 10 truy vấn, cộng 1 truy vấn trực tiếp vào Wikidata. Tôi **chỉ đọc kết quả tìm kiếm, chưa đọc trọn trang nào**. Mọi điều khoản giấy phép dưới đây phải đọc lại trang gốc trước khi nạp dữ liệu.
> **Bảng theo dõi:** [`data/sources/data-register.csv`](../../data/sources/data-register.csv). Nguồn transit xem thêm [`transit-data-feasibility.md`](./transit-data-feasibility.md).

## 1. Tóm tắt

- Dữ liệu **mở và hợp pháp** đủ để làm dày Discover và bản đồ ngay:
  - **OpenStreetMap** (ODbL): đã dùng.
  - **Wikidata** (CC0): tên tiếng Anh, mô tả ngắn, ảnh, liên kết Wikipedia.
  - **Wikimedia Commons** (giấy phép riêng cho từng ảnh): ảnh.
  - **Wikipedia và Wikivoyage** (CC BY-SA): nội dung dài, phải ghi công và chia sẻ cùng giấy phép.
  - **Foursquare OS Places / Overture Places** (Apache 2.0 / CDLA 2.0): thêm địa điểm và danh mục.
- **Đo thật ngày 14-11-2026:** Wikidata có **1.910** đối tượng có toạ độ trong khung TP.HCM (`106.35,10.35,107.05,11.2`). Trong đó 434 có ảnh (P18), 1.000 có nhãn tiếng Việt, 823 có bài Wikipedia tiếng Việt, 87 đã có ID liên kết sang OSM.
- **Không dùng:**
  - Dữ liệu từ app khác (Google, Naver, Kakao, BusMap, Vietmap) khi không có hợp đồng; không scrape.
  - GADM: chỉ cho phi thương mại.
  - API miễn phí của Open-Meteo: cũng chỉ cho phi thương mại.
- Còn thiếu nguồn mở cho: lịch xe buýt và Metro chính thức (xem DATA-1 task 2), số nhà, ranh giới phường/xã mới sau sáp nhập 01-07-2025 (bản chính thức).

## 2. Nguồn theo loại dữ liệu

### 2.1. Địa điểm (Discover, tìm kiếm)

| Nguồn | Có gì | Giấy phép / điều kiện | Kết luận |
|---|---|---|---|
| OpenStreetMap (Geofabrik) | POI, tên, loại, địa chỉ một phần, tag `wikidata=` | ODbL 1.0: ghi "© OpenStreetMap contributors"; cơ sở dữ liệu phái sinh công bố ra ngoài phải theo ODbL | **Đang dùng** (ADR-028) |
| Wikidata | Tên nhiều ngôn ngữ, mô tả ngắn, loại, di sản, ảnh P18, liên kết Wikipedia | **CC0**, không cần ghi công | **Dùng**: làm giàu dữ liệu, khoá ghép `wikidata` |
| Foursquare OS Places | Hơn 100 triệu POI toàn cầu, hơn 1.000 danh mục, cập nhật hằng tháng | **Apache 2.0**, giữ thông báo "Copyright 2024 Foursquare Labs, Inc."; Hugging Face yêu cầu đăng ký | **Thử**: đếm số POI ở TP.HCM, so độ phủ với OSM |
| Overture Maps Places | POI ghép từ nhiều nguồn (có Foursquare) | Giấy phép **theo từng bản ghi**: Foursquare là Apache 2.0, nguồn khác là CDLA Permissive 2.0. Phải đọc trường `sources` | **Thử** cùng Foursquare; giữ giấy phép của từng dòng |
| Wikivoyage (Thành phố Hồ Chí Minh) | Gợi ý tham quan, ăn uống, kèm mô tả | CC BY-SA (bản 3.0 hay 4.0 cần kiểm tra lại) | **Dùng có điều kiện**: ghi công và giữ cùng giấy phép cho phần chữ |

### 2.2. Ảnh

| Nguồn | Điều kiện | Kết luận |
|---|---|---|
| Wikimedia Commons | Giấy phép **riêng cho từng file**. Lấy bằng API `prop=imageinfo&iiprop=extmetadata` (`LicenseShortName`, `Artist`, `LicenseUrl`). Ảnh nhiều giấy phép thì metadata không chắc đúng. API yêu cầu User-Agent mô tả rõ | **Dùng**: chỉ nhận PD, CC0, CC BY, CC BY-SA; lưu tác giả và giấy phép vào `place_media` |
| Mapillary | CC BY-SA 4.0, ghi "Photos from Mapillary, CC BY-SA, by @user"; điều khoản sử dụng riêng cần đọc. Theo blog Mapillary 05-2024, Be Group đã chụp gần như toàn bộ Hà Nội và TP.HCM | **Dùng cho NAV-1 "Xem trước tuyến"** (ADR-033). Token ở server; Mapillary tự làm mờ mặt và biển số |
| Streetview.vn (Openmap.vn, 44+ Technologies) | Ảnh 360° ở Việt Nam | Chưa rõ giấy phép | Chỉ ghi nhận; không dùng khi chưa có điều khoản |
| Panoramax / KartaView | CC BY-SA; chưa thấy độ phủ ở Việt Nam | Mở | Theo dõi |
| Ảnh vệ tinh (MapTiler, Esri World Imagery, Mapbox, Sentinel-2) | Gói miễn phí của MapTiler chỉ cho phi thương mại; Esri cần giấy phép ArcGIS; Sentinel-2 chỉ 10 m | Trả phí hoặc mờ | **Chờ chủ dự án chọn** (ADR-033 §6) |
| Ảnh do chủ dự án tự chụp | Chủ dự án giữ quyền | **Ưu tiên** cho các điểm quan trọng |

### 2.3. Mô tả, lịch sử, món ăn

- **Wikipedia tiếng Việt và tiếng Anh** (CC BY-SA): đoạn mở đầu bài làm mô tả. Phải ghi tên bài, link và giấy phép; phần chữ đã sửa vẫn theo CC BY-SA.
- **Wikidata**: món ăn Việt (loại, nguồn gốc, ảnh) làm danh mục `food_items`. Quán nào bán món gì thì Wikidata không có; phải nhập tay có nguồn (rule B của ADR-028).
- **Câu chuyện lịch sử** (`historical_stories`): lấy từ Wikipedia (CC BY-SA) hoặc chủ dự án tự viết. Agent không tự viết nội dung không có nguồn.

### 2.4. Giao thông

- Xem `transit-data-feasibility.md` §9: xe buýt từ OSM (ADR-032), Metro giả định (ADR-020).
- Đường và hướng một chiều: OSM. Overture transportation dựng từ OSM nên cũng theo ODbL.

### 2.5. Địa chỉ và ranh giới hành chính

| Nguồn | Có gì | Điều kiện | Kết luận |
|---|---|---|---|
| ThangLeQuoc/vietnamese-provinces-database (GitHub) | 34 tỉnh và 3.321 phường/xã mới, có GeoJSON | MIT (theo một trang liệt kê); README ghi nguồn GSO có thể chậm cập nhật | **Kiểm tra** file, ngày cập nhật, giấy phép trong repo |
| nguyenduy1133/Free-GIS-Data | GeoJSON 34 tỉnh và 63 tỉnh cũ | Chưa rõ giấy phép | Chỉ tham khảo cho tới khi có giấy phép |
| Bản đồ địa giới chính thức của TP.HCM mới | 168 đơn vị cấp xã | Sở Nội vụ phải bàn giao trước 30-09-2026; chưa thấy bản mở | Theo dõi, xin qua DATA-1 |
| Photon / OSM `addr:*` | Địa chỉ | ODbL | Đang dùng (ADR-031); thiếu số nhà, Vietmap là phương án thương mại |

### 2.6. Thời tiết và độ cao

- **Open-Meteo:** dữ liệu CC BY 4.0. **API miễn phí chỉ cho phi thương mại**, dưới 10.000 lệnh gọi mỗi ngày. Pilot thương mại phải mua gói hoặc tự host.
- **Copernicus DEM GLO-30:** giấy phép miễn phí của Copernicus, ghi "© DLR e.V.". Điều khoản dùng thương mại phải đọc trong PDF giấy phép. Dùng cho cầu, hầm, dốc ở UPG-1.

## 3. Cách ghép thành dữ liệu GoWander

1. **OSM là bản ghi gốc** cho vị trí và tên, vì đã có trong `places`.
2. **Ghép với Wikidata qua tag `wikidata=Q…` của OSM.** Không có tag thì ghép theo tên đã bỏ dấu và khoảng cách dưới 100 m, và **ghi là ghép tự động, cần duyệt**. Chỉ dòng đã duyệt mới hiện trong app.
3. **Ghép Foursquare/Overture** bằng tên và khoảng cách giống bước 2. Dùng để thêm địa điểm OSM thiếu và danh mục; không ghi đè tên OSM.
4. **Ảnh:** P18 của Wikidata, rồi file Commons, rồi lấy metadata giấy phép. Thiếu tác giả hoặc giấy phép thì bỏ ảnh.
5. **Mô tả:** đoạn đầu Wikipedia tiếng Việt, rồi tiếng Anh, rồi mô tả ngắn của Wikidata. Lưu nguồn, link và giấy phép cho từng đoạn.
6. **Lưu nguồn cho từng trường**, không chỉ cho từng địa điểm. Hiện `places` chỉ có một cột `source`/`license`. Cần migration thêm bảng `place_sources` (place_id, field, source, ref, license, attribution, retrieved_at). Việc này cần ADR mới và review database.
7. **Giấy phép đi kèm khi xuất ra:**
   - CSDL địa điểm phái sinh từ OSM theo ODbL;
   - phần chữ từ Wikipedia/Wikivoyage theo CC BY-SA;
   - ảnh theo giấy phép của từng file;
   - màn "Nguồn dữ liệu" trong app liệt kê tất cả.
8. Script đặt trong `scripts/`, test trước, giống `build_discover_seed.py`. Tải dữ liệu thô về `data/raw/` (không commit), seed sinh ra kèm manifest.

## 4. Việc tiếp theo (DATA-1)

1. `scripts/enrich_places_wikidata.py`: ghép OSM với Wikidata qua tag `wikidata`, lấy tên tiếng Anh, mô tả ngắn, P18, bài viwiki/enwiki. Sau đó lấy metadata giấy phép từ Commons.
2. Đếm Foursquare OS Places trong khung TP.HCM và so với OSM. Chủ dự án phải đăng ký Hugging Face hoặc Foursquare để tải.
3. Migration `place_sources` (ADR mới).
4. Soạn thư xin dữ liệu chính thức: Trung tâm Quản lý giao thông công cộng (xe buýt), HURC1 (Metro), Sở Nội vụ (ranh giới). Chủ dự án gửi.

## 5. Nguồn

1. [Overture Maps — FAQ](https://overturemaps.org/about/faq/) · [Release notes 24-09-2025](https://docs.overturemaps.org/blog/2025/09/24/release-notes/) · [Release notes 19-08-2026](https://docs.overturemaps.org/blog/2026/08/19/release-notes/)
2. [Foursquare — Open Source Places](https://foursquare.com/resources/blog/products/foursquare-open-source-places-a-new-foundational-dataset-for-the-geospatial-community/) · [Evolving FSQ OS Places](https://foursquare.com/resources/blog/data/evolving-fsq-open-source-places/) · [Docs](https://docs.foursquare.com/data-products/docs/fsq-places-open-source) · [Hugging Face](https://huggingface.co/datasets/foursquare/fsq-os-places) · [Source Cooperative](https://source.coop/fused/fsq-os-places)
3. [Wikidata — Thành phố Hồ Chí Minh (Q1854)](https://www.wikidata.org/wiki/Q1854) · [Wikidata Query Service — hướng dẫn](https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual/nan)
4. [Wikivoyage — Ho Chi Minh City](https://en.wikivoyage.org/wiki/Ho_Chi_Minh_City) · [Wikivoyage (giấy phép)](https://en.wikipedia.com/wiki/Wikivoyage) · [DataHub — Wikivoyage dump](https://old.datahub.io/dataset/wikivoyage)
5. [CommonsMetadata extension](https://www.mediawiki.org/wiki/Extension:CommonsMetadata/en) · [Commons: Credit line](https://c.enwp.org/wiki/Commons:Credit_line) · [Commons: Machine-readable data](https://c.enwp.org/wiki/Commons:MRD)
6. [Mapillary — Licenses](https://help.mapillary.com/hc/en-us/articles/115001770409-Licenses) · [Mapillary chuyển sang CC BY-SA](https://blog.mapillary.com/update/2014/04/29/mapillary-drops-nc.html)
7. [Open-Meteo — Terms](https://open-meteo.com/en/terms) · [Licence](https://open-meteo.com/en/licence) · [Pricing](https://open-meteo.com/en/pricing)
8. [Copernicus DEM GLO-30 — giấy phép (PDF)](https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Data/DEM/resources/license/License-COPDEM-30.pdf) · [Earth Engine catalog](https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_DEM_GLO30_2024_1)
9. [ThangLeQuoc/vietnamese-provinces-database](https://github.com/ThangLeQuoc/vietnamese-provinces-database/releases) · [nguyenduy1133/Free-GIS-Data](https://github.com/nguyenduy1133/Free-GIS-Data) · [AWS Location — ranh giới mới Việt Nam](https://aws.amazon.com/about-aws/whats-new/2025/10/amazon-location-service-new-admin-boundaries-vietnam)
10. [VTC News — TP.HCM còn 102 phường, xã](https://vtcnews.vn/tp-hcm-chot-sap-nhap-con-102-xa-phuong-ar937876.html) · [Thư viện Pháp luật — 168 phường, xã TP.HCM mới](https://thuvienphapluat.vn/phap-luat-nha-dat/ban-do-168-phuong-xa-tphcm-ban-do-thanh-pho-ho-chi-minh-sau-sap-nhap-tu-ngay-0172025-9548.html) · [Cafeland — bản đồ hành chính mới](https://cafeland.vn/tin-tuc/tin-vui-dan-tphcm-ban-do-hanh-chinh-moi-sap-xong-dan-duoc-loi-gi-149831.html)

## 6. Phương pháp

- 10 truy vấn web cho 6 nhóm câu hỏi: địa điểm, ảnh, mô tả/món ăn, ranh giới/địa chỉ, thời tiết, độ cao.
- 1 truy vấn SPARQL trực tiếp vào `query.wikidata.org` (đếm đối tượng trong khung TP.HCM), User-Agent chung, không gửi thông tin cá nhân.
- **Chưa đọc trọn trang gốc nào.** Các điều khoản ở trên lấy từ trích đoạn kết quả tìm kiếm.
