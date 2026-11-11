### 10. CORE USER FLOWS

#### Flow A — Destination-first

1. Mở Map.
2. Chọn điểm xuất phát/đến.
3. Gửi Route Request.
4. Xem các phương án khả dụng.
5. Chọn tuyến.
6. Xem các chặng.
7. Bắt đầu điều hướng nếu chức năng được hỗ trợ.
8. Báo lỗi hoặc mở địa điểm gần tuyến nếu muốn.

#### Flow B — Discover-first

1. Mở Discover.
2. Chọn Food/Place/Story.
3. Xem chi tiết.
4. Chọn Directions.
5. Chuyển Map.
6. Tìm tuyến.
7. Lưu hoặc đánh giá sau trải nghiệm.

#### Flow C — Google Sign-In

1. Chọn Sign in with Google.
2. Đăng nhập và đồng ý theo flow của provider.
3. Xác thực identity.
4. Tạo user hoặc lấy user đã tồn tại.
5. Vào My Profile.
6. Tùy chọn cập nhật email/phone liên hệ.

#### Flow D — Community Contribution

1. Đăng nhập.
2. Tạo post/review.
3. Gửi nội dung.
4. Hệ thống kiểm duyệt.
5. Công khai hoặc từ chối.
6. Nếu đủ điều kiện, ghi điểm hợp lệ.
7. Xem trạng thái và lịch sử.

### 11. SCREEN INVENTORY — MOBILE

    Group                                          Screens

    Onboardin    Welcome, Language, Permission Education
g

    Mobility     Map, Search, Origin/Destination, Route Results, Route Detail, Navigation, Stop
Detail

    Discover     Home, Category, Food Detail, Place List, Place Detail, Historical Story, Trip
Idea

    Identity     Login, Account, Contacts, Linked Providers, Privacy

Exploratio   Saved, Exploration Map, Place Status, Journal List, Journal Editor
n

    Communit      Feed, Post Detail, Create Post, Reviews, Public Profile, Followers/Following,
    y             Content Report

    Rewards       Wallet, Transaction History, Contribution Status

    Supporting    Feedback Form, Notifications, Settings, Error/Empty States

Screen Count sẽ được chốt sau khi xác định màn hình nào dùng bottom sheet, dialog hoặc route
độc lập. Không cố giữ một con số bất biến chỉ để khớp tài liệu cũ.

### 12. UI DESIGN SYSTEM

#### 12.1. Visual Principles

- ​   Bản đồ ưu tiên diện tích hiển thị.
- ​   Điều hướng dễ thao tác bằng một tay.
- ​   Cards có hierarchy rõ.
- ​   Route summaries dễ quét bằng mắt.
- ​   Nội dung dài dùng progressive disclosure.
- ​   Không gây quá tải trong khi di chuyển.
- ​   Màu sắc không là phương tiện duy nhất truyền trạng thái.

#### 12.2. Components

- ​   Search Bar.
- ​   Place Card.
- ​   Route Option Card.
- ​   Transit Leg.
- ​   Stop Marker.
- ​   Status Chip.
- ​   Category Filter.
- ​   Media Gallery.
- ​   Post Card.
- ​   Profile Header.
- ​   Reward Transaction.
- ​   Feedback Form.

#### 12.3. Accessibility

- ​ Tương phản đủ tốt.

- ​   Touch targets phù hợp.
- ​   Typography có thể đọc.
- ​   Screen reader labels.
- ​   States không phụ thuộc màu.
- ​   Hỗ trợ ngôn ngữ vi/en.

