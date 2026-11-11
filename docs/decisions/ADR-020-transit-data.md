# ADR-020 — Dữ liệu transit: dùng feed giả định cho Metro số 1 trong khi chờ dữ liệu chính thức

- **Trạng thái:** Accepted (tạm thời) · **Ngày:** 11-11-2026 · **Người quyết định:** chủ dự án
- **Liên quan:** [`docs/research/transit-data-feasibility.md`](../research/transit-data-feasibility.md), `PIPELINE.md` M01–M02
- **Ghi chú đánh số:** ADR-001 đến ADR-019 đã được dùng trong tài liệu kiến trúc (`docs/design/04-technical-architecture-v2.1/08-poc-roadmap-adrs-open-decisions.md`).

## Bối cảnh

Nghiên cứu M01 không tìm thấy bộ GTFS nào dùng được hợp pháp cho xe buýt hay metro TP.HCM. Chủ dự án không muốn dừng lại chờ dữ liệu chính thức.

## Quyết định

1. **Lưu lại chỗ kẹt.** M01 vẫn là BLOCKED về dữ liệu chính thức. Việc xin dữ liệu từ Trung tâm Quản lý giao thông công cộng và HURC1 được hoãn, chưa huỷ.
2. **Đi tiếp bằng một feed GTFS tự biên soạn, có gắn nhãn giả định**, dựng từ thông tin công khai có nguồn.
3. **Phạm vi theo hai bước:**
   - Bước 1 (đang làm): Metro số 1 Bến Thành – Suối Tiên, 14 ga, kết hợp đi bộ.
   - Bước 2 (sau khi bước 1 chạy được trên OTP): thêm 3–5 tuyến xe buýt nối các ga với khu trung tâm. Chưa chọn tuyến.
4. **Feed nằm ở** `data/assumed/hcmc-metro-l1-gtfs/`, sinh bởi `scripts/build_metro_l1_assumed_gtfs.py`. Không sửa tay các file `.txt`; sửa script rồi chạy lại.

## Dữ liệu: cái gì có nguồn, cái gì giả định

| Hạng mục | Giá trị | Loại | Nguồn |
|---|---|---|---|
| Thứ tự, tên, toạ độ 14 ga | Xem `stops.txt` | Có nguồn | OpenStreetMap, relation [11919223](https://www.openstreetmap.org/relation/11919223), dữ liệu ngày 11-11-2026, ODbL |
| Chuyến đầu | 05:00 mọi ngày | Có nguồn | [PLO](https://plo.vn/metro-so-1-dieu-chinh-giam-chuyen-tau-trong-dip-he-2026-post910778.html), [MRTDi](https://mrtdi.com/lich-tau-chay-metro-hcm) |
| Chuyến cuối xuất bến | 22:00 thứ Hai–thứ Năm; 23:00 thứ Sáu–Chủ nhật | Có nguồn | Như trên |
| Số lượt tàu mỗi ngày (cả hai chiều) | 228 (T2–T5), 236 (T6), 230 (T7–CN) | Có nguồn | PLO, biểu đồ áp dụng từ 01-06-2026 |
| Giãn cách | 6–7 phút giờ cao điểm; 8–15 phút giờ thấp điểm | Có nguồn | PLO |
| Thời gian chạy toàn tuyến | 29 phút 30 giây, gồm 30 giây dừng mỗi ga | Có nguồn, **số liệu chạy thử** trước khai thác | Kết quả tìm kiếm báo chí về đợt chạy thử |
| Khung giờ cao điểm | 06:00–08:30 và 16:30–19:00, ngày thường | **Giả định** | Không có nguồn |
| Giãn cách cụ thể | 390 giây cao điểm; thấp điểm tính ngược từ số lượt tàu (655 giây T2–T5, 669 giây T6, 564 giây T7–CN) | **Giả định** | Suy ra từ các số có nguồn |
| Chia đều số lượt cho hai chiều | 114 / 118 / 115 mỗi chiều | **Giả định** | Không có nguồn |
| Thời gian từng chặng | Chia 29 phút 30 giây theo khoảng cách đường thẳng giữa các ga | **Giả định** | Không có nguồn |
| Cuối tuần không có giờ cao điểm | Giãn cách đều cả ngày | **Giả định** | Không có nguồn |
| Hiệu lực lịch | 01-06-2026 đến 31-12-2026 | Ngày đầu có nguồn; ngày cuối **giả định** | PLO; SGGP nêu biểu đồ còn áp dụng tới 13-10-2026 |
| Ngày lễ, Tết | Không mô hình hoá | **Bỏ qua** | — |
| Giá vé | Không đưa vào feed | **Bỏ qua** | — |
| `agency_url` | Trỏ tới relation OSM | **Tạm thời** | Chưa xác minh trang chính thức của HURC1 |

Chưa đọc được văn bản biểu đồ chạy tàu gốc của HURC1; mọi số liệu lịch chạy đều qua báo chí.

## Hệ quả

- M02 được làm như một **PoC kỹ thuật**. Kết quả không được gọi là sẵn sàng cho TP.HCM, và không làm M01 thành PASS.
- Feed mang nhãn giả định trong `feed_info.txt`, `routes.txt` (`route_desc`), `README.txt` và `feed_version` (`assumed-…`). Ứng dụng không được hiển thị nó như lịch chính thức; thời gian phải ghi là theo lịch ước tính.
- `stops.txt` là dữ liệu phái sinh từ OpenStreetMap: phải ghi công "© OpenStreetMap contributors" và tuân thủ ODbL khi phân phối.
- Feed sẽ sai khi HURC1 đổi biểu đồ chạy tàu. Không có cơ chế tự cập nhật.
- Chưa chạy trình kiểm tra GTFS chuẩn (canonical validator); mới có kiểm tra nội bộ trong script.

## Điều kiện xem lại quyết định

- Trước pilot với người dùng thật (M10): phải có dữ liệu có quyền sử dụng hoặc xác nhận bằng văn bản của đơn vị vận hành.
- Khi có feed chính thức, hoặc khi HURC1 công bố biểu đồ chạy tàu mới.
- Khi bắt đầu bước 2 (thêm xe buýt): viết ADR mới hoặc cập nhật ADR này với danh sách tuyến và nguồn.
