# Runbook sự cố — GoWander API

> Dùng khi có cảnh báo (xem `infrastructure/monitoring/alerts.yml`) hoặc khi chủ dự án báo app lỗi.
> Nguyên tắc: **đọc trước khi sửa**, ghi lại `requestId` và thời điểm, không xoá log/dữ liệu để "cho sạch".

## 0. Ba lệnh đầu tiên, luôn luôn

```bash
curl -s http://127.0.0.1:8090/health/live      # tiến trình còn sống?
curl -s http://127.0.0.1:8090/health/ready     # routing + database ổn?
curl -s http://127.0.0.1:8090/metrics | grep -E "gowander_ready|5xx" | head
docker ps --format '{{.Names}}\t{{.Status}}'   # Postgres, OTP, Photon, Mailpit
```

Log: `backend/var/api.log` (JSON, mỗi dòng có `requestId`, `status`, `durationMs`). Tìm theo mã trong câu lỗi app hiện:

```bash
grep '"requestId":"<mã>"' backend/var/api.log
grep '"level":"ERROR"' backend/var/api.log | tail -20
```

## 1. API không chạy

**Dấu hiệu:** `curl /health/live` không trả lời; Prometheus `up == 0`.

```bash
tail -50 backend/var/api.log          # lỗi cấu hình, cổng bận, DB không kết nối
lsof -nP -iTCP:8090 -sTCP:LISTEN      # ai đang giữ cổng
```

**Sửa:** dừng tiến trình cũ, chạy lại:

```bash
cd backend && set -a && . ../.env && set +a && go build -o bin/api ./cmd/api
(nohup ./bin/api > var/api.log 2>&1 &)
```

Nếu log báo `DATABASE_URL is required` hoặc lỗi cấu hình khác: `.env` bị thiếu/sai — **không dán giá trị vào chat**.

## 2. Không sẵn sàng

**Dấu hiệu:** `gowander_ready == 0`, `/health/ready` trả 503.

- Log có `"msg":"not ready","dependency":"database"` → Postgres. `docker ps` xem container `gowander-postgres`; nếu
  thoát: `docker compose -f infrastructure/postgres/compose.yaml up -d` (xem `infrastructure/postgres/README.md`).
  Kiểm kết nối: `docker exec gowander-postgres pg_isready -U postgres`.
- `"dependency":"routing"` → OpenTripPlanner. `docker ps` xem `gowander-otp-*`; OTP nạp graph có thể mất vài phút sau khi
  khởi động lại (xem log container). Trong lúc chờ, app vẫn mở được nhưng tìm tuyến trả `ROUTING_UNAVAILABLE`.
- Cả hai OK mà vẫn 503: kiểm `statement_timeout` và tải; `/metrics` xem `gowander_db_pool_acquired`.

## 3. Lỗi 5xx

```bash
curl -s http://127.0.0.1:8090/metrics | grep 'status="5xx"'
grep '"level":"ERROR"' backend/var/api.log | tail -20
```

Nhóm theo `route` để biết phần nào hỏng. Nếu là một route duy nhất: đọc log theo `requestId` của lần lỗi gần nhất.
Nếu toàn bộ: quay lại mục 2. Không bao giờ trả stack trace ra ngoài — API đã gói mọi lỗi thành `INTERNAL_ERROR` kèm
`requestId`; câu đó chính là chìa khoá để tra log.

## 4. Chậm

```bash
curl -s http://127.0.0.1:8090/metrics | grep duration_seconds_count
curl -s http://127.0.0.1:8090/metrics | grep _sum | head
```

- `/api/v1/routes/plan` chậm: OTP. Kiểm `curl -s $OTP_URL/otp/routers` trong container OTP, xem CPU/RAM máy.
- `/api/v1/places*`, `/api/v1/geocode*`: database hoặc Photon. `docker stats --no-stream`.
- Đừng tăng `OTP_TIMEOUT` để "cho hết chậm": người dùng đã bỏ đi trước khi có kết quả.

## 5. Database

```bash
docker exec -e PGPASSWORD="$POSTGRES_SUPERUSER_PASSWORD" gowander-postgres \
  psql -U postgres -d gowander -c "SELECT pid, state, wait_event_type, left(query, 120) FROM pg_stat_activity WHERE datname='gowander' ORDER BY query_start;"
```

- Nhiều `idle in transaction` → một request bị treo giữ transaction. Tìm `requestId` trong log, sửa code, đừng `kill` bừa.
- `gowander_db_pool_acquired` gần `max` lâu: truy vấn chậm hoặc thiếu index; xem `EXPLAIN (ANALYZE, BUFFERS)`.
- **Không** chạy `VACUUM FULL`, `DROP`, `TRUNCATE` trên database dev/production để "thử". Khôi phục dữ liệu là việc của M08 (backup + PITR) — hiện chưa có, nên cẩn trọng gấp đôi.

## 6. Email

**Dấu hiệu:** `gowander_email_send_failures_total` tăng; app báo "Chưa gửi được email".

```bash
grep -i "smtp\|email" backend/var/api.log | tail -10
```

- Gmail: kiểm `SMTP_HOST/PORT/USERNAME/PASSWORD/FROM` trong `.env` (mật khẩu ứng dụng, không phải mật khẩu Gmail), và hạn
  mức gửi/ngày của tài khoản.
- Test nhanh bằng Mailpit: trỏ `SMTP_HOST=127.0.0.1`, `SMTP_PORT=1025`, mở `http://localhost:8025`.
- Người dùng không nhận mã ⇒ họ không xác minh được email và không đặt lại được mật khẩu. Ưu tiên sửa.

## 7. Đăng nhập Google

Xem `docs/runbooks/google-sign-in.md` (bảng lỗi: audience, Test users, SHA-1, URL scheme, `LINK_REQUIRED`).

## 8. Sau sự cố

1. Ghi vào `docs/changes/` mục cùng ngày: triệu chứng, `requestId`, nguyên nhân, cách sửa, bằng chứng.
2. Nếu do một giả định sai (ví dụ "máy ảo có tài khoản Google"), thêm một dòng vào runbook tương ứng.
3. Nếu cần một cảnh báo mới, thêm vào `infrastructure/monitoring/alerts.yml` **và** mục tương ứng ở đây — mỗi cảnh báo
   phải có chỗ đọc tiếp.
