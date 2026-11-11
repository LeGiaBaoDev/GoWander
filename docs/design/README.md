# Bộ tài liệu thiết kế GoWander (design sources)

Sáu tài liệu thiết kế được chuyển từ `GoWander.pdf` sang Markdown, rồi tách nguyên văn thành các file nhỏ theo nhóm mục để đọc nhẹ hơn. Mỗi tài liệu là một thư mục; nối các file trong thư mục theo thứ tự tên sẽ ra đúng tài liệu gốc.

**Đây là tài liệu thiết kế, không phải bằng chứng hệ thống đã chạy.** Trạng thái thực tế của dự án nằm ở `PIPELINE.md` và `docs/changes/`.

**Cách dùng:** tìm task đang làm trong bảng bên dưới và chỉ mở đúng file đó. Không đọc cả thư mục.

## 01 — Project Overview v4.0 (`01-project-overview-v4.0/`)

| File | Mục | Nội dung |
|---|---|---|
| `01-introduction-users-problems.md` | 01–03 | Giới thiệu, tầm nhìn, đối tượng người dùng, vấn đề P1–P10 |
| `02-features-dashboards-business-model.md` | 04–06 | Hệ thống chức năng, 6 dashboard, mô hình doanh thu |
| `03-ux-technology-data.md` | 07–09 | Tổng quan UX, bảng công nghệ, chiến lược dữ liệu |
| `04-roadmap-finance-metrics-risks.md` | 10–15 | Phạm vi pilot, roadmap 5 phase, ngân sách, metric, rủi ro |

## 02 — Strategy & Research v7.0 (`02-strategy-research-v7.0/`)

| File | Mục | Nội dung |
|---|---|---|
| `01-foundation-market-competition.md` | 01–04 | Nguyên tắc chiến lược, giả thuyết H1–H8, thị trường, đối thủ |
| `02-personas-journey-research-priorities.md` | 05–08 | Persona, hành trình, phương pháp nghiên cứu, ưu tiên A/B/C |
| `03-identity-technical-feasibility-admin.md` | 09–11 | Chiến lược identity/cộng đồng, giả thuyết kỹ thuật T1–T6, vận hành Admin |
| `04-business-metrics-roadmap-risks-decisions.md` | 12–18 | Doanh thu, metric, roadmap, rủi ro, quyết định đã chọn / đang chờ |

## 03 — Product & UX v4.0 (`03-product-ux-v4.0/`)

| File | Mục | Nội dung |
|---|---|---|
| `01-objectives-priorities-mobile-ia.md` | 01–03 | Mục tiêu, nguyên tắc UX, ưu tiên tính năng, 4 tab mobile |
| `02-mobility-discover-requirements.md` | 04–05 | FR-01…FR-19, nhãn dữ liệu routing, trạng thái lỗi |
| `03-identity-exploration-community-rewards.md` | 06–09 | Guest/Google Sign-In, FR-20…FR-36 |
| `04-user-flows-screens-design-system.md` | 10–12 | Flow A–D, danh sách màn hình, design system, accessibility |
| `05-admin-web.md` | 13–16 | Sidebar Admin, KPI từng dashboard, Flow E–H, chuẩn UX Admin |
| `06-nfr-validation-release-metrics.md` | 17–22 | Gợi ý/thông báo, NFR, kịch bản kiểm thử UX, kế hoạch release |

## 04 — Technical & System Architecture v2.1 (`04-technical-architecture-v2.1/`)

| File | Mục | Nội dung |
|---|---|---|
| `01-objectives-system-overview-stack.md` | 01–03 | Mục tiêu, thành phần hệ thống, stack mobile/backend/vận hành |
| `02-mobile-admin-backend-architecture.md` | 04–06 | Kiến trúc KMP, Admin Web, module Go và cấu trúc thư mục |
| `03-auth-routing-database.md` | 07–09 | OIDC, RBAC, RoutingProvider/OTP, chiến lược database |
| `04-backup-strategy-automation.md` | 10–13 | RPO/RTO, pgBackRest, lịch backup, retention, backup thủ công |
| `05-pitr-incident-response-recovery-ha.md` | 14–18 | WAL/PITR, phát hiện sự cố, phản ứng tự động, chế độ phục hồi, HA |
| `06-restore-drills-runbook-monitoring.md` | 19–22 | Restore drill, dashboard backup, runbook sự cố, giám sát và log |
| `07-security-revenue-rewards-cicd-testing.md` | 23–27 | Bảo mật API/Admin/DB, thiết kế doanh thu và điểm thưởng, CI/CD, kiểm thử |
| `08-poc-roadmap-adrs-open-decisions.md` | 28–33 | Danh sách PoC, Stage 0–9, ADR-001…019, quyết định còn mở, nguồn tham khảo |

## 05 — Database Design & Data Modeling v1.2 (`05-database-design-v1.2/`)

| File | Mục | Nội dung |
|---|---|---|
| `01-overview-conventions-inventory.md` | 01–02 | Quy ước kiểu dữ liệu, danh mục 49 bảng / 9 domain |
| `02-identity-tables.md` | 03 | `users`, `user_auth_identities`, `user_contacts`, `user_profiles`, `user_preferences` |
| `03-discover-tables.md` | 04 | `places` và 10 bảng Discover |
| `04-mobility-exploration-tables.md` | 05–06 | Bảng transit và bảng khám phá cá nhân |
| `05-community-tables.md` | 07 | `posts`, `reviews`, `user_follows`, báo cáo, kiểm duyệt |
| `06-rewards-supporting-admin-tables.md` | 08–10 | `points_ledger`, feedback, thông báo, sự kiện, `roles`, audit |
| `07-business-revenue-tables.md` | 11 | Đối tác, hợp đồng, doanh thu, đối soát |
| `08-patterns-constraints-indexes.md` | 12–14 | Pattern mô hình dữ liệu, ma trận quan hệ, chiến lược index |
| `09-dashboards-privacy-migrations-testing.md` | 15–20 | Nguồn dữ liệu dashboard, quyền riêng tư, thứ tự migration, kiểm thử |

## 06 — Development & Implementation Guide v1.0 (`06-development-guide-v1.0/`)

| File | Milestone | Nội dung |
|---|---|---|
| `00-purpose-rules-project-structure.md` | mọi milestone | Quy tắc làm việc, công cụ, cấu trúc repository |
| `phase-00-environment-repository.md` | M00 | Kiểm tra công cụ, khởi tạo repo, README, commit đầu |
| `phase-01-transit-data-feasibility.md` | M01 | Chọn khu vực pilot, nguồn dữ liệu, kiểm tra GTFS, test case |
| `phase-02-otp-poc.md` | M02 | Build graph, chạy OTP, truy vấn GraphQL, đo hiệu năng |
| `phase-03-kmp-map-poc.md` | M03 | Dự án KMP, MapLibre, kiểm thử Android/iOS |
| `phase-04-go-backend.md` | M04 | Go module, HTTP server, health, RoutingProvider, API tìm tuyến |
| `phase-05-postgresql-postgis.md` | M05 | Database local, PostGIS, migration đầu, truy vấn không gian |
| `phase-06-end-to-end-slice.md` | M06 | Hai luồng end-to-end trên mobile |
| `phase-07-admin-foundation.md` | M07 | Chọn framework, identity Admin, quản lý nội dung, dashboard |
| `phase-08-backup-recovery.md` | M08 | pgBackRest, lịch backup, restore cách ly, PITR, cảnh báo |
| `phase-09-observability-incident-response.md` | M09 | Metric, alert, phân loại sự cố, kịch bản lỗi |
| `phase-10-cicd-production-pilot.md` | M09–M10 | CI, staging, cổng triển khai, pilot |
| `phase-11-13-identity-community-business.md` | M11+ | Tài khoản, khám phá cá nhân, cộng đồng, điểm thưởng, doanh thu |
| `zz-release-checklist-log-format-first-milestone.md` | mọi release | Release checklist, mẫu implementation log, Milestone M1 |

## Quan hệ với các tài liệu khác

- `OVERVIEW.md` và `PIPELINE.md` ở gốc repository là bản rút gọn dành cho agent. Chi tiết đầy đủ nằm ở đây.
- Nếu tài liệu ở đây mâu thuẫn với `OVERVIEW.md`, `PIPELINE.md` hoặc với kết quả triển khai thực tế: báo lại và sửa tài liệu, không tự chọn một bên trong im lặng.

## Vấn đề đã biết

- **Thiếu hình:** `01-project-overview-v4.0/03-ux-technology-data.md` tham chiếu `GoWander_assets/figure_page_008_1.png` (sơ đồ hành trình người dùng) nhưng thư mục `GoWander_assets/` chưa có trong repository. Đặt thư mục đó vào `docs/design/01-project-overview-v4.0/` thì liên kết sẽ hoạt động.
- **Số ADR:** `04-technical-architecture-v2.1/08-poc-roadmap-adrs-open-decisions.md` đã dùng ADR-001 đến ADR-019 (ADR-001 là KMP + Compose). `PIPELINE.md` mục 5 lại gợi ý `ADR-001-transit-data.md`; ADR về dữ liệu transit phải dùng số tiếp theo còn trống (ADR-020).
- **Tiêu đề không đều:** bản chuyển từ PDF để một số tiêu đề mục ở dạng chữ thường không có dấu `#` (ví dụ "07. AUTHENTICATION &"). Nội dung được giữ nguyên văn, chưa chuẩn hoá định dạng.
- **Phần đầu mỗi tài liệu:** file đầu tiên của mỗi thư mục chứa trang bìa (phiên bản, trạng thái) của tài liệu đó; file cuối kết thúc bằng dòng ngăn cách `---` của bản gộp.
