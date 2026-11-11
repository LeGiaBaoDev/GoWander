### 13. ADMIN WEB — INFORMATION ARCHITECTURE

Admin Web là sản phẩm nội bộ tách giao diện khỏi Mobile, sử dụng chung Go Backend.

#### Sidebar đề xuất

Monitoring

- ​   Executive Overview.
- ​   Mobility & Transit.
- ​   Discover & Content.
- ​   System Health.

Content Management

- ​   Places.
- ​   Categories.
- ​   Food Items.
- ​   Historical Stories.
- ​   Trip Ideas.
- ​   Media.

Community & Users

- ​   Users.
- ​   Posts & Reviews.
- ​   Content Reports.
- ​   Moderation.

Business

- ​   Partners.
- ​   Revenue.
- ​   Settlements.
- ​   Rewards.

Operations

- ​ Transit Sources.
- ​ Data Versions.
- ​ Feedback.

System

- ​ Roles & Permissions.
- ​ Audit Logs.
- ​ Settings.

Các mục được ẩn hoặc bảo vệ theo role và theo release.

### 14. ADMIN DASHBOARD REQUIREMENTS

#### 14.1. Dashboard Rule

Một dashboard chỉ có giá trị khi hỗ trợ câu hỏi quản trị rõ ràng.

Mỗi metric phải mô tả:

1. Business Question.
2. Formula.
3. Source.
4. Time Window.
5. Segmentation.
6. Data Freshness.
7. Owner.
8. Decision/Action.
9. Limitations.

#### 14.2. Executive Overview

    KPI                               Mục đích

    Weekly Active Users               Theo dõi sử dụng có ý nghĩa

    Core Journey Completion Rate      Phát hiện bỏ cuộc trong flow chính

    Route Found Rate                  Theo dõi khả năng tìm tuyến

    Verified Content Coverage         Đánh giá sẵn sàng nội dung

    Critical Open Issues             Ưu tiên xử lý lỗi

    Net Revenue                      Chỉ hiển thị sau thương mại hóa

Trình bày: KPI cards, xu hướng một số metric chính, task/action list và cảnh báo; không phải
mọi chỉ số đều cần chart.

#### 14.3. Mobility & Transit

    KPI                          Quyết định

    Route Found Rate                  Kiểm tra coverage

    Routing Technical Failure Rate    Phát hiện sự cố OTP/API

    P95 Routing Latency               Ưu tiên tối ưu hiệu năng

    Route Selection Rate              Đánh giá chất lượng kết quả

    Data Freshness                    Cập nhật GTFS/OSM

    Confirmed Route Errors            Điều tra dữ liệu sai

No Route hợp lệ phải tách khỏi Technical Failure.

#### 14.4. Discover & Content

    KPI                         Quyết định

    Published & Verified Places      Kiểm soát phạm vi nội dung

    Content Completeness             Sửa nội dung thiếu

    Discover-to-Route Conversion     Đánh giá giá trị Discover

    Confirmed Place Error Rate       Ưu tiên kiểm chứng

    Unresolved Content Tasks         Phân bổ biên tập

#### 14.5. Users & Community

    KPI                      Quyết định

    D7 Retention               Đo giữ chân

    Valid Contribution Rate    Chất lượng nội dung

    Repeat Contributors        Mức đóng góp bền vững

    Moderation Backlog         Bố trí kiểm duyệt

    Confirmed Abuse Rate       Tối ưu moderation

#### 14.6. Revenue & Business

    KPI                        Quyết định

    Gross Revenue              Quy mô khoản thu

    Net Revenue                Thu sau điều chỉnh

    Revenue by Channel         Ưu tiên kênh hiệu quả

    Partner Conversion Rate    Đánh giá đối tác

    Contribution Margin        Hiệu quả sau chi phí biến đổi

    Unsettled Amount           Đối soát và thu hồi

Chart phù hợp: Revenue Trend, Revenue by Channel, Partner Performance.

Không hiển thị biểu đồ doanh thu giả nếu chưa có giao dịch.

#### 14.7. Rewards & Engagement

    KPI                            Quyết định

    Valid Contribution Rate     Điều chỉnh quy tắc

    Points Granted              Theo dõi lượng cấp

    Points Reversed             Điều tra sai lệch

    Confirmed Reward Abuse      Chống gian lận

    Redemption Cost             Kiểm soát chi phí khi có chương trình

### 15. ADMIN WORKFLOWS


#### Flow E — Content Publishing

1. Content Editor tạo/chỉnh Place, Food hoặc Story.
2. Kiểm tra thông tin và nguồn.
3. Gửi duyệt nếu chính sách yêu cầu.
4. Người có quyền phê duyệt.
5. Nội dung xuất hiện trên Mobile.
6. Có thể ẩn/chỉnh sửa và ghi audit.

#### Flow F — Transit Data Operations

1. Quản trị viên xem nguồn GTFS/OSM.
2. Kiểm tra license và phiên bản.
3. Nhập dữ liệu thử nghiệm.
4. Chạy validation và route tests.
5. Phê duyệt graph/release.
6. Theo dõi lỗi và rollback khi cần.

#### Flow G — Moderation

1. Nhận report.
2. Xem bằng chứng/nội dung.
3. Quyết định giữ, ẩn hoặc từ chối.
4. Ghi moderator, action, reason.
5. Cập nhật trạng thái.
6. Thông báo phù hợp.

#### Flow H — Revenue Reconciliation

1. Nhận dữ liệu giao dịch/hoa hồng.
2. Kiểm tra nguồn và trạng thái xác nhận.
3. Ghi adjustments/refunds.
4. Đối chiếu hợp đồng đối tác.
5. Chốt số được hưởng.
6. Cập nhật settlements.
7. Hiển thị metric đã đối soát trên dashboard.

### 16. ADMIN UX STANDARDS

- ​   Bảng dữ liệu có tìm kiếm, lọc và phân trang.
- ​   Tác vụ nguy hiểm cần xác nhận.
- ​   Không cho phép sửa audit trail tự do.
- ​   Phân quyền ở Backend, không chỉ ẩn nút.
- ​   Cho biết người chỉnh sửa và thời điểm.
- ​   Có trạng thái Draft, Pending, Published rõ ràng.

- ​    Các chỉ số có đơn vị và thời gian.
- ​    Không trộn dữ liệu thật với dữ liệu demo.
- ​    Dashboard có Drill-down đến danh sách cần xử lý.
- ​    Empty State giải thích chưa có dữ liệu hay chưa triển khai.

