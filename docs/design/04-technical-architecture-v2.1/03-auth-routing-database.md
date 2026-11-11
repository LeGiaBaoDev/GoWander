07. AUTHENTICATION &
AUTHORIZATION
### 7.1. Authentication Strategy


Google Sign-In là phương án khởi đầu khi có Account.

Apple Sign-In cần được đánh giá theo yêu cầu iOS App Store và authentication provider.

Sử dụng OIDC/OAuth 2.0 phù hợp.

Đối với Mobile public client, ưu tiên Authorization Code Flow với PKCE theo provider/SDK
được chọn.

### 7.2. Identity Tables

- ​ users.
- ​ user_auth_identities.
- ​ user_contacts.
- ​ user_profiles.
- ​ user_preferences.

### 7.3. Identity Verification

Backend hoặc trusted auth layer cần kiểm tra:

- ​   Token signature.
- ​   Issuer.
- ​   Audience.
- ​   Expiration.
- ​   Nonce khi luồng yêu cầu.
- ​   Provider Subject.

Không dùng email đơn thuần để xác định chủ tài khoản.

### 7.4. Account Linking

Một user có thể liên kết nhiều providers.

Thao tác phải yêu cầu một phiên xác thực hợp lệ và kiểm tra ownership.

Không tự động merge hai users có email giống nhau.

### 7.5. Contact Data

Email và số điện thoại là private data.

Không tự động yêu cầu phone khi Google Sign-In không cần.

Các trạng thái email/phone verification phải được xác minh qua quy trình đáng tin cậy.

### 7.6. RBAC

Vai trò đề xuất:

- ​   CONTENT_EDITOR.
- ​   MODERATOR.
- ​   OPERATIONS_ADMIN.
- ​   BUSINESS_ADMIN.
- ​   SYSTEM_ADMIN.

Bên cạnh RBAC, phải kiểm tra quyền sở hữu dữ liệu cá nhân.

08. ROUTING & TRANSIT
ARCHITECTURE
### 8.1. Routing Provider Boundary

Mobile chỉ giao tiếp Go API.

Go Mobility sử dụng RoutingProvider interface.

Implementation đầu tiên là OTP Adapter.

### 8.2. OTP Integration

Sử dụng GraphQL API phù hợp với GTFS và phiên bản OTP được chọn.

Không xây integration mới dựa trên OTP REST API cũ đã bị loại bỏ.

### 8.3. Transit Data Pipeline

1. Tìm nguồn dữ liệu.
2. Xác minh giấy phép.
3. Tải GTFS/OSM.
4. Kiểm tra cấu trúc và phạm vi.
5. Build OTP Graph.
6. Chạy Routing Regression Tests.
7. Phê duyệt graph.
8. Deploy và theo dõi.
9. Có phương án quay về graph trước.

### 8.4. Realtime Data

GTFS Static cung cấp timetable.

GTFS Realtime chỉ được sử dụng khi có nguồn hợp lệ.

Không tự gọi scheduled arrival là realtime ETA.

### 8.5. Routing Observability

Theo dõi:

- ​   OTP Health.
- ​   Route Found Rate.
- ​   Technical Error Rate.
- ​   Timeout Rate.
- ​   P50/P95 Latency.
- ​   Data Version.
- ​   Routing Test Failures.

No Route hợp lệ phải tách khỏi lỗi kỹ thuật.

## 09. DATABASE & DATA MODELING

### 9.1. Database Strategy

PostgreSQL + PostGIS.

Một database chính, tổ chức theo các domain.

Tài liệu Database v1.2 hiện có 49 bảng trong logical inventory, bao gồm các bảng tương lai
và điều kiện.

Không triển khai toàn bộ 49 bảng chỉ để chuẩn bị MVP.

### 9.2. Data Access

Go sử dụng pgx/sqlc theo hướng đã chọn.

Ưu tiên:

- ​ Explicit SQL.
- ​ Parameterized Queries.

- ​   Transactions.
- ​   Referential Integrity.
- ​   Application-level Authorization.
- ​   Query-specific DTOs.

### 9.3. Query Optimization

Theo thứ tự:

1. Correct Query.
2. Appropriate Index.
3. Projection.
4. Pagination.
5. Batch Queries.
6. Benchmark.
7. Computed Read Models.
8. Cache/Partition khi cần.

### 9.4. PostGIS

Dùng spatial indexes và những hàm truy vấn phù hợp như ST_DWithin.

Không truyền toàn bộ địa điểm thành phố xuống Mobile để lọc.

### 9.5. Schema Management

Migrations có version.

Không tự động áp dụng migration phá hủy dữ liệu khi chưa có review, backup phù hợp và
kế hoạch khôi phục.

