### 07. USER EXPERIENCE OVERVIEW

Bốn tab Mobile:

1. Map

![Sơ đồ hành trình người dùng — hình từ PDF, trang 8](GoWander_assets/figure_page_008_1.png)

2. Discover
3. Community
4. Profile

Trong Profile bao gồm My Exploration Map, Journal, Rewards và Settings theo release.

Luồng Destination-first:

Điểm xuất phát → Điểm đến → Tìm tuyến → So sánh → Chọn tuyến → Điều hướng → Khám phá
→ Lưu/Chia sẻ.

Luồng Discover-first:

Discover → Chọn món/địa điểm → Place Details → Tìm tuyến → Điều hướng → Trải nghiệm.

Khi không có tuyến, hệ thống phải phân biệt rõ No Route, Out of Coverage, Missing Data và
Technical Failure.

### 08. TECHNOLOGY & SYSTEM ARCHITECTURE

    Layer                            Technology                                Status

    Mobile                 Kotlin Multiplatform + Compose Multiplatform Selected

    Mobile Architecture    Clean Architecture + MVVM                      Selected

    Map Integration        MapLibre Compose                               Pending PoC

    Backend                Go Modular Monolith                            Selected

    API                    REST/JSON + OpenAPI                            Selected

    Database               PostgreSQL + PostGIS                           Selected

    Transit Routing        OpenTripPlanner via Adapter                    Pending PoC

    Authentication        OIDC, Google Sign-In                           Provider selection pending

    Admin Web             Separate browser-based UI                      Framework pending

    Authorization         RBAC + resource ownership                      Selected approach

    Deployment            Docker Compose initially                       Selected

Go Backend phục vụ Mobile và Admin, không cho browser kết nối PostgreSQL trực tiếp.

Routing graph do OTP quản lý; PostgreSQL lưu dữ liệu ứng dụng và metadata giao thông cần thiết.

### 09. DATA DEVELOPMENT STRATEGY

#### 9.1. Nhóm dữ liệu chính

- ​   Identity & Contacts.
- ​   Places, Categories, Foods & Historical Stories.
- ​   Transit Sources & Versions.
- ​   Personal Exploration.
- ​   Community & Moderation.
- ​   Rewards Ledger.
- ​   Admin Roles & Audit.
- ​   Business Partners & Revenue.
- ​   Feedback & Product Analytics.

#### 9.2. Nguyên tắc

- ​   PostgreSQL + PostGIS là nguồn dữ liệu chính.
- ​   Chuẩn hóa dữ liệu và ràng buộc PK/FK.
- ​   Tối ưu read queries bằng projection và index.
- ​   JSONB chỉ dành cho metadata linh hoạt.
- ​   Computed/denormalized models khi có nhu cầu đo được.
- ​   Database migrations có phiên bản.
- ​   Data Dictionary nằm trong tài liệu Database Design riêng.

