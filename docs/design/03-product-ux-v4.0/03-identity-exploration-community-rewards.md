### 06. IDENTITY & ACCOUNT UX

#### 6.1. Guest Experience

Guest vẫn có thể:

- ​   Xem Map.
- ​   Tìm tuyến.
- ​   Xem Discover.
- ​   Xem nội dung Community công khai.

Không yêu cầu đăng nhập ngay màn hình đầu tiên.

#### 6.2. Google Sign-In

Các trạng thái:

- ​   Login Available.
- ​   Authenticating.
- ​   Success.
- ​   Cancelled.
- ​   Provider Unavailable.
- ​   Invalid Session.
- ​   Account Suspended.

Khi đăng nhập lần đầu, tạo tài khoản GoWander và hồ sơ mặc định theo dữ liệu có quyền sử
dụng.

#### 6.3. Account & Contacts

My Account có thể hiển thị:

- ​   Email liên hệ.
- ​   Trạng thái Email Verification.
- ​   Số điện thoại tùy chọn.
- ​   Trạng thái Phone Verification.
- ​   Linked Login Providers.
- ​   Logout.
- ​   Account Deactivation/Deletion Flow.

Email và số điện thoại không được mặc định đưa lên Public Profile.

#### 6.4. Apple Sign-In

Được đánh giá theo yêu cầu phát hành iOS và kiến trúc xác thực đã chọn.

Không tự động liên kết tài khoản chỉ vì email Google và Apple giống nhau.

### 07. PERSONAL EXPLORATION UX

    ID                Feature                     UX Behavior

    FR-20     Save/Want to Visit    Lưu địa điểm vào danh sách cá nhân

    FR-21     Favorite              Đánh dấu yêu thích

    FR-22     Visited               Người dùng tự đánh dấu đã đến

    FR-23     My Exploration Map    Xem marker theo trạng thái

    FR-24     Travel Journal        Viết ghi chú, chọn ngày, thêm ảnh khi hỗ trợ

    FR-25     Collections           Tạo bộ sưu tập trong phase mở rộng

#### 7.1. Privacy

- ​    Mặc định PRIVATE.
- ​    Không bắt buộc GPS.
- ​    Không biến Visited thành bằng chứng đã hiện diện.
- ​    Không tự chia sẻ Journal sang Community.

#### 7.2. Screen States

Empty Map, No Saved Places, Loading, Sync Error và Permission Error phải có cách xử lý rõ.

### 08. WANDER PROFILE & COMMUNITY UX

#### 8.1. Profile

- ​    Avatar, Cover Photo, Bio.
- ​    Public Posts.
- ​    Followers/Following.
- ​    Featured Places khi có.
- ​    Settings/Privacy.

#### 8.2. Community

    ID              Feature                      Acceptance

    FR-26     Feed                Xem bài đã công khai theo trang

    FR-27     Create Post         Đăng nội dung được phép, có kiểm duyệt

    FR-28     Place Review        Đánh giá theo quy tắc

    FR-29     Follow              Theo dõi/bỏ theo dõi

    FR-30     Following Feed      Xem bài từ tài khoản đã Follow

    FR-31     Content Report      Báo cáo nội dung

    FR-32     Moderation State    Người đăng nhận biết bài đang duyệt/bị ẩn

#### 8.3. Community Rules

- ​    Không Rank/Level/Leaderboard.
- ​    Không cấp điểm cho Like/Follow.
- ​    Không bắt buộc đánh giá tích cực.
- ​    Người bị báo cáo hoặc bị ẩn nội dung có cơ chế xử lý minh bạch.

### 09. REWARDS UX

    ID           Feature                              Acceptance

    FR-33   Reward Wallet          Hiển thị điểm được xác nhận

    FR-34   Points History         Hiển thị từng giao dịch theo trang

    FR-35   Contribution Status    Phân biệt Pending, Approved, Rejected

    FR-36   Reward Information     Giải thích quy tắc và trường hợp không đủ điều kiện

Voucher Redemption chỉ xuất hiện khi có chương trình đối tác hoạt động thật.

