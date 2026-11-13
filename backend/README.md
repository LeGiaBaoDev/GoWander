# GoWander backend (Go)

API duy nhất giữa app mobile, hệ thống tìm tuyến và database Discover. App chỉ phụ thuộc vào [`api/openapi/openapi.yaml`](api/openapi/openapi.yaml), không phụ thuộc OpenTripPlanner. Quyết định: [ADR-027](../docs/decisions/ADR-027-go-routing-api.md).

> **Dữ liệu giả định:** OTP đang chạy trên feed Metro số 1 giả định (ADR-020). Mọi response có `dataStatus: "ASSUMED"`.

## Yêu cầu

- Go 1.27.4 (`go.mod`). Thư viện ngoài: `github.com/go-chi/chi/v5` v5.5.0 (ADR-027), `github.com/jackc/pgx/v5` v5.13.0 và `github.com/golang-migrate/migrate/v4` v4.20.1 (ADR-028).
- OTP đang chạy để tìm tuyến: `infrastructure/otp/scripts/serve.sh start` (cần Docker; xem `infrastructure/otp/README.md`).
- PostgreSQL/PostGIS đang chạy, đã migrate và nạp seed: xem [`infrastructure/postgres/README.md`](../infrastructure/postgres/README.md).

## Chạy

```bash
cd backend
set -a && . ../.env && set +a     # nạp DATABASE_URL, MIGRATE_DATABASE_URL… từ .env (không commit)
go run ./cmd/migrate up           # role owner; in "version: N dirty: false"
go run ./cmd/api
```

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `HTTP_ADDR` | `127.0.0.1:8090` | Địa chỉ lắng nghe; mặc định chỉ máy này gọi được (Android emulator vẫn tới qua `10.0.2.2`). Container/máy chủ đặt riêng |
| `OTP_URL` | `http://127.0.0.1:8088` | OTP (không được chứa user/mật khẩu) |
| `OTP_TIMEOUT` | `8s` | Thời gian tối đa mỗi lần gọi OTP, trong khoảng (0, 30s] |
| `ROUTING_DATA_STATUS` | `ASSUMED` | `ASSUMED` hoặc `OFFICIAL`, trả về trong mọi response tìm tuyến |
| `DATABASE_URL` | **bắt buộc** | `postgres://gowander_app:…` (role chỉ đọc). Máy chủ thật dùng `sslmode=verify-full` |
| `MIGRATE_DATABASE_URL` | (chỉ cho `cmd/migrate` và test) | `postgres://gowander_owner:…` (role sở hữu schema) |

Cấu hình sai thì API dừng ngay khi khởi động và ghi lý do. Log dạng JSON ra stdout; mỗi request có `X-Request-Id` ngẫu nhiên, cũng có trong log và trong body lỗi. `Ctrl-C`/`SIGTERM` tắt êm (chờ tối đa 15 giây cho request đang chạy).

## Endpoint

- `GET /health/live`: luôn 200 khi process còn chạy.
- `GET /health/ready`: 200 khi OTP **và** database trả lời trong 1 giây, ngược lại 503 (log ghi dependency nào lỗi).
- `POST /api/v1/routes/plan`: tìm tuyến.
- `GET /api/v1/place-categories`, `GET /api/v1/places` (lọc `category`, tìm `q` không dấu, phân trang `cursor`), `GET /api/v1/places/nearby` (`lat`, `lon`, `radiusM` ≤ 5000), `GET /api/v1/places/{id}`: Discover, chỉ địa điểm `PUBLISHED`. Mỗi địa điểm có `source` (nguồn, giấy phép, ghi công) phải hiển thị cho người dùng.

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
set -a && . ../.env && set +a
go test -tags integration -p 1 ./...                            # migration, Discover (database gowander_test), 10 route case (OTP)
```

Integration test **báo lỗi** (không bỏ qua) khi OTP hoặc database chưa chạy. Phải chạy tuần tự (`-p 1`) vì các package dùng chung database `gowander_test`; test chỉ đụng database có đuôi `_test`. Kết quả gate M04: [`tests/results/route-cases-2026-11-12.md`](tests/results/route-cases-2026-11-12.md). Kế hoạch truy vấn Discover (M05): [`tests/results/discover-queries-2026-11-13.md`](tests/results/discover-queries-2026-11-13.md).

## Cấu trúc

```
cmd/api/                    khởi động, health, nối route (newHandler), integration test
cmd/migrate/                chạy migration: up | down (1 bước) | version | force N
migrations/                 file SQL NNNN_*.up.sql / .down.sql, nhúng vào binary
internal/discover/          Discover: kiểu dữ liệu, truy vấn PostGIS (store.go), handler
internal/platform/database/ pool pgx, migrator, TestDatabaseURL
internal/platform/config/   đọc và kiểm tra biến môi trường
internal/platform/httpx/    router, request ID, access log, recover, JSON lỗi chung
internal/mobility/          domain, interface RoutingProvider, kiểm tra input, handler, mã lỗi
internal/mobility/otp/      adapter OTP 2.10 (truy vấn plan.graphql nhúng sẵn) — nơi duy nhất biết schema OTP
api/openapi/openapi.yaml    hợp đồng API
```
