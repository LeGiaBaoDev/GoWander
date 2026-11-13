# ADR-029 — App mobile gọi API GoWander: thư viện, trạng thái màn hình, kết nối khi dev

- **Trạng thái:** Accepted cho M06 · **Ngày:** 13-11-2026
- **Liên quan:** `PIPELINE.md` mục 10 (M06), `docs/design/06-development-guide-v1.0/phase-06-end-to-end-slice.md`, ADR-026 (toolchain mobile), ADR-027/028 (API), `backend/api/openapi/openapi.yaml`

## Quyết định

### 1. Thư viện (khớp phiên bản Compose Multiplatform 1.12.1 đang kéo về)

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Ktor Client (`ktor-client-core`, `-content-negotiation`, `ktor-serialization-kotlinx-json`) | 3.6.1 (29-10-2026) | Thiết kế phase 6 chỉ định Ktor. Build bằng Kotlin 2.3.21, coroutines 1.11.0 |
| Engine | `ktor-client-okhttp` (Android), `ktor-client-darwin` (iOS) | `ktor-client-mock` chỉ cho test |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Cùng bản lifecycle mà Compose 1.12.1 đã dùng |
| `kotlinx-coroutines-test` | 1.11.0 | Cùng bản coroutines của Compose và Ktor; chỉ cho test |
| kotlinx-serialization | 1.11.0 (đã có) | |

Không thêm thư viện điều hướng: chỉ có 2 tab (Bản đồ, Khám phá) và 1 màn chi tiết, quản lý bằng một biến trạng thái.

### 2. Cấu trúc

- `shared/.../api/`: DTO đúng `openapi.yaml`, `GoWanderApi` (Ktor), ánh xạ lỗi HTTP + mã lỗi sang `ApiError` (không trả chuỗi lỗi thô của Ktor cho UI).
- `shared/.../route/`: `RouteViewModel` (điểm đi/đến, trạng thái tìm tuyến), giải mã polyline, màn Bản đồ.
- `shared/.../discover/`: `DiscoverViewModel`, màn danh sách và chi tiết.
- Điểm đi/đến nằm trong ViewModel, nên **giữ nguyên khi lỗi và khi thử lại**.
- Bỏ dữ liệu tuyến vẽ sẵn của M03 (`MetroL1Demo`): bản đồ vẽ tuyến API trả về; vẫn hiện nhãn DEMO khi `dataStatus = ASSUMED`.

### 3. Kết nối khi phát triển (chỉ máy ảo; chủ dự án chọn 13-11-2026)

- Địa chỉ API theo nền tảng: Android emulator `http://10.0.2.2:8090` (máy Mac nhìn từ emulator), iOS simulator `http://127.0.0.1:8090`.
- HTTP không mã hoá chỉ được phép tới đúng các địa chỉ đó: Android `network_security_config` (chỉ `10.0.2.2`), iOS `NSAllowsLocalNetworking`. Không bật cho mọi domain.
- API Go mặc định lắng nghe `127.0.0.1:8090` (trước đây `:8090` mở trên mọi địa chỉ mạng, người cùng Wi-Fi gọi được). Container/máy chủ đặt `HTTP_ADDR` riêng.
- Thiết bị thật để sau, khi có HTTPS và máy chủ.

### 4. Giao diện

Ưu tiên chức năng (chủ dự án chọn): đủ hai luồng và mọi trạng thái (đang tải, không có tuyến, ngoài vùng phủ, quá giờ, lỗi kỹ thuật, thử lại), dùng màu Design Kit v4 như M03, bố cục đơn giản. Bám bố cục Figma từng màn để milestone sau. Chữ tiếng Việt gom về một file để dịch tiếng Anh sau (ADR-022).

### 5. Sau review và chạy thật (13-11-2026)

- Một `HttpClient` duy nhất cho cả process (`App.kt`), không tạo lại mỗi lần Activity được dựng lại.
- Chỉ lỗi I/O và quá giờ được coi là "mất mạng"; lỗi khác là `Unexpected` (không bảo người dùng kiểm tra mạng).
- Back của Android: đóng chi tiết địa điểm, rồi từ tab Khám phá về Bản đồ. Dùng `ui-backhandler` 1.12.1 (API **thử nghiệm** của Compose Multiplatform, `@OptIn`); kiểm tra lại khi nâng Compose.
- Cấu hình HTTP không mã hoá của Android chỉ ở bản debug. **Chưa tách** cho iOS: `NSAllowsLocalNetworking` nằm trong `Info.plist` chung (chỉ cho địa chỉ cục bộ); tách theo cấu hình build khi có bản phát hành.
- `RouteMapScreen` rời composition khi sang tab Khám phá: GPS chỉ thành điểm đi khi chưa có điểm đi hoặc người dùng bấm "Dùng vị trí của tôi" (lỗi tìm thấy khi chạy thật: request "Chỉ đường" bị huỷ).
- Chấp nhận cho M06: mất điểm đi/đến và kết quả khi hệ điều hành tắt process (process death); camera bản đồ về mặc định khi chuyển tab nếu chưa có điểm; chọn điểm chỉ bằng nhấn giữ (chưa có cách thay thế cho trình đọc màn hình). Xử lý ở M06b/NAV-1.

## Rủi ro

- Lịch tàu vẫn là feed giả định (ADR-020): gate M06 chỉ có thể kết luận "đạt về kỹ thuật, dữ liệu tuyến giả định".
- Ktor 3.6.1 build bằng Kotlin 2.3.21 trong khi dự án dùng 2.4.20: hướng tương thích được Kotlin hỗ trợ (thư viện cũ hơn trình biên dịch), kiểm chứng bằng build Android và iOS.
