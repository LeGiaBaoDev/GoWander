### 12. DATA MODELING PATTERNS

#### 12.1. Subset Pattern

Áp dụng: place_media, post_media, journal_media, historical_stories và có
thể place_details.

Mục tiêu: Không tải dữ liệu lớn khi chỉ cần summary.

Điều kiện: Không tách vật lý bằng mọi giá; PostgreSQL Projection có thể đủ.

#### 12.2. Attribute Pattern

Áp dụng: place_details.attributes, user_preferences.settings,
notifications.payload.

Mục tiêu: Lưu metadata linh hoạt nhưng có schema validation.

#### 12.3. Computed Pattern

Đề xuất tương lai:

- ​ place_rating_stats.
- ​ user_social_stats.
- ​ post_stats.
- ​ daily_product_metrics.
- ​ revenue_daily_summary.

Điều kiện: Chỉ bổ sung khi truy vấn aggregate thực tế cho thấy cần. Phải có quy tắc
freshness, recompute và reconciliation.

#### 12.4. Extended Reference Pattern

Có thể dùng snapshot cho Feed Read Model khi cần, nhưng không sao chép
email/phone vào Posts.

Ban đầu dùng JOIN và batch fetching.

#### 12.5. Bucket Pattern

Không gom raw usage events thành JSONB lớn ngay.

Có thể tổng hợp analytics theo ngày hoặc thời gian sau này, đồng thời cân nhắc
partition khi có dữ liệu đủ lớn.

#### 12.6. Schema Versioning


Áp dụng versioned SQL migrations và Expand–Migrate–Contract.

Không thêm schema_version vào mọi bảng.

#### 12.7. Tree Pattern

place_categories.parent_id.

Không dùng closure table nếu chỉ có vài cấp danh mục.

#### 12.8. Polymorphic Pattern

content_reports.target_type + target_id có trade-off về FK.

Cần kiểm tra target ở application hoặc điều chỉnh schema nếu cần toàn vẹn tham
chiếu mạnh hơn.

#### 12.9. Ledger / Append-only

points_ledger, revenue_adjustments, admin_audit_logs.

Ngăn thao tác sửa lịch sử làm mất tính truy vết.

#### 12.10. Idempotency

Áp dụng cho:

- ​   Google first-login/create identity.
- ​   Reward Grants.
- ​   Provider callbacks.
- ​   Revenue Transaction Imports.
- ​   Partner Referrals.

### 13. RELATIONSHIP & CONSTRAINT MATRIX

    Relationship          Cardinality            Constraint

    users → auth identities      1               UQ(issuer,
provider_subject)

    users → contacts             1:0..1          PK(user_id)

    users → profiles          1:0..1    PK(user_id)

    users → preferences       1:0..1    PK(user_id)

    places ↔ categories       N         Composite PK

    places ↔ foods            N         Composite PK

    users ↔ places (status)   N         PK(user_id, place_id)

    users → reviews →         1 / N:1   UQ(user_id, place_id)
places

    users ↔ users (follows)   N         PK + no self-follow

    posts ↔ users (likes)     N         Composite PK

    users ↔ roles             N         Composite PK

    partners → agreements     1         FK

    partners → referrals      1         FK

    revenue → adjustments     1         FK

    partners → settlements    1         FK

    users → points ledger      1             Unique event key

### 14. INDEXING STRATEGY

    Table                          Index                       Mục đích

    user_auth_identiti   UQ(issuer, provider_subject)           Login lookup
es

    user_auth_identiti   (user_id)                              Linked identities
es

    user_profiles        UQ(username)                           Public profile

    places               GiST(location)                         Nearby

    place_category_li    (category_id, place_id)                Category filter
nks

    place_media          (place_id, sort_order)                 Place details

    posts                (published_at DESC, id DESC) WHERE     Feed
Published

    posts                (author_id, published_at DESC)         Author posts

    reviews              UQ(user_id, place_id)                  One review rule

    reviews             (place_id, created_at DESC)           Place reviews

    user_follows        (followee_id, follower_id)            Followers

    user_place_status PK(user_id, place_id)                   Exploration

    points_ledger       UQ(event_key)                         Idempotency

    points_ledger       (user_id, created_at DESC, id DESC)   Wallet

    notifications       (user_id, created_at DESC, id DESC)   Notification list

    user_roles          (role_id, user_id)                    Role memberships

    admin_audit_logs    (actor_id, created_at DESC)           Audit lookup

    partner_agreemen (partner_id, effective_from DESC)        Contract history
ts

    partner_referrals   (partner_id, occurred_at DESC)        Partner attribution

    revenue_transacti   (channel, recognized_at)              Revenue metrics
ons

    revenue_transacti   (partner_id, recognized_at)           Partner revenue
ons

    revenue_adjustm     (revenue_transaction_id)              Revenue
    ents                                                      reconciliation

    partner_settlemen   (partner_id, period_start DESC)              Settlement history
ts

Index trên là đề xuất. Khi triển khai, sử dụng EXPLAIN ANALYZE và query workload;
không tạo mọi index khi chưa cần.

