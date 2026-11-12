# Route cases M02 — sau-sua-exact-times

> Feed **giả định** (ADR-020): chỉ chứng minh công nghệ, không chứng minh TP.HCM sẵn sàng.

- Chạy lúc: 2026-11-12 15:16:33 +0700
- OTP: 2.10.0, graph `49cf4ddca7dabbb6…` (dựng 2026-11-12T15:16:23+0700), feed `assumed-2026-11-12`
- Ngày dùng cho thứ trong tuần: tuần 16-11-2026 → 22-11-2026, giờ +07:00
- Độ trễ: đo từ client trên cùng máy, 20 lần mỗi case sau 1 lần khởi động
- Kết quả: 10/10 PASS

| Case | Mô tả | Lúc đi | Kỳ vọng | Kết quả | Chi tiết | p50 ms | p95 ms | max ms |
|---|---|---|---|---|---|---|---|---|
| RC-01 | Metro toàn tuyến Bến Thành -> Bến xe Suối Tiên | Tue 17-11 09:00 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Tue 09:02:45→09:32:16: SUBWAY L1 Bến Thành→Bến xe Suối Tiên 09:02:45; trên tàu 1771 s; chờ 165 s; theo lịch | 39 | 56 | 77 |
| RC-02 | Metro chiều ngược Bến xe Suối Tiên -> Bến Thành | Tue 17-11 09:00 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Tue 09:02:45→09:32:16: SUBWAY L1 Bến xe Suối Tiên→Bến Thành 09:02:45; trên tàu 1771 s; chờ 165 s; theo lịch | 29 | 34 | 35 |
| RC-03 | Metro một đoạn giờ cao điểm Ba Son -> Thảo Điền | Wed 18-11 07:30 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Wed 07:34:11→07:39:58: SUBWAY L1 Ba Son→Thảo Điền 07:34:11; trên tàu 347 s; chờ 251 s; theo lịch | 15 | 19 | 28 |
| RC-04 | Đi bộ ngắn Bến Thành -> Nhà Hát Thành Phố | Tue 17-11 10:00 | walk_only | PASS | HTTP 200; 3 hành trình; đầu tiên: Tue 10:00:00→10:10:40: WALK 753 m | 15 | 17 | 18 |
| RC-05 | Đi bộ + metro + đi bộ: gần Bến Thành -> gần Thủ Đức | Thu 19-11 14:00 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Thu 14:02:43→14:37:58: WALK 268 m + SUBWAY L1 Bến Thành→Thủ Đức 14:08:25 + WALK 527 m; trên tàu 1230 s; chờ 163 s; theo lịch | 31 | 34 | 36 |
| RC-06 | Ngoài giờ chạy: thứ Ba sau chuyến cuối | Tue 17-11 22:30 | no_transit_same_day | PASS | HTTP 200; 0 hành trình; routingErrors=NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW | 33 | 40 | 93 |
| RC-07 | Ranh giới lịch: thứ Sáu 22:30 vẫn còn tàu | Fri 20-11 22:30 | transit | PASS | HTTP 200; 2 hành trình; đầu tiên: Fri 22:43:00→23:12:31: SUBWAY L1 Bến Thành→Bến xe Suối Tiên 22:43:00; trên tàu 1771 s; chờ 780 s; theo lịch | 33 | 48 | 68 |
| RC-08 | Trước giờ chạy: 04:30 sáng | Mon 16-11 04:30 | transit | PASS | HTTP 200; 2 hành trình; đầu tiên: Mon 05:00:00→05:29:31: SUBWAY L1 Bến Thành→Bến xe Suối Tiên 05:00:00; trên tàu 1771 s; chờ 1800 s; theo lịch | 31 | 35 | 38 |
| RC-09 | Ngoài vùng phủ: điểm đến ở Hà Nội | Tue 17-11 09:00 | outside_bounds | PASS | HTTP 200; 0 hành trình; routingErrors=OUTSIDE_BOUNDS | 3 | 3 | 4 |
| RC-10 | Toạ độ không hợp lệ | Tue 17-11 09:00 | invalid_input | PASS | HTTP 200; 0 hành trình; errors=Exception while fetching data (/planConnection) : The latitude is not in range[-90.0, 90.0]: 95.0 | 2 | 3 | 4 |
