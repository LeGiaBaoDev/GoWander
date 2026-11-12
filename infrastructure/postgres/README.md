# PostgreSQL + PostGIS (local development)

Database cho Discover (M05). Quyết định: [ADR-028](../../docs/decisions/ADR-028-postgis-discover.md).

- Image `postgis/postgis:18-3.6` khoá theo digest (PostgreSQL 18, PostGIS 3.6). Chỉ có bản amd64: trên Mac Apple Silicon chạy qua Rosetta, chậm hơn — không dùng để đo hiệu năng cho máy chủ.
- Cổng chỉ mở ở `127.0.0.1:5432`. Mật khẩu nằm trong `.env` ở gốc repo (không commit; chép từ `.env.example`, tạo mật khẩu bằng `openssl rand -hex 24`).

## Lệnh

```bash
infrastructure/postgres/db.sh up                      # khởi động, chờ healthy
infrastructure/postgres/db.sh status
infrastructure/postgres/db.sh psql app                # psql bằng role app (owner | app | postgres)
infrastructure/postgres/db.sh seed data/discover/seed.sql
infrastructure/postgres/db.sh down                    # dừng, giữ dữ liệu
infrastructure/postgres/db.sh reset --yes-delete-data # XOÁ toàn bộ dữ liệu local
```

Dựng từ đầu:

```bash
infrastructure/postgres/db.sh up
cd backend && set -a && . ../.env && set +a && go run ./cmd/migrate up && cd ..
python3 scripts/build_discover_seed.py              # tuỳ chọn: sinh lại seed từ OSM + data/curated/places.csv
infrastructure/postgres/db.sh seed data/discover/seed.sql
```

## Vai trò (không role nào của app là superuser)

| Role | Dùng cho | Quyền |
|---|---|---|
| `postgres` | Chỉ `init/01-roles.sh`, chạy **một lần** khi volume trống | superuser của image |
| `gowander_owner` | `cmd/migrate`, nạp seed | sở hữu schema `gowander` |
| `gowander_app` | API (`DATABASE_URL`) | `SELECT` trên đúng các bảng được `GRANT` trong migration; `statement_timeout` 5 s |

- Không có `ALTER DEFAULT PRIVILEGES`: bảng mới **phải** có `GRANT SELECT … TO gowander_app` trong migration nếu API công khai cần đọc. Test `TestMigrationsUpDownUp` kiểm tra danh sách quyền.
- Thời gian truy vấn của API được giới hạn bởi `statement_timeout` của role `gowander_app`; đổi role thì phải đặt lại giới hạn này.
- Có hai database: `gowander` (dev) và `gowander_test` (chỉ integration test dùng).

## Lưu ý

- `init/` chỉ chạy khi volume **trống**. Sửa script khởi tạo thì phải `reset --yes-delete-data` rồi dựng lại. Nếu lần khởi tạo đầu lỗi giữa chừng, volume bị dở dang và không tự chạy lại: cũng `reset`.
- Migration lỗi giữa chừng làm `schema_migrations` bị đánh dấu `dirty`: sửa database bằng tay cho khớp một phiên bản, rồi `go run ./cmd/migrate force <phiên bản>`.
- Hàm `search_text()` (bỏ dấu tiếng Việt) dựa vào `unaccent`; sau khi nâng PostgreSQL hoặc đổi luật `unaccent`, chạy `REINDEX INDEX gowander.places_search_trgm_idx` và cập nhật lại `search_doc`.
- Máy chủ thật: kết nối bằng `sslmode=verify-full`; không đặt mật khẩu trong biến môi trường container như bản dev này.
