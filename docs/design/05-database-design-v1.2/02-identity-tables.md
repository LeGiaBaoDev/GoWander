### 03. IDENTITY — 5 BẢNG

#### 3.1. users

Ý nghĩa: Tài khoản nội bộ ổn định, không phụ thuộc Google/Apple.

    Field       Type / Constraint                    Ý nghĩa

    id                   UUID · PK               ID người dùng GoWander

    status               VARCHAR(20) · NN        ACTIVE, SUSPENDED, DEACTIVATED

    created_at           TIMESTAMPTZ · NN        Thời điểm tạo

    updated_at           TIMESTAMPTZ · NN        Lần cập nhật

    last_login_a         TIMESTAMPTZ             Đăng nhập gần nhất
t

    deactivated_         TIMESTAMPTZ             Vô hiệu hóa tài khoản
at

Pattern: Stable Internal Identity.

#### 3.2. user_auth_identities

Ý nghĩa: Các danh tính đăng nhập Google, Apple hoặc OIDC khác liên kết một user.

    Field                   Type / Constraint                Ý nghĩa

    id                                  UUID · PK               ID bản ghi định danh

    user_id                             UUID · FK users · NN    Chủ tài khoản

    provider                            VARCHAR(30) · NN        GOOGLE, APPLE, OIDC

    issuer                              VARCHAR(255) · NN       Issuer được tin cậy

    provider_subject                VARCHAR(255) · NN    sub từ provider

    email_at_provider               VARCHAR(320)         Email provider trả về gần
nhất

    email_verified_at_provi         BOOLEAN · NN         Claim email đã xác minh
der

    linked_at                       TIMESTAMPTZ · NN     Thời điểm liên kết

    last_used_at                    TIMESTAMPTZ          Sử dụng gần nhất

Constraint: UNIQUE(issuer, provider_subject).

Pattern: External Identity Reference.

Không gộp account theo email; không lưu Google token khi không cần.

#### 3.3. user_contacts

Ý nghĩa: Email và phone riêng tư, dùng liên hệ/khôi phục theo chính sách.

    Field          Type / Constraint                   Ý nghĩa

    user_id              UUID · PK/FK users    Chủ sở hữu

    email                VARCHAR(320)          Email liên hệ

    email_normalize      VARCHAR(320)          Giá trị tra cứu theo quy tắc chuẩn
    d                                          hóa

    email_verified_      TIMESTAMPTZ          Mốc xác minh email
at

    phone_e164           VARCHAR(16)          Số điện thoại E.164

    phone_verified_      TIMESTAMPTZ          Mốc xác minh phone
at

    updated_at           TIMESTAMPTZ · NN     Lần cập nhật

Pattern: Sensitive Data Separation.

Không public; phone tùy chọn; chưa mặc định UNIQUE(email/phone) trước khi chốt
chính sách tài khoản.

#### 3.4. user_profiles

Ý nghĩa: Hồ sơ công khai hoặc thông tin người dùng chọn chia sẻ.

    Field       Type / Constraint                Ý nghĩa

    user_id         UUID · PK/FK users    Chủ hồ sơ

    display_nam     VARCHAR(100) · NN     Tên hiển thị
e

    username        VARCHAR(50) · UQ      Định danh công khai tùy
chọn

    bio             TEXT                  Giới thiệu

    avatar_key       TEXT                   Ảnh đại diện

    cover_key        TEXT                   Ảnh bìa

    profile_the      VARCHAR(40)            Chủ đề hồ sơ
me

    created_at       TIMESTAMPTZ · NN       Ngày tạo

    updated_at       TIMESTAMPTZ · NN       Ngày sửa

Pattern: Profile Separation.

Không chứa email/số điện thoại riêng tư.

#### 3.5. user_preferences

Ý nghĩa: Ngôn ngữ, sở thích và cấu hình tài khoản.

    Field          Type / Constraint              Ý nghĩa

    user_id           UUID · PK/FK users      Chủ sở hữu

    language_cod      VARCHAR(10) · NN        Ngôn ngữ ưu tiên
e

    interest_cod      JSONB · NN              Mã sở thích dạng array
es

    settings          JSONB · NN              Cấu hình khác dạng
object

    updated_at        TIMESTAMPTZ · NN         Lần cập nhật

Pattern: Controlled Attribute.

JSONB phải có schema validation; không chứa secret.

