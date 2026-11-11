### 05. MOBILITY — 3 BẢNG

#### 5.1. transit_data_sources

Ý nghĩa: Danh sách nguồn transit và giấy phép.

    Field        Type / Constraint                   Ý nghĩa

    id              UUID · PK               ID nguồn

    name            VARCHAR(200) · NN       Tên

    provider        VARCHAR(200)           Đơn vị cung cấp

    source_typ      VARCHAR(30) · NN       GTFS_STATIC, GTFS_RT, OSM
e

    source_url      TEXT                   URL

    license_in      TEXT                   Quyền sử dụng
fo

    status          VARCHAR(20) · NN       Trạng thái

    created_at      TIMESTAMPTZ · NN       Ngày tạo

Pattern: Reference Data.

#### 5.2. transit_data_versions

Ý nghĩa: Theo dõi nhập, kiểm tra và sử dụng phiên bản dữ liệu.

    Field         Type / Constraint                      Ý nghĩa

    id               UUID · PK               ID phiên bản

    source_id        UUID · FK sources ·     Nguồn
NN

    version_lab      VARCHAR(100) · NN       Phiên bản
el

    checksum        VARCHAR(128)              Dấu kiểm tra

    imported_at     TIMESTAMPTZ               Ngày nhập

    validated_a     TIMESTAMPTZ               Ngày xác minh
t

    status          VARCHAR(20) · NN          IMPORTED, VALIDATED, FAILED, ACTIVE

    artifact_ke     TEXT                      Vị trí artifact
y

    error_summa     TEXT                      Lỗi nhập/xác minh
ry

Pattern: Data Versioning.

#### 5.3. transit_stop_refs

Ý nghĩa: Tham chiếu trạm khi GoWander cần dữ liệu độc lập OTP.

    Field                Type / Constraint                Ý nghĩa

    id                  UUID · PK                       ID nội bộ

    source_id           UUID · FK sources · NN          Nguồn

    external_stop_      VARCHAR(150) · NN               Mã trạm bên
    id                                                  ngoài

    name                  VARCHAR(200) · NN              Tên trạm

    location              GEOGRAPHY(POINT,4326)          Tọa độ

    updated_at            TIMESTAMPTZ · NN               Ngày đồng bộ

Pattern: External Reference.

Constraint: UNIQUE(source_id, external_stop_id).

Status: Conditional.

### 06. EXPLORATION — 5 BẢNG

#### 6.1. user_place_status

Ý nghĩa: Trạng thái cá nhân của user với một Place.

    Field        Type / Constraint             Ý nghĩa

    user_id         UUID · PK/FK users       Chủ sở hữu

    place_id        UUID · PK/FK places      Địa điểm

    want_to_vis     BOOLEAN · NN             Muốn đến
it

    visited         BOOLEAN · NN             Tự đánh dấu đã đến

    favorite        BOOLEAN · NN             Yêu thích

    visited_at       TIMESTAMPTZ             Mốc ghi nhận trạng
thái

    updated_at       TIMESTAMPTZ · NN        Lần cập nhật

Pattern: User-specific State.

Không phải GPS proof.

#### 6.2. exploration_entries

Ý nghĩa: Nhật ký/lần trải nghiệm do user ghi lại.

    Field      Type / Constraint           Ý nghĩa

    id           UUID · PK                ID trải nghiệm

    user_id      UUID · FK users · NN     Chủ

    place_id     UUID · FK places · NN    Địa điểm

    visited_     DATE                     Ngày user nhập
on

    note         TEXT                     Ghi chú

    visibili     VARCHAR(20) · NN         PRIVATE, PUBLIC
ty

    created_     TIMESTAMPTZ · NN         Ngày tạo
at

    updated_     TIMESTAMPTZ · NN          Ngày sửa
at

Pattern: Relational.

#### 6.3. journal_media

Ý nghĩa: Ảnh liên kết với Exploration Entry.

    Field       Type / Constraint      Ý nghĩa

    id             UUID · PK                ID

    entry_id       UUID · FK entries · NN Nhật ký

    storage_k      TEXT · NN                Khóa
    ey                                      tệp

    sort_orde      INTEGER · NN             Thứ tự
r

    created_a      TIMESTAMPTZ · NN         Ngày
    t                                       tạo

Pattern: Subset / Media Separation.

#### 6.4. exploration_collections

Ý nghĩa: Bộ sưu tập địa điểm cá nhân.

    Field       Type / Constraint       Ý nghĩa

    id               UUID · PK               ID

    user_id          UUID · FK users · NN    Chủ

    title            VARCHAR(150) · NN       Tiêu đề

    descripti        TEXT                    Mô tả
on

    visibilit        VARCHAR(20) · NN        Quyền chia
    y                                        sẻ

    created_a        TIMESTAMPTZ · NN        Ngày tạo
t

    updated_a        TIMESTAMPTZ · NN        Ngày sửa
t

Status: Future.

#### 6.5. collection_places

Ý nghĩa: Các địa điểm trong Collection.

    Field              Type / Constraint         Ý nghĩa

    collection_        UUID · PK/FK                  Bộ sưu
    id                 collections                   tập

    place_id           UUID · PK/FK places           Địa điểm

    position        INTEGER · NN              Thứ tự

    added_at        TIMESTAMPTZ · NN          Ngày
thêm

Pattern: Many-to-Many.

