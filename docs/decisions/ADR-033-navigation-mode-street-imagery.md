# ADR-033 — Chế độ dẫn đường (NAV-1 MVP), xem trước tuyến bằng ảnh đường phố, chọn loại bản đồ

- **Trạng thái:** Accepted (MVP) · **Ngày:** 14-11-2026 · **Người quyết định:** chủ dự án
- **Liên quan:** `PIPELINE.md` NAV-1; ADR-025 (MapLibre, OpenFreeMap); ADR-027 (API); ADR-032 (graph toàn TP.HCM); `docs/research/data-sources-inventory.md` §2.2

## Bối cảnh

Chủ dự án gửi ảnh Google Maps (14-11-2026) và yêu cầu:
- nút "Bắt đầu" và bản đồ toàn màn hình đi theo người dùng;
- danh sách "Đường đi";
- đặc biệt là màn **"Xem trước tuyến đường" chia đôi**: trên là ảnh đường phố tại điểm rẽ, dưới là bản đồ 2D;
- Chi tiết tuyến dùng bản đồ lớn;
- chọn được bản đồ 2D, 3D hoặc vệ tinh.

Không được lấy Street View hay dữ liệu của Google.

## Quyết định

1. **Chỉ dẫn từng bước lấy từ OTP.**
   - `plan.graphql` lấy `steps { distance relativeDirection streetName lat lon }`.
   - API trả `legs[].steps[]` (`direction`, `street`, `distanceM`, `lat`, `lon`); chặng công cộng không có steps.
   - App tự tạo câu chỉ dẫn tiếng Việt ("Rẽ trái vào …", "Lên Buýt 18 tại …", "Xuống tại …", "Đến …").
   - OTP đặt tên "road", "path"… cho đường không tên; app đọc là "đường không tên".

2. **Chế độ dẫn đường** (`NavigationScreen`):
   - Bản đồ toàn màn hình, nghiêng 55° ở chế độ 3D. Camera đi theo từng lần cập nhật GPS, xoay theo hướng di chuyển hoặc la bàn, chọn nguồn nào chính xác hơn (`LocationTrackingEffect` + `updateCamera` của maplibre-compose 0.20.0, không tự viết).
   - Băng chỉ hướng ở trên: mũi tên, khoảng cách tới bước rẽ, câu chỉ dẫn.
   - Thẻ dưới: thời gian còn lại, km, giờ đến dự kiến, nút **Thoát**, **Đường đi**, **Xem trước**.
   - Tiến trình tính trên đường tuyến đã giải mã (`NavTrack`, phép chiếu phẳng cục bộ). Bước rẽ cách dưới 8 m coi như đã qua.
   - **Lệch tuyến** (> 60 m) chỉ báo sau khi người dùng đã vào tuyến (< 30 m), vì điểm xuất phát hay nằm ngoài đường (trong chợ, bãi xe). Nút "Tìm lại tuyến từ vị trí này" dùng vị trí GPS làm điểm đi và tìm lại.
   - Đến nơi khi còn dưới 25 m.
   - Thời gian còn lại = thời gian tuyến × tỉ lệ quãng đường còn lại, **chỉ là ước tính**.

3. **Xem trước tuyến** (`PreviewScreen`):
   - Nửa trên: ảnh đường phố gần điểm rẽ. Nếu không có ảnh thì hiện **bản đồ 3D nghiêng 60°, nhìn theo hướng tới bước sau**.
   - Nửa dưới: bản đồ 2D tại điểm rẽ.
   - Nút Trước/Sau đi qua từng bước; "Quay lại tuyến đường". Chạm một bước trong "Đường đi" sẽ mở xem trước tại bước đó.

4. **Nguồn ảnh đường phố: Mapillary** (ảnh CC BY-SA 4.0; Mapillary tự làm mờ mặt người và biển số).
   - Theo blog Mapillary 05-2024, Be Group đã chụp gần như toàn bộ Hà Nội và TP.HCM.
   - Backend có `GET /api/v1/street-images/nearest?lat&lon`:
     - tìm ảnh gần nhất trong ô khoảng 60 m (API v4 `/images?bbox=`);
     - **token đặt ở server** (`MAPILLARY_TOKEN`, gửi trong header, không đưa vào URL hay log);
     - trả kèm `source.attribution` ("© <người chụp>, Mapillary, CC BY-SA 4.0"), app hiện dòng này trên ảnh.
   - Mã lỗi: `STREET_IMAGE_NOT_FOUND` (404), `STREET_IMAGERY_UNAVAILABLE` (503, khi chưa có token hoặc Mapillary lỗi). App xử lý cả hai bằng cách hiện bản đồ 3D.
   - App tải ảnh thẳng từ CDN của Mapillary và giải mã bằng `decodeToImageBitmap` của Compose resources (đã có sẵn, không thêm thư viện).
   - Riêng tư: server chỉ gửi toạ độ điểm rẽ cho Mapillary, không gửi danh tính người dùng.

5. **Chi tiết tuyến** dùng bản đồ toàn màn hình, thẻ thông tin nổi ở dưới (chạm vào tay nắm để mở rộng hoặc thu gọn), có nút **Bắt đầu / Xem trước / Đường đi**. Tuyến ô tô và xe đạp vẽ nét liền; đi bộ vẫn nét đứt.

6. **Chọn loại bản đồ:** nút "Loại bản đồ" có **2D / 3D / Vệ tinh**, dùng chung cho mọi bản đồ.
   - 3D = nghiêng 55° với lớp nhà nổi khối có sẵn trong style OpenFreeMap liberty.
   - **Vệ tinh chưa bật.** Chưa có nguồn ảnh vệ tinh vừa nét vừa được dùng thương mại miễn phí:
     - gói miễn phí của MapTiler chỉ cho phi thương mại;
     - World Imagery của Esri cần giấy phép ArcGIS;
     - Sentinel-2 chỉ có độ phân giải 10 m, quá mờ ở mức đường phố.
   - Chủ dự án phải chọn nhà cung cấp trả phí (MapTiler, ArcGIS Location Platform, Mapbox, Vietmap…). App hiện ghi chú khi chọn Vệ tinh.

## Hệ quả

- Không thêm thư viện mới. Thêm 1 biến cấu hình tuỳ chọn (`MAPILLARY_TOKEN`, `MAPILLARY_URL`) và 1 endpoint.
- Thiếu token thì tính năng vẫn dùng được, chỉ thay ảnh bằng bản đồ 3D.

## Để sau

- Giọng nói, làn đường, tự tìm lại tuyến khi lệch, chia sẻ tiến trình, tìm dọc tuyến.
- Thời gian theo từng phương tiện trên tab.
- Thử trên máy thật (GPS, la bàn, pin).
- Đọc điều khoản API của Mapillary trước pilot.

## Rủi ro

- Thứ tự bước rẽ của OTP đôi khi có hai bước liền nhau cùng một đường (ví dụ "Rẽ phải vào Trương Đăng Quế" hai lần).
- Đã thử trên Android emulator với GPS giả; **chưa** thử trên iOS và trên máy thật.
