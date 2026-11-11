## 06. PHASE 3 — KMP & MAP POC

### 6.1. Objective

Kiểm chứng một codebase Kotlin Multiplatform có thể triển khai giao diện Map tương đương
trên Android và iOS.

### Step 3.1 — Create KMP Project

Dùng Kotlin Multiplatform Wizard hoặc công cụ khởi tạo chính thức hiện hành.

Đặt ứng dụng trong thư mục mobile/.

Không tạo sẵn toàn bộ màn hình của sản phẩm.

### Step 3.2 — Configure Dependencies

- ​   Compose Multiplatform.
- ​   Coroutines.
- ​   StateFlow.
- ​   Ktor Client.
- ​   kotlinx.serialization.
- ​   Koin khi cần.
- ​   MapLibre Compose.

Khóa các dependency versions tương thích.

### Step 3.3 — Add MapLibre

Tham khảo đúng phiên bản MapLibre Compose.

Cần kiểm tra runtime Android và iOS riêng theo hướng dẫn thư viện, không chỉ thêm shared
dependency là đủ.

### Step 3.4 — Implement Basic Map

Màn hình PoC phải hỗ trợ:

- ​   Render map tiles.
- ​   Pan/Zoom.
- ​   Fixed Marker.
- ​   Map Camera.
- ​   Marker Tap.
- ​   Polyline.
- ​   User Location khi được cấp quyền.

### Step 3.5 — Test Native Platform Behavior

Android:

- ​   Location permission.
- ​   Back navigation.
- ​   Device rotation.
- ​   App background/foreground.
- ​   Map gestures.

iOS:

- ​   Location permission.
- ​   Lifecycle.
- ​   Map rendering.
- ​   Memory.
- ​   Gestures.
- ​   Native interop.

### Step 3.6 — Route Visualization

Sử dụng polyline thử nghiệm được đánh dấu DEMO trước khi tích hợp dữ liệu OTP.

Không dùng dữ liệu đó để khẳng định routing đã hoạt động.

### Exit Criteria

- ​   Android Map hoạt động.
- ​   iOS Map hoạt động.
- ​   Marker/Polyline hoạt động.
- ​   Permission handling đúng.
- ​   Có báo cáo lỗi và compatibility.

Deliverable: KMP Map PoC.

Status: NOT STARTED.

