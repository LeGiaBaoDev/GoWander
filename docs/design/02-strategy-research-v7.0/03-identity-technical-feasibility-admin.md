### 09. IDENTITY & SOCIAL STRATEGY

#### 9.1. Identity


#### Google Sign-In là phương án khởi đầu khi có tài khoản.

#### Apple Sign-In cần đánh giá với chính sách phát hành iOS và identity provider.

#### Thiết kế tài khoản cho phép một user có nhiều authentication identities.

#### Email/Phone là dữ liệu liên hệ được bảo vệ, không phải public identifier.

#### Không tự động gộp hai tài khoản vì email giống nhau.

#### 9.2. Wander Profile

Hồ sơ thể hiện bản sắc khám phá, không phải địa chỉ hay danh tính pháp lý của
người dùng.

#### 9.3. Community

#### Ưu tiên nội dung hữu ích, Follow và công cụ báo cáo/kiểm duyệt.

#### Không sử dụng Rank, Level hoặc Leaderboard.

#### 9.4. Rewards

#### Thưởng đóng góp được phê duyệt, chống spam và trùng lặp.

Lượt Like, Follow, đánh giá tích cực và tự đánh dấu Visited không phải điều kiện
tự động cộng điểm.

### 10. TECHNICAL FEASIBILITY STRATEGY

#### 10.1. Định hướng kiến trúc

- ​ Mobile: KMP + Compose Multiplatform.
- ​ Backend: Go Modular Monolith.
- ​ Data: PostgreSQL + PostGIS.
- ​ Routing: OTP qua Routing Provider Adapter.
- ​ Map: MapLibre Compose — chờ PoC.
- ​ Admin: Web Dashboard chung Go Backend, RBAC.
- ​ Deployment: Docker Compose ban đầu.

#### 10.2. Technical Hypotheses


    I             Câu hỏi cần chứng minh                        PoC
D

    T    Có nguồn GTFS/OSM hợp lệ trong khu vực        Data Audit
    1    pilot?

    T    OTP tính đúng tuyến bus/metro/walking?        Routing Test Cases
2

    T    MapLibre đáp ứng Android/iOS?                 Map PoC
3

    T    Go API tích hợp OTP ổn định?                  End-to-end Slice
4

    T    Database phục vụ Nearby/Discover nhanh?       PostGIS Query
    5                                                  Benchmark

    T    Hạ tầng đáp ứng ngân sách dự kiến?            Resource/Cost
    6                                                  Benchmark

#### 10.3. Technical Decision Gate

Pass: Có dữ liệu hợp lệ và hành trình kiểm thử đúng, UI chạy cả hai nền tảng, lỗi
được xử lý minh bạch.

Conditional Pass: Thu hẹp khu vực, giới hạn phương tiện, thay provider hoặc
điều chỉnh hạ tầng.

#### Fail: Không cam kết hành trình thực tế bằng dữ liệu demo giả lập.

### 11. ADMIN & OPERATIONAL STRATEGY


Admin không chỉ là công cụ CRUD. Nó là hệ thống vận hành nội dung, độ tin cậy
và kinh doanh.

#### 11.1. Admin Roles

- ​ Content Editor.
- ​ Moderator.
- ​ Operations Admin.
- ​ Business Admin.
- ​ System Admin.

#### 11.2. Six Decision-oriented Dashboards

    Dashboard                    Decision Question

    Executive            Những mục tiêu cốt lõi nào đang lệch?

    Mobility             Vấn đề là dữ liệu tuyến hay kỹ thuật?

    Discover             Nội dung nào cần xác minh/bổ sung?

    Users &              Người dùng có quay lại và đóng góp chất
    Community            lượng?

    Revenue &            Kênh thu nào hiệu quả, khoản nào chưa
    Business             đối soát?

    Rewards              Điểm có tạo giá trị và bị gian lận không?

#### 11.3. KPI Governance

Mỗi metric có: định nghĩa, công thức, điều kiện tính, nguồn, kỳ đo, owner, giới
hạn và hành động.

#### Không thêm chỉ số vì dễ tạo biểu đồ mà không có quyết định tương ứng.


