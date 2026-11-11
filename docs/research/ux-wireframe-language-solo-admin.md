# GoWander — Rà soát UX, ngôn ngữ và Admin cho người phát triển solo

Ngày nghiên cứu: 11-11-2026 (Asia/Ho_Chi_Minh).

## 1. Phạm vi và mức độ bằng chứng

Theo yêu cầu chủ dự án: nghiên cứu, liệt kê và lưu vào docs; chưa sửa prototype hoặc triển khai tính năng. Báo cáo tổng hợp các trao đổi về wireframe, xác thực, khách du lịch và quy mô vận hành.

- Đã đọc mã `code.js` và `manifest.json` trong `/Users/legiabao/Downloads/GoWander-Figma-Plugin`.
- Đã đối chiếu [Mobility/Discover](../design/03-product-ux-v4.0/02-mobility-discover-requirements.md), [Identity](../design/03-product-ux-v4.0/03-identity-exploration-community-rewards.md), [Admin](../design/03-product-ux-v4.0/05-admin-web.md), `OVERVIEW.md` và `PIPELINE.md`.
- Đã tra cứu các nguồn chính thức ở mục 8 trong phiên nghiên cứu ngày trên. Đây không phải khảo sát toàn bộ thị trường.
- Phát hiện từ mã không đồng nghĩa đã kiểm chứng bố cục render. Chưa chạy thử prototype Figma, chưa phỏng vấn người dùng và chưa có đo lường usability.
- Không tuyên bố GoWander tốt hơn đối thủ. Các lợi ích dưới đây là giả thuyết cần kiểm thử.
- Không thay trạng thái milestone, không gỡ chặn dữ liệu transit và không coi nghiên cứu này là phê duyệt triển khai scope hoãn lại.

## 2. Yêu cầu người dùng đã xác nhận

1. Prototype nằm ngoài repository; tài liệu phân tích nằm trong `docs/`.
2. Dùng ảnh thật phù hợp địa điểm, có nguồn/quyền sử dụng; không dùng ảnh AI để thay thế ảnh nhận diện thực tế.
3. Admin có 1–2 người, chủ dự án làm solo; một giao diện quản trị chung, có thể mở rộng về sau.
4. Cả admin và người dùng cần email/tên đăng nhập/SĐT + mật khẩu; quên mật khẩu nhận OTP qua email, dùng dịch vụ gửi mail Google.
5. Phân tích ngôn ngữ cho cả khách Việt Nam và khách quốc tế. Thứ tự ngôn ngữ ở mục 5 là đề xuất, chưa phải phạm vi phát hành được xác nhận.

## 3. Phát hiện trực tiếp từ mã wireframe

| ID | Quan sát | Hệ quả / đề xuất |
|---|---|---|
| F01 | Không có prototype reactions trong mã đã đọc | Chưa thử được hành trình bấm và quay lại; cần nối luồng trước usability test |
| F02 | Màn dài tăng chiều cao frame nhưng chưa cấu hình scrolling | Chưa chứng minh trải nghiệm cuộn trong viewport điện thoại và CTA cố định |
| F03 | A2b truyền nhãn bản đồ ở đối số thứ năm; `screen` nhận bốn đối số | Lời gọi này không tạo nền bản đồ như ý định |
| F04 | Wireframe dùng bảng xám `G`; biến màu chỉ được bind trên bảng swatch | Đổi palette chưa đổi diện mạo các màn |
| F05 | Ảnh đều là placeholder, chưa tạo image fill | Cần ảnh và nội dung thật để đánh giá khả năng nhận diện và bố cục |
| F06 | Admin rộng 1440, sidebar 248, main padding ngang tổng 64 | Vùng nội dung còn 1128 px; hai cột 700 + 24 + 404 đã dùng hết, padding của component bên trong có thể gây tràn; cần render kiểm tra |
| F07 | Hàng 5 KPI rộng 5 × 212 + 4 × 16 = 1124 px; card có padding và nhãn dài | Sát giới hạn 1128 px; chưa kết luận hàng KPI tự nó tràn. Cần kiểm tra kích thước thực sau Auto Layout, không cộng padding hai lần |
| F08 | Một số bảng có tổng cột, gap và padding vượt chiều rộng nội dung | Nguy cơ bị cắt do frame cha `clipsContent`; cần cuộn ngang có chủ đích hoặc giảm cột |
| F09 | Nhiều text node trong input, listRow, weather không có chiều rộng xuống dòng | Nguy cơ tràn với tên/địa chỉ dài, đặc biệt khi dịch |
| F10 | A4 ghi tổng 24 phút nhưng chặng 4 + 9 + 6 = 19 phút | Cần giải thích thời gian chờ/chuyển bằng dữ liệu |
| F11 | Nhiều empty/error/permission state mới là chú thích | Chưa có màn phục hồi thao tác thực sự |
| F12 | Admin và Profile mới có nút Google | Chưa thể hiện yêu cầu mật khẩu/OTP mới |

Đính chính nhận xét hội thoại trước: không có đủ bằng chứng để khẳng định riêng hàng KPI tràn chỉ từ phép tính 1124/1128; cần render. Các bảng rộng và text chưa wrap vẫn là rủi ro quan sát được từ cấu trúc.

## 4. Danh sách cải thiện UX và đối chiếu

### 4.1. Điều hướng, tìm kiếm và bản đồ

1. Giữ hai luồng đích-đến-trước và khám-phá-trước; không bắt đăng nhập để xem bản đồ/tìm tuyến.
2. Điều hướng theo bản phát hành: Core ưu tiên Bản đồ/Khám phá; không mở tab chưa có chức năng. Nếu trình bày bản tương lai, tách khỏi Core.
3. Ô tìm điểm đến và nút Tìm tuyến dùng chung dữ liệu; không bắt nhập lại khi chuyển điểm vào.
4. Giữ từ khoá, bộ lọc, vị trí cuộn và điểm đi/đến khi quay lại. W3C khuyến nghị tránh nhập lại thông tin trong cùng quy trình [S06].
5. Bottom sheet bản đồ có trạng thái thu gọn/nửa màn/toàn màn, không che tuyến và vị trí quan trọng.
6. Xin quyền vị trí khi cần; hỗ trợ nhập thủ công/chọn bản đồ khi từ chối. Phân biệt quyền bị từ chối và GPS chưa có vị trí.
7. Phân biệt ga với địa điểm cùng tên bằng loại, địa chỉ và khu vực.
8. Tìm kiếm hỗ trợ tiếng Việt không dấu và tên gọi phổ biến đã kiểm chứng; không giả định danh sách ga cố định là lịch sử tìm kiếm.
9. Sau khi kéo bản đồ, cân nhắc “Tìm trong khu vực này” thay vì đổi kết quả ngoài ý muốn.

### 4.2. Tìm tuyến và thực hiện hành trình

10. Có hoán đổi điểm đi/đến; Đi ngay/Khởi hành lúc/Đến trước khi dữ liệu cho phép. Google Maps đã có chọn thời gian [S01]; đây là tính tiện dụng, không phải lợi thế độc quyền.
11. So sánh tuyến bằng cùng bộ chỉ số: thời gian, đi bộ, chuyển tuyến, giờ đến và thời gian chờ nếu có.
12. Giải thích đánh đổi như “lâu hơn nhưng ít đi bộ”; không dùng nhãn Rẻ nhất khi thiếu giá.
13. Chi tiết cần hướng tàu, ga lên/xuống, chặng chuyển và bước tiếp theo; thời gian theo lịch không được gọi realtime.
14. Cơ hội địa phương: ảnh thật cửa vào/ra, biển chỉ hướng và mặt tiền điểm đến; Citymapper đã có hướng dẫn cửa ga [S02], GoWander cần chất lượng dữ liệu TP.HCM để tạo giá trị.
15. Màn sau Bắt đầu ưu tiên chặng hiện tại và bước kế tiếp. Nhắc xuống ga/tự phát hiện đi sai cần kiểm chứng định vị trước [S03].
16. Ít đi bộ không đồng nghĩa không cầu thang. Chỉ cung cấp tuyến tiếp cận khi có dữ liệu cho cả hành trình [S04, S05].
17. Mất mạng giữ thông tin đã nhập; có thể đề xuất lưu tóm tắt tuyến kèm thời điểm cập nhật, không hứa tìm tuyến offline.
18. Tách NoRoute/OutOfCoverage/Timeout/ProviderError/NoNetwork; mỗi trạng thái có hành động phù hợp.
19. Thời tiết ngắn gọn, ghi nguồn/giờ; chi tiết tại tuyến có đi bộ. Giữ quy tắc ADR-021, không tự xếp lại tuyến theo thời tiết.
20. Báo sai tuyến gắn đúng chặng/ga, cho chọn loại lỗi; chỉ đính kèm dữ liệu cần thiết, không âm thầm thu cả hành trình GPS.

### 4.3. Discover, ảnh và nội dung

21. Phân biệt món ăn và địa điểm phục vụ món; kết quả dẫn tới đúng loại nội dung.
22. Thử tổ chức theo nhu cầu: Gần ga metro, Đi dạo, Ẩm thực, Lịch sử. Bộ lọc giờ mở cửa/giá cần dữ liệu xác minh.
23. Ưu tiên ảnh, tên, địa chỉ và Chỉ đường trong Place Detail; câu chuyện dài ở phía sau. CTA dễ tìm khi cuộn.
24. Ảnh phục vụ nhận diện: mặt tiền, lối vào, không gian; có chú thích, nguồn/quyền sử dụng và trạng thái tải lỗi.
25. Cơ hội khác biệt: gợi ý gần ga xuống/gần tuyến, giải thích vì sao thuận đường; không dùng khoảng cách đường chim bay để cam kết quãng đi bộ.
26. Trip Ideas kết hợp danh sách điểm với bản đồ như mẫu Wanderlog [S07], nhưng lịch trình biên tập không đồng nghĩa tuyến tự tối ưu.
27. Đưa chú thích kỹ thuật ra ngoài màn sản phẩm; dùng nội dung mẫu có nhãn ở phần trình bày prototype, không giả số liệu thật.
28. Thống nhất nhãn tiếng Việt, hỗ trợ chữ lớn, vùng chạm và trạng thái không chỉ dựa vào màu.

## 5. Ngôn ngữ và khách du lịch

### 5.1. Căn cứ và giới hạn

Theo Cục Du lịch Quốc gia [S08], 10 tháng năm 2026: Trung Quốc gần 4,4 triệu lượt, Hàn Quốc 3,4 triệu, Nga 1,25 triệu, Đài Loan 1,07 triệu. Các thị trường tiếp theo có Mỹ, Campuchia, Nhật Bản, Ấn Độ, Philippines và Úc. Báo cáo năm 2025 cũng cho thấy quy mô lớn của Trung Quốc và Hàn Quốc [S09].

Đây là số liệu khách quốc tế toàn Việt Nam, không phải khách đi metro TP.HCM hay người dùng ứng dụng. Quốc tịch không xác định ngôn ngữ ưa thích. Không suy ra tỷ lệ bao phủ ngôn ngữ từ số lượt khách này. Chưa có khảo sát ngôn ngữ riêng cho GoWander.

### 5.2. Thứ tự đề xuất cho người phát triển solo

| Giai đoạn | Ngôn ngữ | Lý do |
|---|---|---|
| Ra mắt | Tiếng Việt (`vi`) | Người dùng Việt Nam, khách nội địa, nội dung địa phương |
| Ra mắt | English (`en`) | Ngôn ngữ cầu nối quốc tế; không giả định mọi khách đều thông thạo |
| Mở rộng đầu | Trung giản thể (`zh-Hans`), Hàn (`ko`) | Ứng viên từ quy mô thị trường; thêm từng ngôn ngữ khi có người rà bản dịch |
| Đánh giá tiếp | Trung phồn thể (`zh-Hant`), Nhật (`ja`), Nga (`ru`) | Quyết định theo nhu cầu thực ở khu vực pilot và năng lực biên tập |
| Theo nhu cầu | Khmer, Pháp, Đức, Thái, Indonesia, Malay, ngôn ngữ khác | Không mở rộng chỉ để tăng số ngôn ngữ trong menu |

Khách Ấn Độ không mặc định dùng Hindi. Trước mắt có tiếng Anh; khảo sát nhu cầu trước khi chọn thêm ngôn ngữ. Thứ tự ngôn ngữ không phải xếp hạng số khách theo quốc gia.

### 5.3. UX đa ngôn ngữ

- Dịch đầy đủ Việt–Anh trước: điều hướng → tìm tuyến/các chặng/lỗi → đăng nhập/OTP → địa điểm → câu chuyện dài.
- Gợi ý theo ngôn ngữ thiết bị, cho đổi khi chưa đăng nhập, nhớ lựa chọn; dùng tên bản địa thay vì chỉ cờ quốc gia.
- Giữ tên chính thức tiếng Việt bên cạnh tên dịch khi chỉ đường để đối chiếu biển ga. Có sao chép địa chỉ và tùy chọn hiển thị cho người địa phương.
- Tên dịch/tên không dấu cùng trỏ tới một ID địa điểm; không tự dịch tên riêng tùy tiện.
- Thiếu bản dịch phải có fallback rõ; dịch máy nội dung dài có nhãn và bản gốc. Hướng dẫn giao thông/xác thực cần rà soát.
- Admin dùng tiếng Việt; editor có tab Việt/English và trạng thái Thiếu bản dịch/Cần cập nhật/Đã rà soát. Chưa cần hệ thống quản lý dịch riêng.
- Kiểm thử text dài, font Việt/CJK, ngày giờ/đơn vị và thông báo lỗi. Hiển thị VND rõ, không tự quy đổi tiền khi thiếu nguồn tỷ giá.

## 6. Đăng nhập và OTP — yêu cầu chung

1. Một trường Email/Tên đăng nhập/SĐT + Mật khẩu; có hiện/ẩn, autofill, dán và Quên mật khẩu. Google có thể là phương thức phụ.
2. Định danh không nhập nhằng: chuẩn hóa SĐT, quy định username; không bắt khai cả ba định danh khi đăng ký.
3. Nếu khôi phục chủ yếu qua email, tài khoản mật khẩu cần email đã xác minh. SĐT dùng đăng nhập phải được xác minh riêng.
4. Đăng nhập xong tiếp tục công việc đang làm; không luôn đẩy về trang chủ.
5. Quên mật khẩu: nhập định danh → OTP tới email đã xác minh → kiểm tra mã → mật khẩu mới → đăng nhập lại.
6. SMTP Google chỉ vận chuyển email; GoWander hoặc hệ thống identity sinh/kiểm tra OTP. Chọn Gmail/Workspace và cấu hình sau, không xem nhà cung cấp identity đã chốt [S12].
7. Hỗ trợ dán cả mã, gửi lại có thời gian chờ, mã sai/hết hạn/đã dùng, email đến chậm, mất quyền email. Không chặn password manager [S10].
8. OTP dùng một lần, hạn ngắn được cấu hình, giới hạn thử/gửi; phản hồi không tiết lộ tài khoản tồn tại. Không tùy tiện hiện email che một phần cho người chưa xác thực [S11].
9. Không gửi mật khẩu qua email. Sau đổi mật khẩu, thông báo chủ tài khoản và thu hồi phiên theo chính sách.
10. Tài khoản chỉ dùng Google cần luồng thiết lập mật khẩu an toàn; không tự gộp tài khoản trùng email [S13].
11. Admin được cấp tài khoản, không đăng ký công khai. MFA cho chủ quản trị và quy trình recovery riêng; reset mật khẩu không được bỏ qua MFA.

Yêu cầu mật khẩu là thay đổi so với hướng OIDC/Google-first trong tài liệu gốc. Báo cáo ghi nhận yêu cầu, chưa thay ADR, schema hay chọn provider; cần quyết định kiến trúc trước triển khai.

## 7. Admin phù hợp quy mô 1–2 người

### 7.1. Phạm vi hiện tại

- Một giao diện chung; một hoặc hai tài khoản riêng có thể cùng quyền Admin. Không chia năm vai trò/phòng ban ở giai đoạn solo.
- Năm mục chính đề xuất: Tổng quan, Nội dung, Tuyến & dữ liệu, Phản hồi, Cài đặt.
- Nội dung chứa địa điểm/món ăn/câu chuyện/media; Cài đặt chứa tài khoản admin, cấu hình gửi mail và nhật ký.
- Nháp → Xem trước → Xuất bản; chủ dự án tự xuất bản, không phải gửi duyệt cho chính mình.
- Chưa cần ma trận quyền tùy biến, tenant, nhiều cấp phê duyệt, dashboard thương mại khi chưa có hoạt động thật.
- Backend vẫn kiểm tra người dùng thường/Admin và quyền sở hữu; giao diện chung không đồng nghĩa dùng chung mật khẩu.

### 7.2. Tiện ích vận hành cần giữ

- Danh sách có tìm/lọc/sắp xếp, số kết quả, xoá lọc; quay lại giữ trang và bộ lọc [S14].
- Lưu nháp có trạng thái, xem trước mobile, cảnh báo rời trang và phát hiện xung đột sửa nội dung.
- Kiểm tra nguồn ảnh, giấy phép, vị trí và trường bắt buộc trước xuất bản; báo lỗi ngay tại trường.
- Nhật ký tối thiểu ai/làm gì/khi nào; thao tác nguy hiểm có xác nhận. Không cho sửa tự do nhật ký.
- Nhãn transit phân biệt PoC, giấy phép, chất lượng và đang phục vụ thật; kiểm tra kỹ thuật đạt không đồng nghĩa được phép phát hành.
- Nếu Community triển khai sau: làm rõ Giữ nội dung/Từ chối báo cáo, tránh hai hành động trùng nghĩa; thêm người nhận xử lý khi có nhu cầu.
- Backup/restore vẫn theo cổng an toàn hiện hành; vận hành solo không bỏ kiểm thử phục hồi.

### 7.3. Mở rộng khi có nhu cầu

Chỉ thêm Editor/Moderator hoặc bước duyệt khi có người thực sự phụ trách. Giữ ID nội dung, bản dịch, tài khoản riêng, audit và kiểm tra quyền server để mở rộng được; chưa xây bộ máy quản trị lớn trước.

## 8. Nguồn đã đối chiếu

Tất cả truy cập trong phiên nghiên cứu ngày 11-11-2026; tài liệu sản phẩm có thể thay đổi. Mẫu đối thủ là tham khảo tính năng, không phải nghiên cứu thực nghiệm về người dùng GoWander.

- S01 — [Google Maps: directions, lựa chọn thời gian](https://support.google.com/maps/answer/144339?hl=en).
- S02 — [Citymapper: station exits](https://citymapper.com/news/668/station-exits).
- S03 — [Citymapper GO: hướng dẫn từng bước và nhắc xuống](https://citymapper.com/news/517/go-your-personal-trip-assistant).
- S04 — [Citymapper: step-free journeys](https://citymapper.com/news/2577/find-the-best-accessible-journey-with-step-free-routes).
- S05 — [TfL Go: step-free journeys](https://tfl.gov.uk/plan-a-journey/using-tfl-go).
- S06 — [W3C: Redundant Entry](https://www.w3.org/WAI/WCAG22/Understanding/redundant-entry.html).
- S07 — [Wanderlog: itinerary và bản đồ](https://wanderlog.com/plan-a-trip).
- S08 — [Cục Du lịch Quốc gia: 10 tháng 2026, công bố 03-11-2026](https://thongke.tourism.vn/index.php/news/items/430).
- S09 — [Cục Du lịch Quốc gia: cả năm 2025, công bố 21-01-2026](https://thongke.tourism.vn/index.php/news/items/354).
- S10 — [W3C: Accessible Authentication](https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html).
- S11 — [OWASP: Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
- S12 — [Google Workspace: SMTP relay](https://knowledge.workspace.google.com/admin/gmail/advanced/route-outgoing-smtp-relay-messages-through-google).
- S13 — [OWASP: Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
- S14 — [Nielsen Norman Group: Data Tables](https://www.nngroup.com/articles/data-tables/).

## 9. Ưu tiên và kiểm chứng tiếp theo

1. Render và nối luồng hiện tại; kiểm tra tràn/cắt, quay lại, cuộn, bàn phím và nội dung dài.
2. Thử tác vụ tìm tuyến, khách từ chối GPS, mất mạng, Discover→Route; đo hoàn thành/lỗi/điểm mắc kẹt.
3. Thử đăng nhập/OTP và editor sửa địa điểm rồi quay lại danh sách; chưa coi xác thực an toàn chỉ vì có wireframe.
4. Hoàn thiện Việt–Anh và ảnh thật; khảo sát ngôn ngữ người dùng pilot trước khi mở rộng.
5. Tính năng cửa ga, tiếp cận, nhắc xuống và gợi ý theo tuyến chỉ tiến hành khi dữ liệu/PoC phù hợp.

Trạng thái: nghiên cứu tài liệu đã tổng hợp; render, usability, chất lượng bản dịch và triển khai đều **chưa kiểm chứng**. Tài liệu này không tự sửa các nguồn thiết kế đang được người khác cập nhật.
