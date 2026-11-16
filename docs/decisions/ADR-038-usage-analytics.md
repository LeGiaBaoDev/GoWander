# ADR-038 — Đo lường sử dụng: chỉ lưu bộ đếm gộp theo ngày, không lưu sự kiện từng người

- **Trạng thái:** Accepted · **Ngày:** 16-11-2026
- **Liên quan:** OVERVIEW §8 (không theo dõi lộ trình GPS, không để dữ liệu cá nhân thô trong analytics), `docs/design/05-database-design-v1.2/09-dashboards-privacy-migrations-testing.md` §16, ADR-037 (Admin), PIPELINE §11 (M07)

## Bối cảnh

Chủ dự án muốn xem trong Admin: số khách dùng app, điểm đến được chọn nhiều, phương tiện hay dùng, tuyến xe buýt được dùng nhiều (lọc theo khu vực). Trước quyết định này hệ thống không ghi gì về việc sử dụng: server tính lộ trình xong là quên, app không gửi sự kiện, khách không có tài khoản.

Vị trí xuất phát và điểm đến là **dữ liệu cá nhân** (Luật Bảo vệ dữ liệu cá nhân, hiệu lực 1-1-2026). Thiết kế cơ sở dữ liệu có bảng `usage_events` (mỗi sự kiện một dòng); một dòng "lúc 7:02 đi từ điểm A đến điểm B" đủ để lần ra nơi ở hay nơi làm của một người.

## Quyết định

1. **Chỉ lưu bộ đếm đã gộp theo ngày** (giờ Việt Nam), không lưu dòng sự kiện từng lượt, không lưu tọa độ, IP, mã thiết bị. Tọa độ chỉ nằm trong bộ nhớ server trong lúc tìm phường; không ghi log, không ghi database. Bảng `usage_events` trong thiết kế **không làm**.
2. **Khu vực là phường/xã** (đơn vị cấp xã sau sáp nhập 7-2025; không còn cấp quận). Ranh giới lấy từ OpenStreetMap (ODbL), bảng `admin_areas`, chỉ trong TP.HCM mới.
3. **Bước 2 — đếm ở server từ yêu cầu tìm đường thành công** (không cần sửa app):
   - `usage_route_plans`: ngày × phương tiện yêu cầu × đầu (`FROM`/`TO`) × phường → số lượt. Phường đi và phường đến đếm **riêng**, không ghép cặp (cặp đi–đến lặp lại hằng ngày chính là nhà–chỗ làm).
   - `usage_bus_lines`: ngày × tuyến × phường xuất phát → số lượt tuyến đó **có trong gợi ý** (mỗi tuyến đếm một lần mỗi lượt tìm).
   - `usage_place_routes`: ngày × địa điểm → số lượt tìm đường tới điểm cách một địa điểm đang hiển thị không quá 30 m.
4. **Gọi tên đúng thứ được đo.** Server chỉ biết khách đã *tìm* và tuyến nào *được gợi ý*; không biết khách có đi, có lên xe hay không. Admin ghi "lượt tìm đường", "có trong gợi ý", "được tìm đường tới".
5. **Ẩn số nhỏ:** khi hiển thị, mục nào dưới **5** lượt trong khoảng đang xem thì không đưa vào danh sách xếp hạng, và mọi con số 1–4 hiện là "<5". Bộ đếm trong database vẫn giữ số thật để cộng dồn đúng.
6. **Giữ 25 tháng** (đủ so sánh cùng kỳ năm trước), xoá tự động mỗi ngày.
7. **Ghi theo kiểu "cố gắng hết sức":** tìm đường không bao giờ chờ hay thất bại vì bộ đếm. Mỗi lượt được xếp vào hàng đợi trong bộ nhớ (1.000 chỗ) và một goroutine ghi; hàng đầy hoặc server tắt thì mất lượt đó (ghi log cảnh báo). Số liệu là ước lượng, không dùng để tính tiền.
8. **Quyền database:** role API công khai `gowander_app` vẫn chỉ đọc nội dung; được thêm `SELECT, INSERT, UPDATE, DELETE` riêng trên ba bảng bộ đếm và `SELECT` trên `admin_areas`. `gowander_admin` chỉ đọc. Trang "Hoạt động" chỉ SYSTEM_ADMIN xem.
9. **Bước 3 (sau, cần bản app mới):** người dùng theo ngày qua một mã ngẫu nhiên đổi mỗi ngày, chỉ dùng để đếm số mã khác nhau trong ngày rồi bỏ; lượt xem, lưu địa điểm; lượt bấm "Bắt đầu" kèm tuyến đã chọn. Cũng đếm gộp như trên; app cho phép tắt trong Cài đặt.

## Không chọn

- **Bảng sự kiện thô (`usage_events`) + gộp sau:** linh hoạt khi cần chỉ số mới, nhưng phải giữ và bảo vệ dữ liệu vị trí từng người, phải có chính sách xoá, đơn xin xoá dữ liệu. Không đáng ở giai đoạn pilot.
- **Firebase Analytics / dịch vụ ngoài:** gửi dữ liệu người dùng ra bên thứ ba, thêm SDK vào app; không lọc được theo tuyến buýt và phường của chính mình.
- **Lưu tọa độ đã làm tròn (ô lưới ~1 km):** vẫn lần ra được ở vùng ít người; phường dễ hiểu hơn với người vận hành.

## Hệ quả

- Đổi định nghĩa bộ đếm thì số liệu cũ không tính lại được (không có sự kiện thô). Thêm chỉ số mới chỉ có số từ ngày bắt đầu đếm.
- Trước pilot gần như chỉ có chủ dự án dùng app: trang ghi "chưa đủ dữ liệu", không hiện số 0 gây hiểu nhầm.
- Ranh giới phường lấy từ OSM có thể lệch với văn bản chính thức; nạp lại bằng `scripts/build_area_seed.py` khi OSM cập nhật.

## Sửa đổi 16-11-2026 (chủ dự án)

Chủ dự án xem trang đầu tiên và sửa: "khu vực là theo thành phố á, và khách truy cập, điểm đến yêu thích hay gì đó dùng cho trang tổng quan mà".

- **Khu vực là tỉnh, thành** (cấp tỉnh sau sáp nhập 7-2025), không phải phường. `admin_areas` giờ chứa tỉnh, thành của Việt Nam từ OSM (`data/areas/vn-provinces.sql`, `scripts/build_area_seed.py`). OSM đang dở việc cập nhật sáp nhập: tỉnh cũ ghi "cũ" bị bỏ, tỉnh cũ nằm trong tỉnh mới bị xoá khi nạp, và khi một điểm thuộc nhiều vùng thì chọn vùng lớn nhất. Đủ 34/34: ranh giới tỉnh Khánh Hòa trong OSM bị hở (osmium không dựng được polygon) nên được ghép từ 64/65 phường/xã con của nó (sửa 16-11-2026). Hoàng Sa và Trường Sa không có polygon trong OSM: bản đồ Admin vẽ và ghi tên hai quần đảo như bản đồ vùng miền của app; lượt tìm đường từ đó hiện "chưa rõ tỉnh, thành".
- **Số liệu nằm ở trang Tổng quan**, phần "Khách dùng app", lọc theo khoảng ngày và tỉnh, thành xuất phát; trang "Lượt sử dụng" riêng bị bỏ. Mọi vai trò Admin xem được (chỉ là số gộp).
- **Lượt mở app** (khách truy cập, tính theo lượt): app gửi `POST /api/v1/usage/open` mỗi lần khởi động, không kèm mã, vị trí hay nội dung gì; server cộng 1 vào `usage_app_opens` của ngày. Không phân theo tỉnh vì không có vị trí. Đây là số lượt, không phải số người khác nhau. Ai cũng gọi được endpoint này nên số có thể bị thổi phồng; chấp nhận ở giai đoạn pilot.
- Điểm đến được tìm nhiều nhất hiện kèm loại địa điểm (chợ, quán ăn, bảo tàng...).

## Cập nhật 16-11-2026 (chiều): thống kê theo tuyến xe buýt thay cho tỉnh, thành

Chủ dự án: "lúc trước tôi nói thống kê tỉnh thành, thì bỏ đi mà thống kê theo tuyến xe buýt nha, và xem chart nào quan trọng cần thiết mới giữ". Admin **không còn hiện** bản đồ tỉnh, bộ lọc tỉnh, danh sách tỉnh xuất phát và tỉnh đến. Thay vào đó: bảng xếp hạng tuyến xe buýt, metro có trong gợi ý, và bộ lọc một tuyến (số lần tuyến đó có trong gợi ý theo ngày, tuần, tháng, năm, so với kỳ trước). Ngưỡng 5 vẫn áp dụng cho bảng xếp hạng. Bộ đếm phía API giữ nguyên (`usage_route_plans` và `usage_bus_lines` vẫn ghi `area_code`); bỏ việc ghi theo tỉnh là việc riêng nếu chủ dự án muốn.

## Cập nhật 16-11-2026 (tối): loại xe, ô điểm đến, đo "khách đi đâu"

Chủ dự án duyệt đợt A ("OK LÀM ĐI"):
- **Tuyến theo loại xe:** `usage_bus_lines.vehicle` (BUS, METRO, BOAT) lấy từ loại chặng của OTP (SUBWAY/RAIL/TRAM là metro, FERRY là buýt sông). Admin có tab Tất cả / Xe buýt / Metro / Buýt sông.
- **Ô điểm đến ~500 m** (`usage_dest_cells`, ô = floor(vĩ độ / 0.0045), floor(kinh độ / 0.0045)): mỗi lượt tìm đường thành công cộng 1 vào ô chứa điểm đến. Không lưu toạ độ, không gắn với điểm đi; ô dưới 5 lượt không hiện; giữ 25 tháng như các bộ đếm khác. Admin vẽ bản đồ điểm nóng, kèm số địa điểm đang hiển thị trong ô; ô đông (mức 3–4) mà có từ 2 địa điểm trở xuống được đánh dấu "Ít quán" để ưu tiên tìm quán affiliate.
- **Đo "khách đến":** hiện chỉ có mức 1 (tìm đường tới, cách địa điểm ≤ 30 m). Mức 2 (bấm "Bắt đầu") và mức 3 (app thấy còn cách đích < 50 m khi đang dẫn đường) cần bản app mới (đợt B); app chỉ gửi id địa điểm, không gửi vị trí; có nút tắt trong Cài đặt. Chưa làm.
