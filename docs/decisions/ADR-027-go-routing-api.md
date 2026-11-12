# ADR-027 — API tìm tuyến bằng Go: thư viện, hợp đồng và mã lỗi

- **Trạng thái:** Accepted cho M04 · **Ngày:** 12-11-2026
- **Liên quan:** `PIPELINE.md` mục 8 (M04), `docs/design/06-development-guide-v1.0/phase-04-go-backend.md`, ADR-020 (feed giả định), ADR-023 (OTP 2.10.0), `backend/api/openapi/openapi.yaml`

## Bối cảnh

App mobile cần tìm tuyến mà không phụ thuộc vào schema GraphQL của OpenTripPlanner (OTP). M02 đã chứng minh OTP 2.10.0 trả được tuyến trên graph Metro số 1 (feed giả định). M04 dựng lớp Go nằm giữa.

## Quyết định

### 1. Thư viện

| Thành phần | Chọn | Lý do |
|---|---|---|
| Go | 1.27.4 (đã cài) | `go.mod` ghi `go 1.27` |
| Router | `github.com/go-chi/chi/v5` **v5.5.0** | PIPELINE ưu tiên chi; tương thích `net/http`; có sẵn middleware `RequestID`, `Recoverer` |
| Log | `log/slog` (thư viện chuẩn), JSON | Không cần thư viện ngoài |
| Cấu hình | biến môi trường, đọc bằng `os` | Ít biến; kiểm tra khi khởi động, sai thì dừng |
| Gọi OTP | `net/http` + truy vấn GraphQL nhúng bằng `embed` | Một truy vấn cố định, không cần thư viện GraphQL |
| OpenAPI | viết tay `backend/api/openapi/openapi.yaml` | Chưa sinh code; một endpoint |

Không thêm thư viện nào khác. Thêm thư viện sau này cần ghi vào ADR.

### 2. Endpoint

- `POST /api/v1/routes/plan`: tìm tuyến.
- `GET /health/live`: process còn chạy (luôn 200).
- `GET /health/ready`: gọi `GET {OTP_URL}/otp/actuators/health` với timeout 1 giây; 200 nếu OTP sẵn sàng, 503 nếu không.

### 3. Mã lỗi và HTTP status

Mọi lỗi có cùng dạng `{"error":{"code","message","requestId"}}`.

| Mã | HTTP | Khi nào |
|---|---|---|
| `INVALID_REQUEST` | 400 | JSON sai, body quá 64 KB, toạ độ ngoài phạm vi, thời gian thiếu múi giờ, `modes`/`timeType`/`maxResults` sai |
| `NO_ROUTE_FOUND` | 422 | OTP trả 0 hành trình kèm lỗi không thuộc hai dòng dưới (ví dụ `NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW`, `NO_STOPS_IN_RANGE`), hoặc trả 0 hành trình không kèm lỗi |
| `OUT_OF_COVERAGE` | 422 | OTP báo `OUTSIDE_BOUNDS` |
| `DATA_UNAVAILABLE` | 422 | OTP báo `OUTSIDE_SERVICE_PERIOD` (ngày không có lịch) |
| `ROUTING_TIMEOUT` | 504 | Gọi OTP quá `OTP_TIMEOUT` (mặc định 8 s, nhỏ hơn `apiProcessingTimeout` 10 s của OTP để Go chủ động cắt trước) |
| `ROUTING_UNAVAILABLE` | 503 | OTP không kết nối được, trả HTTP khác 200, trả lỗi GraphQL, trả JSON không đọc được, hoặc trả hành trình thiếu dữ liệu (không có leg, leg không có `mode`, leg phương tiện công cộng không có polyline) |
| `NOT_FOUND` | 404 / 405 | Sai đường dẫn hoặc phương thức (bổ sung sau review, để mọi lỗi cùng một dạng JSON) |
| `INTERNAL_ERROR` | 500 | Lỗi không lường trước (panic); stack ghi vào log, không trả cho client |

- Chọn **422** thay vì 200 + danh sách rỗng để app biết lý do không có tuyến mà không phải đoán.
- Nếu OTP có hành trình kèm cảnh báo (ví dụ `WALKING_BETTER_THAN_TRANSIT`), API vẫn trả 200 với các hành trình đó.
- Client huỷ request: không ghi response; chỉ ghi log.
- Mã lỗi OTP chưa biết (hoặc 0 hành trình mà không có mã nào) được xếp vào `NO_ROUTE_FOUND`, nhưng ghi log mức **Warn** kèm mã gốc, để mã mới không bị che.
- Giờ của leg ưu tiên giờ thời gian thực (`estimated`) khi OTP có, giống giờ của cả hành trình.
- Gọi OTP qua tối đa 16 kết nối đồng thời; request vượt mức chờ và nhận `ROUTING_TIMEOUT` thay vì dồn tải lên OTP. Không theo redirect.

### 4. Dữ liệu trả về

- Response có `dataStatus` lấy từ biến `ROUTING_DATA_STATUS` (`ASSUMED` hoặc `OFFICIAL`, mặc định `ASSUMED`), để app luôn biết tuyến đang dựa trên feed giả định (ADR-020).
- Mỗi leg có `realtime` = có dữ liệu thời gian thực hay không (lấy từ `estimated` của OTP; hiện luôn `false`).
- Polyline giữ nguyên định dạng của OTP (Google encoded polyline, độ chính xác 5), không giải mã ở server.

## Hệ quả

- App chỉ phụ thuộc `openapi.yaml`. Đổi OTP sang engine khác chỉ cần adapter mới cho interface `RoutingProvider`.
- Chưa có xác thực, giới hạn tần suất, CORS; làm khi có màn hình gọi thật (M06) và M07.
- Tuỳ chọn tìm tuyến nâng cao (tránh đi bộ, giới hạn chuyển tuyến) và `GetRouteDetails` chưa làm vì chưa có màn hình cần.

## Rủi ro

- Bảng ánh xạ lỗi dựa trên các mã OTP đã gặp ở 10 route case M02; mã mới chỉ được phát hiện qua log.
- chi v5.5.0 là bản mới nhất trên Go proxy (12-11-2026); nếu có lỗi thì hạ về v5.4.0 (cùng API).
