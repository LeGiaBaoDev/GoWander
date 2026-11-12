# Route cases qua API Go — 12-11-2026

> **Feed giả định (ADR-020).** Kết quả chỉ chứng minh lớp Go → OTP chạy đúng, không chứng minh độ đúng của lịch tàu.

- Lệnh: `go test -tags integration -count=1 -v -run TestRouteCases ./cmd/api/` (trong `backend/`)
- API: `POST /api/v1/routes/plan` (in-process, `newHandler` như `cmd/api`), OTP 2.10.0 tại `http://127.0.0.1:8088`, graph `metro-l1-assumed` (`graph.obj` 49cf4ddca7da…, xem `infrastructure/otp/manifests/metro-l1-assumed.serve.txt`)
- Ngày chạy theo thứ trong tuần 16–22/11/2026, như `infrastructure/otp/scripts/run_route_cases.py` (M02). Mã ga có tiền tố feed `1:`.
- Cột ms: thời gian một request tới API Go, gồm cả gọi OTP; lần đầu (RC-01) có khởi động kết nối.

| Case | expect_kind | Kết quả | HTTP | Ghi chú | ms |
|---|---|---|---|---|---|
| RC-01 | transit | PASS | 200 | 3 hành trình; Bến Thành→Bến xe Suối Tiên, trên tàu 1771 s; chờ 165 s | 43 |
| RC-02 | transit | PASS | 200 | 3 hành trình; Bến xe Suối Tiên→Bến Thành, trên tàu 1771 s; chờ 165 s | 29 |
| RC-03 | transit | PASS | 200 | 3 hành trình; Ba Son→Thảo Điền, trên tàu 347 s; chờ 251 s | 15 |
| RC-04 | walk_only | PASS | 200 | 3 hành trình | 16 |
| RC-05 | transit | PASS | 200 | 3 hành trình; Bến Thành→Thủ Đức, trên tàu 1230 s; chờ 163 s | 49 |
| RC-06 | no_transit_same_day | PASS | 422 | 0 hành trình; error=NO_ROUTE_FOUND | 31 |
| RC-07 | transit | PASS | 200 | 2 hành trình; Bến Thành→Bến xe Suối Tiên, trên tàu 1771 s; chờ 780 s | 31 |
| RC-08 | transit | PASS | 200 | 2 hành trình; Bến Thành→Bến xe Suối Tiên, trên tàu 1771 s; chờ 1800 s | 32 |
| RC-09 | outside_bounds | PASS | 422 | 0 hành trình; error=OUT_OF_COVERAGE | 4 |
| RC-10 | invalid_input | PASS | 400 | 0 hành trình; error=INVALID_REQUEST | 0 |

Kết quả go test: `ok  	github.com/LeGiaBaoDev/GoWander/backend/cmd/api	0.341s`
