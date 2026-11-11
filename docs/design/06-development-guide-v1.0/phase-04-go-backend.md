07. PHASE 4 — GO BACKEND
FOUNDATION
### 7.1. Objective

Xây dựng Go API làm lớp trung gian duy nhất giữa Mobile, Database và Routing Engine.

### Step 4.1 — Initialize Go Module

None‎
cd backend
go mod init github.com/REPLACE_OWNER/gowander/backend

Thay REPLACE_OWNER bằng GitHub owner/repository thực tế.

### Step 4.2 — Establish Package Structure

None‎
backend/
cmd/api/
internal/platform/
config/
http/
database/
logging/
internal/mobility/
internal/discover/
api/openapi/
migrations/
tests/

Không tạo module trống cho tất cả 49 database tables.

### Step 4.3 — Configure HTTP Server


Thành phần:

- ​    HTTP Router.
- ​    Configuration.
- ​    Request ID.
- ​    Structured Logging.
- ​    Error Handling.
- ​    Graceful Shutdown.
- ​    Health Endpoint.
- ​    Readiness Endpoint.

### Step 4.4 — Implement Health API

Ví dụ:

GET /health/live

GET /health/ready

Liveness xác định process còn hoạt động.

Readiness phản ánh khả năng phục vụ theo các dependency cần thiết; không nên để một
dependency không thiết yếu làm restart toàn bộ service.

### Step 4.5 — Implement RoutingProvider

Interface nghiệp vụ nên bao gồm:

- ​    PlanRoute.
- ​    GetRouteDetails nếu cần.
- ​    Provider Health.
- ​    Normalize Routing Errors.

Tạo OTP adapter riêng.

Mobile không gọi OTP trực tiếp.

### Step 4.6 — Implement Route Planning API

Ví dụ:

POST /api/v1/routes/plan

Request gồm:

- ​ Origin.
- ​ Destination.

- ​ Departure/Arrival Preference.
- ​ Allowed Modes.
- ​ Optional Preferences.

Response gồm normalized routes và dữ liệu nguồn thực sự có.

### Step 4.7 — Integration Tests

- ​   Valid route.
- ​   No route.
- ​   Timeout.
- ​   Invalid input.
- ​   Provider unavailable.
- ​   Cancelled request.
- ​   Error mapping.

### Step 4.8 — Weather Adapter (bổ sung 11-11-2026, ADR-021)

Làm sau khi Route Planning API chạy được; không thuộc Exit Criteria của phase này.

- Interface `WeatherProvider` và một adapter cho nhà cung cấp được chọn.
- Lưu đệm theo khu vực, làm mới khoảng 15 phút; timeout ngắn.
- Khi nhà cung cấp lỗi: trả về "không có dữ liệu thời tiết", API tìm tuyến vẫn trả kết quả bình thường.
- Response ghi rõ giờ cập nhật và tên nguồn.
- Test: có dữ liệu, hết hạn đệm, timeout, nhà cung cấp lỗi.

### Exit Criteria

Một API request được xử lý bởi Go, gọi OTP và trả Route DTO hợp lệ.

Deliverable: Backend Routing Vertical Slice.

