# GoWander

**Move Smart. Explore More.** Ứng dụng Android/iOS đồng hành cùng du khách tại TP.HCM: tìm tuyến bus / metro / đi bộ và khám phá địa điểm, món ăn, câu chuyện lịch sử, kèm một Admin Web để vận hành.

> **Trạng thái:** đang ở giai đoạn khởi tạo repository (M00). Chưa có ứng dụng, API hay dữ liệu nào chạy được. Tiến độ thật nằm ở [`PIPELINE.md`](./PIPELINE.md) mục 3.

## Kiến trúc dự kiến

```text
Android / iOS (Kotlin Multiplatform + Compose)  ─┐
                                                 ├─ HTTPS ─ Go API (modular monolith) ─ PostgreSQL + PostGIS
Admin Web (framework chưa chọn)                 ─┘                  │
                                                                    └─ OpenTripPlanner (GTFS + OSM)
```

Mobile và Admin chỉ nói chuyện với Go API; không gọi thẳng OpenTripPlanner hay database. Chi tiết: [`OVERVIEW.md`](./OVERVIEW.md).

## Cấu trúc repository

| Thư mục | Vai trò |
|---|---|
| `mobile/` | Ứng dụng Android/iOS (KMP) |
| `backend/` | Go API và business logic |
| `admin/` | Admin Web |
| `infrastructure/` | Docker, OTP, PostgreSQL, backup, monitoring |
| `data/` | Danh mục nguồn dữ liệu transit và test case (dữ liệu thô không commit) |
| `docs/` | Thiết kế, quyết định (ADR), nghiên cứu, runbook, nhật ký thay đổi |
| `scripts/` | Công cụ hỗ trợ phát triển |

Các thư mục code hiện còn trống và sẽ được lấp dần theo milestone.

## Yêu cầu công cụ

| Công cụ | Dùng cho | Cần từ milestone |
|---|---|---|
| Git | Quản lý mã nguồn | M00 |
| Docker + Docker Compose | PostgreSQL/PostGIS, OpenTripPlanner | M02 |
| JDK 21 | Toolchain Kotlin/Android | M03 |
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

Hiện chưa có lệnh chạy, build hay test nào vì chưa có code. Mục này sẽ được cập nhật khi từng milestone tạo ra thứ chạy được.

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
- `main` chỉ nhận merge từ `develop` sau mỗi release lớn.
- Commit theo Conventional Commits (`feat`, `fix`, `docs`, `chore`, …).
- Mọi thay đổi phải có một mục trong `docs/changes/change-DD-MM-YYYY.md`.
- Không commit secrets, `.env`, dữ liệu GTFS/OSM thô, database volume hay backup.
