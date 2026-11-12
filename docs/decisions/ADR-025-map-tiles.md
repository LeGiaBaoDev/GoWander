# ADR-025 — Nguồn bản đồ nền cho M03

- **Trạng thái:** Accepted cho PoC M03 · **Ngày:** 12-11-2026
- **Liên quan:** `PIPELINE.md` mục 7 (M03), `OVERVIEW.md` mục rủi ro 4 (giấy phép tile), ADR-026 (phiên bản công cụ mobile)

## Quyết định

PoC M03 dùng style vector **OpenFreeMap "liberty"**: `https://tiles.openfreemap.org/styles/liberty`.

- Không cần đăng ký, không có API key, nên repo không chứa secret nào cho bản đồ.
- Là style mà tài liệu maplibre-compose v0.20.0 dùng làm ví dụ (`docs/src/content/docs/styling.mdx`).
- Dữ liệu bản đồ: OpenStreetMap (ODbL), lược đồ OpenMapTiles. Màn bản đồ phải hiện attribution "© OpenMapTiles © OpenStreetMap contributors".

## Bằng chứng (đọc ngày 12-11-2026)

| Mục | Nội dung | Nguồn |
|---|---|---|
| Giới hạn | Bản public miễn phí, không giới hạn số lượt xem hay request | https://openfreemap.org |
| Khoá/tài khoản | Không đăng ký, không API key, không cookie | https://openfreemap.org |
| Attribution | Bắt buộc với client khác MapLibre, in ấn và video: "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" | https://openfreemap.org |
| Điều khoản | Dịch vụ "as-is", không bảo đảm; có thể đổi điều khoản bất cứ lúc nào | https://openfreemap.org/tos/ |

## Vì sao không chọn bản khác

- **Tile raster của OpenStreetMap (`tile.openstreetmap.org`):** chính sách sử dụng hạn chế app dùng nhiều và dùng thương mại; không hợp làm nền lâu dài.
- **MapTiler, Protomaps API, Stadia:** cần API key. Ở PoC chưa cần, và key không được nằm trong app.
- **Tự host tile (PMTiles, OpenMapTiles):** đúng hướng cho pilot nếu cần SLA, nhưng là việc hạ tầng, không phải của PoC bản đồ.

## Chưa kiểm chứng / rủi ro

- **Không có SLA.** Trước pilot (M10) phải quyết định lại: tự host tile hoặc dùng nhà cung cấp có hợp đồng.
- Chưa đọc hết điều khoản về dùng thương mại; liên hệ OpenFreeMap trước khi phát hành.
- Chất lượng dữ liệu bản đồ cho TP.HCM (tên đường tiếng Việt, ga Metro số 1) chưa đánh giá; xem khi chạy trên emulator/simulator.
