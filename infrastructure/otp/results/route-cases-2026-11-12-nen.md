# Route cases M02 — nen

> Feed **giả định** (ADR-020): chỉ chứng minh công nghệ, không chứng minh TP.HCM sẵn sàng.

- Chạy lúc: 2026-11-12 15:12:42 +0700
- OTP: 2.10.0, graph `eb523c294cb80a48…` (dựng 2026-11-12T14:58:10+0700), feed `assumed-2026-11-11`
- Ngày dùng cho thứ trong tuần: tuần 16-11-2026 → 22-11-2026, giờ +07:00
- Độ trễ: đo từ client trên cùng máy, 1 lần mỗi case sau 1 lần khởi động
- Kết quả: 8/10 PASS

| Case | Mô tả | Lúc đi | Kỳ vọng | Kết quả | Chi tiết | p50 ms | p95 ms | max ms |
|---|---|---|---|---|---|---|---|---|
| RC-01 | Metro toàn tuyến Bến Thành -> Bến xe Suối Tiên | Tue 17-11 09:00 | transit | FAIL | HTTP 200; 3 hành trình; đầu tiên: Tue 09:12:55→09:42:26: SUBWAY L1 Bến Thành→Bến xe Suối Tiên 09:12:55; trên tàu 1771 s; chờ 775 s; > tối đa 655 s; theo lịch | 69 | 69 | 69 |
| RC-02 | Metro chiều ngược Bến xe Suối Tiên -> Bến Thành | Tue 17-11 09:00 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Tue 09:12:55→09:42:26: SUBWAY L1 Bến xe Suối Tiên→Bến Thành 09:12:55; trên tàu 1771 s; chờ 775 s; theo lịch | 40 | 40 | 40 |
| RC-03 | Metro một đoạn giờ cao điểm Ba Son -> Thảo Điền | Wed 18-11 07:30 | transit | FAIL | HTTP 200; 3 hành trình; đầu tiên: Wed 07:38:30→07:44:17: SUBWAY L1 Ba Son→Thảo Điền 07:38:30; trên tàu 347 s; chờ 510 s; > tối đa 390 s; theo lịch | 27 | 27 | 27 |
| RC-04 | Đi bộ ngắn Bến Thành -> Nhà Hát Thành Phố | Tue 17-11 10:00 | walk_only | PASS | HTTP 200; 1 hành trình; routingErrors=WALKING_BETTER_THAN_TRANSIT; đầu tiên: Tue 10:00:00→10:10:40: WALK 753 m | 19 | 19 | 19 |
| RC-05 | Đi bộ + metro + đi bộ: gần Bến Thành -> gần Thủ Đức | Thu 19-11 14:00 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Thu 14:00:00→14:46:10: WALK 268 m + SUBWAY L1 Bến Thành→Thủ Đức 14:16:37 + WALK 527 m; trên tàu 1230 s; chờ 655 s; theo lịch | 49 | 49 | 49 |
| RC-06 | Ngoài giờ chạy: thứ Ba sau chuyến cuối | Tue 17-11 22:30 | no_transit_same_day | PASS | HTTP 200; 0 hành trình; routingErrors=NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW | 34 | 34 | 34 |
| RC-07 | Ranh giới lịch: thứ Sáu 22:30 vẫn còn tàu | Fri 20-11 22:30 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Fri 22:43:09→23:12:40: SUBWAY L1 Bến Thành→Bến xe Suối Tiên 22:43:09; trên tàu 1771 s; chờ 789 s; theo lịch | 34 | 34 | 34 |
| RC-08 | Trước giờ chạy: 04:30 sáng | Mon 16-11 04:30 | transit | PASS | HTTP 200; 3 hành trình; đầu tiên: Mon 05:00:00→05:29:31: SUBWAY L1 Bến Thành→Bến xe Suối Tiên 05:00:00; trên tàu 1771 s; chờ 1800 s; theo lịch | 38 | 38 | 38 |
| RC-09 | Ngoài vùng phủ: điểm đến ở Hà Nội | Tue 17-11 09:00 | outside_bounds | PASS | HTTP 200; 0 hành trình; routingErrors=OUTSIDE_BOUNDS | 8 | 8 | 8 |
| RC-10 | Toạ độ không hợp lệ | Tue 17-11 09:00 | invalid_input | PASS | HTTP 200; 0 hành trình; errors=Exception while fetching data (/planConnection) : The latitude is not in range[-90.0, 90.0]: 95.0 | 4 | 4 | 4 |
