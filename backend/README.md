# GoWander backend (Go)

API duy nhất giữa app mobile và hệ thống tìm tuyến. App chỉ phụ thuộc vào [`api/openapi/openapi.yaml`](api/openapi/openapi.yaml), không phụ thuộc OpenTripPlanner. Quyết định: [ADR-027](../docs/decisions/ADR-027-go-routing-api.md).

> **Dữ liệu giả định:** OTP đang chạy trên feed Metro số 1 giả định (ADR-020). Mọi response có `dataStatus: "ASSUMED"`.

## Yêu cầu

- Go 1.27.4 (`go.mod`), một thư viện ngoài: `github.com/go-chi/chi/v5` v5.5.0.
- OTP đang chạy để tìm tuyến: `infrastructure/otp/scripts/serve.sh start` (cần Docker; xem `infrastructure/otp/README.md`).

## Chạy

```bash
cd backend
go run ./cmd/api
```

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `HTTP_ADDR` | `:8090` | Địa chỉ lắng nghe |
| `OTP_URL` | `http://127.0.0.1:8088` | OTP (không được chứa user/mật khẩu) |
| `OTP_TIMEOUT` | `8s` | Thời gian tối đa mỗi lần gọi OTP, trong khoảng (0, 30s] |
| `ROUTING_DATA_STATUS` | `ASSUMED` | `ASSUMED` hoặc `OFFICIAL`, trả về trong mọi response tìm tuyến |

Cấu hình sai thì API dừng ngay khi khởi động và ghi lý do. Log dạng JSON ra stdout; mỗi request có `X-Request-Id` ngẫu nhiên, cũng có trong log và trong body lỗi. `Ctrl-C`/`SIGTERM` tắt êm (chờ tối đa 15 giây cho request đang chạy).

## Endpoint

- `GET /health/live`: luôn 200 khi process còn chạy.
- `GET /health/ready`: 200 khi OTP trả lời health trong 1 giây, ngược lại 503.
- `POST /api/v1/routes/plan`: tìm tuyến.

```bash
curl -s -X POST localhost:8090/api/v1/routes/plan -d '{
  "origin": {"lat": 10.770775, "lon": 106.697569},
  "destination": {"lat": 10.879569, "lon": 106.81417},
  "dateTime": "2026-11-17T09:00:00+07:00"
}'
```

Lỗi luôn có dạng `{"error":{"code","message","requestId"}}`; bảng mã và HTTP status ở ADR-027.

## Kiểm thử

```bash
go vet ./...
go test -race ./...                                              # unit test, không cần OTP
go test -tags integration -v -run TestRouteCases ./cmd/api/      # 10 route case qua API tới OTP thật
```

Integration test **báo lỗi** (không bỏ qua) khi OTP chưa chạy. Kết quả lần chạy gate M04: [`tests/results/route-cases-2026-11-12.md`](tests/results/route-cases-2026-11-12.md).

## Cấu trúc

```
cmd/api/                    khởi động, health, nối route (newHandler), integration test
internal/platform/config/   đọc và kiểm tra biến môi trường
internal/platform/httpx/    router, request ID, access log, recover, JSON lỗi chung
internal/mobility/          domain, interface RoutingProvider, kiểm tra input, handler, mã lỗi
internal/mobility/otp/      adapter OTP 2.10 (truy vấn plan.graphql nhúng sẵn) — nơi duy nhất biết schema OTP
api/openapi/openapi.yaml    hợp đồng API
```
