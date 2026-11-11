### 17. PERSONALIZATION & NOTIFICATIONS

#### 17.1. Recommendation

Phase 1: Rule-based theo danh mục, vị trí và chất lượng nội dung.

Phase 2: Có thể sử dụng dữ liệu hành vi hợp lệ.

Later: Context-aware/ML khi đủ dữ liệu và khả năng đánh giá.

#### 17.2. Notifications

- ​    In-app Notification Center.
- ​    Trạng thái bài/review.
- ​    Phản hồi moderation.
- ​    Reward Status.
- ​    Push Notifications khi có lý do rõ.

Tránh thông báo làm gián đoạn người dùng khi đang di chuyển.

### 18. NON-FUNCTIONAL UX REQUIREMENTS

    ID                            Requirement

    NFR-01      Android/iOS có behavior tương đương ở flow chính

    NFR-02      Không giả lập tuyến, ETA hoặc fare như dữ liệu thật

    NFR-03      Phục hồi sau lỗi mạng và routing

    NFR-04      Bảo vệ email, phone, GPS và Journal

    NFR-05     Có Loading, Empty, Error, Retry states

    NFR-06     Admin có RBAC, Audit và dữ liệu KPI có nguồn

Performance targets cần được chốt sau PoC thay vì đặt số liệu không có cơ sở.

### 19. PROTOTYPE & UX VALIDATION

#### 19.1. Mobile Scenarios

- ​   Tìm tuyến bus/metro.
- ​   Chuyển từ Discover sang Route.
- ​   Không tìm thấy tuyến.
- ​   GPS bị từ chối.
- ​   Xem Food/Place/Story.
- ​   Đăng nhập Google.
- ​   Lưu/Visited/Favorite.
- ​   Đăng review và xem trạng thái.

#### 19.2. Admin Scenarios

- ​   Tạo Place có tọa độ.
- ​   Biên tập Story và nguồn.
- ​   Kiểm tra lỗi route.
- ​   Duyệt nội dung bị báo cáo.
- ​   Phân quyền Editor/Moderator.
- ​   Xem dashboard có filter thời gian.
- ​   Xem doanh thu được đối soát ở chế độ dữ liệu test.

#### 19.3. Validation Measures

Task Success, Time-on-Task, Critical Errors, Comprehension of Routing Labels, Recovery
Success và Feedback.

Dữ liệu mẫu trong prototype phải được đánh dấu DEMO.

### 20. PRODUCT ROADMAP & RELEASE PLAN


    Release                  Mobile                               Admin

    PoC              Map + Route Vertical Slice           Data/Transit Tools tối thiểu

    Core MVP         Map, Discover, Feedback              Overview, Mobility, Content
Management

    Community        Accounts, Profiles, Posts, Follow,   Users, Moderation, Community
    Reviews                              Dashboard

    Personal         Exploration, Journal                 Content/Privacy Support
Experience

    Commerce         Partner Offers, Vouchers khi hợp     Revenue, Partners, Settlements
lệ

    Expansion        Advanced Recommendations             Advanced Analytics

### 21. SUCCESS METRICS & UX DECISIONS

    Metric                Không được suy luận

    Route Found        Không đồng nghĩa đã di chuyển

    Route Selected     Không đồng nghĩa hoàn thành chuyến

    Place Viewed       Không đồng nghĩa đã tới

    Visited               Không chứng minh định vị

    Affiliate Click       Không chứng minh đã thanh toán

Gross Partner Value   Không tự động là doanh thu GoWander

    Reward Points         Không mặc định là tiền mặt

Metrics phải được xây trên events và nguồn được xác minh, không dựa vào việc đếm màn hình
một cách vô nghĩa.

### 22. PRODUCT & UX CONCLUSION

GoWander gồm hai trải nghiệm chính:

Mobile: Discover, Move, Remember, Share.

Admin: Monitor, Verify, Manage, Moderate, Reconcile.

Core MVP tập trung tính đúng đắn của hành trình và nội dung; những khả năng cá nhân, cộng
đồng và thương mại được mở rộng theo bằng chứng.

GoWander — Move Smart. Explore More.

---

