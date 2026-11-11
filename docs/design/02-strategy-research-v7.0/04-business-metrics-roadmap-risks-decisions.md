### 12. BUSINESS MODEL & REVENUE STRATEGY

#### 12.1. Commercial Hypotheses

- ​ Merchant Partnership.
- ​ Affiliate/Booking Redirect.
- ​ Sponsored Places.
- ​ Advertising.
- ​ Voucher/Redemption.
- ​ Ticketing Partnerships.

#### Đây là cơ hội tương lai, không phải doanh thu đang tồn tại.

#### 12.2. Revenue Quality

    Chỉ số                         Cách hiểu

    Gross Revenue           Khoản doanh thu ghi nhận trước giảm
trừ

    Net Revenue             Doanh thu sau điều chỉnh/hoàn phù hợp

    Revenue by Channel      Nguồn thu tạo giá trị

    Partner Conversion      Chuyển đổi được xác nhận từ giới thiệu
    Rate                    hợp lệ

Contribution Margin Doanh thu thuần trừ chi phí biến đổi xác
định

    Unsettled Amount        Khoản cần đối soát/thanh toán theo hợp
đồng

Không gộp giá trị giao dịch của đối tác vào doanh thu GoWander nếu GoWander
chỉ được hưởng hoa hồng.

#### 12.3. Revenue Validation


Chỉ kích hoạt Business Dashboard khi có nguồn giao dịch đáng tin cậy, hợp đồng
và quy tắc đối soát.

#### 12.4. Monetization Guardrails

- ​ Sponsored content phải được phân biệt rõ.
- ​ Quảng cáo không làm sai kết quả tuyến.
- ​ Không bán dữ liệu cá nhân.
- ​ Không gắn phần thưởng với đánh giá tích cực.
- ​ Không làm gián đoạn Navigation vì quảng cáo.

### 13. PRODUCT METRICS & EXPERIMENTATION

    Mục tiêu         Primary Metric                     Câu hỏi

    Routing      Route Found Rate           Có tìm được tuyến khả dụng?

    Reliabilit   Technical Failure Rate     Dịch vụ có ổn định?
y

    UX           Core Journey Completion    Có hoàn thành luồng chính?

    Discover     Discover-to-Route          Nội dung có dẫn đến tìm tuyến?
Conversion

    Retention    D7 Retention               Có quay lại sau trải nghiệm ban
đầu?

    Communi Valid Contribution Rate         Nội dung đóng góp có ích?
ty

    Moderati     Overdue Review Backlog     Có quá tải kiểm duyệt?
on

    Rewards      Duplicate/Abuse Incidents Có lạm dụng điểm?

    Commerc      Net Revenue, Margin         Có tạo hiệu quả kinh tế?
e

#### Metric cần được đánh giá theo cohort, thời kỳ và phân khúc phù hợp.

### 14. DEVELOPMENT ROADMAP

    Phase              Thời gian định             Validation Focus
hướng

    1 — Core Pilot          Tháng 1–6             Data, OTP, Map, Mobility &
Discover

    2 — Personal Value      Tháng 7–10            Exploration, retention

    3 — Contextual &        Tháng 11–15           Recommendations, community
Social

    4 — Business            Tháng 16–21           Partners, conversion, revenue

    5 — Expansion           Tháng 22–27+          AI, scale, advanced operations

#### Phase chỉ chuyển khi đạt gate quan trọng, không tự động theo lịch.

### 15. FINANCIAL STRATEGY


Dự toán gốc 5 phase khoảng 10,5–26 triệu VNĐ là ước tính sơ bộ chưa xác minh,
không bao gồm đầy đủ nhân công và có thể thiếu chi phí OTP, lưu trữ media,
map tiles, store accounts hoặc vận hành production.

#### Cần quản lý chi phí theo nhóm:

- ​ Infrastructure & Hosting.
- ​ Map/Routing Data.
- ​ Storage & Media.
- ​ Auth & Communication.
- ​ Application Distribution.
- ​ Monitoring.
- ​ Commercial Operations.

Cost Gate: Benchmark CPU/RAM OTP, chi phí hosting và tính toán chi phí theo
số request trước khi xác nhận mô hình vận hành.

### 16. RISKS, DECISION GATES & VALIDATION

    Risk                      Signal              Strategic Response

    Thiếu transit data          Nhiều tuyến không có lịch Thu hẹp pilot/đổi
    trình                     nguồn

    Map không ổn định iOS       Crash/gesture failure      Thử adapter khác

    Không có người dùng         Retention thấp             Nghiên cứu lại core
    quay lại                                               value

    Discover không tạo route    Conversion thấp            Cải thiện nội
    requests                                               dung/flow

    Community spam              Moderation backlog         Giảm scope, siết duyệt

    Revenue không xác nhận       Conversion/settlement        Dừng mở rộng
    không đạt                    thương mại

    Chi phí hosting cao          Cost/request quá lớn         Tối ưu hoặc đổi giải
pháp

    Dữ liệu cá nhân rủi ro       Incident, access violation   Ưu tiên security
remediation

### 17. STRATEGIC DECISIONS

#### Selected:

- ​ TP.HCM là thị trường pilot.
- ​ Core Mobility + Discover.
- ​ Option B Modular & Scalable.
- ​ KMP + Go + PostgreSQL/PostGIS.
- ​ Guest Mode cho chức năng công khai.
- ​ Admin Web hỗ trợ vận hành.
- ​ Google Sign-In khi có account.
- ​ Six decision-oriented dashboards theo giai đoạn.
- ​ Monetization sau Product Validation.

#### Pending:

- ​ GTFS license & coverage.
- ​ OTP/MapLibre PoC.
- ​ Identity Provider.
- ​ Admin Web framework.
- ​ Data Dictionary physical schema.
- ​ Business partners và hợp đồng.
- ​ Ngưỡng KPI/chi phí chính thức.

#### Not Selected:

- ​ Public Rank/Level/Leaderboard.
- ​ Realtime ETA giả định.

- ​ Full citywide routing ngay MVP.
- ​ Premature Microservices/Kafka/Kubernetes.
- ​ Advanced AI khi chưa có dữ liệu.

### 18. STRATEGIC CONCLUSION

GoWander không phải một ứng dụng giao thông cộng thêm mạng xã hội tùy ý.
Chiến lược là xây một hành trình liên tục từ tìm ý tưởng đến di chuyển và trải
nghiệm, đặt tính đáng tin cậy của dữ liệu lên trước.

Admin và Dashboard giúp kiểm soát chất lượng sản phẩm; Revenue & Business
mở rộng khi mô hình đã được kiểm chứng.

Research → Validate Feasibility → Build Core → Pilot → Learn → Expand →
Monetize.

#### GoWander — Move Smart. Explore More.


---

