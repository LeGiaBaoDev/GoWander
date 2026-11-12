# GoWander — Tiến trình đến hết ngày 12-11-2026

Đọc file này đầu phiên mới, sau `AGENTS.md`. Chi tiết từng thay đổi nằm ở `docs/changes/change-11-11-2026.md` (mục 1–20).

## Quy tắc làm việc đã chốt

- **Chủ dự án tự chạy mọi lệnh Git ghi lịch sử** (add, commit, merge, push, tạo/đổi nhánh). Agent chỉ đọc Git và đưa lệnh (`AGENTS.md` rule 6).
- **Nhánh:** làm trên `develop`; `main` chỉ nhận merge khi ra version mới (`AGENTS.md` rule 3).
- **Mọi thay đổi** ghi vào `docs/changes/change-DD-MM-YYYY.md` theo mẫu trong `docs/changes/README.md`.
- Trả lời chủ dự án bằng tiếng Việt.

## Trạng thái milestone

| Milestone | Trạng thái | Ghi chú |
|---|---|---|
| M00 | PASS | Khung repo, tài liệu, `.gitignore`, `.env.example` |
| M01 | BLOCKED (dữ liệu chính thức) | Không có GTFS hợp pháp cho TP.HCM. Đi tiếp bằng feed Metro số 1 giả định (ADR-020) |
| M02 | PASS về công nghệ (12-11-2026) | Graph, phục vụ, `planConnection`, 10/10 route case; chi tiết `docs/changes/change-12-11-2026.md` mục 1–8 |
| M03 | NOT STARTED | Phần Android bị chặn: máy chưa cài Android Studio/SDK |
| M04–M10 | Chưa bắt đầu | |

## Tài sản quan trọng

- Feed GTFS giả định: `data/assumed/hcmc-metro-l1-gtfs/`, sinh bởi `scripts/build_metro_l1_assumed_gtfs.py`.
- Test case tuyến: `data/test-cases/route-cases.csv` (10 trường hợp, kết quả mong đợi suy từ feed, chưa chạy).
- Nguồn dữ liệu: `data/sources/source-inventory.csv`; báo cáo `docs/research/transit-data-feasibility.md`.
- OSM: Geofabrik `vietnam-latest.osm.pbf` (ODbL), chưa tải.
- Image OTP: `opentripplanner/opentripplanner@sha256:8d54e5c589186707ee365417f2202dc878c451fa3001b8edff07019531100933`.

## Quyết định sản phẩm (ADR)

- ADR-020: feed Metro số 1 giả định, chỉ cho PoC kỹ thuật.
- ADR-021: thời tiết mức nhẹ qua Go API; dự kiến điều chỉnh thời gian đi bộ khi mưa sau M02.
- ADR-022: đăng nhập mật khẩu + Google, OTP qua email; ngôn ngữ Việt/Anh rồi Trung/Hàn; Admin 1–2 người (Owner/Editor).
- ADR-023: OTP 2.10.0.
- Hướng sản phẩm: giữ Core (Map, tìm tuyến, Discover); Journey + Memory là mở rộng sau Core, cá nhân hoá theo lựa chọn người dùng (xem `docs/handoffs/journey-memory-personalization.md`).

## Thiết kế (ngoài repository)

- Plugin Figma chính: `~/Downloads/GoWander-Design-Kit-v4/` (bảng màu → wireframe → UI → prototype; 31 màn mobile, 4 màn Admin; không gradient). Chưa chạy trong Figma thật; chờ chủ dự án gửi ảnh chụp.
- Plugin cũ `GoWander-Figma-Plugin` và `GoWander-UX-Lab-v3` giữ nguyên, không dùng tiếp.
- Tài khoản Figma gói Starter đã hết lượt gọi MCP, nên chỉnh Figma qua plugin.

## Việc tiếp theo: M04 (cập nhật 12-11-2026 chiều)

M02 xong (về công nghệ). Bắt đầu M04 — Go routing service; đọc `PIPELINE.md` §8, `infrastructure/otp/README.md` (API, lỗi, cách chạy OTP ở cổng 8088). Lập kế hoạch qua `/ecc:plan` và chờ chủ dự án duyệt.
