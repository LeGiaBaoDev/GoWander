# GoWander — Bản căn cứ cập nhật bộ tài liệu dự án

**Ngày chốt:** 16-11-2026, Asia/Ho_Chi_Minh.  
**Mục đích:** dùng để biên tập lại 10 tab Google Docs: Overview, Strategy & Research, Product & UX, Technical, Database Design & Data Modeling, Development & Implementation, Intellectual Property, Legal & Compliance, Business & Finance, Launch & Growth.  
**Trạng thái đề xuất cho trang bìa:** “Đang phát triển MVP — đã có bản chạy thử và bằng chứng PoC kỹ thuật; đang hoàn thiện Admin, dữ liệu và điều kiện vận hành; chưa đủ điều kiện production pilot.”

## 0. Cách dùng và giới hạn của bản tổng hợp

Đây là bản rà soát xuyên suốt các thành phần trong repository, gồm cấu trúc dự án, thiết kế, ADR, pipeline, nhật ký, mã nguồn trọng tâm, migration, cấu hình và hồ sơ kiểm thử. Đây **không phải** kiểm toán từng dòng code, kiểm toán bảo mật hoặc báo cáo chạy lại toàn bộ hệ thống.

Sáu tài liệu gốc có bản Markdown trong `docs/design/` (46 file). Bốn tab Intellectual Property, Legal & Compliance, Business & Finance và Launch & Growth xuất hiện trong ảnh nhưng chưa được đọc trực tiếp từ Google Docs. Các mục tương ứng dưới đây là nội dung đề xuất dựa trên repo, không phải kết luận về toàn bộ nội dung đang có trong bốn tab đó. Không sửa Google Docs trong đợt này.

Snapshot tham chiếu: nhánh `feat/admin-rbac`, commit HEAD lúc đọc là `d414943`. Working tree không sạch: có thay đổi upload ảnh và cấu hình iOS do công việc khác đang thực hiện. Không đánh đồng nội dung working tree với bản đã commit hoặc bản đang chạy.

Quy ước dùng trong tài liệu:

| Nhãn | Ý nghĩa |
|---|---|
| Có triển khai | Đã tìm thấy mã nguồn hoặc cấu hình thực hiện chức năng; không tự suy ra đã kiểm thử trên thiết bị thật |
| Có bằng chứng trước đây | Repo lưu kết quả kiểm thử có ngày và phạm vi; đợt tổng hợp này không chạy lại |
| Đang làm | Có thay đổi chưa hoàn tất hoặc milestone chưa qua gate |
| Mẫu / giả định | Phục vụ UI hoặc PoC; không thể dùng làm bằng chứng người dùng, doanh thu hay lịch vận hành thật |
| Định hướng | Quyết định hoặc kế hoạch; chưa phải tính năng đã phát hành |
| Chưa kiểm chứng | Chưa đủ bằng chứng để xác nhận tại snapshot này |

Khi các nguồn mâu thuẫn: đối chiếu quyết định mới nhất của chủ dự án, code/migration hiện tại và bằng chứng có ngày. Giữ lại lịch sử quyết định, nhưng viết phần hiện trạng theo trạng thái mới nhất đã xác minh. Không sao chép nguyên nhãn PASS mà bỏ mất điều kiện “technical only”, “assumed” hoặc “emulator/simulator”.

## 1. Overview — Nội dung đề xuất thay trang tổng quan

### 1.1. Giới thiệu

GoWander là ứng dụng Android/iOS kết hợp tìm phương án di chuyển và khám phá địa điểm, hỗ trợ người dùng xây dựng hành trình ở một thành phố chưa quen thuộc. Sản phẩm bắt đầu với TP.HCM; giao diện khám phá có định hướng mở rộng theo vùng của Việt Nam, nhưng phạm vi dữ liệu và định tuyến chưa tương đương một sản phẩm phủ toàn quốc.

Tagline: **Move Smart. Explore More.**

Hai điểm vào chính là tìm đường tới một điểm đến đã chọn và khám phá địa điểm trước khi quyết định di chuyển. Vòng trải nghiệm dài hạn là Discover → Plan → Navigate → Experience → Remember → Share. Các bước chia sẻ và cộng đồng chưa được triển khai đầy đủ.

### 1.2. Hiện trạng

Dự án đã có mobile Kotlin Multiplatform/Compose, backend Go, PostgreSQL/PostGIS, OpenTripPlanner và Admin Web. Repository lưu bằng chứng các luồng kỹ thuật Android/iOS trên emulator/simulator. Dự án đang hoàn thiện M07 Admin và vẫn có dữ liệu mẫu, nội dung cần biên tập, tính năng cần kiểm tra trên thiết bị thật.

Chưa có đủ căn cứ để công bố production pilot. Dữ liệu transit chính thức và quyền sử dụng phù hợp chưa giải quyết xong; backup/PITR, quan sát vận hành và các gate phát hành vẫn cần hoàn tất.

### 1.3. Phạm vi và ranh giới

- Core: bản đồ, tìm địa điểm/địa chỉ, tìm và so sánh tuyến, chi tiết tuyến, Discover, nội dung cẩm nang, kế hoạch và địa điểm lưu trên thiết bị, Admin vận hành nội dung.
- Có triển khai mở rộng kỹ thuật cho bus, ô tô và xe đạp; điều đó không đồng nghĩa đã xác nhận chất lượng định tuyến production cho mọi phương tiện.
- Sau: tài khoản người dùng và đồng bộ, cộng đồng, đánh giá thật, phần thưởng, đối tác và thương mại.
- Chưa cam kết: realtime ETA, giá vé đầy đủ, chỉ dẫn xe máy chuyên biệt, dữ liệu toàn quốc, dẫn đường có độ tin cậy tương đương sản phẩm thương mại.

**Nguồn:** `OVERVIEW.md`, `PIPELINE.md`, ADR-020, ADR-029, ADR-032, ADR-034.

## 2. Strategy & Research — Giả thuyết và dữ liệu cần chứng minh

### 2.1. Định vị

Giá trị cần kiểm chứng là khả năng nối liền “tìm nơi đáng đi” với “biết cách tới đó” và “lưu thành hành trình cá nhân”. Nhóm người dùng mục tiêu là khách du lịch và người khám phá thành phố; persona trong tài liệu thiết kế là giả thuyết, chưa phải kết quả khảo sát riêng của GoWander.

Không lấy số liệu ngành du lịch, lượt tìm đường do chủ dự án tạo hoặc các con số mẫu trên Admin làm bằng chứng về sức hút thị trường.

### 2.2. Kết quả nghiên cứu nội bộ đã có

| Nhóm | Điều repo ghi nhận | Giới hạn cần giữ |
|---|---|---|
| Transit | Có nghiên cứu nguồn GTFS/OSM, feed metro giả định và bus dựng từ OSM | Chưa có nguồn GTFS chính thức đáp ứng gate M01 |
| Routing | OTP có kết quả PoC, các case và số đo trên máy phát triển | Không phải benchmark production hoặc kiểm chứng thời gian đi thật |
| Địa điểm | Pipeline OSM và dữ liệu biên tập; M05 ghi nhận 1.016 địa điểm quanh hành lang Metro L1 | Là snapshot lịch sử, không phải số đếm database hiện tại; có tên/toạ độ không đồng nghĩa chất lượng nội dung đã đạt |
| Địa chỉ | Photon tự chạy với index Việt Nam | Độ đầy đủ số nhà và độ mới của index có giới hạn |
| UX | Có thiết kế, code và hồ sơ kiểm thử kỹ thuật | Chưa đủ căn cứ kết luận đã usability-test với nhóm người dùng đại diện |
| Kinh doanh | B2B2C, quảng bá địa điểm và affiliate là hướng nghiên cứu | Chưa xác nhận đối tác trả tiền hoặc doanh thu thật |

### 2.3. Nghiên cứu tiếp theo

DATA-1 là giai đoạn nghiên cứu dữ liệu riêng sau khi hoàn thiện chức năng/giao diện theo quyết định chủ dự án ngày 16-11. Cần đăng ký nguồn, giấy phép, độ mới, phạm vi, đầu mối và trạng thái nhận dữ liệu cho transit, POI, ảnh, bài viết, ranh giới và các dịch vụ phụ trợ.

Pilot cần kiểm chứng: người dùng có tìm được nơi phù hợp, chọn được tuyến hiểu được, xử lý được mất mạng/không có tuyến, và có tiếp tục dùng kế hoạch đã tạo hay không. Các chỉ số này là kế hoạch đo, chưa phải kết quả nghiên cứu.

**Nguồn:** `docs/research/transit-data-feasibility.md`, `docs/research/data-sources-inventory.md`, `docs/design/02-strategy-research-v7.0/`, `PIPELINE.md` §5b.

## 3. Product & UX — Cấu trúc sản phẩm hiện tại

### 3.1. Điều hướng

Thanh dưới có năm mục: **Bản đồ · Khám phá · Đi đâu? · Hành trình · Hồ sơ**. “Đi đâu?” là hành động mở tìm điểm đến. Không giữ mô tả cũ “tab Tìm tuyến” như cấu trúc chính hiện tại.

- Bản đồ: vị trí hiện tại hoặc điểm chọn thủ công, địa điểm trên bản đồ, sheet địa điểm dùng chung.
- Khám phá: gần bạn và điểm đến Việt Nam; cẩm nang vùng.
- Tìm tuyến: tìm điểm đến, xem phương án, chi tiết, bắt đầu hoặc xem từng bước.
- Hành trình: “Của tôi” và “Gợi ý”; tạo kế hoạch thủ công và theo dõi kế hoạch.
- Hồ sơ: Portfolio và Đã lưu. Kế hoạch đã chuyển sang Hành trình theo sửa đổi ADR-034.

### 3.2. Ma trận chức năng

| Nhóm | Có trong triển khai | Cần ghi đúng giới hạn |
|---|---|---|
| Map | MapLibre, bản đồ 2D/3D, địa điểm, chọn vị trí | Vệ tinh và 3D/AR cao cấp chưa được xác nhận có nhà cung cấp/phát hành |
| Tìm kiếm | POI, ga, Photon geocoding/reverse | Không hứa đầy đủ địa chỉ số nhà hoặc routing toàn quốc |
| Tuyến | Go → OTP, phương án và hình học tuyến | Lịch transit giả định, không phải realtime |
| Dẫn đường | Mã nguồn follow camera, bước chỉ dẫn, tiến trình, TTS, xem từng bước | Phải xác minh GPS/heading/TTS, vòng đời app và lệch tuyến trên thiết bị thật |
| Khám phá | Danh sách/chi tiết/nearby/map/clusters; cẩm nang HCM qua API | Vùng khác, ảnh cộng đồng và một phần nội dung vẫn mẫu |
| Kế hoạch | Tạo/sửa, lưu trên máy, địa điểm đã lưu | Có kế hoạch mẫu lần đầu; chưa đồng bộ tài khoản |
| Hồ sơ | Portfolio và UI chỉnh nội dung | Chưa chứng minh đây là hồ sơ tài khoản thật hoặc mạng xã hội |
| Đánh giá/chia sẻ | Một số UI hoặc nút giải thích | COM-1 chưa hoàn tất; không viết “đã có cộng đồng” |
| Thời tiết | Có ADR và yêu cầu thiết kế | Chưa có adapter thời tiết hoàn tất trong phạm vi đã rà |

### 3.3. Trạng thái dữ liệu và UX

UX-2 được chủ dự án cho đóng ngày 16-11 với các kiểm tra còn thiếu, bỏ P6 và giữ một số nút chỉ giải thích. Đây là quyết định chấp nhận backlog, không phải bằng chứng mọi tiêu chí UX đã PASS. Giữ danh sách phần chưa kiểm trong kế hoạch phát hành.

Ngôn ngữ Việt/Anh là định hướng ban đầu; Trung/Hàn là kế hoạch sau. Cần kiểm độ phủ chuỗi và hành vi đổi ngôn ngữ thực tế trước khi tuyên bố hỗ trợ đầy đủ.

**Nguồn:** `mobile/shared/src/commonMain/kotlin/com/gowander/`, ADR-022, ADR-033, ADR-034, ADR-036, `docs/plans/UX-2-ke-hoach-lam-lai-trai-nghiem.md`, `PIPELINE.md` §5c.

## 4. Technical — Kiến trúc triển khai

### 4.1. Sơ đồ tổng thể

```text
Android / iOS (KMP + Compose)
          │ HTTPS REST/JSON
          ▼
Go API + Admin Web (cùng binary)
    ├── PostgreSQL / PostGIS: nội dung, admin, bộ đếm
    ├── OpenTripPlanner: tính tuyến từ graph GTFS + OSM
    ├── Photon: tìm địa chỉ
    ├── Street imagery: tích hợp có điều kiện theo cấu hình
    └── Media storage: local đang làm; R2 là hướng production

Map tiles là luồng dịch vụ bản đồ riêng của mobile.
```

Admin dùng Go `html/template` + htmx, CSS và JavaScript nhúng trong backend; không phải một SPA React/Vue đang chờ chọn framework. Mobile không gọi trực tiếp database hoặc OTP.

### 4.2. Phiên bản trong manifest tại thời điểm đọc

| Thành phần | Khai báo |
|---|---|
| Go | 1.27.4 |
| Chi / pgx / golang-migrate | 5.5.0 / 5.13.0 / 4.20.1 |
| Kotlin / Compose Multiplatform | 2.4.20 / 1.12.1 |
| Android Gradle Plugin | 9.3.1 |
| Ktor / MapLibre Compose | 3.6.1 / 0.20.0 |
| Android min / target / compile SDK | 24 / 36 / 37 |
| OTP | 2.10.0 theo ADR-023 |
| Photon | 0.7.4 theo runbook |

Đây là phiên bản repo khai báo, không phải xác nhận toolchain hiện đang cài hoặc phiên bản mới nhất trên Internet.

### 4.3. Các nhóm API thực tế

- Health: `GET /health/live`, `GET /health/ready`.
- Routing: `POST /api/v1/routes/plan`.
- Discover: `GET /api/v1/place-categories`, `/api/v1/places`, `/api/v1/places/nearby`, `/api/v1/places/map`, `/api/v1/places/map/clusters`, `/api/v1/places/{id}`.
- Guide: `GET /api/v1/guide/hcm`.
- Geocode: `GET /api/v1/geocode`, `/api/v1/geocode/reverse`.
- Street images: `GET /api/v1/street-images/nearest`.
- Analytics: `POST /api/v1/usage/open`.
- Admin: `/admin/`, đăng nhập/đặt lại mật khẩu, tài khoản, địa điểm, loại địa điểm, món ăn, câu chuyện, báo cáo và audit.

`/health/ready` kiểm OTP và database; không đại diện cho sức khoẻ của mọi dịch vụ, chẳng hạn Photon. Đường dẫn gốc `/` trả NOT_FOUND không có nghĩa server không truy cập được.

### 4.4. Admin và bảo vệ truy cập

ADR-039 thay thế đăng nhập Google của ADR-037 cho Admin: mã tài khoản + mật khẩu argon2id; không có tự đăng ký; tài khoản cấp qua CLI. Sai năm lần khoá 15 phút. Đặt lại mật khẩu qua email bằng link một lần, hạn năm phút, gửi lại tối thiểu sau một phút. Đây là link, không phải màn nhập mã OTP đã được triển khai.

Có session phía server, CSRF, kiểm vai trò ở server, audit chỉ thêm. Trang tài khoản hỗ trợ đổi tên, email khôi phục có kiểm mật khẩu hiện tại và đổi mật khẩu. Đây không phải bằng chứng đăng nhập người dùng mobile đã xong. ADR còn ghi các việc cần hoàn thiện trước mở rộng, bao gồm giới hạn theo IP và cân nhắc 2FA.

### 4.5. Ảnh và lưu trữ

ADR-035 chọn Cloudflare R2 cho production. Snapshot working tree có package `media`, handler upload và migration `0009_photos`, chưa commit. Code xử lý JPEG/PNG/WebP, giới hạn kích thước, tạo JPEG 480/1600 và bỏ metadata bằng tái mã hoá; storage hiện có triển khai local cho dev. Không tuyên bố R2 đã tích hợp hoặc upload đã hoạt động đầu–cuối vì chưa có bằng chứng trong đợt rà soát này.

**Nguồn:** `backend/cmd/api/main.go`, `backend/internal/`, `backend/go.mod`, `mobile/gradle/libs.versions.toml`, `backend/api/openapi/openapi.yaml`, ADR-023/025/026/027/031/035/037/039.

## 5. Database Design & Data Modeling — Tách thiết kế khỏi schema thật

49 bảng trong thiết kế là danh mục dài hạn, không phải số bảng đã triển khai. Schema hiện tại phải được mô tả theo chuỗi migration `up`, kể cả thao tác DROP; không đếm đơn thuần tất cả lệnh CREATE trong cả file up/down.

| Migration | Nội dung |
|---|---|
| 0001 | Bảy bảng Discover: categories, places, links, media, food_items, place_foods, historical_stories |
| 0002 | Nền users, identities, contacts, roles, user_roles, admin_sessions, admin_audit_logs |
| 0003–0004 | Quyền đọc/sửa nội dung Admin và cơ chế giữ chỉnh sửa |
| 0005 | admin_areas và ba bảng bộ đếm route/line/place |
| 0006 | Mở rộng nội dung cẩm nang |
| 0007 | usage_app_opens |
| 0008 | admin_credentials, admin_password_resets; bỏ user_auth_identities |
| 0009 | Metadata/quyền ảnh cho nội dung; đang trong working tree, chưa xác minh đã áp dụng vào DB |

Các bảng cộng đồng, phần thưởng và kinh doanh trong thiết kế chưa được coi là schema đang vận hành. `users` hiện phục vụ nền Admin không chứng minh tài khoản khách dùng app đã triển khai.

Kế hoạch và địa điểm đã lưu của mobile nằm trong một chuỗi JSON `store.v1`: SharedPreferences trên Android, NSUserDefaults trên iOS. `AppStore` có giữ bản không đọc được trước khi quay về mẫu; chưa có đồng bộ server. Không mô tả PostgreSQL là nơi giữ toàn bộ kế hoạch cá nhân hiện tại.

Analytics lưu bộ đếm tổng hợp theo ngày, phương tiện, tỉnh/thành, tuyến được gợi ý và địa điểm được tìm đường tới. Không triển khai bảng sự kiện từng người `usage_events` theo thiết kế cũ. Toạ độ được xử lý phục vụ phân vùng, không được lưu vào các bảng bộ đếm; không nên mở rộng thành tuyên bố rằng mọi log hoặc mọi thành phần hệ thống đều không xử lý dữ liệu vị trí.

Bộ đếm có retention 25 tháng và hàng đợi trong bộ nhớ, có thể mất lượt khi đầy hoặc tắt server. Trang so sánh nhiều năm không tạo ra dữ liệu lịch sử vượt quá thời gian lưu. Cần ghi rõ giới hạn này khi trình bày dashboard.

**Nguồn:** `backend/migrations/`, `backend/internal/analytics/usage.go`, `mobile/shared/src/commonMain/kotlin/com/gowander/home/AppStore.kt`, ADR-028/036/038/039.

## 6. Development & Implementation — Tiến độ và bằng chứng

### 6.1. Bảng trạng thái dùng cho tài liệu

| Hạng mục | Cách ghi tại ngày chốt |
|---|---|
| M00 | Repo/toolchain có bằng chứng hoàn thành trước đây |
| M01 | BLOCKED về dữ liệu transit phù hợp; được phép dùng giả định cho PoC |
| M02 | PoC OTP đạt về công nghệ trên dữ liệu giả định |
| M03 | Có bằng chứng Android emulator và iOS simulator; không tự suy ra thiết bị thật |
| M04 | Có Go routing và bằng chứng case kỹ thuật trên feed giả định |
| M05 | Có PostGIS/Discover và bằng chứng truy vấn/seed |
| M06 | Có bằng chứng hai luồng end-to-end kỹ thuật trên máy ảo |
| M06b | Có UI Design Kit và bằng chứng trước redesign |
| RD-0 | Có feed bus OSM, graph mở rộng, car/bicycle; một số gate/review còn mở |
| NAV-1 | Có code và bằng chứng Android GPS giả lập; kiểm chứng thiết bị thật còn thiếu |
| UX-2 | Đóng theo quyết định chủ dự án 16-11; có backlog kiểm thử và P6 chưa thực hiện |
| M07 | Đang làm; nhiều lát Admin đã có, upload ảnh đang thay đổi |
| DATA-1 | Đã lên kế hoạch, làm sau phần chức năng/giao diện theo quyết định mới |
| M08 | Chưa có bằng chứng backup off-host, WAL/PITR và restore drill hoàn tất |
| M09 | Chưa có bằng chứng gate CI/observability hoàn tất |
| M10 | Chưa đủ điều kiện production pilot |
| COM-1 / rewards / business / nâng cấp | Kế hoạch sau theo phụ thuộc và quyết định phạm vi |

M08 trong bảng pipeline vẫn ghi “BLOCKED on DB setup” dù M05 đã có bằng chứng. Cần rà lại lý do blocker; không tự nâng M08 thành PASS hoặc tự kết luận đã triển khai backup.

### 6.2. Quy trình phát triển cần giữ

- `develop` là nhánh làm việc chung; `main` nhận bản phát hành do chủ dự án quyết định.
- Chủ dự án thực hiện mọi thao tác Git ghi lịch sử/remote. Agent chỉ đọc và đưa lệnh bàn giao.
- Mọi thay đổi repo có change record tiếng Việt theo ngày.
- Kiểm thử phải ghi đúng lệnh, kết quả, ngày, môi trường và giới hạn.
- UX-2 được đóng theo chấp nhận backlog không xoá nghĩa vụ kiểm tra trước production.

### 6.3. Những gì đã kiểm trong đợt tổng hợp này

Đã đọc/đối chiếu cấu trúc bằng `rg --files`, xem `git status --short`, HEAD/nhánh, tìm route và migration bằng `rg`, đọc code trọng tâm và các tài liệu liên quan. Không chạy Go test, Gradle, Xcode, migration, truy vấn DB hoặc deploy trong đợt này. Kết quả test cũ được dẫn về hồ sơ nguồn, không gắn nhãn là vừa chạy lại.

Hồ sơ cần tra khi biên tập: `docs/evidence/m03/`, `docs/evidence/m06/`, `docs/evidence/nav1/`, `docs/evidence/ux/`, `docs/evidence/ux2/`, `backend/tests/results/`, `infrastructure/otp/results/`, `docs/changes/`.

## 7. Intellectual Property — Nội dung cần bổ sung vào hồ sơ sở hữu

Phần này là danh mục việc cần xác minh, không phải xác nhận pháp lý về quyền sở hữu hoặc khả năng đăng ký bảo hộ.

| Tài sản | Cần ghi trong hồ sơ |
|---|---|
| Tên GoWander, slogan, logo | Người tạo, file gốc, điều kiện chuyển giao nếu thuê ngoài, kết quả tra cứu/đăng ký nếu có |
| Mã nguồn | Lịch sử đóng góp, giấy phép dự án, quyền sử dụng thư viện, chính sách đóng góp |
| UI, icon, font | Nguồn Design Kit/Figma, điều khoản dùng icon/asset, licence Be Vietnam Pro |
| OSM và dữ liệu dẫn xuất | Attribution, giấy phép nguồn và nghĩa vụ áp dụng cho cách sử dụng/phân phối thực tế |
| Bài viết và ảnh | Tác giả, nguồn, quyền tái sử dụng, giấy phép, phạm vi cho phép sửa/cắt/phát hành |
| Ảnh cộng đồng tương lai | Quyền người tải lên, điều khoản cấp phép cho GoWander, cơ chế gỡ nội dung |

Có `docs/licenses/BeVietnamPro-OFL.txt` và `docs/photo-credits.md` để đối chiếu. Việc có nguồn/URL không tự chứng minh quyền sao chép; việc dùng thư viện mã nguồn mở cũng không có nghĩa mọi asset kèm theo dùng cùng giấy phép. Chưa xác minh tình trạng đăng ký nhãn hiệu hay hợp đồng chuyển giao trong đợt này.

## 8. Legal & Compliance — Các điều kiện cần kiểm trước pilot

Chưa tiến hành rà soát luật hiện hành trong đợt tổng hợp repository này. Không đưa số điều luật, kết luận tuân thủ, nghĩa vụ đăng ký cụ thể hoặc khẳng định có giấy phép nếu chưa được xác minh bằng nguồn có thẩm quyền.

Các đầu việc cần có chủ sở hữu và bằng chứng:

1. Quyền sử dụng dữ liệu transit, OSM, tile, geocoding, ảnh đường phố và ảnh nội dung theo từng nguồn.
2. Chính sách quyền riêng tư phản ánh đúng vị trí, nội dung lưu trên máy, analytics, tài khoản Admin, SMTP và nhà cung cấp bên thứ ba.
3. Quy định lưu giữ/xoá dữ liệu, quyền truy cập, xử lý yêu cầu của người dùng và thông báo sự cố.
4. Điều khoản sử dụng, nhãn lịch giả định, phạm vi hỗ trợ và cơ chế báo sai dữ liệu.
5. Quy trình kiểm duyệt, khiếu nại, gỡ nội dung trước khi nhận đóng góp cộng đồng.
6. Hồ sơ phát hành và khai báo dữ liệu cho từng nền tảng khi lên store.
7. Rà soát nghĩa vụ thương mại/thanh toán/thuế khi mô hình doanh thu và chủ thể vận hành được chốt.

Cơ chế CSRF, argon2id, audit và bộ đếm tổng hợp là biện pháp kỹ thuật; không đủ để viết “đã tuân thủ pháp luật” hoặc “đã bảo mật hoàn toàn”. Không có dữ liệu thô trong một bảng analytics không đồng nghĩa toàn hệ thống không có dữ liệu cá nhân.

## 9. Business & Finance — Số liệu thật và giả thuyết

### 9.1. Mô hình

B2B2C là định hướng chưa xác thực thương mại. Quảng bá địa điểm/quán và affiliate là các kênh cần kiểm chứng về nhu cầu, hợp đồng, attribution và đối soát trước khi xây thành nguồn doanh thu. Không biến khả năng có kênh thành dự báo chắc chắn.

### 9.2. Dashboard hiện tại

`backend/internal/admin/mock.go` tạo dữ liệu mẫu cho phần chưa có nguồn thật. Revenue/profit là số mẫu; usage có cơ chế chuyển sang mẫu khi chưa đủ lượt; bước “Bắt đầu” trong funnel vẫn mẫu. Nhãn mẫu phải đi cùng ảnh chụp báo cáo hoặc slide giới thiệu.

| Chỉ số | Định nghĩa được phép dùng |
|---|---|
| Lượt mở app | Lượt gọi endpoint mở app, không phải người dùng duy nhất |
| Lượt tìm đường | Bộ đếm tìm đường thành công, không chứng minh đã thực hiện chuyến đi |
| Tuyến phổ biến | Tuyến xuất hiện trong gợi ý, không phải số lượt lên xe |
| Điểm được tìm đường tới | Tín hiệu ý định, không phải lượt ghé thăm đã xác thực |
| Doanh thu/lợi nhuận mẫu | Chỉ minh hoạ giao diện; không dùng tính tăng trưởng, định giá hay hiệu quả kinh doanh |

Bộ chọn 30 ngày / 12 tuần / 12 tháng / 5 năm đã có trong Admin. Có so sánh kỳ trước không chứng minh dự án đã hoạt động từng đó thời gian.

### 9.3. Ngân sách

Chưa xác minh bảng giá hiện hành hoặc hoá đơn nên không chốt tổng chi phí. Lập bảng chi phí thực tế cho: compute Go/OTP/Photon, DB và disk, backup ngoài máy, lưu trữ/CDN ảnh, domain, email, monitoring, tài khoản phát hành, nội dung/giấy phép và kiểm thử thiết bị. Phân biệt chi phí phát triển, demo, pilot và production; tài nguyên trên máy cá nhân không phải hạ tầng production miễn phí vô hạn.

**Nguồn:** `docs/design/01-project-overview-v4.0/`, `docs/design/02-strategy-research-v7.0/`, ADR-038, `backend/internal/admin/mock.go`, `backend/internal/admin/period.go`, `PIPELINE.md` §11.

## 10. Launch & Growth — Demo trước, pilot sau

### 10.1. Bản thử nội bộ

Android có thể build APK debug để người thử tải/cài. iOS cần bản build và signing dành cho nền tảng iOS; APK không cài trên iPhone. Hình thức phân phối iOS cho nhóm rộng hơn cần được chọn và kiểm tra điều kiện tài khoản/nền tảng riêng.

Tailscale Serve hiện được dùng để truy cập backend trên Mac trong mạng riêng. Theo log/ảnh chủ dự án cung cấp trong phiên 16-11: Serve chạy nền và Safari trên iPhone nhận phản hồi JSON từ backend. Điều này chứng minh đường truy cập trong thử nghiệm đó, không chứng minh app iOS đã gọi thành công toàn bộ API hoặc đã qua gate thiết bị thật.

Tại thời điểm đọc file, Android vẫn khai báo `http://10.0.2.2:8090`, iOS vẫn khai báo `http://127.0.0.1:8090`. Vì vậy không ghi “APK/iOS đã cấu hình server từ xa” chỉ dựa vào việc Tailscale hoạt động. Cần kiểm URL của chính bản build được phân phối và build lại sau thay đổi. Không đưa hostname cá nhân, IP, email hay thông tin tài khoản vào tài liệu công khai.

Mac cần thức và chạy đủ dịch vụ. Tailscale không thay thế OTP/database, quy trình backup hoặc môi trường production. Không chốt Vercel hay nhà cung cấp khác là hạ tầng đã triển khai; cần quyết định triển khai phù hợp toàn bộ Go + PostGIS + OTP + Photon và nhu cầu lưu trữ.

### 10.2. Điều kiện chuyển sang pilot thực tế

- Chốt vùng, nhóm người thử và phương tiện được hỗ trợ.
- Gỡ blocker dữ liệu; phân biệt nội dung chính thức, biên tập và giả định.
- Chạy luồng cốt lõi trên Android/iPhone thật với URL bản thử, GPS, quyền vị trí, mất mạng và retry.
- Hoàn thiện M07, luồng nội dung/ảnh và khả năng báo sai theo phạm vi đã duyệt.
- Backup ngoài máy, WAL/PITR và restore cách ly có bằng chứng; RPO/RTO phải được đo.
- Monitoring, log, cảnh báo và xử lý sự cố được kiểm tra.
- Chính sách/giấy phép và điều kiện phân phối được xác minh.
- Phân biệt sample analytics với dữ liệu người thử thật; chỉ công bố metric đã định nghĩa và đo được.

### 10.3. Growth sau khi có pilot

Ưu tiên quan sát điểm rơi trong luồng khám phá → xem địa điểm → tìm đường → sử dụng kế hoạch, sau đó phỏng vấn hoặc thu phản hồi. Đo xem/lưu/bắt đầu và retention cần thiết kế thêm; không suy ra các chỉ số chưa có từ lượt mở app tổng hợp. Chỉ mở rộng địa lý, cộng đồng hoặc kênh trả phí khi có dữ liệu và nguồn lực vận hành tương ứng.

## 11. Bảng sửa những thông tin dễ gây hiểu nhầm

| Nội dung cũ hoặc dễ hiểu sai | Cách thay |
|---|---|
| “Research & MVP Planning — Technical PoC Pending” | Đã có PoC/bản chạy kỹ thuật, đang phát triển MVP, chưa production-ready |
| “Chưa có ứng dụng/API; các thư mục code trống” | README gốc đã lỗi thời; dùng hiện trạng mục 1 và 4 |
| Admin framework chưa chọn / SPA riêng | Go server-rendered + htmx trong backend |
| Admin đăng nhập Google OIDC | ADR-039: mã tài khoản + mật khẩu; Google đã bỏ cho Admin |
| Quên mật khẩu bằng OTP nhập tay, hạn 30 phút | Link một lần, năm phút; tối thiểu một phút giữa các lần gửi |
| 49 bảng đã triển khai | 49 là thiết kế dài hạn; schema theo migration hiện hành |
| user_auth_identities đang dùng | Migration 0008 bỏ bảng; không lấy CREATE từ 0002 làm hiện trạng cuối |
| Plans nằm trong Profile | Kế hoạch ở Hành trình → Của tôi |
| UX-2 đã pass mọi bài test | Đóng theo quyết định owner với backlog; không đánh đồng hoàn thành kiểm thử |
| Lịch metro/bus là dữ liệu vận hành chính thức | Giả định; giữ nhãn DEMO/ASSUMED và blocker M01 |
| Đã tích hợp Cloudflare R2 | Quyết định kiến trúc; code ảnh local còn đang làm tại snapshot |
| Có khách hàng/doanh thu theo biểu đồ | Có sample; số lượt không phải người duy nhất hoặc doanh thu thật |
| Bộ đếm 5 năm có dữ liệu lịch sử đủ | UI có lựa chọn; dữ liệu giữ 25 tháng, dự án mới và có sample |
| App thật đã chạy vì Safari mở được server | Hai bằng chứng độc lập: kết nối trình duyệt và app end-to-end |
| Tailscale nghĩa là đã deploy production | Truy cập demo backend trên máy phát triển |
| RPO ≤15 phút/RTO ≤4 giờ là SLA | Mục tiêu thiết kế, chưa có drill xác nhận |

## 12. Những câu hỏi chủ dự án cần chốt khi biên tập

1. Tài liệu này dùng nội bộ, báo cáo học thuật, giới thiệu đối tác hay gọi vốn? Giữ nguyên trạng thái chứng cứ, điều chỉnh độ chi tiết theo người đọc.
2. Phạm vi pilot cụ thể: vùng nào, bao nhiêu người, thời gian nào, phương tiện nào?
3. Các số và nội dung mẫu có tiếp tục hiện trong bản người thử không; nếu có, cách gắn nhãn ra sao?
4. Tiêu chí được phép mở bản thử rộng hơn và người chịu trách nhiệm trực vận hành là ai?
5. Thứ tự hoàn thiện ảnh, feedback, app events và DATA-1 trong M07/phase tiếp theo?
6. Nhà cung cấp production, ngân sách và quy trình backup/khôi phục nào được duyệt?
7. Quyền sở hữu logo, thiết kế, ảnh và nội dung có đủ hồ sơ chưa?

Không tự trả lời các câu này bằng số liệu hoặc cam kết không có nguồn.

## 13. Chỉ dẫn cho người hoặc AI cập nhật Google Docs

Có thể dùng yêu cầu biên tập sau cùng với file này:

> Cập nhật bộ tài liệu GoWander theo 10 tab hiện có. Giữ tầm nhìn dài hạn nhưng phân biệt Hiện trạng, Dữ liệu mẫu/giả định, Đang làm và Kế hoạch. Dùng bản căn cứ ngày 16-11-2026 để thay thông tin lỗi thời; không coi toàn bộ thiết kế là đã triển khai. Viết bằng tiếng Việt, giữ technical identifiers. Không bịa người dùng, doanh thu, hợp đồng, dữ liệu chính thức, giấy phép, kết quả kiểm thử hoặc SLA. Khi thiếu bằng chứng, ghi “chưa kiểm chứng” và câu hỏi cần chủ dự án xác nhận. Đổi trạng thái trang bìa; cập nhật auth Admin, IA mobile, schema migrations, analytics và giới hạn pilot. Giữ lịch sử thay đổi kiến trúc thay vì xoá dấu vết các quyết định trước. Không đưa thông tin tài khoản, hostname cá nhân hoặc secrets vào bản chia sẻ.

## 14. Bàn giao thay đổi tài liệu trong repository

Chỉ file này và mục bổ sung trong `docs/changes/change-16-11-2026.md` thuộc đợt tổng hợp này. Không sửa code, không chạy build/test/deploy và không chỉnh Google Docs. Những thay đổi khác trong working tree có trước hoặc thuộc công việc khác.

Commit đề xuất: `docs: add project documentation update brief`.

Chủ dự án kiểm tra rồi tự chạy; không dùng `git add .` vì working tree có thay đổi khác:

```bash
git diff -- docs/changes/change-16-11-2026.md
git add docs/handoffs/GoWander-cap-nhat-tai-lieu-16-11-2026.md docs/changes/change-16-11-2026.md
git diff --cached --stat
git commit -m "docs: add project documentation update brief"
```

Nếu index đã có file của công việc khác, cần kiểm tra phần staged trước khi commit. Không có yêu cầu merge hay phát hành trong công việc này.
