# ADR-030 — Giao diện mobile theo GoWander Design Kit v4

- **Trạng thái:** Accepted cho M06b · **Ngày:** 14-11-2026
- **Liên quan:** `PIPELINE.md` M06b, ADR-029 (M06 làm chức năng trước), `docs/design/03-product-ux-v4.0/04-user-flows-screens-design-system.md`

## Nguồn thiết kế

Design Kit v4 là một Figma plugin do chủ dự án cung cấp (`GoWander-Design-Kit-v4/src/main.js`, ngày 12-11-2026). Plugin sinh file Figma từ mã nguồn, nên token (màu, cỡ chữ, bo góc), icon (SVG) và bố cục từng màn được lấy **trực tiếp từ mã nguồn**, không đo lại từ ảnh. Màn dùng cho M06b: M1–M8 (Bản đồ, gợi ý khi gõ, tìm tuyến, chọn trên bản đồ, kết quả, chi tiết tuyến, không có tuyến, mất mạng), D1–D2 (Khám phá, chi tiết địa điểm).

## Quyết định

1. **Font:** Be Vietnam Pro (Regular, Medium, SemiBold, Bold), giấy phép SIL OFL 1.1 (`docs/licenses/BeVietnamPro-OFL.txt`), tải từ kho `google/fonts`, đóng gói qua Compose Resources (`org.jetbrains.compose.components:components-resources` 1.12.1, cùng bản Compose). Không tải font lúc chạy.
2. **Token:** đúng theme `ui` của kit: `bg #FFFFFF`, `subtle #F6FAFA`, `line #E5F2F1`, `ink #123C48`, `muted #52717A`, `primary #047A7D`, `soft #DDF7F0`, `warn #FFF4D6`, `accent #C8234F`, `accentSoft #FDE7EC`, `metro #0079B8`, `bus #08805F`. Bo góc: thẻ 20, nút 14, ô tìm kiếm 16, chip 999. Bóng đổ của kit (y 6, blur 20, ink 14%).
3. **Icon:** SVG nét 2 px, khung 24 của kit, sinh thành `ImageVector` (file sinh tự động `ui/KitIcons.kt`, không sửa tay).
4. **Thanh tab:** 3 tab thật — Map, Discover, Tìm tuyến (nút tròn ở giữa) — theo kiểu dáng kit; Hành trình và Profile thêm khi có tính năng (chủ dự án chọn 14-11-2026).
5. **Ga metro trong tìm kiếm:** 14 ga Metro số 1 lấy từ OSM (toạ độ thật, ODbL) thành danh mục `station` trong seed Discover (chủ dự án chọn 14-11-2026).
6. **Ngoài phạm vi M06b:** thời tiết, Hành trình, Profile, câu chuyện, lưu tuyến/địa điểm, thêm vào chuyến, nhắc ga xuống, chế độ tối, tiếng Anh, bản đồ minh hoạ màu của kit (app dùng bản đồ thật OpenFreeMap). Ảnh địa điểm: dữ liệu chưa có ảnh → ô biểu tượng như bản wireframe của kit.

## Rủi ro

- Font tăng kích thước app khoảng 550 KB.
- Kit có số liệu mẫu (thời gian, giá vé, ảnh); app chỉ hiển thị dữ liệu thật từ API, nên một số chỗ trông "trống" hơn bản Figma.
