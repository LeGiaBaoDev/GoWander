# GoWander

**Move Smart. Explore More.** Ứng dụng Android/iOS đồng hành cùng du khách tại TP.HCM: tìm tuyến bus / metro / đi bộ và khám phá địa điểm, món ăn, câu chuyện lịch sử, kèm một Admin Web để vận hành.

> **Trạng thái:** đã có app Android/iOS, Go API, Admin và tài khoản app; đang triển khai M09 (CI + observability). Dữ liệu transit còn giả định và M08 backup/PITR còn bị chặn, chưa đạt cổng production pilot. Tiến độ và giới hạn kiểm chứng nằm ở [`PIPELINE.md`](./PIPELINE.md) mục 3.

## Kiến trúc dự kiến

```text
Android / iOS (Kotlin Multiplatform + Compose)  ─┐
                                                 ├─ HTTPS ─ Go API (modular monolith) ─ PostgreSQL + PostGIS
Admin Web (Go templates + htmx)                 ─┘                  │
                                                                    └─ OpenTripPlanner (GTFS + OSM)
```

Mobile và Admin chỉ nói chuyện với Go API; không gọi thẳng OpenTripPlanner hay database. Chi tiết: [`OVERVIEW.md`](./OVERVIEW.md).

## Cấu trúc repository

| Thư mục | Vai trò |
|---|---|
| `mobile/` | Ứng dụng Android/iOS (KMP) |
| `backend/` | Go API và business logic |
| `backend/internal/admin/` | Admin Web, nhúng trong Go API |
| `infrastructure/` | Docker, OTP, PostgreSQL, backup, monitoring |
| `data/` | Danh mục nguồn dữ liệu transit và test case (dữ liệu thô không commit) |
| `docs/` | Thiết kế, quyết định (ADR), nghiên cứu, runbook, nhật ký thay đổi |
| `scripts/` | Công cụ hỗ trợ phát triển |

Lệnh chạy từng thành phần nằm trong README của `backend/`, `mobile/` và `infrastructure/`.

## Yêu cầu công cụ

| Công cụ | Dùng cho | Cần từ milestone |
|---|---|---|
| Git | Quản lý mã nguồn | M00 |
| Docker + Docker Compose | PostgreSQL/PostGIS, OpenTripPlanner | M02 |
| JDK 25 | Toolchain Kotlin/Android hiện tại | M03 |
| Android Studio + Android SDK | Build và chạy Android | M03 |
| Xcode | Build và chạy iOS | M03 |
| Go | Backend | M04 |
| Python 3 | Script kiểm tra dữ liệu | M01 |

Phiên bản đã ghi nhận trên máy phát triển và công cụ còn thiếu: [`docs/development/toolchain-versions.md`](./docs/development/toolchain-versions.md).

## Bắt đầu

```bash
git clone git@github.com:LeGiaBaoDev/GoWander.git
cd GoWander
cp .env.example .env    # chỉnh giá trị cho máy của bạn; không commit .env
```

Xem [backend](backend/README.md), [mobile](mobile/README.md) và [PostgreSQL](infrastructure/postgres/README.md) để chạy local. Quy trình CI, database test cách ly và phạm vi kiểm chứng: [runbook CI](docs/runbooks/ci.md).

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [`OVERVIEW.md`](./OVERVIEW.md) | Sản phẩm, kiến trúc, ranh giới, những gì không làm |
| [`PIPELINE.md`](./PIPELINE.md) | Milestone M00–M11+, cổng kiểm tra, trạng thái |
| [`AGENTS.md`](./AGENTS.md) | Quy trình cho coding agent, agent/skill theo milestone |
| [`docs/design/`](./docs/design/README.md) | Sáu tài liệu thiết kế đầy đủ, chia theo mục |
| [`docs/changes/`](./docs/changes/README.md) | Nhật ký mọi thay đổi theo ngày |

## Quy ước

- Nhánh làm việc chính là `develop`. Mỗi milestone hoặc task làm trên một nhánh ngắn tách từ `develop` và merge lại vào `develop` khi qua cổng kiểm tra.
- `main` chỉ nhận merge từ `develop` mỗi khi phát hành một version mới (ví dụ `v0.1.0`).
- Commit theo Conventional Commits (`feat`, `fix`, `docs`, `chore`, …).
- Mọi thay đổi phải có một mục trong `docs/changes/change-DD-MM-YYYY.md`.
- Không commit secrets, `.env`, dữ liệu GTFS/OSM thô, database volume hay backup.
