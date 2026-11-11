### 15. DASHBOARD DATA SOURCES

    Dashboar                  Data Source                          Core Metrics
d

    Executive   users, places, usage_events,                 WAU, Core Journey
    feedback_reports, monitoring                 Completion, Critical Issues

    Mobility    transit_data_versions, usage_events, OTP     Route Found, Technical
    metrics, feedback                            Failure, P95

    Discover    places, media, stories, events               Completeness, Coverage,
Discover-to-Route

    Communi     posts, reviews, follows, moderation          Retention, Contributions,
    ty                                                       Backlog

    Revenue     partners, referrals, revenue transactions,   Net Revenue, Channel,
    adjustments, expenses, settlements           Margin

    Rewards     points_ledger, reward_grants, moderation     Points Granted, Reversed,
Abuse

#### 15.1. Metric Quality Requirements

Một KPI phải có công thức được duyệt, source, kỳ đo, refresh policy và chủ sở hữu.

Không được:

- ​   Dùng Route Request để khẳng định hoàn thành chuyến đi.
- ​   Dùng Visit Marker để khẳng định GPS verification.
- ​   Dùng Affiliate Click để ghi Revenue.
- ​   Gộp Total Partner Transaction Value vào GoWander Revenue.
- ​   Dùng Points Granted làm chi phí tiền mặt khi chưa có nghĩa vụ đổi thưởng.

#### 15.2. Technical Metrics

API/OTP latency, error rate, CPU/RAM nên lấy từ observability system khi có, không
nhất thiết từ relational event tables.

### 16. DATA PRIVACY, OWNERSHIP & LIFECYCLE

#### 16.1. Private Data

- ​ user_contacts.
- ​ user_auth_identities.
- ​   user_preferences.
- ​   Private Exploration/Journal.
- ​   Reward Transactions.
- ​   Business Agreements & Settlements.

#### 16.2. Access Control

Phía Mobile chỉ lấy dữ liệu được cấp phép thông qua Backend.

Admin cũng phải qua RBAC và resource policy.

Không cho Browser kết nối database trực tiếp.

#### 16.3. Data Retention

Cần chốt chính sách riêng cho:

- ​   Authentication/Contact Data.
- ​   Deleted Accounts.
- ​   Community Content.
- ​   Reward Ledger.
- ​   Business Records.
- ​   Usage Events.
- ​   Audit Logs.
- ​   Media.

Thời gian lưu phải có cơ sở vận hành và pháp lý, không giữ vĩnh viễn mọi dữ liệu
mặc định.

### 17. MIGRATION & IMPLEMENTATION ORDER

Migration 01 — Foundation

- ​   PostgreSQL/PostGIS.
- ​   Identity.
- ​   Roles/User Roles.
- ​   Place Categories/Places.

Migration 02 — Discover

- ​   Categories Links.
- ​   Media.
- ​   Foods.
- ​   Historical Stories.

Migration 03 — Mobility & Feedback

- ​ Transit Sources/Versions.
- ​ Feedback.
- ​ Usage Events.

Migration 04 — Account/Exploration

- ​ Contacts/Profiles/Preferences nếu chưa triển khai.
- ​ User Place Status.
- ​ Journal.

Migration 05 — Community

- ​ Posts, Reviews, Follows.
- ​ Reports, Moderation.
- ​ Admin Audit.

Migration 06 — Rewards

- ​ Ledger, Rules/Grants khi cần.

Migration 07 — Business

- ​   Partners.
- ​   Agreements.
- ​   Referrals.
- ​   Revenue.

- ​ Adjustments.
- ​ Expenses.
- ​ Settlements.

Migration 08 — Conditional Optimization

- ​ Outbox.
- ​ Aggregate Read Models.
- ​ Additional Indexes/Partitions.

Migrations phải được tổ chức theo phụ thuộc khóa ngoại thực tế, không áp dụng mù
quáng một thứ tự nếu môi trường đã có dữ liệu.

### 18. TESTING & VALIDATION

#### 18.1. Identity

- ​   Google first-login tạo đúng một account.
- ​   Login lặp lại giữ user ID.
- ​   Không duplicate identity khi concurrent requests.
- ​   Email trùng không tự gộp accounts.
- ​   Contact fields không bị lộ.

#### 18.2. Discover

- ​   Spatial queries trả đúng phạm vi.
- ​   Multi-category links nhất quán.
- ​   Media có nguồn và quyền sử dụng.
- ​   Food không bị gộp thành Place.

#### 18.3. Community

- ​   Không Follow chính mình.
- ​   Không Follow/Like trùng.
- ​   Một review chính/user/place.
- ​   Nội dung Hidden không xuất hiện public.

#### 18.4. Rewards

- ​ Không double-credit.
- ​ Điều chỉnh bằng ledger mới.
- ​ Không thưởng hành vi bị loại.

#### 18.5. Admin

- ​ Role-based access.

- ​ Unauthorized action bị chặn tại Backend.
- ​ Audit log ghi thao tác nhạy cảm.

#### 18.6. Revenue

- ​   External callback idempotent.
- ​   Refund không double count.
- ​   Net Revenue đúng theo giao dịch/adjustments.
- ​   Partner Value khác GoWander Revenue.
- ​   Settlement liên kết được chứng từ.
- ​   Currency được kiểm tra trước aggregation.

#### 18.7. Performance

- ​   EXPLAIN ANALYZE.
- ​   Nearby Places.
- ​   Feed Pagination.
- ​   Login Lookup.
- ​   Revenue Aggregations.
- ​   Dashboard Refresh Time.
- ​   Backup/Restore.

### 19. OPEN DESIGN DECISIONS

Cần xác nhận trước khi viết DDL production:

1. Identity Provider và phương thức đăng nhập iOS.
2. UUID generation.
3. Email/Phone uniqueness policy.
4. Có cần place_details vật lý không.
5. Place Primary Category và multi-category logic.
6. Historical Story translation/attribution.
7. Transit stops cần lưu local hay không.
8. Polymorphic content reports hay bảng riêng.
9. Reward Grant Workflow.
10. Role-to-permission mapping có cần bảng permissions không.
11. Admin Audit Reference cho đối tượng BIGINT/composite keys.
12. Business Agreement và số tiền theo currency.
13. Revenue recognition theo contract.
14. Partner settlement item-level reconciliation.
15. Media lifecycle và account deletion.
16. Data retention, backups và observability.

Đây là những quyết định có thể bổ sung bảng ở các bản sau; 49 bảng là logical
inventory, không phải con số khóa cứng.

### 20. DATABASE CONCLUSION

GoWander lựa chọn PostgreSQL + PostGIS, cấu trúc chuẩn hóa theo domain, ID
người dùng nội bộ ổn định và một số kỹ thuật NoSQL Data Modeling Patterns được
chuyển đổi có chọn lọc.

9 Domains → 49 Proposed Tables, gồm các bảng tương lai/điều kiện.

Ba lớp dữ liệu quan trọng được phân biệt:

Operational Truth: Các bản ghi nghiệp vụ có khóa, giao dịch và constraints.

Read Optimization: DTO projections, indexes, pagination và aggregate models khi
cần.

Decision Metrics: Những chỉ số có nguồn, công thức và hành động quản trị.

Mục tiêu cuối cùng là dữ liệu đúng, bảo mật, truy vấn hiệu quả và có thể kiểm toán,
không phải có càng nhiều bảng hoặc pattern càng tốt.

GoWander — Move Smart. Explore More.

---

