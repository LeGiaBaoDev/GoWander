# ADR-023 — Khoá phiên bản OpenTripPlanner cho M02

- **Trạng thái:** Accepted cho PoC M02 · **Ngày:** 12-11-2026
- **Liên quan:** `PIPELINE.md` mục 6 (M02, task 1), ADR-020 (feed Metro số 1 giả định)

## Quyết định

Dùng **OpenTripPlanner 2.10.0**, khoá theo digest của image Docker, không dùng tag `latest`.

```text
opentripplanner/opentripplanner@sha256:8d54e5c589186707ee365417f2202dc878c451fa3001b8edff07019531100933
```

## Bằng chứng (kiểm tra ngày 12-11-2026 trên máy phát triển)

| Mục | Kết quả | Cách kiểm tra |
|---|---|---|
| Phiên bản OTP | 2.10.0, commit `521cd5d8acaae628878f43bf5946f4942c118548` | `docker run --rm <image> --version` |
| Ngày phát hành tag | 09-09-2026 | Docker Hub API |
| Java trong image | OpenJDK 25.0.4 LTS (Temurin) | `docker run --rm --entrypoint java <image> -version` |
| Kiến trúc | Có bản `amd64` và `arm64`; máy phát triển dùng `arm64` | Docker Hub API, `docker image inspect` |
| Dung lượng image | khoảng 291 MB | `docker image inspect` |
| Entrypoint | `/docker-entrypoint.sh` | `docker image inspect` |
| Docker trên máy | Engine 29.6.2, cấp cho Docker khoảng 9,4 GB RAM | `docker info` |

Java nằm sẵn trong image, nên máy phát triển không cần cài thêm JDK cho OTP.

## Vì sao không chọn bản khác

- **Tag `latest` và các tag `2.11.0_<ngày>`:** là bản dựng hằng ngày của phiên bản đang phát triển, thay đổi nhiều lần mỗi ngày. `PIPELINE.md` cấm dùng `latest` làm phụ thuộc lâu dài.
- **2.9.0, 2.8.x:** cũ hơn, không có lý do cụ thể để chọn.

## Chưa kiểm chứng

- **API tìm tuyến.** Theo tài liệu OTP, bản 2.x dùng GraphQL thay cho REST cũ. Tên trường truy vấn (ví dụ `planConnection`) và đường dẫn endpoint **chưa được kiểm tra** trên bản 2.10.0; việc này làm ở task 4 của M02 bằng cách đọc schema thật.
- **Yêu cầu bộ nhớ** khi dựng graph với dữ liệu khu vực pilot: chưa đo, làm ở task 6.
- **Thư mục và tên file đầu vào** (GTFS, OSM, file cấu hình) mà bản 2.10.0 yêu cầu: chưa kiểm tra, làm ở task 2.

## Điều kiện xem lại

- Khi OTP phát hành 2.11.0 chính thức, hoặc khi bản 2.10.0 gặp lỗi chặn PoC.
- Đổi phiên bản phải cập nhật digest ở đây và chạy lại toàn bộ test case.
