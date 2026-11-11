# GoWander — Bàn giao Journey Memory và cá nhân hoá hành trình

Ngày: 11-11-2026 · Đối tượng: chat/agent tiếp theo và chủ dự án.

**Đọc file này trước khi tiếp tục thiết kế Journey.** Đây là bản bàn giao tự đủ ngữ cảnh về hướng sản phẩm đang thảo luận, không phải bằng chứng tính năng đã triển khai. Không cần có lịch sử hội thoại để hiểu ý chính.

## 1. Điều chủ dự án vừa làm rõ

> “Cái memory cũng chính là cái tôi muốn để làm cá nhân hoá người dùng mỗi chuyến đi hành trình của họ.”

**Ý định được xác nhận:** Memory phục vụ cá nhân hoá trải nghiệm của từng người theo từng chuyến đi/hành trình. Không thu hẹp Memory thành trang nhật ký hoặc công cụ làm ảnh kỷ niệm.

**Diễn giải đề xuất của agent:** Memory có thể vừa giữ trải nghiệm của chuyến hiện tại, vừa cung cấp tín hiệu do người dùng kiểm soát để gợi ý chuyến sau phù hợp hơn. Việc học xuyên chuyến, cơ chế đồng ý, dữ liệu được dùng và cách xếp hạng **chưa được chủ dự án chốt chi tiết**.

**Phân biệt:** chủ dự án đã yêu cầu tổng hợp tài liệu. Điều đó không có nghĩa đã duyệt mọi cơ chế cá nhân hoá, thuật toán, schema hay scope phát hành bên dưới.

## 2. GoWander là gì và đang ở đâu?

GoWander là ứng dụng Android/iOS kết hợp Mobility và Discover tại TP.HCM: tìm tuyến giao thông công cộng/đi bộ, khám phá địa điểm, món ăn và câu chuyện văn hoá. Hai điểm vào: biết điểm đến thì tìm đường; chưa biết đi đâu thì khám phá ý tưởng.

- Chủ dự án làm solo; Admin chỉ 1–2 người. Theo ADR-022: một giao diện, Owner/Editor; Việt–Anh trước, Trung–Hàn sau; mật khẩu và Google song song, quên mật khẩu qua OTP email.
- Stack định hướng: Kotlin/Compose Multiplatform, MapLibre, Go modular monolith, PostgreSQL/PostGIS, OTP qua Go API.
- Theo PIPELINE tại lần bàn giao: M01 vẫn BLOCKED về dữ liệu transit chính thức/quyền sử dụng; ADR-020 cho phép feed Metro L1 giả định phục vụ PoC, không phải lịch chính thức. M02 OTP và M03 map chưa có gate PASS được chứng minh trong trao đổi này.
- Định hướng Journey không làm thay đổi gate kỹ thuật/dữ liệu. Prototype có nhãn mẫu không chứng minh routing, cá nhân hoá hay lưu trữ đã chạy.

## 3. Mô hình sản phẩm thống nhất

**GoWander Journey** là trải nghiệm xuyên suốt được đề xuất:

**Discover → Plan → Explore → Remember → Personalize next journey**

Chia sẻ là nhánh tùy chọn, không phải bước bắt buộc để hoàn thành hành trình.

| Khái niệm | Ý nghĩa đề xuất | Không được đánh đồng với |
|---|---|---|
| Journey | Một chuyến cụ thể, có mục đích, bối cảnh, kế hoạch và trạng thái | Mọi hoạt động của người dùng trong ứng dụng |
| Journey Memory | Hồ sơ trải nghiệm cá nhân gắn chuyến: lựa chọn, điều đã xác nhận, ảnh/ghi chú và phản hồi | Chỉ một album ảnh hoặc lịch sử GPS |
| Personalization | Sử dụng thông tin phù hợp, được phép, để điều chỉnh trải nghiệm | Tự suy đoán mọi sở thích từ mọi hành động |
| Storybook | Cách trình bày/xuất một phần Journey Memory thành câu chuyện | Toàn bộ dữ liệu Memory hoặc dữ liệu học bắt buộc |
| Community | Nơi người dùng chủ động công bố nội dung đã chọn | Kho Memory riêng tư |

Ba chức năng hỗ trợ Journey: Explore Along My Route giúp chọn điểm ghé thuận đường; Story Mode giúp hiểu địa điểm; Memory giữ trải nghiệm và hỗ trợ cá nhân hoá. Explore by Time là một điểm vào; Budget/audio/tối ưu nhiều điểm để sau khi đủ dữ liệu và bằng chứng nhu cầu.

## 4. Hai tầng cá nhân hoá cần tách riêng

### A. Theo chuyến hiện tại

Tôn trọng điều kiện người dùng chọn **lần này**: thời gian rảnh, điểm kết thúc/quay về, mức đi bộ, chủ đề muốn khám phá, nhịp chuyến đi và nhu cầu của nhóm đi cùng nếu họ chủ động cung cấp.

Ví dụ: một người thường thích đi bộ nhưng hôm nay chỉ có 45 phút và muốn ít đi bộ. Lựa chọn hôm nay phải ưu tiên hơn sở thích lịch sử. Khi họ đổi kế hoạch, giữ bản nháp và cập nhật gợi ý; không cố ép hoàn thành tuyến cũ.

### B. Giữa các chuyến — đề xuất cần chốt

Nhớ các tín hiệu rõ ràng như nơi muốn đến, nơi muốn quay lại, “gợi ý thêm kiểu này”, “ít gợi ý kiểu này”. Dùng để giảm việc chọn lại và đề xuất trải nghiệm mới phù hợp.

Không mặc định “đã ghé” = “thích”, “đã lưu” = “đã đi”, “bỏ qua” = “ghét”. Bỏ một điểm có thể do mưa, giờ đóng cửa, thiếu thời gian hoặc nhu cầu của người đi cùng. Không suy ra sở thích nhạy cảm từ nơi đã đến.

## 5. Nội dung Memory đề xuất

| Lớp | Nội dung có thể lưu | Cách dùng / giới hạn |
|---|---|---|
| Dự định | Điểm dự kiến, route đã chọn, thời gian, sở thích riêng chuyến | Giúp tiếp tục kế hoạch; không chứng minh đã đi |
| Trải nghiệm xác nhận | Điểm người dùng tự đánh dấu đã ghé, bỏ/đổi điểm, kết thúc sớm | Phản ánh khai báo của người dùng, không xác nhận GPS |
| Kỷ niệm | Ảnh được chọn, ghi chú, cảm nhận, khoảnh khắc | Riêng tư; không tự đọc nội dung để suy sở thích ở bản đầu |
| Phản hồi cá nhân hoá | Muốn quay lại, muốn gợi ý tương tự, ít gợi ý hơn | Tín hiệu rõ nghĩa; xem/sửa/xoá được |
| Kiến thức liên quan | Tham chiếu Historical Story có nguồn và version | Tách khỏi lời kể/cảm nhận cá nhân |

Không yêu cầu người dùng điền đủ các lớp. Một Journey dở dang, không ảnh hoặc không ghi chú vẫn có thể được lưu. Không dùng “hoàn thành” để ép xác nhận đã ghé các điểm chưa đi.

## 6. Ví dụ trải nghiệm — không phải kết quả routing thực

Người dùng nói: “Mình ở Bến Thành, có 90 phút, thích kiến trúc và muốn đi ít.”

1. Chọn thời gian, mức đi bộ và có cần quay về hay không; không bắt trả lời bảng khảo sát dài.
2. Xem một vài Journey biên tập có dữ liệu phù hợp; thời gian chưa kiểm chứng phải ghi rõ.
3. Chọn kế hoạch, đọc câu chuyện tại điểm; bỏ một điểm nếu không muốn tiếp tục.
4. Sau chuyến, tự chọn ảnh/ghi chú và xác nhận điểm đã ghé. Có thể lưu ngay mà không viết gì.
5. Nếu muốn, phản hồi “Muốn quay lại” hoặc “Gợi ý thêm kiến trúc”; không bắt đánh giá để được lưu.
6. Lần sau, ứng dụng có thể giải thích: “Gợi ý vì bạn đã chọn chủ đề kiến trúc”; có nút đổi sở thích và lựa chọn khám phá khác.
7. Storybook trình bày nội dung người dùng chọn. Lưu riêng tư và xuất/chia sẻ là hai hành động riêng.

Các bước 5–6 mô tả hướng đề xuất, chưa chốt quy tắc học xuyên chuyến.

## 7. Bản đầu phù hợp người phát triển solo

**Đề xuất rule-based trước**, chưa cần ML hoặc chatbot:

- Lọc cứng theo vùng dữ liệu, khả năng tiếp cận đã xác minh, thời gian và điều kiện chuyến hiện tại khi có dữ liệu.
- Xếp gợi ý theo chủ đề chủ động chọn và phản hồi rõ ràng; chưa chốt trọng số hay công thức.
- Giữ sự đa dạng, không chỉ lặp nơi đã thích. Nơi đã đến vẫn có thể xuất hiện nếu người dùng muốn quay lại.
- Hiển thị lý do gợi ý và cho thay đổi; không viết lý do giả khi thiếu tín hiệu.
- Một Journey có 2–3 điểm, thẻ story ngắn, một mẫu Memory/Storybook là đủ cho prototype.
- Thử nháp trên máy trước; đồng bộ nhiều thiết bị và lựa chọn storage chưa chốt. Phải nói rõ khả năng mất nháp nếu gỡ app.

Chưa làm: tracking nền liên tục, tự quét cả thư viện ảnh, phân tích ghi chú riêng bằng AI mặc định, suy luận tâm trạng, mạng xã hội lớn, tối ưu ngân sách/đa điểm khi dữ liệu thiếu.

## 8. Quyền riêng tư và khả năng kiểm soát — đề xuất thiết kế

1. Memory mặc định private; không tự đăng Community hoặc gửi nội dung cá nhân vào analytics.
2. Phân biệt **lưu kỷ niệm** với **dùng thông tin để gợi ý**. Đề xuất cho tắt học xuyên chuyến mà vẫn dùng nhật ký và chọn sở thích thủ công.
3. Có nơi xem/sửa/xoá sở thích; không chỉ nút xoá Journey trong khi dữ liệu suy ra vẫn tồn tại mà người dùng không biết.
4. Xoá Journey cần quy định ảnh hưởng đến các tín hiệu phát sinh từ Journey đó, bản chia sẻ và bản xuất. Chưa có retention policy được chốt.
5. Preview trước chia sẻ: bỏ ảnh/ghi chú, vị trí hoặc mốc thời gian nhạy cảm; kiểm soát metadata ảnh.
6. Thu hồi link không thu hồi file ảnh/PDF người khác đã tải. Không hứa ngược lại.
7. Không coi Planned Route là hành trình thực; Visited là tự khai báo. Địa điểm tôn giáo/y tế hoặc nơi nhạy cảm không tự trở thành nhãn sở thích.

## 9. Kiến trúc và schema: chưa triển khai

Giữ nền KMP + Go modular monolith + PostGIS + OTP. Có thể đặt Journey trong miền exploration hiện có; chưa cần service riêng.

Khái niệm để thảo luận: Journey → Stops → Memories, cùng phản hồi cá nhân hoá và tham chiếu nội dung. Tái sử dụng places, historical_stories, exploration_entries và media khi phù hợp. Storybook ban đầu là cách render, không mặc định phải có bảng riêng.

Phải xác định trước khi migration: chủ sở hữu, ID ổn định, planned/visited, bản nháp local/cloud, sửa/xoá, version story, tín hiệu dùng cá nhân hoá và quan hệ không trùng dữ liệu. Không tự thêm bảng chỉ vì file bàn giao nhắc đến khái niệm.

## 10. Điều cần kiểm chứng và câu hỏi còn mở

- Người dùng muốn lưu trải nghiệm đến mức nào, sẵn sàng thao tác bao nhiêu, có quay lại mở Memory không?
- Memory cá nhân hoá chủ yếu bố cục/câu chuyện của từng chuyến, gợi ý chuyến sau, hay cả hai? Ý định cá nhân hoá đã rõ, tỷ trọng hai mục đích chưa chốt.
- Những tín hiệu nào được lưu lâu dài; cách đồng ý dùng dữ liệu và reset sở thích?
- Nháp local có thể dùng khi guest đến đâu; khi đăng nhập có đồng bộ/ghép bản nháp không?
- Có chia sẻ Storybook ở bản đầu hay chỉ lưu riêng? Định dạng xuất nào được ưu tiên?
- Một người có thể có nhiều Journey đang dở? Chuyến nhóm có thuộc scope không? Mặc định đề xuất chưa làm cộng tác nhóm.
- Go/no-go của prototype cần dựa vào tác vụ: tạo Journey, đổi kế hoạch, lưu Memory, hiểu dữ liệu cá nhân hoá, xoá/tắt và phân biệt private/public. Không dùng lời khen giao diện làm bằng chứng nhu cầu.

## 11. Chỉ dẫn cho agent tiếp theo

- Tóm tắt lại ý định: **Memory phục vụ cá nhân hoá hành trình; Storybook là một đầu ra**.
- Giữ rõ phần chủ dự án xác nhận và phần agent đề xuất; không đổi toàn bộ sang Accepted.
- Đọc nguồn bên dưới nếu thay kiến trúc hoặc phạm vi. Giữ M01/M02/M03 gate và ADR-022; không suy ra task này cho phép code/deploy.
- Nếu người dùng yêu cầu prototype: đề xuất luồng nhỏ gồm sở thích chuyến → kế hoạch → trải nghiệm → Memory → kiểm soát gợi ý; plugin mới riêng ngoài repo, không ghi đè bản cũ.
- Nếu yêu cầu triển khai: làm rõ nghiệp vụ còn mở và thứ tự milestone trước; chỉ tạo migration cần cho vertical slice được duyệt.
- Mọi phân tích/sửa repo có docs và change record. Agent không chạy Git ghi lịch sử hoặc remote; chủ dự án tự commit.

## 12. Nguồn nội bộ

- [Đề xuất GoWander Journey](../research/gowander-journey-product-proposal.md): đối thủ, ưu tiên, detour, Story Mode và kiến trúc đề xuất. Đọc kèm bản bàn giao này để cập nhật cách hiểu Memory.
- [Nghiên cứu UX, ngôn ngữ và Admin solo](../research/ux-wireframe-language-solo-admin.md).
- [ADR-022 — auth, ngôn ngữ và Admin](../decisions/ADR-022-auth-languages-admin-scope.md).
- [ADR-021 — thời tiết](../decisions/ADR-021-weather.md).
- [PIPELINE](../../PIPELINE.md), [OVERVIEW](../../OVERVIEW.md), [AGENTS](../../AGENTS.md).

File này không cập nhật tính năng đối thủ hoặc dữ liệu thị trường; các nguồn web đã nghiên cứu nằm trong báo cáo Journey. Chưa thay code, schema, plugin hoặc trạng thái milestone.
