### 04. DISCOVER — 11 BẢNG

#### 4.1. places

Ý nghĩa: Danh mục địa điểm cốt lõi; được dùng bởi Discover, Map, Reviews và
Exploration.

    Field                    Type / Constraint                    Ý nghĩa

    id                     UUID · PK                          ID địa điểm

    name                   VARCHAR(200) · NN                  Tên

    slug                   VARCHAR(220) · UQ                  Mã URL-friendly

    short_descriptio VARCHAR(500)                             Mô tả ngắn
n

    location               GEOGRAPHY(POINT,4326) ·            Tọa độ
NN

    address                TEXT                               Địa chỉ hiển thị

    primary_category BIGINT · FK categories                 Danh mục chính nếu hỗ trợ
_id

    status                  VARCHAR(20) · NN                DRAFT, PUBLISHED,
HIDDEN

    created_at              TIMESTAMPTZ · NN                Ngày tạo

    updated_at              TIMESTAMPTZ · NN                Ngày sửa

Pattern: Relational + Spatial Search + Projection.

Index: GiST(location).

#### 4.2. place_details

Ý nghĩa: Nội dung dài, chi tiết và metadata có thể tách khỏi Place Summary.

    Field             Type / Constraint              Ý nghĩa

    place_id            UUID · PK/FK places     Địa điểm

    description         TEXT                    Mô tả đầy đủ

    opening_hours       JSONB                   Giờ hoạt động có cấu trúc

    attributes          JSONB · NN              Thuộc tính linh hoạt

    official_url        TEXT                    Trang chính thức

    contact_phone       VARCHAR(40)           Liên hệ công khai của nơi
này

    data_source_no      TEXT                  Nguồn và ghi chú
te

    updated_at          TIMESTAMPTZ · NN      Cập nhật gần nhất

Pattern: Subset + Attribute.

Status: Conditional; không bắt buộc tách vật lý ngay MVP.

#### 4.3. place_categories

Ý nghĩa: Hệ thống phân loại địa điểm có thể phân cấp.

    Field     Type / Constraint      Ý nghĩa

    id           BIGINT · PK           ID danh mục

    parent_i     BIGINT · FK self      Danh mục
    d                                  cha

    name         VARCHAR(100) · NN     Tên

    slug         VARCHAR(100) · UQ     Mã

    sort_ord     INTEGER · NN          Thứ tự
er

    is_activ      BOOLEAN · NN             Trạng thái
e

Pattern: Tree — Adjacency List.

Chặn quan hệ vòng nếu phân cấp nhiều mức.

#### 4.4. place_category_links

Ý nghĩa: Liên kết nhiều-nhiều giữa địa điểm và danh mục.

    Field         Type / Constraint          Ý nghĩa

    place_id       UUID · PK/FK places          Địa điểm

    category_      BIGINT · PK/FK               Danh
    id             categories                   mục

Pattern: Normalized Many-to-Many.

Index: (category_id, place_id).

#### 4.5. place_media

Ý nghĩa: Media và nguồn hình ảnh của địa điểm.

    Field        Type / Constraint          Ý nghĩa

    id               UUID · PK                 ID media

    place_id         UUID · FK places · NN     Địa điểm

    storage_key      TEXT · NN                 Khóa tệp

    media_type       VARCHAR(30) · NN          Loại media

    alt_text         VARCHAR(300)              Mô tả ảnh

    source_cred      TEXT                      Tác
    it                                         giả/nguồn

    license_inf      TEXT                      Giấy phép
o

    sort_order       INTEGER · NN              Thứ tự

    is_cover         BOOLEAN · NN              Ảnh chính

    created_at       TIMESTAMPTZ · NN          Ngày thêm

Pattern: Subset / Media Separation.

#### 4.6. food_items

Ý nghĩa: Thực thể món ăn, độc lập với nhà hàng.

    Field       Type / Constraint     Ý nghĩa

    id             UUID · PK              ID món

    name           VARCHAR(150) · NN      Tên món

    slug           VARCHAR(170) · UQ      Mã

    descripti     TEXT                    Mô tả
on

    image_key     TEXT                    Ảnh đại
diện

    status        VARCHAR(20) · NN        Trạng thái

    created_a     TIMESTAMPTZ · NN        Ngày tạo
t

    updated_a     TIMESTAMPTZ · NN        Ngày sửa
t

Pattern: Normalized Entity.

#### 4.7. place_foods

Ý nghĩa: Địa điểm nào phục vụ món gì và giá tham khảo.

    Field           Type / Constraint             Ý nghĩa

    place_id        UUID · PK/FK places           Địa điểm

    food_id         UUID · PK/FK                  Món ăn
food_items

    price_min       NUMERIC(12,2)                 Giá thấp tham
khảo

    price_max        NUMERIC(12,2)              Giá cao tham
khảo

    currency_co      CHAR(3) · NN               Đơn vị tiền
de

    updated_at       TIMESTAMPTZ · NN           Lần cập nhật

Pattern: Many-to-Many.

CHECK giá không âm và min ≤ max.

#### 4.8. historical_stories

Ý nghĩa: Bài viết lịch sử/văn hóa theo địa điểm.

    Field         Type / Constraint          Ý nghĩa

    id               UUID · PK              ID câu chuyện

    place_id         UUID · FK places       Địa điểm liên
quan

    title            VARCHAR(220) · NN      Tiêu đề

    summary          VARCHAR(500)           Tóm tắt

    content          TEXT · NN              Nội dung

    language_co      VARCHAR(10) · NN       Ngôn ngữ
de

    status           VARCHAR(20) · NN          Trạng thái xuất
bản

    published_a      TIMESTAMPTZ               Ngày công khai
t

    updated_at       TIMESTAMPTZ · NN          Ngày sửa

Pattern: Long-content Separation.

#### 4.9. story_sources

Ý nghĩa: Nguồn tham khảo của Historical Stories.

    Field          Type / Constraint           Ý nghĩa

    id                  UUID · PK                  ID nguồn

    story_id            UUID · FK stories ·        Câu chuyện
NN

    title               TEXT · NN                  Tài liệu

    source_url          TEXT                       URL

    publisher           VARCHAR(200)               Đơn vị phát
hành

    publication_da      DATE                       Ngày công bố
te

    sort_order            INTEGER · NN          Thứ tự

Pattern: One-to-Many.

Status: Conditional nhưng không được bỏ nguồn nội dung.

#### 4.10. trip_ideas

Ý nghĩa: Các hành trình/ý tưởng tham quan được biên tập.

    Field           Type / Constraint                Ý nghĩa

    id                     UUID · PK            ID ý tưởng

    title                  VARCHAR(200) · NN    Tên

    description            TEXT                 Giới thiệu

    estimated_minut        INTEGER              Thời lượng tham quan ước
    es                                          tính

    cover_key              TEXT                 Ảnh

    status                 VARCHAR(20) · NN     Trạng thái

    created_at             TIMESTAMPTZ · NN     Ngày tạo

    updated_at             TIMESTAMPTZ · NN     Ngày sửa

Pattern: Relational.

#### 4.11. trip_idea_places

Ý nghĩa: Các điểm dừng có thứ tự trong Trip Idea.

    Field         Type / Constraint          Ý nghĩa

    trip_idea_      UUID · PK/FK              Trip Idea
    id              trip_ideas

    position        INTEGER · PK              Thứ tự

    place_id        UUID · FK places · NN     Địa điểm

    visit_note      TEXT                      Gợi ý tham
quan

Pattern: Ordered Many-to-Many.

