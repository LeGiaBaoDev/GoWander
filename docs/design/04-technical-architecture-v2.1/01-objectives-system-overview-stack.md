## GOWANDER

## TECHNICAL & SYSTEM ARCHITECTURE

Version: 2.1 — Research-backed Architecture, Reliability, Backup & Disaster Recovery

Product: GoWander — Move Smart. Explore More.

Platform: Android / iOS + Admin Web

Architecture: Option B — Modular & Scalable

Mobile: Kotlin Multiplatform + Compose Multiplatform

Backend: Go Modular Monolith

Database: PostgreSQL + PostGIS

Routing Engine: OpenTripPlanner 2.x — Pending PoC

Map Integration: MapLibre Compose — Pending Android/iOS PoC

Authentication: OIDC; Google Sign-In initially

Database Backup: pgBackRest + WAL Archiving + PITR

Deployment: Containers + Linux Hosting

Status: Proposed Architecture — Implementation and Operational Validation Pending

Related Documents:

- ​   Project Overview v4.0
- ​   Strategy & Research v7.0
- ​   Product & UX v4.0
- ​   Database Design & Data Modeling v1.2

## 01. TECHNICAL OBJECTIVES

### 1.1. Architecture Goals


GoWander được xây dựng để phục vụ đồng thời ứng dụng Mobile, hệ thống quản trị Web
và các tích hợp giao thông, bản đồ, dữ liệu thương mại.

Những yêu cầu kỹ thuật cốt lõi:

1. Hỗ trợ Android và iOS.
2. Chia sẻ business logic và UI giữa hai nền tảng khi phù hợp.
3. Tách rõ Mobile, Admin, Backend và Routing Engine.
4. Phục vụ truy vấn địa lý hiệu quả.
5. Bảo vệ dữ liệu đăng nhập và thông tin cá nhân.
6. Đảm bảo tính nhất quán của Reward Points và doanh thu.
7. Cho phép quản trị nội dung và giám sát vận hành.
8. Có cơ chế backup tự động và thủ công.
9. Có khả năng khôi phục dữ liệu tới thời điểm xác định.
10. Có cảnh báo khi backup, database hoặc routing xảy ra sự cố.
11. Có quy trình kiểm thử phục hồi và xử lý sự cố.
12. Giữ chi phí, công sức bảo trì phù hợp Solo Developer.

### 1.2. Architecture Principles

Modular Monolith: Chia Backend theo domain nhưng không phát triển thành nhiều
microservices ngay.

Clean Architecture + MVVM: Mobile tách Presentation, Domain và Data.

Pragmatic Hexagonal Architecture: Sử dụng ports/adapters cho các tích hợp thực sự có
khả năng thay thế.

Relational-first: PostgreSQL là nguồn dữ liệu nghiệp vụ chính.

Provider Independence: Mobile không phụ thuộc trực tiếp vào API của OTP.

Reliability by Design: Hệ thống có health checks, backup, monitoring và recovery plan.

Security by Default: Không để tài khoản, dữ liệu cá nhân và API quản trị được truy cập trái
phép.

Measured Scaling: Chỉ mở rộng hạ tầng sau khi có kết quả đo.

02. HIGH-LEVEL SYSTEM
ARCHITECTURE
### 2.1. System Components


    Component                           Responsibility

Mobile Application   Trải nghiệm Map, Discover, Community và Profile

    Admin Web            Quản lý nội dung, người dùng, vận hành, doanh
thu

    Go Backend           Business Logic, REST API, Authorization

    PostgreSQL           Lưu dữ liệu ứng dụng

    PostGIS              Spatial Queries

    OpenTripPlanner      Multimodal Route Planning

    Identity Provider    Google/OIDC Authentication

    Object Storage       Media và backup repositories

    Monitoring           Metrics, Alerts, Health

    Backup Service       pgBackRest, WAL Archiving

    Scheduler            Scheduled Backup & Recovery Drills

    Background Worker    Xử lý tác vụ bất đồng bộ khi cần

### 2.2. Main Communication Paths

Mobile:

Mobile → HTTPS → Go REST API → Domain Module → PostgreSQL.

Admin:

Admin Web → HTTPS → Admin API → RBAC → Domain Module → PostgreSQL.

Routing:

Mobile → Go Mobility → Routing Provider Adapter → OTP GraphQL.

Authentication:

Mobile/Admin → Identity Provider → Go Authorization → User Database.

Backup:

PostgreSQL → pgBackRest/WAL Archive → External Backup Repository.

Monitoring:

Application/Database/OTP/Backup Jobs → Metrics & Logs → Alerting.

Weather (bổ sung 11-11-2026, ADR-021):

Mobile → Go Mobility → Weather Provider Adapter → nhà cung cấp thời tiết.

Go API lưu đệm kết quả theo khu vực (làm mới khoảng 15 phút) và có timeout riêng. Lỗi thời tiết không được làm hỏng việc tìm tuyến. Mobile không gọi thẳng nhà cung cấp. Nhà cung cấp chưa được chọn.

### 2.3. Deployment Boundaries

Go Backend ban đầu là một deployable chính.

OTP chạy riêng do có runtime JVM và nhu cầu tài nguyên khác.

PostgreSQL chạy trong dịch vụ được quản lý riêng, tránh phụ thuộc vòng đời container ứng
dụng.

Admin có thể deploy riêng frontend.

Backup repository phải độc lập với ổ lưu trữ production và, khi khả thi, độc lập cả máy chủ
hoặc tài khoản vận hành.

## 03. TECHNOLOGY STACK

### 3.1. Mobile

    Component                        Technology                       Status

    Language                Kotlin                                    Selected

    Shared Code             Kotlin Multiplatform                      Selected

    UI                      Compose Multiplatform                     Selected

    Architecture            Clean Architecture + MVVM                 Selected

    Asynchronous            Coroutines                                Selected

    State                   StateFlow                                 Selected

    HTTP                    Ktor Client                               Preferred

    Serialization           kotlinx.serialization                     Preferred

    Dependency Injection    Koin                                      Preferred

    Map                     MapLibre Compose                          Pending PoC

    Native Location         Android/iOS Location APIs                 Selected
Approach

    Secure Storage           Platform Keychain/Keystore abstraction   Required

    Local Database           SQLDelight                               Conditional

### 3.2. Backend

    Component                 Technology                       Status

    Language           Go                            Selected

    Architecture       Modular Monolith              Selected

    HTTP Router        Chi                           Preferred

    API                REST/JSON                     Selected

    API Contract       OpenAPI                       Selected

    Database Driver    pgx                           Preferred

    SQL Generation     sqlc                          Preferred

    Database           PostgreSQL + PostGIS          Selected

    Authentication     OIDC-compatible Provider      Pending Provider
Selection

    Background         Go Worker                     Conditional
Tasks

    Event Delivery     PostgreSQL Outbox             Conditional

    Cache              Redis/Valkey                  Deferred

### 3.3. Operations & Recovery

    Component                    Preferred Technology                         Purpose

    Physical Backup    pgBackRest                                         Full/Diff/Incremental

    Continuous         PostgreSQL WAL + pgBackRest                        PITR
Archive

    Backup Scheduler   systemd timer hoặc cron                            Tự động theo lịch

    Backup Storage     S3-compatible Object Storage hoặc máy              Off-host retention
backup độc lập

    Database             PostgreSQL statistics + exporter             Database Health
Monitoring

    Metrics Platform     Prometheus-compatible                        Thu thập metrics

    Visualization        Grafana-compatible                           Operational
Dashboards

    Alerting             Alertmanager-compatible hoặc managed         Gửi cảnh báo
alerts

    Log Collection       Structured Logs + log collector              Điều tra lỗi

    Container            Docker                                       Deployment
Runtime

    CI/CD                Git-based CI                                 Build/Test/Deploy

    High Availability    PostgreSQL Standby/Patroni                   Future, not Core MVP

Các phần mềm monitoring có thể được thay bằng managed services tương đương nếu chi
phí và khả năng vận hành phù hợp hơn.

