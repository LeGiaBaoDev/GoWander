09. PHASE 6 — FIRST END-TO-END
VERTICAL SLICE
### 9.1. Objective

Hoàn thành một luồng có giá trị sử dụng từ Mobile đến dữ liệu thật.

Luồng 1: Destination-first

Mobile Map → Select Origin/Destination → Go API → OTP → Route DTO → Route Polyline.

Luồng 2: Discover-to-Route

Discover → Place Detail → Directions → Go API → OTP → Route Options.

### Step 6.1 — Integrate Mobile API Client

Mobile dùng Ktor Client và DTO mapping.

### Step 6.2 — Build Route UI

Các states:

- ​   Loading.
- ​   Success.
- ​   No Route.
- ​   Timeout.
- ​   Technical Failure.
- ​   Retry.

### Step 6.3 — Integrate Place Detail

Khi chọn Directions, tọa độ địa điểm được dùng làm destination.

### Step 6.4 — End-to-End Verification

Kiểm thử trên Android/iOS:

- ​   Map renders.
- ​   Place details load.
- ​   Route request works.
- ​   Itinerary displays.
- ​   Polyline matches route.
- ​   Error states work.

### Exit Criteria

Hai luồng chính có thể chạy end-to-end.

Đây là milestone quan trọng nhất trước khi phát triển thêm Community và Rewards.

