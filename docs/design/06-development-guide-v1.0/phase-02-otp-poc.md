05. PHASE 2 — OPENTRIPPLANNER
POC
### 5.1. Objective

Xây dựng routing graph và truy vấn được hành trình thực tế bằng OTP.

### Step 2.1 — Prepare OTP Directory

None‎
mkdir -p infrastructure/otp/graph-data

Thư mục graph-data chứa:

- ​     GTFS files đã được kiểm chứng.
- ​     OSM PBF.
- ​     OTP configuration files.
- ​     graph.obj sau khi build.

### Step 2.2 — Select OTP Version

Chọn một OTP container image tag hoặc digest cụ thể sau khi kiểm tra compatibility.

Không dùng latest làm cấu hình production cố định.

Ghi vào:

docs/decisions/adr-otp-version.md

### Step 2.3 — Build Graph

Ví dụ quy trình theo official OTP container image:

None‎
docker run --rm \
-e JAVA_TOOL_OPTIONS="-Xmx4g" \
-v "$(pwd)/infrastructure/otp/graph-data:/var/opentripplanner"
\
opentripplanner/opentripplanner:latest \
--build --save

latest trong ví dụ chỉ dành cho thử nghiệm ban đầu. Khi chạy thật, thay bằng phiên bản
đã khóa.

Bộ dữ liệu phải được đặt đúng vị trí, tên file và cấu hình mà phiên bản OTP yêu cầu.

### Step 2.4 — Start OTP

None‎
docker run --rm \
-p 8080:8080 \
-e JAVA_TOOL_OPTIONS="-Xmx4g" \
-v "$(pwd)/infrastructure/otp/graph-data:/var/opentripplanner"
\
opentripplanner/opentripplanner:latest \
--load --serve

### Step 2.5 — Verify Startup

Mở:

http://localhost:8080/graphiql

Kiểm tra OTP đã nạp routing graph và mở được GraphQL explorer.

### Step 2.6 — Query Transit Data

Ví dụ truy vấn GraphQL:

None‎
query {
routes {
gtfsId

shortName
longName
mode
}
}

Kiểm tra kết quả bằng dữ liệu GTFS đã nhập.

### Step 2.7 — Test Route Planning

Sử dụng GraphiQL schema của đúng OTP version để tạo query lập tuyến.

Ưu tiên planConnection nếu phiên bản API được sử dụng hỗ trợ và khuyến nghị; không
xây integration lâu dài dựa trên query đã deprecated.

Kiểm tra:

- ​   Itinerary count.
- ​   Modes.
- ​   Stops.
- ​   Transfer instructions.
- ​   Departure/Arrival.
- ​   Walking legs.
- ​   Geometry.
- ​   No-route behavior.

### Step 2.8 — Evaluate Performance

Đo:

- ​   Graph build duration.
- ​   Graph file size.
- ​   JVM memory usage.
- ​   Routing P50/P95.
- ​   Routing failures.
- ​   Startup time.

Ghi cấu hình phần cứng, graph version và dữ liệu thử nghiệm để các kết quả so sánh có ý
nghĩa.

### Exit Criteria

OTP phải:

- ​   Load graph thành công.
- ​   Trả dữ liệu transit hợp lệ.
- ​   Có các test case routing đạt.
- ​   Có test error/no route.
- ​   Có kết quả benchmark tối thiểu.

Deliverable: OTP PoC Report.

Status: BLOCKED UNTIL DATA VERIFIED.

