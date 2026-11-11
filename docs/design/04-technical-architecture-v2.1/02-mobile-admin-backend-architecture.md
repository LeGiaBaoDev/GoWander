## 04. MOBILE ARCHITECTURE

### 4.1. Architectural Flow

Compose Screen → ViewModel → Use Case → Repository Interface → Repository
Implementation → API/Local Storage.

Presentation quản lý UI state và user interaction.

Domain chứa business models, validation và use cases.

Data chứa API client, DTO, mapper và repository implementations.

Platform cung cấp GPS, permissions, maps, notifications và secure storage.

### 4.2. Suggested Structure

None‎
mobile/
composeApp/

src/
commonMain/
app/
core/
network/
designsystem/
navigation/
maps/
location/
security/
feature/
mobility/
discover/
identity/
exploration/
community/
rewards/
profile/
androidMain/
iosMain/
iosApp/

Đây là cấu trúc logic. Không bắt buộc tạo Gradle module cho mọi feature.

### 4.3. UI State Management

Mỗi màn hình có trạng thái rõ ràng:

- ​   Idle.
- ​   Loading.
- ​   Success.
- ​   Empty.
- ​   Error.
- ​   Retry.

Routing cần phân biệt thêm:

- ​   No Route Found.
- ​   Out of Coverage.
- ​   Data Unavailable.
- ​   Provider Timeout.
- ​   Technical Error.

Không biểu diễn mọi trường hợp bằng một biến isLoading và thông báo lỗi chung.

### 4.4. Map SDK Strategy

MapLibre Compose là lựa chọn ưu tiên.

Tuy nhiên, API chưa được xem là hoàn toàn ổn định và có thể thay đổi giữa các phiên bản.

Vì vậy cần:

1. Khóa dependency versions.
2. Xây PoC Android.
3. Xây PoC iOS.
4. Kiểm tra markers/polylines.
5. Kiểm tra GPS và permissions.
6. Kiểm tra gesture, lifecycle và memory.
7. Kiểm tra performance trên thiết bị thật.
8. Chuẩn bị adapter thay thế SDK nếu cần.

Không tạo nhiều abstraction không cần thiết, nhưng phải tránh rải trực tiếp MapLibre SDK
API khắp các feature.

## 05. ADMIN WEB ARCHITECTURE

### 5.1. Purpose

Admin Web hỗ trợ vận hành nội dung, transit, cộng đồng, người dùng, bảo mật, Rewards và
Business.

Admin không phải một phiên bản Mobile dành cho nhân viên mà là giao diện quản lý riêng.

### 5.2. Modules

Content: Places, Foods, Categories, Stories, Trip Ideas, Media.

Mobility Operations: Transit Sources, Data Versions, Routing Incidents.

Community: Posts, Reviews, Moderation.

Identity: Users, Roles, Account Status.

Rewards: Rules, Grants, Ledger Review.

Business: Partners, Agreements, Revenue, Settlements.

System: Monitoring, Backup Status, Audit Logs.

### 5.3. Admin Frontend

Framework chưa khóa.

Có thể xem xét React/Next.js hoặc Vue/Nuxt theo những tiêu chí:

- ​   Thư viện form và data tables.
- ​   Typed API integration.
- ​   Authentication.
- ​   RBAC-aware rendering.
- ​   Khả năng maintain.
- ​   Deployment complexity.

Không chọn framework chỉ vì mức độ phổ biến.

### 5.4. Authorization

Backend phải kiểm tra quyền thực sự.

Frontend ẩn chức năng theo role chỉ là UX.

Các thao tác nguy hiểm như:

- ​   Ban User.
- ​   Change Role.
- ​   Delete Content.
- ​   Approve Reward Adjustment.
- ​   Confirm Settlement.
- ​   Request Production Restore.

phải có server-side authorization và audit.

## 06. BACKEND MODULAR MONOLITH

### 6.1. Domain Modules

    Module                    Responsibilities

    Identity       Account, Contact, Authentication Identity

    Access         Roles, Authorization

    Mobility        Route Planning, OTP Adapter

    Discover        Places, Food, Stories

    Exploration     Saved, Visited, Journal

    Community       Posts, Reviews, Following

    Moderation      Reports, Actions

    Rewards         Ledger, Grants

    Business        Partners, Revenue

    Feedback        User Reports

Notifications   In-app Notifications

    Analytics       Product Events & Metric Definitions

    Operations      Transit Data, Health/Incidents

### 6.2. Suggested Structure

None‎
backend/
cmd/
api/
worker/
internal/
platform/
config/
database/
http/
auth/
logging/
observability/
identity/
access/
mobility/
discover/
exploration/
community/
moderation/
rewards/

business/
feedback/
notifications/
analytics/
operations/
api/openapi/
migrations/
tests/

worker chỉ triển khai khi có công việc nền thực tế.

### 6.3. Internal Boundaries

Domain không phụ thuộc trực tiếp HTTP framework.

Infrastructure triển khai repository hoặc external adapter.

Modules giao tiếp qua application services/interfaces.

Không tùy tiện sửa dữ liệu do module khác sở hữu.

### 6.4. Transaction Strategy

Những nghiệp vụ nhiều bước phải xác định transaction boundary.

Ví dụ:

- ​    User + Authentication Identity creation.
- ​    Approve Contribution + Record Reward.
- ​    Record Revenue + Adjustment.
- ​    Change Roles + Audit Record.

Không tạo transaction kéo dài trong lúc gọi OTP hoặc external payment APIs.

