# Photon — tìm địa chỉ (geocoding) tự chạy

Tìm địa chỉ cho ô tìm kiếm của app (`GET /api/v1/geocode` của API Go). Chủ dự án chọn phương án miễn phí ngày 14-11-2026; Vietmap là hướng nâng cấp trả phí (xem change record `docs/changes/change-14-11-2026.md`).

- Phần mềm: [Photon](https://github.com/komoot/photon) 0.7.4 (Apache 2.0), chạy bằng JDK 21 trên máy, chỉ lắng nghe `127.0.0.1:2322`.
- Dữ liệu: index Việt Nam dựng sẵn từ OpenStreetMap (graphhopper.com, 21-07-2025, 142 MB nén, 251 MB giải nén), © OpenStreetMap contributors, ODbL 1.0. App hiển thị dòng ghi nguồn cạnh kết quả địa chỉ.
- Jar và dữ liệu được kiểm tra SHA-256 / MD5 trước khi dùng; thư mục `work/` không vào Git.

```bash
infrastructure/photon/serve.sh start    # tải (lần đầu), kiểm tra, khởi động, chờ sẵn sàng
infrastructure/photon/serve.sh status
infrastructure/photon/serve.sh stop
```

Photon không nằm trong `/health/ready`: khi Photon dừng, chỉ phần "Địa chỉ" trong ô tìm kiếm báo lỗi (`GEOCODER_UNAVAILABLE`), ga/địa điểm và tìm tuyến vẫn chạy.

Giới hạn: index tháng 7-2025 (cũ hơn OSM hiện tại); địa chỉ số nhà ở Việt Nam trên OSM chưa đầy đủ — lý do cân nhắc Vietmap sau pilot. Tìm được địa chỉ ngoài hành lang Metro số 1 nhưng tìm tuyến tới đó sẽ báo ngoài vùng hỗ trợ.
