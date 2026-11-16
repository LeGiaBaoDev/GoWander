# Quan sát API (M09) — cách dựng và đọc số

API tự phơi số liệu ở `GET /metrics` (định dạng Prometheus text, **chỉ nhận kết nối loopback** vì API đang bind
`127.0.0.1`). Không có thư viện Prometheus trong Go: bộ đếm viết tay trong `backend/internal/platform/httpx/metrics.go`.

## Số liệu có gì

| Series | Nghĩa |
|---|---|
| `gowander_http_requests_total{route,method,status}` | Số request theo **pattern route** (`/api/v1/places/{id}`), method và lớp trạng thái (`2xx`…`5xx`) |
| `gowander_http_request_duration_seconds_*` | Histogram thời gian xử lý (bucket 0.1 / 0.5 / 2 / 5 s) |
| `gowander_ready` | 1 khi routing + database trả lời trong 1 giây (đọc lúc scrape) |
| `gowander_db_pool_{acquired,idle,total,max}` | Tình trạng pool kết nối |
| `gowander_account_google_verify_failures_total` | ID token Google bị từ chối (sai `aud`, Google đổi khoá…) |
| `gowander_email_send_failures_total` | Email xác minh / đặt lại mật khẩu không gửi được |
| `gowander_feedback_created_total` | Feedback app đã ghi (không có tên, email, nội dung) |

Nhãn `route` là pattern của chi, **không phải URL thật** — nhờ vậy số series có hạn và không lộ id người dùng.

## Chạy Prometheus trên máy dev

1. Tạo `infrastructure/monitoring/prometheus.yml` (không commit nếu cấu hình riêng):

   ```yaml
   global:
     scrape_interval: 30s
   rule_files:
     - /etc/prometheus/alerts.yml
   scrape_configs:
     - job_name: gowander-api
       static_configs:
         - targets: ["host.docker.internal:8090"]
   ```

2. Chạy (Linux thêm `--add-host=host.docker.internal:host-gateway`):

   ```bash
   docker run --rm --name gowander-prometheus -p 9090:9090 \
     -v "$PWD/infrastructure/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
     -v "$PWD/infrastructure/monitoring/alerts.yml:/etc/prometheus/alerts.yml:ro" \
     prom/prometheus:v2.55.1
   ```

3. Mở `http://localhost:9090/targets` (phải `UP`) và `http://localhost:9090/alerts` (7 luật trong `alerts.yml`).

> Trên macOS, Prometheus trong Docker không thấy `127.0.0.1:8090` của máy; hoặc dùng `host.docker.internal`, hoặc chạy
> Prometheus trực tiếp trên máy. Đừng đổi API sang nghe `0.0.0.0` chỉ để scrape.

## Kiểm nhanh không cần Prometheus

```bash
curl -s http://127.0.0.1:8090/metrics | head -20
curl -s http://127.0.0.1:8090/metrics | grep -E "gowander_ready|gowander_db_pool_acquired"
```

## Giới hạn đã biết

- **Một tiến trình = một registry**: nhiều instance thì Prometheus scrape từng cái (đúng chuẩn), không có tổng hợp.
- **Không có Grafana/dashboard** trong repo này: số liệu đủ để cảnh báo và để đọc bằng `curl`; dashboard dựng ở M10 khi
  có máy chủ thật.
- `/metrics` chỉ loopback. Khi API ra Internet phải thêm `METRICS_TOKEN` hoặc listener riêng **trước** khi mở cổng.
- Cảnh báo cần một Alertmanager (hoặc Grafana) để gửi đi; file `alerts.yml` chỉ định nghĩa luật.
