# ADR-024 — Giám sát hệ thống và xem lỗi trong Admin

- **Trạng thái:** Proposed (dự kiến) · **Ngày:** 12-11-2026 · chốt lại khi bắt đầu M07/M09
- **Liên quan:** ADR-022 (Admin 1–2 người, vai trò Owner/Editor); `PIPELINE.md` §11 (M07), §12 (M08), §13 (M09); `docs/design/03-product-ux-v4.0/05-admin-web.md` §13; `docs/design/04-technical-architecture-v2.1/06-restore-drills-runbook-monitoring.md` §20–22

## Bối cảnh

Thiết kế Admin có trang **System Health** (gồm Backup & Recovery Dashboard) và **Audit Logs**, và liệt kê 7 lớp cần giám sát: hạ tầng (CPU, RAM, ổ đĩa, mạng), Go API (request, tỷ lệ lỗi, độ trễ), PostgreSQL, OTP, tác vụ nền, nghiệp vụ, backup. Thiết kế yêu cầu log có cấu trúc và cảnh báo có mức độ, ngưỡng, runbook, người phụ trách, nhưng **không nói log và biểu đồ được xem ở đâu**.

Admin chỉ có 1–2 người (ADR-022). Gate M09 yêu cầu tình trạng restore phải xem được **từ bên ngoài database đang bị restore**.

## Quyết định (dự kiến)

1. **Không tự viết** trình xem log hay biểu đồ CPU/RAM trong Admin. Dùng công cụ có sẵn, tự host:
   - Phương án chính: Prometheus (số liệu) + Loki (log) + Grafana (biểu đồ, cảnh báo).
   - Phương án nhẹ nếu tài nguyên máy chủ ít: Uptime Kuma (còn sống/chết, thời gian phản hồi) + log có cấu trúc ra file, xem bằng `docker logs`/`jq`.
   - Chọn phương án cụ thể ở M09, sau khi biết cấu hình máy chủ pilot.
2. Trang **System Health** trong Admin chỉ hiện **tóm tắt**: trạng thái xanh/vàng/đỏ cho từng lớp, các lỗi/cảnh báo gần nhất, tuổi backup gần nhất, lần thử restore gần nhất, phiên bản graph OTP; mỗi mục có link sang Grafana (hoặc công cụ đã chọn).
3. Số liệu giám sát và backup **không lấy từ database ứng dụng** đang được bảo vệ, để database hỏng thì vẫn xem được tình trạng.
4. Quyền theo ADR-022: **Owner** xem System Health, chi tiết backup, gửi yêu cầu restore; **Editor** không xem. Không có nút restore production trực tiếp; yêu cầu restore cần mã sự cố, lý do, bản backup nguồn, lượng dữ liệu dự kiến mất, kế hoạch huỷ và người duyệt (thiết kế §21.3, `AGENTS.md` §3).
5. **Audit Logs** vẫn là trang riêng trong Admin (dữ liệu nghiệp vụ, nằm trong DB ứng dụng, chỉ đọc, không sửa được).

## Vì sao

- 1–2 người không đủ sức duy trì một hệ thống biểu đồ/log tự viết; công cụ có sẵn đã giải quyết lưu trữ, truy vấn, cảnh báo.
- Tách nguồn số liệu khỏi DB ứng dụng là điều kiện của gate M09.
- Tóm tắt trong Admin giữ một chỗ vào duy nhất cho chủ dự án mà không nhân đôi công cụ.

## Hệ quả

- M09 cần thêm dịch vụ giám sát vào hạ tầng (tốn RAM/ổ đĩa; ước lượng khi chọn phương án).
- Go API, OTP, PostgreSQL phải xuất số liệu ở dạng công cụ đọc được (ví dụ endpoint `/metrics`, log JSON có request ID).
- Admin cần một API tóm tắt tình trạng lấy từ công cụ giám sát, không truy vấn thẳng DB ứng dụng cho phần này.

## Chưa kiểm chứng

- Chưa đo tài nguyên Prometheus/Loki/Grafana hay Uptime Kuma trên máy chủ pilot.
- Đã kiểm tra ngày 12-11-2026 (M02 task 3): OTP 2.10.0 có `/otp/actuators/health` và `/otp/actuators/prometheus` (trả 200) khi bật `ActuatorAPI` trong `otp-config.json`. Chưa xem nội dung số liệu có đủ cho giám sát độ trễ/lỗi tìm tuyến không.

## Điều kiện xem lại

- Khi bắt đầu M07 (thiết kế trang System Health) và M09 (chọn công cụ).
- Khi số quản trị viên tăng hoặc cần nhiều vai trò hơn Owner/Editor.
