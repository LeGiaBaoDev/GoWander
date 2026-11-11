### 08. REWARDS — 3 BẢNG

#### 8.1. points_ledger

Ý nghĩa: Nguồn sự thật cho điểm đã cộng/trừ.

    Field            Type / Constraint           Ý nghĩa

    id             UUID · PK                     ID giao dịch

    user_id        UUID · FK users · NN          Người sở hữu
điểm

    amount         INTEGER · NN, CHECK <> 0      Điểm cộng/trừ

    source_ty      VARCHAR(40) · NN              Nguồn đóng góp
pe

    source_id      UUID                          ID nguồn

    event_key      VARCHAR(200) · UQ, NN         Khóa
idempotency

    descripti      TEXT                          Lý do
on

    created_a      TIMESTAMPTZ · NN              Ngày ghi nhận
t

Pattern: Ledger + Idempotency.

Không sửa trực tiếp giao dịch đã xác nhận; điều chỉnh bằng giao dịch mới.

#### 8.2. reward_rules

Ý nghĩa: Quy tắc cấp điểm cấu hình được.

    Field      Type / Constraint          Ý nghĩa

    id             BIGINT · PK            ID rule

    action_ty      VARCHAR(40) · NN       Loại đóng
    pe                                    góp

    points         INTEGER · NN           Số điểm

    is_active      BOOLEAN · NN           Hoạt động

    valid_fro      TIMESTAMPTZ            Bắt đầu
m

    valid_unt      TIMESTAMPTZ            Kết thúc
il

Pattern: Rule Configuration.

Status: Conditional.

#### 8.3. reward_grants

Ý nghĩa: Quá trình xét duyệt một đóng góp đủ điều kiện.

    Field           Type / Constraint                  Ý nghĩa

    id                    UUID · PK              ID yêu cầu

    user_id               UUID · FK users · NN   Người nhận

    source_type           VARCHAR(40) · NN       Loại đóng góp

    source_id             UUID · NN              Nguồn

    status                VARCHAR(20) · NN       PENDING, APPROVED, REJECTED

    points_awarde         INTEGER                Điểm xét duyệt
d

    ledger_entry_         UUID · FK ledger       Giao dịch liên kết
id

    created_at            TIMESTAMPTZ · NN       Ngày tạo

    resolved_at           TIMESTAMPTZ            Ngày xử lý

Pattern: Idempotent Workflow.

Status: Conditional.

Cần business unique key phù hợp để tránh xét/cấp cùng một đóng góp nhiều lần.

### 09. SUPPORTING — 4 BẢNG

#### 9.1. feedback_reports

Ý nghĩa: Báo lỗi tuyến, địa điểm hoặc góp ý ứng dụng.

    Field              Type / Constraint                 Ý nghĩa

    id                    UUID · PK              ID phản hồi

    user_id               UUID · FK users        User nếu có

    report_type           VARCHAR(40) · NN       ROUTE, PLACE, APP

    related_place_        UUID · FK places       Place liên quan
id

    description           TEXT · NN              Nội dung

    status                VARCHAR(20) · NN       OPEN, REVIEWING, RESOLVED

    created_at            TIMESTAMPTZ · NN       Ngày gửi

    resolved_at           TIMESTAMPTZ            Ngày xử lý

Pattern: Relational.

#### 9.2. notifications

Ý nghĩa: Thông báo trong ứng dụng.

    Field      Type / Constraint            Ý nghĩa

    id           UUID · PK                  ID

    user_id      UUID · FK users · NN       Người nhận

    type         VARCHAR(40) · NN      Loại

    title        VARCHAR(200) · NN     Tiêu đề

    body         TEXT                  Nội dung

    payload      JSONB · NN            Metadata điều
hướng

    created_     TIMESTAMPTZ · NN      Ngày tạo
at

    read_at      TIMESTAMPTZ           Ngày đọc

Pattern: JSONB + Pagination.

#### 9.3. usage_events

Ý nghĩa: Product Events có kiểm soát phục vụ đo lường.

    Field     Type / Constraint           Ý nghĩa

    id           UUID · PK             ID

    user_id      UUID · FK users       User nếu có

    session_     UUID                  Phiên
id

    event_ty      VARCHAR(80) · NN      Loại event
pe

    metadata      JSONB · NN            Thuộc tính có
schema

    created_      TIMESTAMPTZ · NN      Thời điểm
at

Pattern: Event Rows; Aggregation/Bucket về sau.

Không mặc định lưu lịch sử GPS hay thông tin nhận dạng cá nhân trong metadata.

#### 9.4. outbox_events

Ý nghĩa: Đồng bộ nghiệp vụ và xử lý nền đáng tin cậy khi cần.

    Field          Type / Constraint       Ý nghĩa

    id                 UUID · PK              ID sự kiện

    event_type         VARCHAR(100) · NN      Loại sự kiện

    aggregate_typ      VARCHAR(50) · NN       Loại đối
    e                                         tượng

    aggregate_id       UUID · NN              ID đối tượng

    payload            JSONB · NN             Dữ liệu

    created_at         TIMESTAMPTZ · NN       Ngày tạo

    processed_at          TIMESTAMPTZ          Ngày xử lý

    attempt_count         INTEGER · NN         Số lần thử

    next_attempt_         TIMESTAMPTZ          Lần xử lý tiếp
at

Pattern: Transactional Outbox.

Status: Conditional.

### 10. ADMINISTRATION & ACCESS — 3 BẢNG

#### 10.1. roles

Ý nghĩa: Danh mục vai trò quản trị RBAC.

    Field        Type / Constraint                         Ý nghĩa

    id            BIGINT · PK                 ID vai trò

    code          VARCHAR(50) · UQ, NN        CONTENT_EDITOR, MODERATOR...

    name          VARCHAR(100) · NN           Tên hiển thị

    descripti     TEXT                        Diễn giải quyền
on

    is_active     BOOLEAN · NN                Còn áp dụng

    created_a      TIMESTAMPTZ · NN            Ngày tạo
t

Pattern: RBAC Role Catalog.

Các role dự kiến:

- ​   CONTENT_EDITOR.
- ​   MODERATOR.
- ​   OPERATIONS_ADMIN.
- ​   BUSINESS_ADMIN.
- ​   SYSTEM_ADMIN.

Bảng này không tự định nghĩa từng permission chi tiết; quyền có thể được map trong
ứng dụng ở giai đoạn đầu.

#### 10.2. user_roles

Ý nghĩa: Gán role cho một user nội bộ.

    Field          Type / Constraint       Ý nghĩa

    user_id        UUID · PK/FK users       Người được
gán

    role_id        BIGINT · PK/FK roles     Vai trò

    assigned_      UUID · FK users          Người cấp
    by                                      quyền

    assigned_      TIMESTAMPTZ · NN         Ngày cấp
at

    expires_a      TIMESTAMPTZ              Hết hạn nếu có
t

Pattern: RBAC Mapping.

Constraint: PK(user_id, role_id).

Phải kiểm tra role và quyền thực hiện ở Backend.

#### 10.3. admin_audit_logs

Ý nghĩa: Nhật ký thao tác quản trị quan trọng.

    Field      Type / Constraint                 Ý nghĩa

    id            UUID · PK              ID audit

    actor_id      UUID · FK users · NN   Người thực hiện

    action        VARCHAR(100) · NN      Hành động

    entity_ty     VARCHAR(60) · NN       Loại đối tượng
pe

    entity_id     UUID                   ID đối tượng khi dùng UUID

    metadata      JSONB · NN             Thông tin thay đổi không nhạy
cảm

    request_i     VARCHAR(100)           Liên kết truy vết request
d

    created_a     TIMESTAMPTZ · NN       Thời điểm thao tác
t

Pattern: Append-only Audit Log.

Security:

- ​ Không log tokens, password, email/phone thô không cần thiết.
- ​ Không cho Admin sửa/xóa log tùy tiện.
- ​ Với đối tượng dùng BIGINT hoặc composite ID, cần biểu diễn tham chiếu
tương ứng trong metadata hoặc điều chỉnh mô hình audit để không mất khả
năng truy vết.

