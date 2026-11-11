### 07. COMMUNITY — 8 BẢNG

#### 7.1. posts

Ý nghĩa: Bài đăng Community.

    Field       Type / Constraint                     Ý nghĩa

    id             UUID · PK              ID bài

    author_id      UUID · FK users · NN   Tác giả

    place_id       UUID · FK places       Địa điểm liên quan

    content        TEXT · NN              Nội dung

    status         VARCHAR(20) · NN       DRAFT, PENDING, PUBLISHED, HIDDEN...

    created_at     TIMESTAMPTZ · NN       Ngày tạo

    published_         TIMESTAMPTZ          Ngày công khai
at

    updated_at         TIMESTAMPTZ · NN     Ngày sửa

Pattern: Feed Projection + Keyset Pagination.

#### 7.2. post_media

Ý nghĩa: Ảnh/media đi kèm bài đăng.

    Field         Type / Constraint    Ý nghĩa

    id             UUID · PK               ID

    post_id        UUID · FK posts · NN    Bài đăng

    storage_k      TEXT · NN               Khóa tệp
ey

    media_typ      VARCHAR(30) · NN        Loại
e

    sort_orde      INTEGER · NN            Thứ tự
r

    created_a      TIMESTAMPTZ · NN        Ngày
    t                                      thêm

Pattern: Subset.

#### 7.3. reviews

Ý nghĩa: Đánh giá một địa điểm.

    Field           Type / Constraint           Ý nghĩa

    id            UUID · PK                       ID review

    user_id       UUID · FK users · NN            Tác giả

    place_id      UUID · FK places · NN           Địa điểm

    rating        SMALLINT · NN, CHECK 1–5        Điểm đánh
giá

    content       TEXT                            Nhận xét

    status        VARCHAR(20) · NN                Trạng thái

    created_      TIMESTAMPTZ · NN                Ngày tạo
at

    updated_      TIMESTAMPTZ · NN                Ngày sửa
at

Constraint: UNIQUE(user_id, place_id).

Pattern: Normalized + Conditional Computed Stats.

#### 7.4. user_follows

Ý nghĩa: Quan hệ Follow hai chiều truy vấn.

    Field        Type / Constraint           Ý nghĩa

    follower_        UUID · PK/FK users    Người Follow
id

    followee_        UUID · PK/FK users    Người được
    id                                     Follow

    created_a        TIMESTAMPTZ · NN      Thời điểm
t

Pattern: Social Graph Relational Model.

Constraint: follower_id <> followee_id.

Index: (followee_id, follower_id).

#### 7.5. post_likes

Ý nghĩa: Mỗi user có thể Like một post tối đa một lần.

    Field       Type / Constraint     Ý nghĩa

    post_id       UUID · PK/FK posts      Bài

    user_id       UUID · PK/FK users      Người
Like

    created_      TIMESTAMPTZ · NN        Ngày Like
at

Pattern: Many-to-Many.

Status: Future.

#### 7.6. post_comments

Ý nghĩa: Bình luận bài đăng.

    Field           Type / Constraint         Ý nghĩa

    id                   UUID · PK              ID

    post_id              UUID · FK posts · NN   Bài

    user_id              UUID · FK users · NN   Người viết

    parent_comment_      UUID · FK self         Bình luận
    id                                          cha

    content              TEXT · NN              Nội dung

    status               VARCHAR(20) · NN       Trạng thái

    created_at           TIMESTAMPTZ · NN       Ngày tạo

    updated_at           TIMESTAMPTZ · NN       Ngày sửa

Pattern: Optional Tree + Pagination.

Status: Future.

#### 7.7. content_reports

Ý nghĩa: Báo cáo vi phạm nội dung/tài khoản.

    Field      Type / Constraint                    Ý nghĩa

    id            UUID · PK               ID

    reporter_     UUID · FK users · NN    Người báo cáo
id

    target_ty     VARCHAR(30) · NN        POST, REVIEW, USER...
pe

    target_id     UUID · NN               ID đối tượng

    reason        VARCHAR(50) · NN        Loại vi phạm

    descripti     TEXT                    Diễn giải
on

    status        VARCHAR(20) · NN        OPEN, REVIEWING, RESOLVED...

    created_a     TIMESTAMPTZ · NN        Ngày gửi
t

    resolved_     TIMESTAMPTZ             Ngày xử lý
at

Pattern: Controlled Polymorphic Reference.

Application phải xác minh target tồn tại; polymorphic FK có trade-off về referential
integrity.

#### 7.8. moderation_actions


Ý nghĩa: Lịch sử quyết định kiểm duyệt.

    Field          Type / Constraint                Ý nghĩa

    id              UUID · PK                ID thao tác

    moderator_      UUID · FK users · NN     Người xử lý
id

    report_id       UUID · FK                Report liên quan
content_reports

    target_typ      VARCHAR(30) · NN         Loại đối tượng
e

    target_id       UUID · NN                Đối tượng

    action          VARCHAR(40) · NN         APPROVE, HIDE, REJECT...

    reason          TEXT                     Lý do

    created_at      TIMESTAMPTZ · NN         Ngày xử lý

Pattern: Audit History.

