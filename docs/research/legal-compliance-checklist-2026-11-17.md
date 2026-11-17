# GoWander — Checklist pháp lý cho web/app du lịch, bản đồ và giao thông công cộng tại TP.HCM

> **Ngày lập:** 17-11-2026 (Asia/Ho_Chi_Minh)  
> **Phạm vi:** ứng dụng web/mobile GoWander, giai đoạn pilot tại TP.HCM; có tài khoản người dùng, nội dung địa điểm/ảnh, có thể có đánh giá cộng đồng, mã QR; về sau có quảng cáo, affiliate, đặt chỗ/bán vé.  
> **Tính chất:** tài liệu nghiên cứu nội bộ, **không phải tư vấn pháp lý** và **không kết luận chắc chắn** về việc một hành vi cụ thể có hợp pháp hay không. Mọi điểm đánh dấu **[Cần luật sư]** phải được luật sư Việt Nam có kinh nghiệm về dữ liệu, thương mại điện tử, bản đồ và du lịch xác nhận trước khi triển khai.  
> **Nguyên tắc:** chỉ nêu nghĩa vụ, tài liệu/UI cần có, nguồn chính thức và câu hỏi cần xác nhận; không thay thế ý kiến pháp lý. Các số điều chỉ được ghi khi đã đối chiếu văn bản hoặc trích lục đáng tin cậy; chỗ chưa chắc ghi rõ **“chưa kiểm chứng”**.

**Mục lục nhanh:** [0. Tóm tắt](#0-tóm-tắt-điều-hành) · [1. Bảng 20 điểm](#1-bảng-tóm-tắt-20-điểm) · [2. Chi tiết 20 điểm](#2-chi-tiết-20-điểm) · [3. Mảng bổ sung](#3-các-mảng-bổ-sung-rất-quan-trọng) · [4. Checklist tài liệu/UI](#4-checklist-tài-liệu-và-ui) · [5. Lộ trình](#5-lộ-trình-thực-hiện-đề-xuất) · [6. Câu hỏi cho luật sư](#6-câu-hỏi-cần-luật-sư-xác-nhận) · [7. Nguồn](#7-danh-sách-nguồn-truy-cập-ngày-17-11-2026) · [8. Chưa kiểm chứng](#8-những-điểm-chưa-kiểm-chứng--cần-xác-nhận-thêm)

---

## 0. Tóm tắt điều hành

### 0.1. Mười hai điểm rủi ro cao nhất

1. **Dữ liệu vị trí là dữ liệu cá nhân nhạy cảm** theo Nghị định 356/2025/NĐ-CP (Điều 4 khoản 1 điểm h). Vì vậy GoWander không thể coi location là dữ liệu “thông thường”; cần thông báo riêng, đồng ý riêng, phân quyền, bảo mật và quy trình sự cố riêng [S2][S44].
2. **Nghị định 13/2023/NĐ-CP đã hết hiệu lực từ 01-01-2026**, được thay bằng Luật 91/2025/QH15 và Nghị định 356/2025/NĐ-CP. Mọi tài liệu còn viện dẫn Nghị định 13 phải được cập nhật.
3. **Nghĩa vụ đánh giá tác động xử lý dữ liệu (DPIA) và hồ sơ chuyển dữ liệu xuyên biên giới** phải nộp trong **60 ngày** kể từ khi bắt đầu xử lý/chuyển dữ liệu; cập nhật định kỳ 6 tháng hoặc ngay trong 10 ngày với một số thay đổi. Miễn trừ cho doanh nghiệp nhỏ/khởi nghiệp **có thể không áp dụng** vì GoWander xử lý dữ liệu nhạy cảm (vị trí).
4. **Luật An ninh mạng 116/2025/QH15 (hiệu lực 01-7-2026) và Nghị định 333/2026/NĐ-CP (hiệu lực 19-8-2026)** đặt nghĩa vụ lưu trữ dữ liệu người dùng tại Việt Nam với doanh nghiệp cung cấp dịch vụ viễn thông, Internet, dịch vụ gia tăng trên không gian mạng. Nghị định 333/2026 Điều 2 khoản 5 định nghĩa “dịch vụ gia tăng trên không gian mạng” là **dịch vụ viễn thông giá trị gia tăng**, nên phạm vi có thể hẹp hơn cách hiểu ban đầu; vẫn cần luật sư xác nhận app du lịch có thuộc diện không. Kiến trúc hiện tại dùng Cloudflare R2/dịch vụ nước ngoài; cần rà soát lại.
5. **Bản đồ Việt Nam có rủi ro chủ quyền rất cao**: Luật Đo đạc và bản đồ 27/2018/QH14 (hợp nhất 70/VBHN-VPQH 2026) cấm xuất bản, lưu hành sản phẩm bản đồ liên quan chủ quyền mà không thể hiện hoặc thể hiện không đúng chủ quyền, biên giới. Nghị định 18/2020/NĐ-CP (hợp nhất 72/VBHN-BNNMT 2026) và Nghị định 174/2026/NĐ-CP (Điều 93 khoản 3 điểm a) có mức phạt 30–40 triệu đồng (cách tính theo cá nhân/tổ chức khác nhau — xem §A). Tile OpenStreetMap/OpenFreeMap **không mặc nhiên** đáp ứng yêu cầu này.
6. **Nghị định 147/2024/NĐ-CP** có thể biến GoWander thành “mạng xã hội” khi có tài khoản + đánh giá/bình luận/theo dõi. Mạng xã hội trong nước phải có giấy phép (nếu lượng truy cập lớn) hoặc giấy xác nhận thông báo (nếu nhỏ), kèm xác thực tài khoản, quản lý nội dung, tên miền `.vn` và máy chủ tại Việt Nam (với một số loại hình).
7. **Khung TMĐT đã thay đổi từ 01-7-2026**: Luật Thương mại điện tử 122/2025/QH15 và Nghị định 248/2026/NĐ-CP thay thế Nghị định 52/2013/NĐ-CP và Nghị định 85/2021/NĐ-CP (Nghị định 248/2026 Điều 52 khoản 3). Nếu GoWander bán vé/dịch vụ có chức năng đặt hàng trực tuyến, phải thông báo nền tảng TMĐT với UBND cấp tỉnh trước khi bán; nếu chỉ affiliate, có thể không thuộc diện nền tảng TMĐT nhưng vẫn chịu luật quảng cáo/người tiêu dùng — cần luật sư xác nhận.
8. **Luật Bảo vệ quyền lợi người tiêu dùng 19/2023/QH15** không quy định “đổi trả 7 ngày” chung. Trường hợp thông tin giao dịch từ xa không đầy đủ, người tiêu dùng có quyền đơn phương chấm dứt trong **30 ngày** và được hoàn tiền trong **30 ngày**; chính sách hoàn tiền tự nguyện không được cắt giảm quyền luật định.
9. **Quảng cáo**: Luật sửa đổi 75/2025/QH15, Nghị định 342/2025/NĐ-CP và Nghị định 87/2026/NĐ-CP siết chặt quảng cáo trên mạng. Dùng “nhất”, “duy nhất”, “tốt nhất”, “số một” mà không có tài liệu hợp pháp chứng minh bị phạt **10–20 triệu đồng với cá nhân, 20–40 triệu đồng với tổ chức**.
10. **Bản quyền ảnh**: ảnh Wikimedia/Mapillary có giấy phép riêng (thường CC BY/CC BY-SA), OSM là ODbL, Google Maps/Street View **không được** trích xuất/lưu trữ/tái sử dụng ngoài dịch vụ. Ảnh Google, Pinterest, “tìm trên mạng” không có giấy phép là rủi ro trực tiếp.
11. **App store không phải luật Việt Nam nhưng là điều kiện phát hành**: Apple yêu cầu privacy policy trong app + App Store Connect, đồng ý thu thập dữ liệu, xoá tài khoản trong app (5.1.1); Google Play yêu cầu Data safety chính xác và đường dẫn xoá tài khoản (kể cả web).
12. **Tiếp cận**: Việt Nam chưa có nghĩa vụ rõ ràng áp dụng WCAG cho app tư nhân. Thông tư 26/2020/TT-BTTTT bắt buộc WCAG 1.0 cho website cơ quan nhà nước/báo chí, khuyến khích WCAG 2.0/2.1; GoWander nên chọn WCAG 2.2 AA làm chuẩn thiết kế và ghi rõ giới hạn.

### 0.2. Ưu tiên theo giai đoạn

| Giai đoạn | Kích hoạt | Việc pháp lý tối thiểu | Mức ưu tiên |
|---|---|---|---|
| **P0 — trước pilot** | Có người dùng thật, dù chưa đăng nhập | Privacy policy, terms, thông báo location, map attribution + rà soát chủ quyền, ảnh có giấy phép, accessibility cơ bản, log/incident tối thiểu | Chặn phát hành nếu thiếu |
| **P1 — mở tài khoản** | Google/Apple Sign-In, profile, saved places | Consent theo mục đích, xoá tài khoản, quyền dữ liệu, DPIA, cross-border, data localization, app store privacy labels, business info | Chặn mở tài khoản |
| **P2 — cộng đồng/đánh giá** | Review, comment, follow, ảnh người dùng | Phân loại mạng xã hội theo Nghị định 147, giấy phép/xác nhận, moderation, xác thực tài khoản, trẻ em, trách nhiệm nền tảng | Chặn mở tính năng |
| **P3 — bán vé/đặt chỗ** | Thanh toán, giữ chỗ, hoàn tiền | Thông báo TMĐT, chính sách hoàn tiền, hợp đồng điện tử, payment partner, hoá đơn điện tử/thuế, giấy phép lữ hành (nếu tour) | Chặn thu tiền |
| **P4 — quảng cáo/affiliate** | Ad SDK, sponsored placement, affiliate link | Tuân thủ Luật Quảng cáo, nhãn “được tài trợ”, chứng minh claim, thuế, hợp đồng đối tác | Chặn bật kiếm tiền |
| **P5 — định kỳ** | Mỗi bản phát hành lớn/đổi vendor/mở thị trường | Rà soát lại toàn bộ checklist, cập nhật DPIA, privacy policy, app store data safety, cookie/SDK list, map | Duy trì |

### 0.3. Xác nhận: Nghị định 13/2023 đã được thay thế

**Có.** Từ **01-01-2026**, Luật Bảo vệ dữ liệu cá nhân 91/2025/QH15 và Nghị định 356/2025/NĐ-CP có hiệu lực; Nghị định 13/2023/NĐ-CP hết hiệu lực kể từ ngày Nghị định 356/2025/NĐ-CP có hiệu lực (Điều 42 khoản 2) [S1][S2]. Không tiếp tục viện dẫn Nghị định 13 trong tài liệu sản phẩm.

| Nội dung | Trước 01-01-2026 (Nghị định 13/2023) | Từ 01-01-2026 (Luật 91/2025 + Nghị định 356/2025) |
|---|---|---|
| Văn bản nền | Nghị định 13/2023/NĐ-CP | Luật 91/2025/QH15; Nghị định 356/2025/NĐ-CP |
| Dữ liệu cơ bản | Danh mục tại Nghị định 13 | Điều 3 Nghị định 356/2025 (11 nhóm) |
| Dữ liệu nhạy cảm | Danh mục tại Nghị định 13 | Điều 4 Nghị định 356/2025; bổ sung rõ **vị trí qua dịch vụ định vị**, dữ liệu theo dõi hành vi, tài khoản ngân hàng, CCCD... |
| Đồng ý | Nghị định 13 | Luật 91/2025 Điều 9; Nghị định 356/2025 Điều 6: 5 phương thức, cấm mặc định đồng ý, phải thông báo dữ liệu nhạy cảm |
| Quyền của chủ thể | Nghị định 13 (thời hạn chung 72 giờ) | Nghị định 356/2025 Điều 5: phản hồi 2 ngày làm việc; thực hiện 10/15/20/30 ngày tùy loại; gia hạn 1 lần |
| Xoá dữ liệu | Nghị định 13 | Luật 91/2025 Điều 14; Nghị định 356/2025 Điều 5 khoản 4: 20 ngày/30 ngày nếu có bên thứ ba |
| DPIA | Nghị định 13 | Luật 91/2025 Điều 21; Nghị định 356/2025 Điều 19: nộp trong 60 ngày |
| Chuyển xuyên biên giới | Nghị định 13 | Luật 91/2025 Điều 20; Nghị định 356/2025 Điều 18: hồ sơ trong 60 ngày |
| DPO | Nghị định 13 | Luật 91/2025 Điều 33; Nghị định 356/2025 Điều 13: điều kiện năng lực |
| Doanh nghiệp nhỏ/khởi nghiệp | Nghị định 13 | Luật 91/2025 Điều 38; Nghị định 356/2025 Điều 41: miễn 5 năm một số nghĩa vụ, **trừ** xử lý dữ liệu nhạy cảm/quy mô lớn |
| Sự cố | Nghị định 13 | Luật 91/2025 Điều 23; Nghị định 356/2025 Điều 28–29: 72 giờ; hồ sơ sự cố vị trí/sinh trắc 5 năm |

**Hệ quả cho GoWander:** vì xử lý vị trí (nhạy cảm), không thể giả định mình thuộc diện miễn trừ DPIA/DPO. Cần xác nhận với luật sư và chuẩn bị hồ sơ ngay từ giai đoạn mở tài khoản.

---

## 1. Bảng tóm tắt 20 điểm

| # | Điểm | Nghĩa vụ cốt lõi | Căn cứ chính | Ưu tiên |
|---|---|---|---|---|
| 1 | Chính sách bảo mật | Công bố công khai, dễ tiếp cận, trước khi thu dữ liệu; nêu loại dữ liệu, mục đích, chia sẻ, chuyển xuyên biên giới, lưu trữ, quyền, liên hệ | Luật 91/2025 Điều 4, 9; Nghị định 356/2025 Điều 6; Apple 5.1.1(i); Google Play | P0 |
| 2 | Điều khoản sử dụng | Hợp đồng điện tử rõ ràng; quyền/nghĩa vụ; nội dung người dùng; đặt chỗ/hoàn tiền; giới hạn trách nhiệm; luật áp dụng | Luật 20/2023; Luật 19/2023 Điều 25; Luật 122/2025; Nghị định 248/2026 | P0 |
| 3 | Chính sách cookie riêng | Liệt kê cookie/SDK, mục đích, thời hạn, bên thứ ba; đồng ý/từ chối/rút lại | Luật 91/2025 Điều 9; Nghị định 356/2025 Điều 6; GDPR/ePrivacy nếu có user EU | P1 |
| 4 | Chính sách hoàn tiền | Điều kiện, thời hạn, phí, ngoại lệ, quy trình, thời gian xử lý, liên hệ; không giảm quyền luật định | Luật 19/2023 Điều 10, 37, 38; Nghị định 55/2024 Điều 22; Luật Du lịch nếu tour | P3 |
| 5 | Chỉ thu dữ liệu cần | Data map; trường bắt buộc tối thiểu; mặc định tắt; guest mode; thời hạn lưu | Luật 91/2025; Nghị định 356/2025 Điều 3–4; Apple 5.1.1(iii) | P0–P1 |
| 6 | Checkbox đồng ý | Từng mục đích; không tích sẵn; không gộp với điều khoản; lưu bằng chứng; rút lại | Luật 91/2025 Điều 9; Nghị định 356/2025 Điều 6; Apple 5.1.1(ii) | P0–P1 |
| 7 | Khai báo mục đích trước khi thu | Thông báo trước/ tại điểm thu; nói rõ mục đích, dữ liệu, bên kiểm soát, quyền; với dữ liệu nhạy cảm phải nói rõ | Luật 91/2025 Điều 9(2); Nghị định 356/2025 Điều 6(4) | P0 |
| 8 | Quyền yêu cầu xoá | Quy trình tiếp nhận, xác minh, xoá/ẩn danh, thông báo bên thứ ba, backup, thời hạn 20/30 ngày | Luật 91/2025 Điều 14; Nghị định 356/2025 Điều 5; Apple 5.1.1(v); Google Play | P1 |
| 9 | Khai báo tracking bên thứ ba | Inventory SDK; mục đích; dữ liệu; chuyển xuyên biên giới; đồng ý; opt-out; DPA | Luật 91/2025 Điều 9, 17, 20; Nghị định 356/2025 Điều 6–7, 18; Apple/Google | P1 |
| 10 | Ảnh có bản quyền | Chỉ dùng ảnh có giấy phép; ghi công; lưu bằng chứng; model release; không dùng Google/Pinterest | Luật SHTT; Nghị định 17/2023; ODbL; CC BY-SA; Google Maps Terms | P0 |
| 11 | Xoá review/testimonial bịa | Không tạo review giả; không mua bán đánh giá; không che review thật; nhãn tài trợ; kiểm duyệt | Luật 19/2023 Điều 10; Luật Quảng cáo; Nghị định 87/2026; Apple 1.2/3.0 | P2 |
| 12 | Xoá claim “số 1”, “tốt nhất” | Không dùng từ tuyệt đối nếu không có tài liệu hợp pháp; nếu có phải ghi rõ tài liệu/số/ngày | Luật Quảng cáo Điều 8(11); Nghị định 87/2026 Điều 50(2)(a); Thông tư 12/2026 Điều 3 | P0/P4 |
| 13 | Thông tin doanh nghiệp thật | Tên pháp nhân, MST, địa chỉ, người đại diện, liên hệ, giấy phép; hiển thị dễ tìm | Luật 122/2025 Điều 15; Nghị định 248/2026 Điều 4–6; Luật 19/2023 Điều 37; Apple 1.5 | P0–P3 |
| 14 | Alt text cho ảnh | Ảnh có ý nghĩa phải có mô tả; ảnh trang trí đánh dấu; bản đồ có tóm tắt chữ | Luật 51/2010 Điều 4, 43; Thông tư 26/2020; WCAG 2.2 1.1.1 | P1 |
| 15 | Tương phản màu | Chữ thường ≥ 4.5:1; chữ lớn ≥ 3:1; thành phần phi văn bản ≥ 3:1; không chỉ dùng màu | WCAG 2.2 1.4.3/1.4.11; Thông tư 26/2020 (khu vực nhà nước/báo chí) | P1 |
| 16 | Form dùng được bằng bàn phím | Mọi thao tác có bàn phím; focus rõ; thứ tự hợp lý; không bẫy focus | WCAG 2.2 2.1.1/2.4.3/2.4.7; Thông tư 26/2020 | P1 |
| 17 | Nút bấm ghi rõ hành động | Nhãn nút là động từ + đối tượng; nhãn hiển thị khớp tên truy cập; lỗi cụ thể | WCAG 2.2 2.4.6/2.5.3/3.3.1; nguyên tắc plain language | P1 |
| 18 | Banner cookie chặn tracking trước đồng ý | Không tải analytics/ads trước đồng ý; “từ chối tất cả” ngang “chấp nhận tất cả”; từng nhóm; lưu log | Luật 91/2025 Điều 9; Nghị định 356/2025 Điều 6; GDPR/ePrivacy nếu EU | P1 |
| 19 | Kiểm tra luật nơi user sống | Xác định EU/UK/California; GDPR/CCPA; đại diện; chuyển dữ liệu; quyền; cookie | GDPR Điều 3, 6, 12–22, 27, 33, 44; ePrivacy Điều 5(3); CCPA | P2 |
| 20 | Rà lại sau update lớn | Chạy lại checklist; cập nhật DPIA, policy, app store label, SDK/cookie, ảnh, quảng cáo, bản đồ | Luật 91/2025 Điều 22; Nghị định 356/2025 Điều 20; Apple/Google | P5 |

---

## 2. Chi tiết 20 điểm

**Nhóm 1 — Tài liệu bắt buộc (điểm 1–4).**

### 1. Trang Chính sách bảo mật (privacy policy)

**(a) Nghĩa vụ/nội dung.** Phải có chính sách bảo mật bằng tiếng Việt, dễ tìm, dễ đọc, hiển thị **trước hoặc tại thời điểm** thu dữ liệu, không chỉ chôn trong Điều khoản sử dụng. Chính sách phải nêu tối thiểu: ai là bên kiểm soát dữ liệu (tên pháp nhân, địa chỉ, email/điện thoại); thu những loại dữ liệu nào (tài khoản, hồ sơ, vị trí, thiết bị, log, ảnh, đánh giá, thanh toán); thu bằng cách nào; mục đích từng loại; cơ sở pháp lý/đồng ý; chia sẻ với ai (vendor, đối tác, cơ quan nhà nước); có chuyển ra nước ngoài hay không; thời hạn lưu; biện pháp bảo mật; quyền của chủ thể và cách thực hiện; dữ liệu trẻ em; cookie/tracking; khiếu nại; ngày hiệu lực/phiên bản.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 4 (quyền được biết), Điều 9 (đồng ý và thông tin); Nghị định 356/2025/NĐ-CP Điều 6 (phương thức đồng ý, không mặc định đồng ý, thông báo dữ liệu nhạy cảm) [S1][S2]. Apple App Store Guideline 5.1.1(i) yêu cầu link privacy policy trong App Store Connect và trong app, nêu dữ liệu, cách thu, mọi mục đích sử dụng, bảo vệ dữ liệu khi chia sẻ bên thứ ba, thời hạn lưu/xoá và cách rút đồng ý/xoá dữ liệu [S26]. Google Play yêu cầu privacy policy và Data safety chính xác [S27].

**(c) Tài liệu/UI cần có.** Trang `/privacy` trên web; mục “Cài đặt › Quyền riêng tư” trong app; link ở màn hình tạo tài khoản, trước khi bật vị trí, trước khi gửi đánh giá/ảnh, trước khi thanh toán; link trong App Store Connect/Google Play; bản in/PDF; lịch sử phiên bản; bản tóm tắt 1 trang (“tóm tắt dễ hiểu”) + bản đầy đủ.

**(d) Rủi ro nếu thiếu.** Đồng ý vô hiệu; xử lý dữ liệu không có căn cứ; phạt hành chính tối đa 3 tỷ đồng với tổ chức cho nhiều hành vi, 5% doanh thu cho chuyển xuyên biên giới trái phép; khiếu nại/kiện; app bị từ chối hoặc gỡ. Apple có thể coi là thiếu privacy policy bắt buộc.

**(e) Ghi chú riêng cho GoWander.** Chính sách phải nói rõ **vị trí là dữ liệu nhạy cảm**, không chỉ “dữ liệu cá nhân”. Phải phân biệt: vị trí dùng để tìm tuyến (xử lý tức thời, không lưu) vs. lịch sử vị trí (nếu có, phải opt-in riêng); dữ liệu giao thông công cộng/điểm đến có thể là dữ liệu cá nhân khi gắn với người dùng; ảnh/đánh giá là nội dung công khai nhưng vẫn có thể chứa dữ liệu cá nhân. Không được viết “chúng tôi không thu thập dữ liệu” nếu có IP, device ID, crash log, analytics. Nếu dùng Google Sign-In, phải nêu việc nhận tên/email/ảnh đại diện từ Google. Nếu dùng Cloudflare R2, phải nêu lưu trữ ngoài Việt Nam (nếu đúng) và cơ chế bảo vệ.

---

### 2. Trang Điều khoản sử dụng

**(a) Nghĩa vụ/nội dung.** Điều khoản là hợp đồng giữa GoWander và người dùng, cần rõ: mô tả dịch vụ (bản đồ, tìm tuyến, nội dung du lịch, cộng đồng, đặt chỗ — nếu có); điều kiện độ tuổi/năng lực; tài khoản (thông tin chính xác, bảo mật, một người một tài khoản); quyền và nghĩa vụ của người dùng; nội dung người dùng (quyền sở hữu, giấy phép cho GoWander, trách nhiệm, kiểm duyệt, gỡ bỏ); đặt chỗ/thanh toán/hoàn tiền (nếu có); sở hữu trí tuệ; giới hạn trách nhiệm; tuyên bố miễn trừ về bản đồ/dẫn đường; liên kết bên thứ ba; chấm dứt; thay đổi điều khoản; luật áp dụng và giải quyết tranh chấp; liên hệ.

**(b) Căn cứ.** Luật Giao dịch điện tử 20/2023/QH15 (hợp đồng điện tử, giá trị pháp lý, giao kết qua thông điệp dữ liệu) [S17]; Luật Bảo vệ quyền lợi người tiêu dùng 19/2023/QH15 Điều 25 (điều khoản không được phép trong hợp đồng với người tiêu dùng) [S11]; Luật Thương mại điện tử 122/2025/QH15 và Nghị định 248/2026/NĐ-CP nếu là nền tảng TMĐT [S18][S19]. Bộ luật Dân sự 2015 là nền tảng chung về hợp đồng.

**(c) Tài liệu/UI cần có.** Trang `/terms`; checkbox “Tôi đã đọc và đồng ý Điều khoản sử dụng” **tách khỏi** checkbox đồng ý xử lý dữ liệu; hiển thị ngày hiệu lực; lưu phiên bản người dùng đã chấp nhận; thông báo khi thay đổi trọng yếu; bản tiếng Việt là bản chính, bản tiếng Anh chỉ tham khảo (nếu có).

**(d) Rủi ro nếu thiếu.** Khó thực thi quyền (khoá tài khoản, gỡ nội dung, thu phí); điều khoản bất lợi có thể bị coi là vô hiệu theo Luật 19/2023; tranh chấp tiêu dùng; app store có thể yêu cầu EULA/terms; không có căn cứ xử lý nội dung xấu.

**(e) Ghi chú riêng cho GoWander.** Phải có **tuyên bố miễn trừ về bản đồ/dẫn đường**: không bảo đảm thời gian thực, không thay thế biển báo/đèn tín hiệu, người dùng phải tuân thủ luật giao thông, không dùng khi đang lái xe. Với nội dung cộng đồng, cần điều khoản cấp phép nội dung cho GoWander (hiển thị, phân phối, quảng bá) và cam kết không đăng nội dung vi phạm; quyền gỡ nội dung; chính sách tài khoản bị khoá. Nếu bán tour/vé, điều khoản phải khớp chính sách hoàn tiền và điều kiện của đối tác.

---

### 3. Chính sách cookie riêng

**(a) Nghĩa vụ/nội dung.** Chính sách riêng (không gộp vào privacy policy) liệt kê: cookie thiết yếu (đăng nhập, bảo mật, phiên), cookie chức năng (ngôn ngữ, giao diện), cookie/SDK phân tích, cookie/SDK quảng cáo; tên, nhà cung cấp, mục đích, thời hạn, loại dữ liệu, có chuyển ra nước ngoài không; cách đồng ý/từ chối/rút lại; cách xoá trong trình duyệt; danh sách SDK trong app (không chỉ cookie web).

**(b) Căn cứ.** Việt Nam **chưa có luật cookie riêng** (chưa kiểm chứng có văn bản mới đến 17-11-2026). Tuy nhiên, tracking/behavioral data là dữ liệu cá nhân; Nghị định 356/2025/NĐ-CP Điều 4 khoản 1 điểm l xếp “dữ liệu theo dõi hành vi, hoạt động sử dụng dịch vụ viễn thông, mạng xã hội, dịch vụ truyền thông trực tuyến và các dịch vụ khác trên không gian mạng” vào nhóm nhạy cảm [S2]. Do đó cần đồng ý theo Luật 91/2025 Điều 9 và Nghị định 356/2025 Điều 6 [S1][S2]. Nếu có người dùng EU, ePrivacy Directive 2002/58/EC Điều 5(3) yêu cầu đồng ý trước khi lưu/truy cập thông tin trên thiết bị, trừ cookie thiết yếu [S30].

**(c) Tài liệu/UI cần có.** Trang `/cookies`; bảng cookie/SDK; banner web; màn hình “Cài đặt quyền riêng tư” trong app; nút “Quản lý cookie” ở footer; log đồng ý (thời điểm, phiên bản, nhóm, ID ẩn danh); cơ chế tải lại banner khi chính sách thay đổi.

**(d) Rủi ro nếu thiếu.** Tracking trước đồng ý; đồng ý không hợp lệ; phạt theo Luật 91/2025; GDPR/ePrivacy nếu có user EU; mất niềm tin; app store Data safety không khớp.

**(e) Ghi chú riêng cho GoWander.** Nếu chỉ dùng cookie thiết yếu (đăng nhập, bảo mật) thì có thể không cần banner, nhưng vẫn nên có chính sách. Nếu dùng Google Analytics/Firebase/AdMob/Crashlytics, phải liệt kê và chặn khởi tạo trước đồng ý. Trong app, “cookie” không tồn tại theo nghĩa trình duyệt, nhưng SDK/device ID/IDFA/GAID tương đương — phải nêu trong chính sách và Data safety/App Privacy.

---

### 4. Chính sách hoàn tiền rõ ràng (nếu bán vé/dịch vụ)

**(a) Nghĩa vụ/nội dung.** Nếu GoWander bán vé, tour, đặt chỗ hoặc dịch vụ: công bố trước khi thanh toán: phạm vi áp dụng; điều kiện huỷ/đổi/hoàn; mốc thời gian và phí; trường hợp không hoàn; trường hợp nhà cung cấp huỷ/thiên tai/bất khả kháng; no-show; cách yêu cầu; hồ sơ cần gửi; thời gian xử lý; phương thức hoàn; liên hệ; quyền luật định không bị hạn chế.

**(b) Căn cứ.** Luật 19/2023/QH15 Điều 10 khoản 1 điểm đ/e (không đền bù/trả lại tiền/đổi khi do lỗi hoặc không đúng công bố), Điều 37 (thông tin giao dịch từ xa, gồm điều kiện đổi trả), Điều 38 (hợp đồng từ xa; nếu thông tin không đầy đủ, người tiêu dùng được đơn phương chấm dứt trong 30 ngày; hoàn tiền trong 30 ngày) [S11]. Nghị định 55/2024/NĐ-CP Điều 22 yêu cầu thông tin quy trình đổi/trả phải có thời hạn cụ thể, các bước, thời hạn từng bước và liên hệ bộ phận xử lý [S12]. Nếu bán tour, Luật Du lịch 09/2017/QH14 và hợp đồng lữ hành cũng điều chỉnh [S39].

**(c) Tài liệu/UI cần có.** Trang `/refund`; link ở checkout trước nút “Thanh toán”; tóm tắt trong email xác nhận; form yêu cầu hoàn; mã yêu cầu; trạng thái xử lý; chính sách riêng cho từng loại vé/tour; bảng thời hạn/phí; FAQ.

**(d) Rủi ro nếu thiếu.** Khiếu nại tiêu dùng; chargeback; phạt hành chính; mất uy tín; app store/đối tác yêu cầu; tranh chấp hợp đồng.

**(e) Ghi chú riêng cho GoWander.** **Không có “quyền đổi trả 7 ngày” chung** trong Luật 19/2023. Chỉ khi thông tin giao dịch từ xa không đầy đủ mới có quyền chấm dứt 30 ngày và hoàn tiền 30 ngày. Chính sách tự nguyện có thể cho phép huỷ trước 24/48/72 giờ, nhưng không được trái luật và phải nhất quán. Nếu GoWander chỉ là đại lý bán vé, phải nêu rõ chính sách của nhà cung cấp/đơn vị vận tải và ai chịu trách nhiệm hoàn tiền. Nếu thanh toán qua Apple IAP/Google Play, nêu rõ quy trình hoàn tiền của nền tảng.

---

**Nhóm 2 — Dữ liệu cá nhân (điểm 5–9).** Lưu ý: Nghị định 13/2023 đã được thay thế từ 01-01-2026; xem §0.3.

### 5. Chỉ thu thập dữ liệu thật sự cần

**(a) Nghĩa vụ/nội dung.** Lập bản đồ dữ liệu (data map) theo từng tính năng; mỗi trường dữ liệu phải có lý do gắn với tính năng; không thu “để sau này dùng”; không thu số điện thoại/ngày sinh/địa chỉ/contacts/ảnh nếu không cần; mặc định tắt các tính năng thu thập; cho khách dùng không cần đăng nhập nếu có thể; đặt thời hạn lưu cho từng loại.

**(b) Căn cứ.** Luật 91/2025/QH15 (nguyên tắc xử lý đúng mục đích, cần thiết, hạn chế); Nghị định 356/2025/NĐ-CP Điều 3–4 (phân loại dữ liệu) [S1][S2]. Apple 5.1.1(iii) yêu cầu data minimization, chỉ yêu cầu dữ liệu liên quan đến chức năng cốt lõi [S26]. Google Play Data safety yêu cầu khai báo trung thực, gồm cả SDK bên thứ ba [S27].

**(c) Tài liệu/UI cần có.** Bảng data map: tính năng → dữ liệu → mục đích → bắt buộc/tuỳ chọn → lưu bao lâu → chia sẻ cho ai. Trong UI: chỉ hỏi khi cần; nút “Bỏ qua”; “Nhập địa chỉ” thay vì bắt buộc vị trí; không bắt đăng nhập để xem bản đồ; không bắt số điện thoại để đọc nội dung.

**(d) Rủi ro nếu thiếu.** Thu thập quá mức là vi phạm; tăng thiệt hại khi breach; tăng chi phí lưu trữ; app store có thể chất vấn; người dùng mất tin.

**(e) Ghi chú riêng cho GoWander.** ADR-038 (chỉ lưu bộ đếm gộp theo ngày, không lưu sự kiện từng người) là hướng đúng; cần giữ nguyên nguyên tắc này khi thêm analytics. Vị trí chỉ nên lấy khi người dùng bấm “Dùng vị trí của tôi” hoặc “Bắt đầu dẫn đường”; không lưu toạ độ thô nếu không có opt-in. Không thu `contacts`, `photos` toàn bộ; dùng photo picker. Không thu ngày sinh nếu không cần xác minh tuổi; nếu cần, chỉ lưu tháng/năm hoặc cờ “dưới 16”.

---

### 6. Checkbox đồng ý ở mọi form

**(a) Nghĩa vụ/nội dung.** Không phải “mọi form” đều cần checkbox, nhưng **mọi hoạt động thu thập dữ liệu dựa trên đồng ý** phải có checkbox riêng, không tích sẵn, nội dung rõ, tách theo mục đích (vị trí, analytics, marketing, chia sẻ với đối tác), không gộp với chấp nhận Điều khoản. Với dữ liệu nhạy cảm (vị trí), phải nói rõ “đây là dữ liệu cá nhân nhạy cảm”. Phải lưu bằng chứng: phiên bản văn bản, thời điểm, mục đích, trạng thái, ID người dùng; cho rút lại dễ như khi đồng ý.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 9 (đồng ý tự nguyện, biết rõ, từng mục đích, không im lặng = đồng ý); Nghị định 356/2025/NĐ-CP Điều 6 (5 phương thức, không mặc định đồng ý, thông báo nhạy cảm) [S1][S2]. Apple 5.1.1(ii) yêu cầu consent, có cách rút lại dễ hiểu [S26].

**(c) Tài liệu/UI cần có.** Component checkbox chuẩn; màn hình consent trước khi tạo tài khoản/bật vị trí;trang “Quyền riêng tư” để bật/tắt; log consent; quy trình re-consent khi thay đổi mục đích; bản dịch tiếng Việt/Anh.

**(d) Rủi ro nếu thiếu.** Đồng ý không hợp lệ; xử lý không có căn cứ; phạt; khiếu nại; app store từ chối nếu privacy manifest không khớp.

**(e) Ghi chú riêng cho GoWander.** Không dùng một checkbox “Tôi đồng ý Điều khoản và Chính sách bảo mật” cho tất cả. Tách: (i) chấp nhận Điều khoản; (ii) đồng ý xử lý dữ liệu cơ bản để tạo tài khoản; (iii) đồng ý vị trí để tìm tuyến; (iv) đồng ý analytics; (v) đồng ý marketing. Với khách không đăng nhập, không cần checkbox tài khoản; nhưng nếu bật vị trí/analytics vẫn cần consent tương ứng. Không dùng “bằng việc tiếp tục sử dụng, bạn đồng ý” — im lặng không phải đồng ý.

---

### 7. Khai báo mục đích trước khi thu

**(a) Nghĩa vụ/nội dung.** Trước khi thu, phải nói rõ: dữ liệu gì, mục đích gì, ai kiểm soát, có bắt buộc không, hậu quả nếu không cung cấp, quyền của người dùng, có chia sẻ/chuyển ra nước ngoài không. Với vị trí, phải nói rõ “dùng để tìm tuyến từ vị trí hiện tại”, không dùng cho quảng cáo nếu chưa đồng ý riêng. Với dữ liệu nhạy cảm, phải nói rõ đó là dữ liệu nhạy cảm.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 9 khoản 2 (đồng ý chỉ có hiệu lực khi biết rõ loại dữ liệu, mục đích, bên kiểm soát, quyền/nghĩa vụ); Nghị định 356/2025/NĐ-CP Điều 6 khoản 4 (thông báo dữ liệu nhạy cảm) [S1][S2]. Apple purpose strings và 5.1.1(ii) yêu cầu mô tả rõ mục đích [S26].

**(c) Tài liệu/UI cần có.** Màn hình “trước khi bật vị trí” (pre-permission) giải thích lợi ích và lựa chọn; tooltip/info icon cạnh trường dữ liệu; privacy notice dạng lớp; câu chữ ngắn, không dùng thuật ngữ mơ hồ; bản đầy đủ link.

**(d) Rủi ro nếu thiếu.** Đồng ý không hợp lệ; người dùng bất ngờ; phạt; khiếu nại; app store rejection nếu purpose string không rõ.

**(e) Ghi chú riêng cho GoWander.** Không dùng “để cải thiện dịch vụ” cho mọi thứ. Phải tách: “để tìm tuyến”, “để gợi ý địa điểm gần bạn”, “để đo lường tính năng”, “để gửi khuyến mãi”. Với bản đồ, nói rõ vị trí được gửi tới server GoWander/OTP trong lúc tìm tuyến và không lưu nếu không cần. Với ảnh, nói rõ ảnh sẽ công khai hay chỉ lưu riêng.

---

### 8. Người dùng có quyền yêu cầu xoá dữ liệu

**(a) Nghĩa vụ/nội dung.** Có quy trình tiếp nhận yêu cầu xoá; xác minh danh tính hợp lý; xoá hoặc ẩn danh dữ liệu; thông báo cho bên xử lý/bên thứ ba; xử lý backup; trả lời trong thời hạn; nêu ngoại lệ (nghĩa vụ pháp lý, tranh chấp, kế toán/thuế); không khôi phục dữ liệu đã xoá. Trong app phải có đường dẫn xoá tài khoản; trên web phải có trang yêu cầu xoá.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 14 (xoá/hủy/khử nhận dạng); Nghị định 356/2025/NĐ-CP Điều 5 khoản 4 (phản hồi 2 ngày làm việc; thực hiện 20 ngày, 30 ngày nếu có bên xử lý/bên thứ ba; gia hạn 1 lần tối đa 20 ngày) [S1][S2]. Apple 5.1.1(v) yêu cầu app có tài khoản phải cho xoá tài khoản trong app [S26]. Google Play yêu cầu đường dẫn xoá tài khoản trong app và web [S28].

**(c) Tài liệu/UI cần có.** “Cài đặt › Tài khoản › Xoá tài khoản”; màn hình giải thích hậu quả; xác nhận; lựa chọn xoá một phần (lịch sử vị trí, ảnh, đánh giá) vs. toàn bộ; form web; email xác nhận; trạng thái; thời hạn; FAQ.

**(d) Rủi ro nếu thiếu.** Khiếu nại; phạt; app bị từ chối/gỡ; dữ liệu tồn đọng trong backup; mất niềm tin.

**(e) Ghi chú riêng cho GoWander.** Phải xoá: tài khoản, profile, saved places, lịch sử tìm tuyến (nếu lưu), ảnh đại diện, review (hoặc ẩn danh nếu cần giữ tính toàn vẹn), token Google/Apple. Phải giữ: hoá đơn, chứng từ thuế, log sự cố, dữ liệu cần cho tranh chấp — nhưng nêu rõ và thời hạn. Phải yêu cầu vendor (Cloudflare, analytics, payment) xoá theo hợp đồng. Backup: đặt lịch xoá/rotate; không phục hồi dữ liệu đã xoá.

---

### 9. Khai báo tracking bên thứ ba

**(a) Nghĩa vụ/nội dung.** Lập inventory mọi SDK/dịch vụ bên thứ ba có thể nhận dữ liệu: tên, mục đích, loại dữ liệu, có định danh thiết bị không, có vị trí không, có chuyển ra nước ngoài không, thời hạn, cơ chế opt-out. Công bố trong privacy policy, cookie policy, App Privacy/Data safety. Ký hợp đồng xử lý dữ liệu (DPA) với vendor; yêu cầu bảo mật tương đương; không để vendor dùng dữ liệu cho mục đích riêng nếu chưa đồng ý.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 9 (đồng ý), Điều 17 (chuyển giao), Điều 20 (chuyển xuyên biên giới); Nghị định 356/2025/NĐ-CP Điều 6 (đồng ý), Điều 7 (thoả thuận chuyển giao), Điều 18 (hồ sơ chuyển xuyên biên giới) [S1][S2]. Apple 5.1.1(i) yêu cầu nêu bên thứ ba và bảo vệ dữ liệu; Google Play Data safety yêu cầu khai báo cả SDK [S26][S27].

**(c) Tài liệu/UI cần có.** Bảng SDK; privacy policy mục “Bên thứ ba”; app settings “Quản lý tracking”; banner cookie; Data safety/App Privacy; DPA; danh sách vendor trong hồ sơ DPIA.

**(d) Rủi ro nếu thiếu.** Tracking không được khai báo; app store từ chối; phạt; chuyển dữ liệu trái phép; mất niềm tin.

**(e) Ghi chú riêng cho GoWander.** Các vendor có thể có: Google Sign-In, Google Analytics/Firebase, Crashlytics, AdMob, Cloudflare R2, OpenFreeMap/OSM tile, Mapillary, weather API, payment gateway, email/SMS, support chat. Mỗi vendor có thể nhận IP, device ID, vị trí, nội dung tìm kiếm. Nếu chưa dùng, ghi “chưa chọn” và cập nhật khi chọn. Không được nói “không chia sẻ dữ liệu” nếu SDK gửi dữ liệu về Google/Meta.

---

**Nhóm 3 — Nội dung và sở hữu (điểm 10–13).**

### 10. Ảnh dùng có bản quyền không (Wikimedia/Mapillary/OSM/Google)

**(a) Nghĩa vụ/nội dung.** Chỉ dùng ảnh có giấy phép cho phép dùng thương mại và hiển thị trong app; lưu bằng chứng nguồn/giấy phép/tác giả; ghi công theo yêu cầu; tôn trọng share-alike; có sự đồng ý của người trong ảnh nếu cần; không dùng ảnh Google Maps/Street View, Pinterest, “Google Images”, ảnh không rõ nguồn.

**(b) Căn cứ.** Luật Sở hữu trí tuệ (quyền tác giả, quyền liên quan); Nghị định 17/2023/NĐ-CP (trích dẫn hợp lý; điều kiện ghi nguồn/tác giả) [S25]. OSM: ODbL, attribution “© OpenStreetMap contributors”, share-alike cho derivative database [S33][S34]. Wikimedia Commons: từng file có giấy phép riêng, thường CC BY/CC BY-SA; phải ghi tác giả, giấy phép, link, chỉ rõ thay đổi [S35]. Mapillary: ảnh CC BY-SA 4.0, cần ghi công và tuân thủ điều khoản [S36]. Google Maps Platform: cấm scrape/cache/tái sử dụng nội dung ngoài dịch vụ; không dùng screenshot/Street View [S37].

**(c) Tài liệu/UI cần có.** Trang “Nguồn ảnh” trong app; bảng credit: file, tác giả, giấy phép, URL, thay đổi; lưu file giấy phép; model release; quy trình takedown; kiểm tra ảnh trước khi đăng; metadata trong CMS.

**(d) Rủi ro nếu thiếu.** Khiếu nại bản quyền; yêu cầu gỡ; bồi thường; app store gỡ; mất uy tín; vi phạm quyền hình ảnh cá nhân.

**(e) Ghi chú riêng cho GoWander.** `docs/photo-credits.md` đã có credit cho ảnh region; cần mở rộng cho mọi ảnh địa điểm/ẩm thực/bài viết. Ảnh Wikimedia CC BY-SA: nếu cắt/ghép/overlay, có thể là tác phẩm phái sinh — phải phát hành theo cùng giấy phép và ghi “đã chỉnh sửa”. Ảnh Mapillary: kiểm tra từng ảnh, không chỉ tin “CC BY-SA”. Ảnh Google: **không dùng** trừ khi có hợp đồng riêng. Ảnh người dùng: điều khoản cấp phép + cam kết có sự đồng ý của người trong ảnh; đặc biệt trẻ em.

---

### 11. Xoá review/testimonial bịa

**(a) Nghĩa vụ/nội dung.** Không tạo, mua, đổi quà, trả tiền để có review; không viết testimonial hư cấu; không sửa/xoá review tiêu cực thật; không che giấu review bằng cách sắp xếp/hiển thị không trung thực; phải công khai nếu review/đánh giá được tài trợ; có quy trình báo cáo/kiểm duyệt; xoá review vi phạm pháp luật/đạo đức; lưu log.

**(b) Căn cứ.** Luật 19/2023/QH15 Điều 10 khoản 1 điểm a (lừa dối/gây nhầm lẫn) và khoản 3 điểm c (không được ngăn/hiển thị không trung thực kết quả phản hồi, đánh giá trên nền tảng số) [S11]. Luật Quảng cáo 16/2012 (sửa đổi 75/2025) cấm quảng cáo sai sự thật/gây nhầm lẫn; Nghị định 87/2026/NĐ-CP Điều 50 xử phạt [S13][S15]. Apple 1.2 yêu cầu lọc nội dung, báo cáo, chặn người lạm dụng; Apple 3.0 cấm thao túng review [S26]. Google Play có chính sách UGC tương tự [S47].

**(c) Tài liệu/UI cần có.** Chính sách đánh giá; nút “Báo cáo”; nút “Chặn”; form xác minh trải nghiệm; nhãn “Đã được tài trợ”; log kiểm duyệt; quy trình kháng nghị; công cụ phát hiện review trùng/lạm dụng.

**(d) Rủi ro nếu thiếu.** Bị coi là quảng cáo sai sự thật; phạt tiền; mất niềm tin; app store gỡ; tranh chấp với người dùng/nhà cung cấp.

**(e) Ghi chú riêng cho GoWander.** Không “seed” review bằng tài khoản nội bộ; không tặng voucher để đổi review mà không công khai; không xoá review xấu chỉ vì xấu. Nếu có “review nổi bật”, phải công khai tiêu chí. Nếu review gắn với booking, chỉ cho người đã thực sự dùng dịch vụ đánh giá (verified review) — nhưng vẫn phải minh bạch.

---

### 12. Xoá claim “số 1”, “tốt nhất”

**(a) Nghĩa vụ/nội dung.** Không dùng “nhất”, “duy nhất”, “tốt nhất”, “số một” hoặc từ tương tự trong quảng cáo/ASO/marketing nếu không có tài liệu hợp pháp chứng minh. Nếu có, phải nêu rõ tên tài liệu, số/ký hiệu, thời gian công bố/cấp; tài liệu phải còn hiệu lực, độc lập, khách quan, không gây nhầm lẫn. Tránh cả so sánh trực tiếp với đối thủ nếu không có tài liệu.

**(b) Căn cứ.** Luật Quảng cáo 16/2012/QH13 (sửa đổi bởi 75/2025/QH15) Điều 8 khoản 11; Nghị định 87/2026/NĐ-CP Điều 50 khoản 2 điểm a (phạt 10–20 triệu đồng với cá nhân, 20–40 triệu đồng với tổ chức) [S13][S15]; Thông tư 12/2026/TT-BVHTTDL Điều 3 (từ ngữ tương tự, tài liệu hợp pháp) [S16].

**(c) Tài liệu/UI cần có.** Claim register: câu claim, nơi dùng, tài liệu chứng minh, ngày hết hạn, người duyệt; checklist marketing; lưu tài liệu; gỡ claim khi hết hiệu lực.

**(d) Rủi ro nếu thiếu.** Phạt 20–40 triệu đồng với tổ chức; buộc tháo gỡ/cải chính; mất niềm tin; app store metadata bị từ chối.

**(e) Ghi chú riêng cho GoWander.** “Số 1” trong App Store subtitle/screenshot cũng là quảng cáo. Không dùng “ ứng dụng bản đồ tốt nhất TP.HCM” nếu không có khảo sát/giải thưởng. Nếu muốn nói “được nhiều người dùng”, phải có số liệu xác thực và nêu thời điểm; tránh ngôn ngữ tuyệt đối. Tốt nhất là bỏ hẳn claim so sánh.

---

### 13. Thông tin doanh nghiệp thật (tên, MST, địa chỉ, người chịu trách nhiệm)

**(a) Nghĩa vụ/nội dung.** Công khai tên pháp nhân/hộ kinh doanh, mã số thuế, địa chỉ trụ sở, người đại diện theo pháp luật hoặc người chịu trách nhiệm, số điện thoại/email, giấy phép kinh doanh/giấy phép lữ hành/đăng ký TMĐT (nếu có), đầu mối khiếu nại. Thông tin phải rõ, chính xác, dễ tìm, cập nhật.

**(b) Căn cứ.** Luật Thương mại điện tử 122/2025/QH15 Điều 15 và Nghị định 248/2026/NĐ-CP Điều 4–6 (công bố thông tin chủ quản, chính sách bảo mật, quyền/nghĩa vụ các bên) [S18][S19]; Luật 19/2023/QH15 Điều 37 (thông tin trong giao dịch từ xa) [S11]; Apple 1.5 (developer contact) [S26]. Nếu là mạng xã hội, Nghị định 147/2024/NĐ-CP có yêu cầu thông tin liên hệ/đại diện [S10]. Nếu kinh doanh dịch vụ quảng cáo trên mạng, Nghị định 342/2025/NĐ-CP có yêu cầu thông báo thông tin liên hệ [S14].

**(c) Tài liệu/UI cần có.** Trang “Giới thiệu”/“Liên hệ” trong app và web; footer website; màn hình checkout; App Store developer info; email hỗ trợ; địa chỉ nhận khiếu nại; số giấy phép.

**(d) Rủi ro nếu thiếu.** Vi phạm TMĐT; người tiêu dùng không biết kiện ai; app store từ chối; khó thực thi điều khoản; mất niềm tin.

**(e) Ghi chú riêng cho GoWander.** Nếu chủ dự án là cá nhân/hộ kinh doanh, vẫn phải có mã số thuế/địa chỉ; không được chỉ ghi “GoWander Team” hoặc email Gmail. Nếu có giấy phép lữ hành/TMĐT, ghi số và nơi cấp. Nếu chưa có, ghi rõ “chưa đăng ký” và không được bán dịch vụ cần giấy phép.

---

**Nhóm 4 — Tiếp cận và trải nghiệm (điểm 14–17).**

### 14. Alt text cho mọi ảnh

**(a) Nghĩa vụ/nội dung.** Ảnh có ý nghĩa phải có mô tả thay thế ngắn, mô tả nội dung/Chức năng, không lặp lại caption vô nghĩa; ảnh trang trí phải đánh dấu `decorative`/alt rỗng; ảnh phức tạp (bản đồ, biểu đồ) cần mô tả dài hoặc bảng/tóm tắt chữ; ảnh trong bài viết cần alt do biên tập viên nhập, không để trống.

**(b) Căn cứ.** Luật Người khuyết tật 51/2010/QH12 Điều 4 (quyền tiếp cận công nghệ thông tin), Điều 43 (khuyến khích ứng dụng CNTT cho người khuyết tật) [S23]. Thông tư 26/2020/TT-BTTTT: WCAG 1.0 bắt buộc với website cơ quan nhà nước/báo chí; WCAG 2.0/2.1 khuyến nghị; tổ chức khác được khuyến khích [S24]. WCAG 2.2 tiêu chí 1.1.1 (Non-text Content) [S32].

**(c) Tài liệu/UI cần có.** Trường alt text trong CMS; validation trước khi publish; hướng dẫn biên tập; kiểm tra bằng screen reader; bản đồ có “Tóm tắt tuyến” dạng chữ.

**(d) Rủi ro nếu thiếu.** Người dùng screen reader không tiếp cận được; loại trừ người khuyết tật; rủi ro uy tín; nếu hợp tác với cơ quan nhà nước có thể bị yêu cầu tuân thủ.

**(e) Ghi chú riêng cho GoWander.** Ảnh địa điểm: alt nên nói “Chợ Bến Thành nhìn từ cửa Nam, nhiều xe máy buổi sáng” thay vì “ảnh chợ”. Ảnh bản đồ: không thể mô tả mọi chi tiết; cung cấp danh sách tuyến/điểm đến bằng chữ. Ảnh người dùng: cho phép nhập alt, nhưng không bắt buộc nếu ảnh chỉ minh hoạ; vẫn cần kiểm duyệt nội dung.

---

### 15. Tương phản màu chữ/nền

**(a) Nghĩa vụ/nội dung.** Chữ thường phải có độ tương phản tối thiểu 4.5:1; chữ lớn (≥18pt hoặc 14pt bold) tối thiểu 3:1; thành phần giao diện/phi văn bản quan trọng (icon, viền, trạng thái) tối thiểu 3:1; không dùng màu làm phương tiện duy nhất để truyền đạt thông tin (ví dụ tuyến đỏ/xanh).

**(b) Căn cứ.** WCAG 2.2 tiêu chí 1.4.3 (Contrast Minimum) và 1.4.11 (Non-text Contrast) [S32]; Thông tư 26/2020/TT-BTTTT áp dụng cho khu vực nhà nước/báo chí, khuyến khích tổ chức khác [S24].

**(c) Tài liệu/UI cần có.** Design token màu; công cụ kiểm tra contrast; chế độ tối/sáng; kiểm tra trên màn hình ngoài trời; trạng thái tuyến có nhãn chữ/icon, không chỉ màu; bản đồ có chế độ tương phản cao.

**(d) Rủi ro nếu thiếu.** Khó đọc ngoài trời/cho người thị lực kém; khiếu nại; rủi ro uy tín; nếu là đối tác công có thể không đạt yêu cầu.

**(e) Ghi chú riêng cho GoWander.** Màu thương hiệu có thể đẹp nhưng chưa đủ tương phản; kiểm tra nút “Bắt đầu dẫn đường”, nhãn tuyến, chip trạng thái, thông báo lỗi. Bản đồ nền OpenFreeMap có thể có label xám nhạt; cần overlay/điều chỉnh style hoặc cung cấp danh sách chữ.

---

### 16. Form dùng được bằng bàn phím

**(a) Nghĩa vụ/nội dung.** Mọi chức năng phải dùng được bằng bàn phím (web) hoặc thiết bị hỗ trợ (mobile); focus hiển thị rõ; thứ tự focus hợp lý; không bẫy focus trong modal; có skip link; phím Enter/Space kích hoạt; ESC đóng modal; form không yêu cầu thao tác chỉ bằng chuột/cảm ứng.

**(b) Căn cứ.** WCAG 2.2 tiêu chí 2.1.1 (Keyboard), 2.4.3 (Focus Order), 2.4.7 (Focus Visible) [S32]; Thông tư 26/2020/TT-BTTTT cho khu vực nhà nước/báo chí [S24].

**(c) Tài liệu/UI cần có.** Kiểm thử bằng Tab/Shift+Tab; kiểm thử mobile với external keyboard/switch access; focus ring; modal focus trap; skip link; tài liệu component.

**(d) Rủi ro nếu thiếu.** Người không dùng chuột không thể đặt tuyến/thanh toán; loại trừ; rủi ro uy tín; nếu là web admin cho đối tác công có thể bị yêu cầu.

**(e) Ghi chú riêng cho GoWander.** Bản đồ cần hỗ trợ phóng to/thu nhỏ/di chuyển bằng bàn phím; danh sách tuyến là phương án thay thế; form tìm kiếm có label; autocomplete điều hướng được bằng phím mũi tên; nút đóng modal nhận focus.

---

### 17. Nút bấm ghi rõ hành động

**(a) Nghĩa vụ/nội dung.** Nhãn nút phải nói rõ hành động và đối tượng: “Mở tuyến”, “Lưu địa điểm”, “Bắt đầu dẫn đường”, “Xoá tài khoản”, “Gửi đánh giá”; không dùng “OK”, “Tiếp tục”, “Đồng ý” mà không có ngữ cảnh. Nhãn hiển thị phải khớp tên truy cập (accessible name). Thông báo lỗi phải nói cái gì sai và sửa thế nào.

**(b) Căn cứ.** WCAG 2.2 tiêu chí 2.4.6 (Headings and Labels), 2.5.3 (Label in Name), 3.3.1 (Error Identification), 3.3.2 (Labels or Instructions) [S32]; nguyên tắc plain language.

**(c) Tài liệu/UI cần có.** Copy deck; component button; kiểm tra với screen reader; localization; hướng dẫn viết microcopy.

**(d) Rủi ro nếu thiếu.** Người dùng bấm nhầm, đặc biệt khi xoá/thanh toán; tăng lỗi hỗ trợ; giảm tin cậy.

**(e) Ghi chú riêng cho GoWander.** Các nút nguy hiểm phải có nhãn rõ + xác nhận: “Xoá vĩnh viễn tài khoản” thay vì “Đồng ý”. Nút “Bắt đầu dẫn đường” phải phân biệt với “Xem tuyến”. Trong bản đồ, icon không nhãn phải có tooltip/accessible label.

---

**Nhóm 5 — Lớp cuối (điểm 18–20).**

### 18. Banner cookie chặn tracking trước khi đồng ý

**(a) Nghĩa vụ/nội dung.** Không tải script/SDK analytics/quảng cáo trước khi người dùng đồng ý; banner phải có “Chấp nhận tất cả”, “Từ chối tất cả” ngang nhau, “Tuỳ chỉnh” theo nhóm; không tích sẵn; không dùng dark pattern; không chặn dịch vụ thiết yếu vì từ chối cookie không thiết yếu; lưu log; cho rút lại; tải lại banner khi chính sách thay đổi.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 9; Nghị định 356/2025/NĐ-CP Điều 6 (đồng ý rõ ràng, không mặc định) [S1][S2]; nếu có user EU, ePrivacy Directive Điều 5(3) và GDPR [S29][S30].

**(c) Tài liệu/UI cần có.** CMP hoặc banner tự xây; tag manager trigger theo consent; SDK init sau consent; trang “Quản lý cookie”; log; kiểm thử bằng cách xoá cookie/kiểm tra network.

**(d) Rủi ro nếu thiếu.** Tracking trước đồng ý; đồng ý không hợp lệ; phạt; GDPR; app store Data safety không khớp.

**(e) Ghi chú riêng cho GoWander.** Việt Nam chưa có luật cookie riêng, nhưng tracking là dữ liệu cá nhân. Nếu chỉ dùng analytics gộp, không định danh, có thể giảm rủi ro; nhưng IP/device ID vẫn có thể là cá nhân. App: màn hình “Cài đặt quyền riêng tư” thay cho banner; phải chặn SDK cho đến khi bật.

---

### 19. Kiểm tra luật nơi user sống (GDPR/CCPA nếu có user EU/US)

**(a) Nghĩa vụ/nội dung.** Xác định GoWander có “hướng tới” người dùng EU/UK/California không (ngôn ngữ, tiền tệ, marketing, app store availability). Nếu có: GDPR/UK GDPR (cơ sở pháp lý, privacy notice, quyền, đại diện tại EU/UK nếu cần, DPIA, breach 72h, chuyển dữ liệu, cookie consent); CCPA/CPRA (quyền biết/xoá/sửa, opt-out bán/chia sẻ, giới hạn dữ liệu nhạy cảm, hợp đồng service provider). Nếu không: ghi lại lý do và rà soát lại khi mở thị trường.

**(b) Căn cứ.** GDPR Điều 3 (phạm vi), 6 (cơ sở), 12–22 (quyền), 27 (đại diện), 33 (breach), 44 (chuyển dữ liệu) [S29]; ePrivacy Điều 5(3) [S30]; CCPA/CPRA [S31].

**(c) Tài liệu/UI cần có.** Đánh giá thị trường; privacy notice bổ sung; form quyền; đại diện; SCCs/IDTA; cookie banner; “Do Not Sell or Share My Personal Information”; log.

**(d) Rủi ro nếu thiếu.** Phạt GDPR tới 20 triệu EUR hoặc 4% doanh thu toàn cầu; CCPA phạt theo vi phạm; app bị điều tra; mất thị trường.

**(e) Ghi chú riêng cho GoWander.** App có thể được tải ở EU/US dù chỉ phục vụ TP.HCM; nếu app store mở toàn cầu, cần đánh giá. Người EU du lịch TP.HCM vẫn có thể là chủ thể dữ liệu. Nếu chưa muốn tuân thủ, có thể giới hạn app store theo quốc gia — nhưng phải kiểm tra khả thi và không phân biệt đối xử trái luật.

---

### 20. Rà lại sau mỗi lần update lớn

**(a) Nghĩa vụ/nội dung.** Sau mỗi bản phát hành lớn, thay đổi vendor/SDK, thêm tính năng (review, booking, ads), mở thị trường, hoặc thay đổi luật: chạy lại checklist 20 điểm; cập nhật DPIA, privacy policy, terms, cookie policy, refund policy, app store Data safety/App Privacy, ảnh/credit, quảng cáo/claim, bản đồ, accessibility; lưu bằng chứng; người chịu trách nhiệm; ngày rà soát.

**(b) Căn cứ.** Luật 91/2025/QH15 Điều 22 (cập nhật hồ sơ đánh giá tác động); Nghị định 356/2025/NĐ-CP Điều 20 (cập nhật định kỳ 6 tháng/10 ngày) [S1][S2]; Apple/Google yêu cầu Data safety/App Privacy luôn chính xác [S26][S27].

**(c) Tài liệu/UI cần có.** Release checklist; changelog pháp lý; owner; lịch rà soát; evidence pack; bảng “đã thay đổi gì — đã cập nhật gì”.

**(d) Rủi ro nếu thiếu.** Chính sách lỗi thời; Data safety sai; tracking mới không khai báo; bản đồ/ảnh mới không kiểm tra; phạt; app store gỡ.

**(e) Ghi chú riêng cho GoWander.** Đặt “legal review” là gate trong pipeline, giống gate kỹ thuật. Khi thêm review/comment, coi là thay đổi lớn (Nghị định 147). Khi thêm booking, coi là thay đổi lớn (TMĐT/hoàn tiền/thuế). Khi thêm ads, coi là thay đổi lớn (Luật Quảng cáo).

---

## 3. Các mảng bổ sung rất quan trọng

### A. Bản đồ và chủ quyền quốc gia

#### A.1. Khung pháp lý

| Văn bản | Điểm chính | Ghi chú |
|---|---|---|
| Luật Đo đạc và bản đồ 27/2018/QH14, hợp nhất 70/VBHN-VPQH 2026 | Điều 6 khoản 4: cấm xuất bản, lưu hành sản phẩm đo đạc/bản đồ, xuất bản phẩm bản đồ liên quan chủ quyền lãnh thổ mà không thể hiện hoặc thể hiện không đúng chủ quyền, biên giới quốc gia [S5] | Đây là điều khoản gốc quan trọng nhất |
| Nghị định 27/2019/NĐ-CP (hợp nhất 22/VBHN-BNNMT 2026) | Quy định chi tiết Luật Đo đạc và bản đồ; nội dung bản đồ, dữ liệu biên giới/địa giới [S6] | Cần đọc toàn văn phần nội dung bản đồ; bản hợp nhất 2026 |
| Nghị định 39/2026/NĐ-CP | Sửa đổi, bổ sung Nghị định 27/2019/NĐ-CP; chủ yếu về hồ sơ cấp phép/chứng chỉ, hiệu lực 25-01-2026 [S7] | **Chưa kiểm chứng toàn bộ** liệu có thay đổi nội dung bản đồ; cần đọc toàn văn |
| Nghị định 18/2020/NĐ-CP (hợp nhất 72/VBHN-BNNMT 2026) | Điều 11: lưu hành sản phẩm bản đồ liên quan chủ quyền mà không thể hiện/không đúng: phạt 30–40 triệu đồng với cá nhân; tổ chức gấp đôi [S9] | Mức phạt theo cá nhân/tổ chức |
| Nghị định 174/2026/NĐ-CP | Điều 93 khoản 3 điểm a: đăng, phát, sử dụng hình ảnh bản đồ Việt Nam không thể hiện đầy đủ hoặc sai chủ quyền: khung **30–40 triệu đồng áp dụng với tổ chức**; cá nhân bằng 1/2 (15–20 triệu) theo Điều 4 khoản 3 [S8] | Hiệu lực 01-7-2026; áp dụng cho trang thông tin điện tử/mạng; đã đối chiếu PDF Công báo |
| Nghị định 147/2024/NĐ-CP | Quản lý thông tin trên mạng; có thể áp dụng cho nội dung bản đồ đăng trên app/website [S10] | Xem §B |

#### A.2. Điều gì bị cấm/khuyến khích?

- Cấm **xuất bản, lưu hành** sản phẩm bản đồ liên quan chủ quyền mà không thể hiện hoặc thể hiện không đúng chủ quyền, biên giới.
- Cấm **đăng, phát, sử dụng hình ảnh bản đồ Việt Nam** không thể hiện đầy đủ hoặc thể hiện sai chủ quyền quốc gia (Nghị định 174/2026).
- Thực tiễn xử phạt coi việc **thiếu Hoàng Sa/Trường Sa** là “không thể hiện đầy đủ chủ quyền quốc gia” (ví dụ vụ in áo thiếu hai quần đảo bị phạt 22,5 triệu đồng và tiêu hủy 6.000 áo) [S45]. Điều luật không nêu đích danh tên hai quần đảo, nhưng cách hiểu và thực thi phổ biến là vậy — **cần luật sư/cơ quan chuyên môn xác nhận** cho trường hợp bản đồ số.
- Không được tự ý vẽ/chèn đường biên, toạ độ, tên đảo nếu không có nguồn chính thức; làm sai còn nguy hiểm hơn thiếu.

#### A.3. Vì sao tile OSM/OpenFreeMap có rủi ro

- OSM là dữ liệu mở do cộng đồng, có thể không thể hiện đầy đủ hoặc thể hiện khác cách Việt Nam quan niệm về chủ quyền; OSM có chính sách riêng về boundary/disputed areas.
- OpenFreeMap chỉ là dịch vụ style/tile, không phải nguồn dữ liệu chính thức của Việt Nam; không có SLA, có thể đổi điều khoản/dữ liệu [S38].
- Attribution “© OpenMapTiles © OpenStreetMap contributors” là bắt buộc theo ODbL/điều khoản, nhưng **attribution không thay thế** việc đáp ứng yêu cầu chủ quyền.
- Nếu app hiển thị bản đồ Việt Nam ở zoom đủ lớn, người dùng/kiểm tra có thể thấy thiếu Hoàng Sa/Trường Sa hoặc đường biên sai.

#### A.4. Cách xử lý đề xuất (không phải kết luận pháp lý)

1. **Đánh giá phạm vi**: app có hiển thị “bản đồ Việt Nam” hay chỉ bản đồ TP.HCM? Ở zoom nào? Có ảnh bản đồ tĩnh trong bài viết/ASO/screenshot không? Có chức năng chia sẻ ảnh bản đồ không?
2. **Chọn nguồn**: ưu tiên dữ liệu nền địa lý quốc gia/bản đồ địa hình quốc gia hoặc nhà cung cấp trong nước được cấp phép hoạt động đo đạc và bản đồ; nếu vẫn dùng OSM/OpenFreeMap, phải có ý kiến pháp lý và quy trình kiểm tra.
3. **Không tự chèn** nhãn/đường biên không có nguồn; nếu cần thể hiện, dùng nguồn chính thức và kiểm duyệt cartographic.
4. **Kiểm tra hiển thị** ở nhiều zoom, trên Android/iOS/web, ảnh chụp màn hình, ảnh chia sẻ, PDF/email.
5. **Ghi nguồn/phiên bản** dữ liệu bản đồ; lưu bằng chứng kiểm tra; lặp lại khi cập nhật tile.
6. **Có thể dùng “inset”** bản đồ Việt Nam chính thức ở màn hình giới thiệu/trang chủ, nhưng không được coi là “chữa cháy” nếu bản đồ chính vẫn sai.
7. **Hỏi luật sư/cơ quan** trước khi phát hành công khai; đặc biệt nếu app có người dùng nước ngoài, app store toàn cầu, hoặc nội dung bản đồ do người dùng tải lên.

#### A.5. Ghi chú dự án

- ADR-025 đang dùng OpenFreeMap “liberty”, attribution “© OpenMapTiles © OpenStreetMap contributors”; cần thêm bước rà soát chủ quyền trước pilot.
- `vn_mask.json` dùng Natural Earth (public domain) và đặt nhãn riêng cho Hoàng Sa/Trường Sa; Natural Earth **không phải** dữ liệu chính thức của Việt Nam. Cần luật sư xem cách thể hiện này có đáp ứng yêu cầu hay không.
- Ảnh bản đồ trong bài viết/ASO cũng là “hình ảnh bản đồ Việt Nam”; phải kiểm tra như bản đồ chính.

**Trạng thái:** **[Cần luật sư]** về việc bản đồ tương tác có phải là “sản phẩm đo đạc và bản đồ” và việc chỉ thêm nhãn có đủ không. **Chưa kiểm chứng** toàn bộ Nghị định 39/2026 và các quy chuẩn kỹ thuật bản đồ.

---

### B. Nghị định 147/2024/NĐ-CP: trang thông tin điện tử, mạng xã hội, app

#### B.1. Phân loại

Nghị định 147/2024/NĐ-CP định nghĩa:

- **Trang thông tin điện tử** là trang thông tin hoặc tập hợp trang thông tin, **ứng dụng (application)** trên Internet... (Điều 3 khoản 19).
- **Trang thông tin điện tử cung cấp dịch vụ chuyên ngành** gồm lĩnh vực văn hóa, thể thao và du lịch... (Điều 3 khoản 23). Loại này **không phải cấp phép** theo Điều 24 khoản 2 điểm a.
- **Mạng xã hội** là hệ thống thông tin trên trang thông tin điện tử hoặc **ứng dụng trên mạng**, cung cấp dịch vụ/công cụ cho phép người dùng cung cấp, trao đổi, tương tác, chia sẻ thông tin với nhau (Điều 3 khoản 25) [S10].

#### B.2. Khi nào GoWander là mạng xã hội?

| Tính năng | Khả năng phân loại | Ghi chú |
|---|---|---|
| Chỉ đọc nội dung, bản đồ, tìm tuyến, không tài khoản | Trang TTĐT chuyên ngành (du lịch) — không cấp phép | Điều 24 khoản 2 điểm a |
| Có tài khoản, lưu địa điểm, không tương tác công khai | Có thể vẫn là trang chuyên ngành; cần luật sư | Tài khoản một mình chưa chắc là mạng xã hội |
| Có review/rating công khai, bình luận, follow | **Có khả năng là mạng xã hội** | Vì có tương tác/chia sẻ thông tin giữa người dùng |
| Có chat/nhóm/livestream | Gần chắc là mạng xã hội | Cần giấy phép/xác nhận; livestream chỉ mạng xã hội đã cấp phép |
| Có “feed” nội dung người dùng, hashtag, mention | Mạng xã hội | Cần đánh giá kỹ |

#### B.3. Ngưỡng và thủ tục

- **Mạng xã hội có lượng truy cập lớn**: từ 10.000 lượt truy cập/tháng (trung bình 6 tháng) hoặc trên 1.000 người dùng thường xuyên/tháng → **Giấy phép cung cấp dịch vụ mạng xã hội**.
- **Mạng xã hội lượng truy cập thấp**: dưới ngưỡng → **Giấy xác nhận thông báo cung cấp dịch vụ mạng xã hội**.
- Điều kiện: pháp nhân Việt Nam, tên miền, tổ chức/nhân sự/kỹ thuật, biện pháp quản lý nội dung (Điều 24 khoản 4 và Điều 25).
- Mạng xã hội/báo điện tử/trang TTĐT tổng hợp phải dùng ít nhất một tên miền `.vn` và lưu thông tin tại hệ thống máy chủ có địa chỉ Internet ở Việt Nam (Điều 19 khoản 11) [S10]. **Cần luật sư xác nhận** phạm vi áp dụng cho app du lịch có tính năng cộng đồng; điều khoản liệt kê nhiều loại hình, không chỉ mạng xã hội.
- Xác thực tài khoản bằng số điện thoại/định danh; trẻ dưới 16 tuổi phải do cha/mẹ/người giám hộ đăng ký và giám sát (Điều 27 khoản 3).
- Trách nhiệm gỡ bỏ: 24 giờ khi có yêu cầu cơ quan nhà nước; 48 giờ khi có khiếu nại có căn cứ từ người dùng.
- Chỉ mạng xã hội đã được cấp phép mới được livestream hoặc hoạt động phát sinh doanh thu (theo Điều 24 khoản 6 điểm d).

#### B.4. Rủi ro và khuyến nghị

- Nếu GoWander mở review/comment/follow mà không có giấy phép/xác nhận: có thể bị xử phạt, đình chỉ, gỡ app; app store có thể yêu cầu mã cấp phép.
- Nếu chỉ có nội dung biên tập + tài khoản lưu địa điểm: có thể vẫn là trang chuyên ngành, nhưng **phải có ý kiến luật sư** vì định nghĩa mạng xã hội rất rộng.
- Khuyến nghị: tách giai đoạn; ra mắt MVP không có review/comment; trước khi mở cộng đồng, làm hồ sơ phân loại, chuẩn bị giấy phép/xác nhận, xác thực tài khoản, moderation, .vn/máy chủ Việt Nam, và cập nhật app store.

**Trạng thái:** **[Cần luật sư]** về việc review/rating có làm GoWander thành mạng xã hội hay không. **Chưa kiểm chứng** thực tế cơ quan quản lý áp dụng cho app du lịch.

---

### C. Thương mại điện tử: khung 2026 (Luật 122/2025 + Nghị định 248/2026)

**Trả lời ngắn:** Nghị định 52/2013/NĐ-CP và Nghị định 85/2021/NĐ-CP **đã hết hiệu lực từ 01-7-2026**. Khung mới là **Luật Thương mại điện tử 122/2025/QH15** (hiệu lực 01-7-2026) và **Nghị định 248/2026/NĐ-CP** (hiệu lực 01-7-2026); Nghị định 248/2026 Điều 52 khoản 3 bãi bỏ 52/2013 và 85/2021. Nền tảng đã được xác nhận thông báo/đăng ký trước 01-7-2026 được chuyển tiếp đến hết 30-6-2027 (Điều 53), nhưng GoWander chưa đăng ký nên áp dụng khung mới ngay khi bán [S18][S19].

#### C.1. Khi nào phải thông báo/đăng ký?

- Luật 122/2025 Điều 3 phân 4 loại nền tảng: kinh doanh trực tiếp, trung gian, mạng xã hội hoạt động TMĐT, nền tảng tích hợp.
- **Nền tảng kinh doanh trực tiếp có chức năng đặt hàng trực tuyến**: phải **thông báo** trước khi vận hành. Hồ sơ gồm Tờ khai theo Mẫu số 01 Phụ lục II và bản chụp giấy phép/giấy chứng nhận nếu kinh doanh ngành nghề có điều kiện; **UBND cấp tỉnh** xác nhận thông báo (Nghị định 248/2026 Điều 23–24).
- **Nền tảng trung gian, mạng xã hội TMĐT, nền tảng tích hợp**: phải **đăng ký** theo Mục 2 Nghị định 248/2026 (cần đối chiếu điều/khoản cụ thể khi nộp hồ sơ).
- Nếu chỉ **affiliate link** và không có đặt hàng/thanh toán trên app: có thể không thuộc diện nền tảng TMĐT, nhưng vẫn chịu Luật Quảng cáo, Luật Người tiêu dùng, thuế. **Cần luật sư xác nhận** vì định nghĩa “hoạt động thương mại điện tử” rộng.
- Bán tour/lữ hành còn phải kiểm tra Luật Du lịch và giấy phép lữ hành; bán vé/đặt chỗ có thể kéo theo nghĩa vụ TMĐT dù không phải sàn.

#### C.2. Nghĩa vụ hiển thị

- Nghị định 248/2026 Điều 4: tên, địa chỉ trụ sở, người đại diện theo pháp luật; số/ngày cấp/nơi cấp ĐKKD hoặc mã số thuế.
- Điều 5: chính sách bảo mật tối thiểu gồm mục đích/phạm vi thu thập, phạm vi sử dụng, thời gian lưu, bên được tiếp cận, biện pháp bảo mật, quy trình xem/chỉnh sửa/xoá và khiếu nại.
- Điều 6–14: quyền/nghĩa vụ các bên, tiếp nhận phản ánh, chính sách giá, điều kiện/hạn chế cung cấp, thanh toán, ưu tiên hiển thị, livestream, giao hàng, đổi trả/hoàn tiền.
- Thông tin phải rõ ràng, chính xác, dễ tìm, hiển thị trước khi người dùng đặt hàng.

#### C.3. Nếu GoWander thành sàn/trung gian

- Có thể phải đăng ký nền tảng TMĐT trung gian; xác thực điện tử danh tính người bán/người livestream (bắt buộc từ 01-01-2027 theo Nghị định 248/2026 Điều 52 khoản 2); công bố quy chế; lưu dữ liệu giao kết tối thiểu 3 năm, trừ mức ngắn hơn cho doanh nghiệp siêu nhỏ/hộ kinh doanh theo Luật 122/2025 Điều 16; chịu trách nhiệm theo Luật 19/2023 Điều 39.
- Nếu có chức năng thanh toán và thu tiền hộ, có thể thuộc đối tượng **khấu trừ, nộp thuế thay** theo Nghị định 117/2025/NĐ-CP [S20].
- Nếu không có chức năng thanh toán, người bán tự kê khai/nộp thuế.

**Trạng thái:** **[Cần luật sư]** về việc mô hình affiliate/booking có phải là nền tảng TMĐT và có phải thông báo/đăng ký hay không. **Đã kiểm chứng** rằng 52/2013 và 85/2021 hết hiệu lực từ 01-7-2026; khung áp dụng là Luật 122/2025 + Nghị định 248/2026.

---

### D. Bảo vệ quyền lợi người tiêu dùng: giao dịch từ xa, nền tảng số

#### D.1. Nghĩa vụ thông tin

Luật 19/2023/QH15 Điều 37 yêu cầu tổ chức/cá nhân kinh doanh trong giao dịch từ xa cung cấp: tên, địa chỉ, điện thoại/liên hệ; số ĐKKD/mã số thuế; thông tin sản phẩm/dịch vụ (số lượng, chất lượng, giá, nguồn gốc, thời hạn); chi phí giao hàng; phương thức/thời hạn thanh toán; thời gian/địa điểm/phương thức cung cấp; điều kiện đổi trả; thời hạn hiệu lực của đề nghị; phí/thuế; bảo hành; quyền của người tiêu dùng; quy trình đổi trả/chấm dứt hợp đồng; quy trình khiếu nại [S11].

#### D.2. Hợp đồng từ xa

Điều 38: phải cung cấp nội dung hợp đồng trước khi giao kết; cho người tiêu dùng trao đổi/làm rõ/xác nhận; cho xem lại/tải hợp đồng đã xác nhận. Nếu thông tin không chính xác/đầy đủ, người tiêu dùng có quyền đơn phương chấm dứt trong **30 ngày** và được hoàn tiền phần chưa sử dụng trong **30 ngày**; quá hạn phải trả lãi [S11].

#### D.3. Nền tảng số trung gian

Điều 39: chỉ định đầu mối liên hệ; công bố quy chế; cung cấp thông tin người bán; cho phép đánh giá và hiển thị trung thực; hiển thị thông tin sản phẩm; tiếp nhận khiếu nại; ưu tiên hiển thị đánh giá của tổ chức xã hội; lưu thông tin giao dịch; minh bạch quảng cáo; báo cáo kiểm duyệt; xác thực danh tính người bán; chịu trách nhiệm theo pháp luật TMĐT [S11]. Nghị định 55/2024/NĐ-CP quy định thêm về nền tảng số lớn (từ 3 triệu tài khoản/năm) [S12].

#### D.4. Ghi chú cho GoWander

- Nếu bán vé/tour: hiển thị đầy đủ thông tin trước khi thanh toán; hợp đồng điện tử; xác nhận; chính sách huỷ/hoàn; không gài điều khoản bất lợi.
- Nếu chỉ giới thiệu/affiliate: vẫn phải minh bạch “được tài trợ/affiliate”; không gây nhầm lẫn là GoWander bán dịch vụ.
- Nếu có review: không được ngăn/hiển thị không trung thực; phải có quy trình báo cáo.

---

### E. Quảng cáo, affiliate và claim

#### E.1. Quảng cáo trên mạng

Luật Quảng cáo 16/2012/QH13 (sửa đổi bởi 75/2025/QH15) Điều 23 yêu cầu:

- Dấu hiệu nhận diện rõ ràng để phân biệt quảng cáo với nội dung khác.
- Quảng cáo không ở vùng cố định phải có tính năng/biểu tượng tắt, báo cáo, từ chối xem.
- Quảng cáo có link: nội dung đích phải hợp pháp; người kinh doanh/phát hành có giải pháp kiểm tra.
- Mạng xã hội phải cung cấp tính năng phân biệt quảng cáo; người dùng mạng xã hội khi quảng cáo phải có dấu hiệu phân biệt nội dung quảng cáo/được tài trợ.
- Người kinh doanh dịch vụ quảng cáo trên mạng phải thông báo thông tin liên hệ, xác minh danh tính người quảng cáo, lưu hồ sơ, có giải pháp kiểm soát, lưu quy tắc/thuật toán, tiếp nhận khiếu nại, báo cáo định kỳ [S13].

Nghị định 342/2025/NĐ-CP quy định chi tiết: quảng cáo không ở vùng cố định phải tắt được bằng một lần tương tác; ảnh tĩnh không có thời gian chờ; video tối đa 5 giây; lưu hồ sơ quảng cáo 3 năm; báo cáo hằng năm trước 25-11; thông báo thông tin liên hệ với Bộ VHTTDL trước khi kinh doanh dịch vụ quảng cáo trên mạng [S14]. Các chi tiết thời hạn/hồ sơ nên đối chiếu lại điều khoản gốc trước khi áp dụng.

#### E.2. Claim “số 1”

- Luật Quảng cáo Điều 8 khoản 11 cấm dùng “nhất”, “duy nhất”, “tốt nhất”, “số một” hoặc tương tự mà không có tài liệu hợp pháp chứng minh theo quy định của Bộ VHTTDL.
- Nghị định 87/2026/NĐ-CP Điều 50 khoản 2 điểm a: phạt 10–20 triệu đồng với cá nhân, 20–40 triệu đồng với tổ chức [S15].
- Thông tư 12/2026/TT-BVHTTDL Điều 3: tài liệu hợp pháp là kết quả khảo sát thị trường của tổ chức có chức năng nghiên cứu thị trường, hoặc giấy chứng nhận của cơ quan/tổ chức có thẩm quyền tại cuộc thi/triển lãm/giải thưởng; phải ghi rõ tên tài liệu, số/ký hiệu, thời gian; tài liệu phải còn hiệu lực, độc lập, khách quan [S16][S43].

#### E.3. Affiliate

- Phải công khai mối quan hệ affiliate/được tài trợ; không làm người dùng nhầm là đánh giá khách quan.
- Không dùng review giả/affiliate link trá hình để tăng hạng.
- Tuân thủ Luật Quảng cáo, Luật Người tiêu dùng, thuế.
- Nếu affiliate với nước ngoài: kê khai thuế, có thể chịu thuế nhà thầu; hỏi kế toán.

#### E.4. Checklist

- [ ] Có nhãn “Quảng cáo”/“Được tài trợ” rõ ràng.
- [ ] Có nút tắt/báo cáo quảng cáo; video ≤ 5 giây; ảnh tĩnh tắt ngay.
- [ ] Có hợp đồng với advertiser/network; lưu hồ sơ 3 năm.
- [ ] Có claim register + tài liệu chứng minh.
- [ ] Có chính sách affiliate disclosure.
- [ ] Có báo cáo thuế cho thu nhập quảng cáo/affiliate.

---

### F. Sở hữu trí tuệ: ảnh, nội dung, thương hiệu

#### F.1. Bảng nguồn ảnh

| Nguồn | Giấy phép | Điều kiện dùng | Rủi ro |
|---|---|---|---|
| Wikimedia Commons | Từng file: CC0/PD/CC BY/CC BY-SA | Ghi tác giả, giấy phép, link; chỉ rõ thay đổi; CC BY-SA có share-alike | Ảnh có người/trẻ em cần model release; một số file có điều kiện khác |
| OpenStreetMap | ODbL 1.0 | Ghi “© OpenStreetMap contributors”; derivative database share-alike; tile usage policy | Không phải nguồn chính thức; có thể sai chủ quyền; không có SLA |
| Mapillary | CC BY-SA 4.0 | Ghi công, giấy phép, link; tuân thủ điều khoản | Ảnh có người/biển số dù đã blur; vẫn là dữ liệu cá nhân |
| Google Maps/Street View | Proprietary | Chỉ dùng trong dịch vụ Google theo hợp đồng; không scrape/cache/tái sử dụng; không dùng screenshot làm tài sản của app/ASO nếu chưa có phép | Vi phạm điều khoản; gỡ app; kiện |
| Ảnh tự chụp/đối tác | Hợp đồng | Có giấy phép bằng văn bản; model release; credit | Thiếu giấy tờ; tranh chấp |
| Ảnh người dùng | Điều khoản app | Người dùng cấp phép; cam kết có quyền; moderation; takedown | Ảnh người khác/trẻ em; bản quyền |

#### F.2. Nội dung văn bản

- Wikipedia/Wikimedia text: CC BY-SA 4.0; phải ghi nguồn, tác giả, giấy phép; nếu sửa, phải ghi “đã sửa” và chia sẻ tác phẩm phái sinh theo cùng giấy phép.
- Trích dẫn hợp lý theo Nghị định 17/2023/NĐ-CP Điều 28: chỉ để giới thiệu/bình luận/làm sáng tỏ; không gây thiệt hại bất hợp lý; ghi nguồn/tác giả. Không được dùng trích dẫn để copy toàn bộ bài/ảnh.
- Nội dung do AI tạo: cần kiểm tra bản quyền/nguồn; không đăng ảnh giả người thật; ghi nhãn nếu cần.

#### F.3. Thương hiệu

- Đăng ký nhãn hiệu “GoWander” nếu có thể; kiểm tra trùng trước khi đầu tư.
- Không dùng logo/nhãn của đối tác khi chưa được phép.
- Không dùng “Metro”, “Vietnam Airlines”... gây nhầm lẫn là đại diện chính thức nếu chưa có hợp đồng.

---

### G. Thuế, hoá đơn điện tử, thanh toán và lưu trú/lữ hành

#### G.1. Khi nào phát sinh nghĩa vụ?

| Mô hình | Nghĩa vụ chính | Ghi chú |
|---|---|---|
| Chỉ affiliate link | Kê khai thu nhập; hợp đồng; có thể thuế nhà thầu nếu đối tác nước ngoài | Chưa phải bán hàng trực tiếp |
| Bán quảng cáo | Đăng ký ngành nghề; hoá đơn; thuế GTGT/TNDN/TNCN; hợp đồng | Luật Quảng cáo + thuế |
| Bán vé/tour của mình | Đăng ký kinh doanh; hoá đơn điện tử; thuế; chính sách hoàn tiền; giấy phép lữ hành nếu tour | Xem G.3 |
| Sàn/trung gian | Đăng ký TMĐT; xác thực người bán; khấu trừ/nộp thuế thay nếu có thanh toán | Nghị định 117/2025 |

#### G.2. Hoá đơn điện tử

- Từ 01-7-2026, Nghị định 254/2026/NĐ-CP thay thế Nghị định 123/2020/NĐ-CP và Nghị định 70/2025/NĐ-CP về hoá đơn, chứng từ điện tử [S21].
- Khi bán hàng hóa/dịch vụ phải lập hoá đơn điện tử giao cho người mua, trừ trường hợp không phải sử dụng.
- Hộ kinh doanh/cá nhân kinh doanh có doanh thu năm trên **01 tỷ đồng** hoặc bán tài sản phải đăng ký quyền sở hữu/sử dụng thì phải áp dụng hoá đơn điện tử có mã của cơ quan thuế/hoá đơn khởi tạo từ máy tính tiền có kết nối dữ liệu (theo Điều 6 Nghị định 254/2026) [S21]. Ngưỡng/diễn giải cần kế toán xác nhận.
- Lưu hoá đơn/chứng từ theo Luật Kế toán 2015 Điều 41: ít nhất 5 năm/10 năm tùy loại; hoá đơn điện tử thường 10 năm (cần đối chiếu Nghị định 254/2026) [S22].

#### G.3. Lữ hành

- Luật Du lịch 09/2017/QH14: kinh doanh dịch vụ lữ hành nội địa/quốc tế cần giấy phép; điều kiện gồm doanh nghiệp, ký quỹ, người phụ trách chuyên ngành [S39].
- Nếu GoWander chỉ bán vé xe/metro, không tổ chức tour: có thể không cần giấy phép lữ hành, nhưng cần hợp đồng với đơn vị vận tải và tuân thủ quy định bán vé.
- Nếu GoWander tổ chức tour/đặt tour trọn gói: cần giấy phép lữ hành; nếu làm đại lý cho đơn vị có phép, cần hợp đồng đại lý và công bố.
- Ký quỹ: nội địa 100 triệu đồng; quốc tế cao hơn (cần kiểm tra Thông tư/Nghị định hiện hành). **Chưa kiểm chứng** mức quốc tế tại thời điểm 17-11-2026.

#### G.4. Thanh toán

- Nếu GoWander giữ tiền/escrow/ví: có thể cần giấy phép trung gian thanh toán theo Nghị định 52/2024/NĐ-CP [S40].
- Nếu chỉ dùng cổng thanh toán được cấp phép (VNPay, MoMo, ngân hàng): tuân thủ hợp đồng, không lưu số thẻ, PCI DSS, đối soát, hoàn tiền.
- QR thanh toán: dùng chuẩn VietQR/EMV và hợp đồng ngân hàng; không tự tạo mã thu tiền khi chưa có phép.

#### G.5. Thuế sàn TMĐT

- Nghị định 117/2025/NĐ-CP: sàn/nền tảng có chức năng thanh toán phải khấu trừ, nộp thuế thay VAT/TNCN cho hộ/cá nhân bán hàng; thời điểm khấu trừ khi giao dịch thành công và chấp nhận thanh toán [S20].
- Nếu GoWander chỉ bán dịch vụ của mình, không phải sàn, thì tự kê khai/nộp thuế.
- Nếu có affiliate/ads, kê khai thu nhập; kiểm tra thuế nhà thầu.

---

### H. App store: Google Play Data safety, Apple App Privacy, 5.1.1

Đây không phải pháp luật Việt Nam nhưng là **điều kiện phát hành**; vi phạm có thể bị từ chối/gỡ app.

#### H.1. Apple

- 5.1.1(i): mọi app phải có link privacy policy trong App Store Connect và trong app; chính sách phải nêu dữ liệu thu, cách thu, mọi mục đích, bên thứ ba và bảo vệ tương đương, thời hạn lưu/xoá, cách rút đồng ý/xoá dữ liệu [S26].
- 5.1.1(ii): phải có sự đồng ý khi thu dữ liệu, kể cả dữ liệu được coi là ẩn danh; phải có cách rút lại dễ hiểu; không bắt buộc mua tính năng phải đồng ý dữ liệu.
- 5.1.1(iii): data minimization; chỉ yêu cầu dữ liệu liên quan chức năng cốt lõi.
- 5.1.1(iv): tôn trọng quyền riêng tư; nếu từ chối vị trí, cho nhập địa chỉ thủ công.
- 5.1.1(v): nếu app có tạo tài khoản, phải cho xoá tài khoản trong app.
- 1.2: UGC phải có lọc nội dung, báo cáo, chặn người lạm dụng, thông tin liên hệ.
- 3.0: cấm thao túng review/xếp hạng bằng feedback giả/được khuyến khích.

#### H.2. Google Play

- Data safety: khai báo tất cả dữ liệu thu thập/chia sẻ, kể cả qua SDK; nêu mục đích, mã hoá, xoá; không khai báo sai [S27].
- Account deletion: nếu app cho tạo tài khoản, phải có đường dẫn xoá trong app **và** web link; phải xoá dữ liệu liên quan, trừ lý do hợp pháp [S28].
- UGC policy: kiểm duyệt, báo cáo, chặn; chính sách gia đình nếu hướng tới trẻ em [S47].

#### H.3. Checklist

- [ ] Privacy policy URL hoạt động, công khai, khớp trong app.
- [ ] App Privacy/Data safety khớp SDK thực tế.
- [ ] Account deletion trong app + web.
- [ ] UGC report/block/moderation.
- [ ] Kids/Families policy nếu có trẻ em.
- [ ] Screenshots/metadata không có claim “số 1” thiếu chứng cứ.
- [ ] Demo account cho reviewer (nếu app cần đăng nhập).

---

### I. Tiếp cận: nghĩa vụ đến đâu?

#### I.1. Kết luận có điều kiện

- **Cơ quan nhà nước, báo chí, đơn vị sự nghiệp công**: Thông tư 26/2020/TT-BTTTT bắt buộc WCAG 1.0 cho website/cổng thông tin; WCAG 2.0/2.1 được khuyến nghị [S24].
- **Doanh nghiệp tư nhân như GoWander**: **chưa có nghĩa vụ pháp lý rõ ràng** áp dụng WCAG cho app/web. Luật Người khuyết tật 2010 quy định quyền tiếp cận công nghệ thông tin và khuyến khích doanh nghiệp ứng dụng CNTT cho người khuyết tật (Điều 4, 43) [S23], nhưng không có chế tài cụ thể cho app tư nhân.
- **Nếu GoWander hợp tác với cơ quan nhà nước, nhận vốn/đấu thầu công, hoặc cung cấp dịch vụ công**: có thể bị yêu cầu tuân thủ WCAG/QCVN theo hợp đồng/đấu thầu.
- **App store**: Apple/Google khuyến khích accessibility, không bắt buộc WCAG, nhưng app kém tiếp cận có thể bị đánh giá thấp/loại khỏi một số chương trình.

#### I.2. Chuẩn nên chọn

- **WCAG 2.2 AA** cho web và app (áp dụng tinh thần; mobile có điều chỉnh).
- Các tiêu chí trọng tâm: 1.1.1 alt text; 1.3.1 cấu trúc; 1.4.3/1.4.11 tương phản; 2.1.1 bàn phím; 2.4.3 focus; 2.4.6 label; 2.5.3 label in name; 2.5.8 target size; 3.3.1/3.3.2 lỗi/nhãn; 4.1.2 name/role/value.
- Bản đồ: cung cấp danh sách tuyến/điểm đến dạng chữ; điều khiển bàn phím; mô tả alt; không chỉ dùng màu.

#### I.3. Tài liệu/UI

- Accessibility statement: mục tiêu WCAG 2.2 AA, phạm vi, giới hạn đã biết (bản đồ), liên hệ phản hồi, ngày rà soát.
- Kiểm thử: TalkBack/VoiceOver, keyboard, 200% zoom, high contrast, reduced motion, switch access.
- Đào tạo design/dev; checklist component.

---

### J. Dữ liệu vị trí, trẻ em, lưu trữ/xoá, backup, log, data localization

#### J.1. Dữ liệu vị trí là nhạy cảm

- Nghị định 356/2025/NĐ-CP Điều 4 khoản 1 điểm h: “Vị trí của cá nhân được xác định qua dịch vụ định vị” là dữ liệu cá nhân nhạy cảm [S2]. Vị trí lấy từ GPS/định vị là nhạy cảm; địa chỉ nhập tay vẫn là dữ liệu cá nhân và có thể nhạy cảm nếu gắn với một người cụ thể — cần xử lý thận trọng như nhau.
- Luật 91/2025/QH15 Điều 31: tổ chức cung cấp nền tảng ứng dụng di động phải thông báo việc sử dụng dữ liệu vị trí; có biện pháp ngăn chặn thu thập bởi bên không liên quan; cung cấp tùy chọn theo dõi vị trí [S1].
- Nghị định 356/2025/NĐ-CP Điều 29: sự cố liên quan vị trí/sinh trắc học phải thông báo cho chủ thể bị ảnh hưởng trong **72 giờ**; báo cáo cơ quan; lưu hồ sơ sự cố tối thiểu **5 năm** [S2].
- Luật 91/2025/QH15 Điều 23: vi phạm có thể gây tổn hại đến quyền/lợi ích của chủ thể phải thông báo cho cơ quan chuyên trách bảo vệ dữ liệu cá nhân chậm nhất **72 giờ** kể từ khi phát hiện; lập biên bản, phối hợp xử lý [S1]. Nghị định 356/2025/NĐ-CP Điều 28 quy định nội dung thông báo [S2].

**UI cần có:** pre-permission screen; lựa chọn “Chỉ khi dùng app”/“Không”; “Nhập địa chỉ” thay thế; công tắc “Lưu lịch sử vị trí” mặc định tắt; xoá lịch sử; giải thích dữ liệu nào ở lại thiết bị/server.

#### J.2. Trẻ em

- Luật 91/2025/QH15 Điều 24: trẻ em (dưới 16 tuổi) do người đại diện theo pháp luật thực hiện quyền; trẻ từ đủ 7 tuổi, việc công bố/tiết lộ đời sống riêng tư phải có sự đồng ý của trẻ và người đại diện [S1].
- Nghị định 147/2024/NĐ-CP: trẻ dưới 16 tuổi dùng mạng xã hội phải được cha/mẹ/người giám hộ đăng ký và giám sát [S10].
- App store: Kids Category/Google Families có yêu cầu riêng; nếu app không hướng tới trẻ em, vẫn nên có age gate và không thu dữ liệu trẻ em nếu không cần.
- Nếu có ảnh/review của trẻ: cần consent của cha/mẹ/người giám hộ; cân nhắc không hiển thị công khai.

#### J.3. Lưu trữ/xoá/backup/log

Gợi ý ma trận (cần luật sư/kế toán xác nhận):

| Loại dữ liệu | Thời hạn gợi ý | Căn cứ/ghi chú |
|---|---|---|
| Tài khoản, profile | Trong thời gian dùng + 30–90 ngày sau xoá | Nghị định 356/2025 Điều 5: xoá trong 20/30 ngày |
| Lịch sử vị trí (nếu opt-in) | 30–90 ngày, hoặc chỉ phiên | Tối thiểu hoá; không lưu nếu không cần |
| Bộ đếm analytics gộp | 25 tháng (theo ADR-038) | Không định danh; kiểm tra lại |
| Log truy cập/hệ thống | 12 tháng | Nghị định 333/2026/NĐ-CP (hiệu lực 19-8-2026; theo bản tin EY, cần đối chiếu điều khoản gốc) |
| Hồ sơ sự cố vị trí/sinh trắc | 5 năm | Nghị định 356/2025 Điều 29 |
| Hoá đơn/chứng từ kế toán | 5/10 năm | Luật Kế toán 2015 Điều 41 |
| Hồ sơ khiếu nại/tranh chấp | Đến khi kết thúc + 2–3 năm | Legal hold |
| Backup | 30–90 ngày, rotating | Phải xoá/không phục hồi dữ liệu đã xoá |

- **Backup**: phải có lịch xoá; nếu người dùng yêu cầu xoá, backup cũ có thể còn nhưng không được phục hồi; ghi rõ trong policy.
- **Log**: không ghi vị trí thô, token, số thẻ, nội dung nhạy cảm; che dữ liệu; phân quyền; lưu 12 tháng.
- **Legal hold**: giữ dữ liệu khi có tranh chấp/yêu cầu cơ quan; thông báo cho người dùng nếu được phép.

#### J.4. Data localization và an ninh mạng

- Luật An ninh mạng 116/2025/QH15 (hiệu lực 01-7-2026) Điều 25 khoản 3: doanh nghiệp trong nước/nước ngoài cung cấp dịch vụ trên mạng viễn thông, Internet, dịch vụ gia tăng trên không gian mạng tại Việt Nam, có thu thập/khai thác/phân tích/xử lý dữ liệu cá nhân, dữ liệu quan hệ, dữ liệu do người dùng tại Việt Nam tạo ra, phải lưu trữ tại Việt Nam theo thời hạn Chính phủ quy định; doanh nghiệp nước ngoài còn phải đặt chi nhánh/văn phòng đại diện [S3].
- Nghị định 333/2026/NĐ-CP (hiệu lực **19-8-2026**) Điều 2 khoản 5 định nghĩa **“dịch vụ gia tăng trên không gian mạng” là dịch vụ viễn thông giá trị gia tăng** — phạm vi hẹp hơn tên gọi. Điều 19 quy định dữ liệu phải lưu gồm thông tin cá nhân của người dùng tại Việt Nam và dữ liệu do người dùng tạo ra (tên tài khoản, thời gian sử dụng, thông tin thẻ, email, IP đăng nhập/đăng xuất gần nhất, số điện thoại); doanh nghiệp trong nước phải lưu tại Việt Nam; doanh nghiệp nước ngoài chỉ sau quyết định của Bộ Công an (theo bản tin EY, cần đối chiếu điều khoản gốc) [S4][S46].
- Thời gian lưu tối thiểu 24 tháng với dữ liệu người dùng (doanh nghiệp nước ngoài sau quyết định); log hệ thống 12 tháng (theo bản tin EY, cần đối chiếu điều khoản gốc) [S46].
- **Tác động GoWander:** app du lịch có thể **không** thuộc “dịch vụ viễn thông giá trị gia tăng”, nên nghĩa vụ data localization có thể không áp dụng. Đây là kết luận sơ bộ từ định nghĩa tại Nghị định 333/2026 Điều 2 khoản 5; **cần luật sư xác nhận**. Nếu thuộc diện, kiến trúc Cloudflare R2/dịch vụ nước ngoài phải rà soát và có thể cần primary DB/storage tại Việt Nam.
- **Chuyển dữ liệu xuyên biên giới**: nếu vẫn chuyển, phải lập hồ sơ đánh giá tác động chuyển dữ liệu, nộp trong 60 ngày, có biện pháp bảo vệ, cập nhật. Không được dùng chuyển dữ liệu để né data localization.

#### J.5. DPIA/DPO

- DPIA: lập trước khi xử lý; nộp trong 60 ngày; cập nhật 6 tháng/10 ngày; lưu luôn có sẵn [S2].
- DPO: bổ nhiệm hoặc thuê; điều kiện cao đẳng + 2 năm kinh nghiệm + đào tạo (Nghị định 356/2025 Điều 13) [S2].
- Miễn trừ doanh nghiệp nhỏ/khởi nghiệp: 5 năm cho Điều 21, 22, 33(2) theo Luật 91/2025 Điều 38 và Nghị định 356/2025 Điều 41, **trừ** doanh nghiệp xử lý dữ liệu nhạy cảm. Vì location là nhạy cảm, GoWander **khó có thể dựa vào miễn trừ** — cần luật sư xác nhận [S1][S2].

---

### K. QR code

- **Chưa có luật riêng về QR code** tại Việt Nam (chưa kiểm chứng văn bản mới đến 17-11-2026). QR chỉ là phương tiện chứa dữ liệu/liên kết; áp dụng luật hiện hành:
  - Nếu QR thanh toán: Nghị định 52/2024/NĐ-CP, hợp đồng ngân hàng/trung gian thanh toán; dùng VietQR/EMV; không tự phát hành mã thu tiền khi chưa được phép.
  - Nếu QR dẫn tới nội dung/quảng cáo: Luật Quảng cáo, Luật Người tiêu dùng; nội dung đích phải hợp pháp.
  - Nếu QR chứa dữ liệu cá nhân: Luật 91/2025; không encode tên/số điện thoại/CCCD; dùng token ngẫu nhiên, có chữ ký, hết hạn, kiểm tra server-side.
  - Nếu QR dùng check-in/điểm danh: có thể thu vị trí/thời gian; phải thông báo/đồng ý; không công khai ai đã check-in.
- **UI**: cung cấp mã ngắn/URL chữ thay thế; hướng dẫn quét; cảnh báo không quét mã lạ; kiểm tra domain trước khi mở.
- **Rủi ro**: quishing, mã giả, lộ dữ liệu, thanh toán sai, tranh chấp.

---

## 4. Checklist tài liệu và UI

### 4.1. Tài liệu phải có

| Tài liệu | Nội dung tối thiểu | Owner | Khi nào |
|---|---|---|---|
| Privacy policy | Như §2.1 | Legal/Product | P0 |
| Terms of use | Như §2.2 | Legal/Product | P0 |
| Cookie policy | Như §2.3 | Legal/Engineering | P1 |
| Refund policy | Như §2.4 | Commercial/Legal | P3 |
| Imprint/Contact | Tên, MST, địa chỉ, đại diện, giấy phép | Ops | P0 |
| Consent record | Log phiên bản/thời điểm/mục đích | Engineering | P0 |
| Data map/records | Bảng dữ liệu, mục đích, vendor, retention | DPO/Legal | P1 |
| DPIA | Hồ sơ đánh giá tác động | DPO/Legal | P1 |
| Cross-border dossier | Hồ sơ chuyển dữ liệu | DPO/Legal | P1 |
| DPO appointment | Quyết định/hợp đồng | HR/Legal | P1 |
| Vendor DPA list | Hợp đồng xử lý dữ liệu | Procurement/Legal | P1 |
| Image credits | File, tác giả, giấy phép, URL | Content | P0 |
| Moderation policy | UGC, báo cáo, chặn, gỡ | Trust & Safety | P2 |
| Ad/affiliate disclosure | Nhãn, chính sách | Marketing/Legal | P4 |
| Accessibility statement | Mục tiêu, giới hạn, liên hệ | Design/Eng | P1 |
| Incident response | Quy trình, đầu mối, 72h | Security/Legal | P0 |
| E-commerce notification | Hồ sơ UBND cấp tỉnh | Ops/Legal | P3 |
| Travel licence | Nếu bán tour | Ops/Legal | P3 |
| Tax/e-invoice | Đăng ký, hoá đơn | Finance | P3 |
| App store privacy labels | Apple/Google | Product/Eng | P1 |
| Map legal review | Nguồn, hiển thị, chủ quyền | Product/Legal | P0 |

### 4.2. UI/screen checklist

- [ ] Màn hình chào: link Privacy/Terms; không tự động bật tracking.
- [ ] Tạo tài khoản: checkbox Terms riêng; checkbox privacy/consent riêng; giải thích dữ liệu bắt buộc/tuỳ chọn.
- [ ] Trước khi bật vị trí: pre-permission; “Dùng vị trí của tôi” / “Nhập địa chỉ”; nói rõ dữ liệu nhạy cảm.
- [ ] Cài đặt: “Quyền riêng tư” (analytics, ads, location history), “Tài khoản” (xoá), “Dữ liệu của tôi” (tải/xoá).
- [ ] Trước khi gửi ảnh/review: nhắc quyền riêng tư, quy tắc cộng đồng, quyền sử dụng nội dung.
- [ ] Trước khi thanh toán: terms/refund link; breakdown giá/phí/thuế; phương thức; xác nhận.
- [ ] Sau thanh toán: email xác nhận; hoá đơn; chính sách huỷ/hoàn; liên hệ.
- [ ] Banner cookie web: accept/reject/customize; log; link policy.
- [ ] Trang “Nguồn ảnh”: credit đầy đủ.
- [ ] Trang “Giới thiệu/Liên hệ”: pháp nhân, MST, địa chỉ, giấy phép.
- [ ] Accessibility: focus, alt, contrast, labels, error messages.
- [ ] Map: attribution; nút zoom; text alternative; disclaimer.
- [ ] Ads: nhãn “Quảng cáo/Được tài trợ”; tắt/báo cáo.

---

## 5. Lộ trình thực hiện đề xuất

### Giai đoạn 0 — Trước pilot (2–4 tuần)

1. Chốt mô hình: guest mode, không account, không review, không monetization.
2. Draft privacy policy/terms/imprint; luật sư review.
3. Data map tối thiểu: location, device, log, feedback; xác định cái gì không thu.
4. Location consent + pre-permission + privacy settings.
5. Map legal review: nguồn, hiển thị, chủ quyền, attribution; quyết định có dùng OpenFreeMap tiếp hay không.
6. Image license audit; cập nhật `photo-credits`.
7. Accessibility basics: alt, contrast, labels, keyboard cho web admin.
8. Incident response tối thiểu; log retention.
9. App store privacy labels (nếu phát hành).

### Giai đoạn 1 — Mở tài khoản (4–6 tuần)

1. Consent theo mục đích; consent log.
2. Account deletion in-app + web; data rights form.
3. DPIA; cross-border dossier; DPO; vendor DPAs.
4. Data localization assessment; kiến trúc lưu trữ Việt Nam.
5. Privacy policy/terms cập nhật; app store labels.
6. Business info page.
7. Accessibility audit WCAG 2.2 AA (web + app).
8. Security: MFA cho admin, phân quyền, mã hoá, backup, incident drill.

### Giai đoạn 2 — Cộng đồng/đánh giá (6–8 tuần)

1. Phân loại Nghị định 147; quyết định giấy phép/xác nhận.
2. Xác thực tài khoản; trẻ em; moderation; report/block.
3. Review authenticity; sponsored disclosure; không seed/fake.
4. Platform liability; quy chế; xử lý tranh chấp.
5. App store UGC policy; cập nhật Data safety.
6. Legal review trước launch.

### Giai đoạn 3 — Bán vé/đặt chỗ (8–12 tuần)

1. Xác định có phải nền tảng TMĐT theo Luật 122/2025 và Nghị định 248/2026; nếu có chức năng đặt hàng trực tuyến thì thông báo UBND cấp tỉnh.
2. Chính sách hoàn tiền; hợp đồng điện tử; xác nhận.
3. Giấy phép lữ hành (nếu tour); hợp đồng đối tác.
4. Payment partner; PCI DSS; đối soát; hoá đơn điện tử/thuế.
5. Consumer law review; complaint handling.
6. App store payment rules.

### Giai đoạn 4 — Quảng cáo/affiliate (4–6 tuần)

1. Ad/affiliate disclosure; hợp đồng.
2. Claim register; bỏ claim “số 1” nếu không chứng minh.
3. Ad format: tắt/báo cáo; video ≤ 5s; lưu hồ sơ 3 năm.
4. Tax/e-invoice cho doanh thu quảng cáo.
5. Consent cho ads tracking; cập nhật privacy/Data safety.

---

## 6. Câu hỏi cần luật sư xác nhận

1. Bản đồ tương tác của GoWander có được coi là “sản phẩm đo đạc và bản đồ”/“xuất bản phẩm bản đồ” theo Luật 27/2018 và Nghị định 18/2020 không?
2. Nếu có, yêu cầu “thể hiện đầy đủ chủ quyền” áp dụng thế nào với bản đồ TP.HCM/zoom nhỏ? Có cần thể hiện Hoàng Sa/Trường Sa ở mọi zoom hay chỉ bản đồ Việt Nam?
3. Việc dùng tile OSM/OpenFreeMap và thêm nhãn Hoàng Sa/Trường Sa có đủ không? Có cần dữ liệu nền địa lý quốc gia/đơn vị được cấp phép?
4. Nghị định 39/2026/NĐ-CP có thay đổi nội dung bản đồ/chủ quyền ngoài thủ tục cấp phép không?
5. GoWander có thuộc “doanh nghiệp trong nước cung cấp dịch vụ gia tăng trên không gian mạng” theo Luật 116/2025 Điều 25(3) và Nghị định 333/2026 Điều 19 không?
6. Nếu có, dữ liệu nào phải lưu tại Việt Nam? Có được dùng Cloudflare R2/foreign cloud nếu có bản sao tại Việt Nam? Thời hạn 24 tháng áp dụng thế nào?
7. Vị trí dùng để tìm tuyến (xử lý tức thời, không lưu) có phải “xử lý dữ liệu vị trí” nhạy cảm không? Consent text cần gì?
8. GoWander có được hưởng miễn trừ doanh nghiệp nhỏ/khởi nghiệp cho DPIA/DPO không, khi xử lý location?
9. Có cần DPO riêng không? Có thể thuê ngoài? Điều kiện?
10. Review/rating công khai có làm GoWander thành “mạng xã hội” theo Nghị định 147/2024 không? Cần giấy phép hay giấy xác nhận?
11. Nếu có, yêu cầu `.vn`, máy chủ Việt Nam, xác thực tài khoản, trẻ dưới 16 tuổi áp dụng thế nào?
12. Affiliate-only có phải thông báo website TMĐT không? Booking thì sao?
13. Nếu bán tour, GoWander cần giấy phép lữ hành nội địa/quốc tế hay chỉ làm đại lý? Ký quỹ bao nhiêu?
14. Nếu GoWander thu tiền hộ/giữ tiền, có cần giấy phép trung gian thanh toán không? Dùng cổng nào?
15. Chính sách hoàn tiền hiện tại có đáp ứng Luật 19/2023 không? Có được quy định “không hoàn” trong trường hợp nào?
16. Có được dùng “số 1” nếu có khảo sát thị trường? Khảo sát nào được chấp nhận? Có cần ghi số/ngày trên app store?
17. Ảnh Wikimedia CC BY-SA dùng trong app có phải share-alike toàn bộ app không? Cách ghi credit?
18. Ảnh Mapillary CC BY-SA có được dùng thương mại không? Có cần xoá mặt/biển số trước khi đăng?
19. Có được dùng screenshot Google Maps/Street View trong bài blog/ASO không? (Dự kiến: không.)
20. Có cần banner cookie ở Việt Nam không? Nếu chỉ dùng analytics gộp, không định danh, có cần?
21. GDPR/CCPA có áp dụng nếu app có sẵn trên store EU/US nhưng chỉ phục vụ TP.HCM? Cần đại diện không?
22. Trẻ em dưới 16 tuổi dùng app du lịch (không phải mạng xã hội) có cần consent cha/mẹ không? Age gate nào?
23. Thời hạn lưu vị trí/log/backup bao lâu là hợp lý? Có cần xoá backup ngay khi user xoá không?
24. Khi xảy ra lộ dữ liệu vị trí, quy trình thông báo 72h gồm những gì? Thông báo cho ai?
25. App store Data safety/App Privacy có cần khai báo dữ liệu xử lý tức thời (ephemeral) không? Khai báo thế nào?
26. Chữ ký điện tử nào đủ cho hợp đồng tour/vé? Có cần chữ ký số không?
27. Hoá đơn điện tử cho hộ kinh doanh dưới 1 tỷ/năm có bắt buộc không? Nếu không, có nên đăng ký?
28. Quảng cáo/affiliate thu nhập từ nước ngoài: thuế nhà thầu, kê khai thế nào?
29. Điều khoản giới hạn trách nhiệm/miễn trừ bản đồ có bị coi là điều khoản không được phép theo Luật 19/2023 không?
30. Nếu có sự cố bảo mật, có phải thông báo app store/người dùng không? Trong bao lâu?

---

## 7. Danh sách nguồn (truy cập ngày 17-11-2026)

> Ghi chú: [Công báo] = Công báo điện tử Chính phủ; [VBPL] = Cơ sở dữ liệu quốc gia về pháp luật; [Bộ/ngành] = trang chính thức; [App Store] = chính sách nền tảng; [Quốc tế] = văn bản/tổ chức quốc tế; [Tham khảo] = bài phân tích/bản tin, dùng để giải thích, không thay thế văn bản gốc.

| ID | Văn bản/nguồn | Cơ quan/loại | Link | Ngày truy cập | Ghi chú |
|---|---|---|---|---|---|
| S1 | Luật Bảo vệ dữ liệu cá nhân 91/2025/QH15 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-91-2025-qh15-45578/57730.htm | 17-11-2026 | Hiệu lực 01-01-2026 |
| S2 | Nghị định 356/2025/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-356-2025-nd-cp-468371/61065.htm | 17-11-2026 | Thay thế Nghị định 13/2023 từ 01-01-2026; một số thủ tục sửa bởi Nghị định 392/2026/NĐ-CP |
| S3 | Luật An ninh mạng 116/2025/QH15 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-116-2025-qh15-468678.htm | 17-11-2026 | Hiệu lực 01-07-2026; thay thế Luật 24/2018 và Luật 86/2015 |
| S4 | Nghị định 333/2026/NĐ-CP | Chính phủ / [VBPL] | https://vbpl.vn/van-ban/chi-tiet/nghi-dinh-so-333-2026-nd-cp-quy-dinh-chi-tiet-mot-so-dieu-va-bien-phap-thi-hanh-luat-an-ninh-mang--05c85440-c526-11f1-bca7-ed1ef8223cce | 17-11-2026 | Hướng dẫn Luật 116/2025; data localization |
| S5 | Luật Đo đạc và bản đồ 27/2018/QH14, hợp nhất 70/VBHN-VPQH 2026 | Văn phòng Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/van-ban-hop-nhat-so-70-vbhn-vpqh-469200/63756.htm | 17-11-2026 | Hợp nhất 24-03-2026 |
| S6 | Nghị định 27/2019/NĐ-CP, hợp nhất 22/VBHN-BNNMT 2026 | Bộ Nông nghiệp và Môi trường / [Công báo] | https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/3/22-vbhn-bnnmt.pdf | 17-11-2026 | Quy định chi tiết Luật Đo đạc và bản đồ |
| S7 | Nghị định 39/2026/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-39-2026-nd-cp-468874/62265.htm | 17-11-2026 | Sửa đổi Nghị định 27/2019; hiệu lực 25-01-2026 |
| S8 | Nghị định 174/2026/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-174-2026-nd-cp-469616.htm | 17-11-2026 | Xử phạt ICT; Điều 93 khoản 3 điểm a; hiệu lực 01-07-2026 |
| S8b | Nghị định 174/2026/NĐ-CP — Điều 93 (bản trích) | [Tham khảo] | https://luatvietnam.vn/tin-van-ban-moi/su-dung-ban-do-viet-nam-the-hien-sai-chu-quyen-quoc-gia-co-the-bi-phat-den-40-trieu-dong-tu-01-7-2026-186-109134-article.html | 17-11-2026 | Dùng để đối chiếu nội dung Điều 93; nên đọc bản gốc |
| S9 | Nghị định 18/2020/NĐ-CP, hợp nhất 72/VBHN-BNNMT 2026 | Bộ Nông nghiệp và Môi trường / [Công báo] | https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/7/72-vbhn-bnnmt.signed.pdf | 17-11-2026 | Điều 11: xử phạt lưu hành bản đồ sai chủ quyền |
| S10 | Nghị định 147/2024/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-147-2024-nd-cp-43155/52693.htm | 17-11-2026 | Hiệu lực 25-12-2024; định nghĩa mạng xã hội, ngưỡng cấp phép |
| S11 | Luật Bảo vệ quyền lợi người tiêu dùng 19/2023/QH15 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-19-2023-qh15-39843.htm | 17-11-2026 | Hiệu lực 01-07-2024 |
| S12 | Nghị định 55/2024/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-55-2024-nd-cp-41942.htm | 17-11-2026 | Hướng dẫn Luật 19/2023; Điều 22 đổi/trả |
| S13 | Luật sửa đổi, bổ sung Luật Quảng cáo 75/2025/QH15 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-75-2025-qh15-45566.htm | 17-11-2026 | Hiệu lực 01-01-2026; Điều 23 quảng cáo trên mạng |
| S14 | Nghị định 342/2025/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-342-2025-nd-cp-468584.htm | 17-11-2026 | Hướng dẫn Luật Quảng cáo; hiệu lực 15-02-2026 |
| S15 | Nghị định 87/2026/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-87-2026-nd-cp-469359.htm | 17-11-2026 | Xử phạt văn hóa/quảng cáo; thay thế 38/2021; hiệu lực 15-05-2026 |
| S16 | Thông tư 12/2026/TT-BVHTTDL | Bộ VHTTDL / [VBPL] | https://vbpl.vn/en/van-ban/chi-tiet/thong-tu-12-2026-tt-bvhttdl-quy-dinh-chi-tiet-mot-so-dieu-cua-luat-quang-cao-va-nghi-dinh-quy-dinh-chi-tiet-mot-so-dieu-cua-luat-quang-cao--c0d77ed0-62ef-11f1-9a9a-f1c60172dcd6 | 17-11-2026 | Hiệu lực 05-07-2026; tài liệu chứng minh claim |
| S17 | Luật Giao dịch điện tử 20/2023/QH15 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-20-2023-qh15-39848.htm | 17-11-2026 | Hiệu lực 01-07-2024 |
| S18 | Luật Thương mại điện tử 122/2025/QH15 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-122-2025-qh15-468683/61714.htm | 17-11-2026 | Hiệu lực 01-07-2026; thay khung 52/2013; Điều 3 phân 4 loại nền tảng |
| S19 | Nghị định 248/2026/NĐ-CP về TMĐT | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-248-2026-nd-cp-469983.htm | 17-11-2026 | Hiệu lực 01-07-2026; Điều 52 khoản 3 bãi bỏ 52/2013 và 85/2021; Điều 23–24 thủ tục thông báo |
| S20 | Nghị định 117/2025/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-117-2025-nd-cp-45045.htm | 17-11-2026 | Quản lý thuế TMĐT; hiệu lực 01-07-2025 |
| S21 | Nghị định 254/2026/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-254-2026-nd-cp-469957.htm | 17-11-2026 | Hoá đơn/chứng từ điện tử; thay 123/2020, 70/2025; hiệu lực 01-07-2026 |
| S22 | Luật Kế toán 88/2015/QH13 | Quốc hội / [VBPL] | https://vbpl.vn/van-ban/chi-tiet/luat-ke-toan-so-88-2015-qh13--95924 | 17-11-2026 | Điều 41: lưu trữ 5/10 năm |
| S23 | Luật Người khuyết tật 51/2010/QH12 | Quốc hội / [Công báo] | https://congbao.chinhphu.vn/van-ban/luat-so-51-2010-qh12-3086.htm | 17-11-2026 | Điều 4, 43: quyền tiếp cận CNTT |
| S24 | Thông tư 26/2020/TT-BTTTT | Bộ TT&TT / [Công báo] | https://congbao.chinhphu.vn/van-ban/thong-tu-so-26-2020-tt-btttt-32175.htm | 17-11-2026 | WCAG 1.0 bắt buộc với cơ quan nhà nước/báo chí; khuyến khích tổ chức khác |
| S25 | Nghị định 17/2023/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-17-2023-nd-cp-39279.htm | 17-11-2026 | Quyền tác giả; trích dẫn hợp lý |
| S26 | Apple App Store Review Guidelines | Apple / [App Store] | https://developer.apple.com/app-store/review/guidelines/ | 17-11-2026 | 5.1.1, 1.2, 3.0 |
| S27 | Google Play Data safety | Google / [App Store] | https://support.google.com/googleplay/android-developer/answer/10787469?hl=en | 17-11-2026 | Khai báo dữ liệu/SDK |
| S28 | Google Play account deletion | Google / [App Store] | https://support.google.com/googleplay/android-developer/answer/13327111?hl=en | 17-11-2026 | Xoá tài khoản trong app + web |
| S29 | GDPR | EU / [Quốc tế] | https://eur-lex.europa.eu/eli/reg/2016/679/oj | 17-11-2026 | Điều 3, 6, 12–22, 27, 33, 44 |
| S30 | ePrivacy Directive 2002/58/EC | EU / [Quốc tế] | https://eur-lex.europa.eu/eli/dir/2002/58/oj | 17-11-2026 | Điều 5(3) cookie |
| S31 | CCPA/CPRA | California AG / [Quốc tế] | https://oag.ca.gov/privacy/ccpa | 17-11-2026 | Quyền và ngưỡng áp dụng |
| S32 | WCAG 2.2 | W3C / [Quốc tế] | https://www.w3.org/TR/WCAG22/ | 17-11-2026 | Chuẩn accessibility |
| S33 | OpenStreetMap copyright | OSM Foundation / [Quốc tế] | https://www.openstreetmap.org/copyright | 17-11-2026 | ODbL, attribution |
| S34 | Open Data Commons ODbL 1.0 | ODC / [Quốc tế] | https://opendatacommons.org/licenses/odbl/ | 17-11-2026 | Share-alike database |
| S35 | Wikimedia Commons licensing | Wikimedia / [Quốc tế] | https://commons.wikimedia.org/wiki/Commons:Licensing | 17-11-2026 | Từng file, CC BY/CC BY-SA |
| S36 | Mapillary terms | Mapillary / [Quốc tế] | https://www.mapillary.com/terms | 17-11-2026 | CC BY-SA 4.0, điều khoản |
| S37 | Google Maps Platform Terms | Google / [Quốc tế] | https://cloud.google.com/maps-platform/terms | 17-11-2026 | Cấm scrape/cache/tái sử dụng |
| S38 | OpenFreeMap Terms | OpenFreeMap / [Quốc tế] | https://openfreemap.org/tos/ | 17-11-2026 | Dịch vụ as-is; attribution |
| S39 | Luật Du lịch 09/2017/QH14 | Quốc hội / [VBPL] | https://vbpl.vn/van-ban/chi-tiet/luat-du-lich-so-09-2017-qh14--129350 | 17-11-2026 | Điều 30–37: lữ hành |
| S40 | Nghị định 52/2024/NĐ-CP | Chính phủ / [Công báo] | https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-52-2024-nd-cp-41938/50259.htm | 17-11-2026 | Thanh toán không dùng tiền mặt; hiệu lực 01-07-2024 |
| S41 | Bộ Công Thương — văn bản hợp nhất quản lý website TMĐT (trước 2026) | Bộ Công Thương / [Bộ/ngành] | http://moit.gov.vn/tin-tuc/bo-cong-thuong-ban-hanh-van-ban-hop-nhat-quan-ly-website-thuong-mai-dien-tu.html | 17-11-2026 | Chỉ dùng tra cứu lịch sử; khung này đã bị thay thế từ 01-07-2026 |
| S42 | Nghị định 146/2025/NĐ-CP (phân cấp TMĐT) | Chính phủ / [Bộ/ngành] | https://xaydungchinhsach.chinhphu.vn/chinh-phu-phan-cap-tham-quyen-linh-vuc-thuong-mai-dien-tu-bao-ve-quyen-loi-nguoi-tieu-dung-119250613083825935.htm | 17-11-2026 | Sửa thủ tục của khung 52/2013; không dùng cho hồ sơ mới sau 01-07-2026 |
| S43 | Bộ VHTTDL — Thông tư 12/2026 | Bộ VHTTDL / [Bộ/ngành] | https://bvhttdl.gov.vn/bo-vhttdl-ban-hanh-thong-tu-quy-dinh-chi-tiet-mot-so-dieu-cua-luat-quang-cao.htm | 17-11-2026 | Tóm tắt Thông tư 12/2026 |
| S44 | Bộ Công an — Luật 91/2025 có hiệu lực | Bộ Công an / [Bộ/ngành] | https://mps.gov.vn/chinh-sach-phap-luat/bai-viet/luat-bao-ve-du-lieu-ca-nhan-chinh-thuc-co-hieu-luc-thi-hanh-tu-ngay-01-01-2026-1767186124 | 17-11-2026 | Tóm tắt quyền/nghĩa vụ |
| S45 | Vụ in áo thiếu Hoàng Sa/Trường Sa | Công an Ninh Bình / [Bộ/ngành] | https://congan.ninhbinh.gov.vn/tieu-huy-6000-ao-phong-in-khong-day-du-ban-do-viet-nam-xu-phat-doanh-nghiep-vi-pham-hon-22-trieu-dong | 17-11-2026 | Ví dụ thực tiễn |
| S46 | EY — Nghị định 333/2026 | EY Việt Nam / [Tham khảo] | https://www.ey.com/vi_vn/technical/tax/tax-and-law-updates/tin-nhanh-phap-ly-nghi-dinh-so-333-2026-nd-cp-quy-dinh-chi-tiet-mot-so-dieu-va-bien-phap-thi-hanh-luat-an-ninh-mang | 17-11-2026 | Tóm tắt data localization; cần đối chiếu bản gốc |
| S47 | Google Play Developer Program Policies | Google / [App Store] | https://play.google.com/about/developer-content-policy/ | 17-11-2026 | UGC, Families, quyền riêng tư |

---

## 8. Những điểm chưa kiểm chứng / cần xác nhận thêm

- **Ứng dụng cụ thể** của Luật 27/2018, Nghị định 18/2020 và Nghị định 174/2026 cho **bản đồ tương tác** của GoWander: chưa có văn bản hướng dẫn riêng hoặc án lệ; cần luật sư/cơ quan chuyên môn.
- **Yêu cầu thể hiện Hoàng Sa/Trường Sa**: điều luật không nêu đích danh; thực tiễn xử phạt coi thiếu hai quần đảo là vi phạm. Mức độ áp dụng cho bản đồ số/zoom nhỏ **chưa kiểm chứng**.
- **Nghị định 39/2026/NĐ-CP**: mới đọc phần sửa đổi thủ tục cấp phép/chứng chỉ; **chưa đọc toàn văn** để khẳng định không thay đổi nội dung bản đồ.
- **Nghị định 333/2026/NĐ-CP**: mới đọc trích lục Điều 19–20 và bản tin EY; **chưa đọc toàn văn** về phạm vi “dịch vụ gia tăng trên không gian mạng”, thời hạn log 12 tháng, và thủ tục.
- **Nghị định 147/2024/NĐ-CP**: việc review/rating có làm GoWander thành “mạng xã hội” hay không **chưa có kết luận**; cần luật sư.
- **Mô hình TMĐT của GoWander**: đã xác định khung mới (Luật 122/2025 + Nghị định 248/2026) thay 52/2013 từ 01-07-2026; còn **chưa kiểm chứng** việc affiliate/booking của GoWander có bị coi là “nền tảng TMĐT” và có phải thông báo/đăng ký hay không.
- **Mức ký quỹ lữ hành quốc tế** và thủ tục cấp phép hiện hành: **chưa kiểm chứng** tại 17-11-2026.
- **Nghĩa vụ accessibility cho app tư nhân**: chưa có quy định rõ; cần xác nhận nếu GoWander hợp tác công.
- **GDPR/CCPA**: chưa đánh giá cụ thể việc GoWander “hướng tới” EU/US; cần đánh giá thị trường.
- **Thuế/hoá đơn**: ngưỡng 1 tỷ đồng và diễn giải hộ kinh doanh cần kế toán xác nhận; chưa đọc toàn văn Nghị định 254/2026.
- **Cookie**: chưa có luật cookie riêng; kết luận dựa trên quy định dữ liệu cá nhân và GDPR/ePrivacy.
- **Mapillary/Wikimedia**: giấy phép từng ảnh có thể khác; cần kiểm tra từng file.
- **Thực tiễn xử phạt** có thể thay đổi; các mức phạt nêu là khung pháp lý, không phải dự đoán.

---

## 9. Kết luận ngắn

GoWander có ba rủi ro pháp lý lớn nhất, theo thứ tự:

1. **Bản đồ/chủ quyền**: nếu bản đồ hiển thị không đầy đủ hoặc sai chủ quyền, rủi ro phạt và gỡ app là trực tiếp; không thể xử lý bằng attribution hay disclaimer.
2. **Dữ liệu vị trí**: là dữ liệu nhạy cảm, kéo theo consent riêng, DPIA, DPO, bảo mật, sự cố 72h, data localization, cross-border.
3. **Mạng xã hội/TMĐT khi mở cộng đồng hoặc bán vé**: Nghị định 147/2024 và Luật TMĐT 122/2025 + Nghị định 248/2026 có thể biến tính năng nhỏ thành nghĩa vụ giấy phép/thông báo.

Cách an toàn nhất là **tách giai đoạn**: pilot không tài khoản/không review/không bán; mở tài khoản sau khi hoàn tất P1; mở cộng đồng sau khi có kết luận Nghị định 147; mở bán vé sau khi có kết luận TMĐT/lữ hành/thanh toán/thuế. Mọi thay đổi lớn phải qua cổng “legal review” như một phần của pipeline, không chỉ là kiểm tra cuối.

> **Nhắc lại:** tài liệu này là checklist nghiên cứu, không phải ý kiến pháp lý. Trước khi phát hành, hãy để luật sư Việt Nam rà soát cùng đội sản phẩm/kỹ thuật, đặc biệt với bản đồ, dữ liệu vị trí, cộng đồng và thanh toán.
