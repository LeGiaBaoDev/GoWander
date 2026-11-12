# Địa điểm nhập tay (nguồn B, ADR-028 §4)

`places.csv` chứa địa điểm do người viết nhập, có mô tả. Script `scripts/build_discover_seed.py` gộp file này với dữ liệu OSM thành `data/discover/seed.sql`, và **dừng lại** nếu có dòng không hợp lệ (in số dòng).

| Cột | Bắt buộc | Ghi chú |
|---|---|---|
| `name` | có | Tên tiếng Việt |
| `name_en` | không | Tên tiếng Anh |
| `lat`, `lon` | có | Toạ độ WGS84, trong TP.HCM |
| `category` | có | Một trong: `museum`, `historic`, `attraction`, `worship`, `park`, `market`, `food`, `cafe`, `entertainment` |
| `short_description` | không | Tối đa 500 ký tự; **phải** lấy hoặc tóm tắt từ `source_url` |
| `address` | không | |
| `source_url` | có | Trang nguồn của mô tả (http/https) |
| `license` | có | Giấy phép của nội dung, ví dụ `CC BY-SA 4.0`, hoặc `Tự viết` nếu bạn tự viết toàn bộ |
| `attribution` | có | Dòng ghi công hiển thị cho người dùng |

Quy tắc:

- Không thêm địa điểm không có thật. Không chép mô tả từ nguồn không cho phép dùng lại.
- Mỗi dòng có `source_ref` = `curated/<slug của tên>`; đổi tên sẽ tạo địa điểm mới và ẩn bản cũ ở lần nạp sau.
- Sau khi sửa file: `python3 scripts/build_discover_seed.py` rồi `infrastructure/postgres/db.sh seed data/discover/seed.sql`.
