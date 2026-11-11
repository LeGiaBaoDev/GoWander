### 04. HỆ THỐNG CHỨC NĂNG

#### 4.1. Smart Mobility — Core

- ​   Interactive Map và GPS.
- ​   Origin & Destination.
- ​   Place Search.
- ​   Bus/Metro/Walking Route Planning.
- ​   Route Comparison & Route Details.
- ​   Stop/Transfer Information.
- ​   Basic Navigation.
- ​   Route-aware Discovery.
- ​   Report Incorrect Route.

Các thông tin realtime, giá vé và tình trạng đông đúc chỉ hiển thị khi có nguồn đáng tin cậy.

**Bổ sung 11-11-2026 (ADR-021) — Thời tiết:** Smart Mobility có thêm phần hiển thị thời tiết ở mức nhẹ: thời tiết hiện tại và cảnh báo mưa trên màn hình Map, kết quả tuyến và chi tiết tuyến. Không có màn hình dự báo thời tiết riêng. Thông tin luôn ghi là dự báo, kèm giờ cập nhật và tên nguồn; khi không có dữ liệu thì ẩn đi.

#### 4.2. Discover — Core

- ​ Food Discovery.
- ​ Place Discovery.
- ​ Hidden Gems.

- ​   Historical Stories có nguồn.
- ​   Curated Trip Ideas.
- ​   Nearby Suggestions.
- ​   Discover-to-Route.
- ​   Rule-based Recommendation.

Món ăn và địa điểm bán món ăn là hai thực thể riêng.

#### 4.3. Personal Exploration

- ​   Saved Places.
- ​   Want to Visit.
- ​   Visited Places do người dùng đánh dấu.
- ​   Favorite Places.
- ​   My Exploration Map.
- ​   Travel Journal.
- ​   Collections trong giai đoạn mở rộng.

Exploration Map và Journal mặc định riêng tư; không yêu cầu ghi GPS liên tục.

#### 4.4. Identity & Wander Profile

- ​   Guest Mode cho Map/Discover.
- ​   Google Sign-In cho phiên bản hỗ trợ tài khoản.
- ​   Apple Sign-In được đánh giá cho bản iOS.
- ​   Profile, Avatar, Bio, Cover.
- ​   Email liên hệ và phone tùy chọn.
- ​   Account Linking có xác minh.
- ​   Privacy & Notification Preferences.

Không công khai email hoặc số điện thoại trong Wander Profile.

#### 4.5. Community

- ​   Community Feed.
- ​   Posts & Place Reviews.
- ​   Public Profile.
- ​   Follow/Unfollow.
- ​   Following Feed.
- ​   Content Reports.
- ​   Moderation.
- ​   Like và Comment ở giai đoạn thích hợp.

Không có Rank, Level hoặc Leaderboard công khai.

#### 4.6. Rewards

- ​ Điểm cho đóng góp được kiểm chứng.

- ​   Wallet & Transaction History.
- ​   Chống cộng điểm trùng.
- ​   Kiểm duyệt đóng góp.
- ​   Voucher Redemption chỉ khi có đối tác.

Không cấp điểm tự động cho Follow, Like, đánh giá tích cực hoặc tự đánh dấu Visited.

#### 4.7. Admin Back-office

GoWander có một Web Admin riêng, dùng chung Backend nhưng được bảo vệ bởi phân quyền và
audit.

Các chức năng:

- ​   Place/Food/Story/Trip Idea Management.
- ​   Transit Data & Feedback Management.
- ​   Users & Access Management.
- ​   Community Moderation.
- ​   Rewards Management.
- ​   Business Partners & Revenue.
- ​   Operational Monitoring.
- ​   Admin Audit Logs.

### 05. HỆ THỐNG DASHBOARD QUẢN TRỊ

GoWander đề xuất 6 dashboard, mỗi dashboard nhằm hỗ trợ một quyết định cụ thể.

    Dashboard                            Câu hỏi trọng tâm                         Thời điểm

    Executive Overview       Sản phẩm có khỏe và phát triển đúng hướng không?             Core MVP

    Mobility & Transit       Routing có chính xác, ổn định và đủ dữ liệu không?           Core MVP

    Discover & Content       Nội dung có đầy đủ, chính xác và dẫn đến hành động           Core MVP
không?

    Users & Community        Người dùng có quay lại và đóng góp hữu ích không?            Community

    Revenue & Business       Nguồn thu nào có hiệu quả và đã được đối soát?               Monetization

    Rewards &                Chương trình điểm có tạo giá trị và bị lạm dụng không?       Rewards
Engagement

Nguyên tắc: Mỗi KPI cần công thức, nguồn dữ liệu, kỳ đo, ngưỡng đánh giá và hành động quản trị.

Không coi Route Request là chuyến đi hoàn thành; không coi Saved/Visited là việc đến nơi được GPS
xác minh; không coi tổng giá trị giao dịch đối tác là doanh thu của GoWander.

### 06. BUSINESS MODEL & MONETIZATION

#### 6.1. Định hướng

Product Value First — Monetization Later.

GoWander ưu tiên xác thực giá trị Mobility + Discover trước khi kích hoạt mô hình thương mại.

#### 6.2. Nguồn doanh thu tiềm năng

    Kênh              Cách phát sinh doanh thu                     Điều kiện

    Affiliate                Hoa hồng từ giao dịch hợp lệ           Hợp đồng và attribution

    Merchant Partnership     Phí dịch vụ/hợp tác                    Có đối tác trả tiền

    Sponsored Places         Phí hiển thị được công bố rõ           Không làm sai lệch routing

    Advertising              Doanh thu quảng cáo thực nhận          Tuân thủ chính sách

    Voucher/Redemption       Phí hoặc hoa hồng theo thỏa thuận      Có giao dịch được xác nhận

    Ticketing                 Commission hoặc partner fee           Hợp tác hợp lệ

#### 6.3. Nguyên tắc ghi nhận doanh thu

Phải phân biệt:

- ​   Gross Revenue.
- ​   Refunds/Adjustments.
- ​   Net Revenue.
- ​   Partner Transaction Value.
- ​   Partner Payables/Settlements.
- ​   Direct Variable Costs.

GoWander chỉ ghi nhận doanh thu thuộc quyền hưởng theo hợp đồng và chính sách kế toán phù hợp.

Revenue Dashboard chỉ hoạt động với giao dịch có căn cứ; giai đoạn chưa có doanh thu sẽ hiển thị
trạng thái Not Monetized Yet, không tạo dữ liệu giả.

