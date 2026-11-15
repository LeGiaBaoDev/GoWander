# ADR-036 — Lưu kế hoạch và địa điểm đã lưu trên máy: một chuỗi JSON trong kho cài đặt của hệ điều hành

- **Trạng thái:** Accepted · **Ngày:** 15-11-2026
- **Liên quan:** ADR-034 (UX-2, phase P5), `PIPELINE.md` §5c (quyết định về đăng nhập, 15-11-2026), `home/AppStore.kt`, `Prefs.kt`

## Bối cảnh

Kế hoạch tự lên và địa điểm đã lưu chỉ nằm trong bộ nhớ: tắt app là mất. Chủ dự án chốt 15-11-2026: các chức năng này chạy được không cần đăng nhập; đăng nhập chỉ hỏi khi cần tài khoản (đồng bộ, chia sẻ, đánh giá).

## Quyết định

- `AppStore` ghi toàn bộ kế hoạch + địa điểm đã lưu thành **một chuỗi JSON** (kotlinx.serialization, đã có sẵn) vào `Prefs` sau mỗi lần thay đổi, khoá `store.v1`. Android: SharedPreferences (file riêng của app); iOS: NSUserDefaults.
- Lần đầu mở app (chưa có gì được lưu) hiện các kế hoạch mẫu. Dữ liệu không đọc được (hỏng, hoặc phiên bản sau đổi cấu trúc không tương thích) thì quay về kế hoạch mẫu và ghi một dòng log, không làm app dừng. Trường lạ trong JSON được bỏ qua.
- Không thêm thư viện.

## Không chọn

- **SQLDelight / Room:** hợp khi dữ liệu lớn hoặc cần truy vấn; ở đây là vài chục kế hoạch, đọc ghi cả khối là đủ, và thêm thư viện + migration là thừa cho phạm vi pilot.
- **Lưu trên server:** cần tài khoản, trái với quyết định "không bắt đăng nhập".

## Hệ quả và giới hạn

- Dữ liệu chỉ ở trên máy đó: gỡ app hoặc đổi máy là mất (Android có thể khôi phục qua sao lưu của Google tuỳ cài đặt máy; chưa kiểm chứng). Khi có tài khoản (COM-1) thêm bước đồng bộ, đọc từ chính chuỗi này.
- Không mã hoá riêng: nội dung là tên kế hoạch và địa điểm, không có bí mật; nằm trong vùng riêng của app.
- **Nâng cấp khi:** một người dùng có hàng trăm kế hoạch, cần tìm kiếm trong kế hoạch, hoặc cần đồng bộ từng phần. Lúc đó chuyển sang SQLDelight và nhập dữ liệu từ `store.v1`.
- Đổi cấu trúc `Plan` / `PlanItem` không tương thích thì phải đổi khoá (`store.v2`) và viết bước chuyển, nếu không người dùng mất kế hoạch.
