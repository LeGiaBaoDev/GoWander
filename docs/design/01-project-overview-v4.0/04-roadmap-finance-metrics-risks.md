### 10. DEVELOPMENT STRATEGY

Research Online → Validate Data & Technical PoC → Build Core MVP → Pilot → Collect
Feedback → Validate → Expand.

GoWander không bắt buộc phỏng vấn hoặc khảo sát trước khi lập prototype. Sau pilot, quyết định dựa
trên dữ liệu hành vi phù hợp quyền riêng tư và feedback thực tế.

#### MVP Pilot Scope

- ​   Một đến hai hành lang/khu vực TP.HCM.
- ​   Bus, metro và walking theo dữ liệu được xác minh.
- ​   Khoảng 30–50 địa điểm.
- ​   Khoảng 10–15 Historical Stories.
- ​   Mẫu tester mục tiêu 50–100 người.

Các con số trên là mục tiêu lập kế hoạch, không phải thành tựu đã đạt.

### 11. DEVELOPMENT ROADMAP

    Phase      Khoảng thời gian dự kiến                             Trọng tâm

    1         Tháng 1–6                      Data/Map/Routing PoC, Core MVP, Pilot, Admin cơ bản

    2         Tháng 7–10                     My Exploration, Journal, Behavioral Discovery

    3         Tháng 11–15                    Contextual Experience, Community Growth

    4         Tháng 16–21                    Partners, Revenue, Commerce

    5         Tháng 22–27+                   Advanced Recommendation/AI, Scaling

Thời gian chỉ là định hướng và được điều chỉnh theo PoC, nguồn lực, dữ liệu và phản hồi.

Community Foundation và Rewards có thể được triển khai trong hoặc sau Phase 1 nếu không làm
chậm chức năng cốt lõi.

### 12. FINANCIAL OVERVIEW

#### 12.1. Ngân sách tham khảo trước đây

    Phase       Ước tính sơ bộ (VNĐ)

    1          0,3–1,5 triệu

    2          1,2–3 triệu

    3          2–4,5 triệu

    4          3–7 triệu

    5          4–10 triệu

    Tổng       10,5–26 triệu

Đây là giả định kế hoạch ban đầu, chưa được xác thực và có thể không đủ cho OTP hosting, media,
observability, authentication, chi phí nền tảng hoặc dữ liệu bên ngoài. Không bao gồm đầy đủ chi phí
lao động.

#### 12.2. Nhóm chi phí cần dự toán

- ​   VPS/API/OTP hosting.
- ​   Database, storage, backups.
- ​   Domain, HTTPS, monitoring.
- ​   Map tiles, transit data/license nếu có.
- ​   Authentication.
- ​   Apple Developer/Google Play.
- ​   Media storage/CDN.
- ​   Chi phí hợp tác và giao dịch ở Phase 4.

### 13. PRODUCT & BUSINESS SUCCESS METRICS

    Mục tiêu                    Metric                                     Ý nghĩa

    Mobility      Route Found Rate                        Người dùng có nhận được tuyến khả dụng

    Reliability   Technical Failure Rate, P95 Latency     Engine/API có đáng tin cậy

    Discover      Discover-to-Route Conversion            Nội dung có tạo hành động

    Quality       Verified Content Coverage               Nội dung đủ chuẩn

    Retention     D7 Retention                            Người dùng có quay lại

    Community     Valid Contribution Rate                 Đóng góp có ích

    Rewards       Points Reversal/Abuse Rate              Điểm có bị lạm dụng

    Business      Net Revenue, Contribution Margin        Tính hiệu quả khi thương mại hóa

    Operations    Critical Issue Backlog                  Mức độ cần can thiệp

Mọi metric phải có định nghĩa và tracking plan; không tự suy luận kết quả từ những sự kiện không
chứng minh hành vi đó.

### 14. RỦI RO VÀ GIỚI HẠN

    Rủi ro                       Mức độ                     Cách xử lý

    Thiếu GTFS hoặc quyền sử dụng        Cao               Data feasibility gate

    OTP không phù hợp phạm vi pilot      Cao               Routing PoC, provider fallback

    MapLibre iOS không đáp ứng           Cao               Cross-platform map PoC

    Nguồn transit không realtime         Cao               Ghi rõ scheduled data

    Dữ liệu Place chưa chính xác         Trung bình–cao    Biên tập, báo lỗi, kiểm tra

    Scope quá rộng với Solo Developer    Cao               Priority A trước

    Rủi ro dữ liệu cá nhân               Cao               Privacy by Design, RBAC

    Community spam/abuse                 Trung bình–cao    Moderation & reporting

    Doanh thu không như giả định         Cao               Business validation sau pilot

    Chi phí hạ tầng vượt dự toán         Trung bình–cao    Benchmark và kiểm soát quy mô

### 15. NEXT STEPS & CONCLUSION

#### Trình tự triển khai

1. Xác minh nguồn và giấy phép GTFS/OSM.
2. Chạy OTP Routing PoC.
3. Chạy MapLibre/KMP PoC trên Android/iOS.
4. Hoàn thiện Go API, PostgreSQL/PostGIS và Database Migrations.
5. Triển khai Map + Discover thành một luồng hoàn chỉnh.
6. Xây Admin cơ bản để quản lý nội dung và feedback.

7. Hoàn thiện kiểm thử và phát hành Pilot.
8. Bổ sung Identity, Community, Exploration và Rewards theo ưu tiên.
9. Đánh giá Business Model và doanh thu khi có người dùng thực tế.

Kết luận: GoWander xây dựng trải nghiệm di chuyển và khám phá đáng tin cậy trước, phát triển cộng
đồng và giá trị cá nhân sau, rồi mới thương mại hóa dựa trên bằng chứng thực tế.

GoWander — Move Smart. Explore More.

---

