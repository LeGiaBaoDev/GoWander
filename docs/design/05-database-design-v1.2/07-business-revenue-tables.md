### 11. BUSINESS & MONETIZATION — 7 BẢNG

Scope: Future Monetization. Các bảng này là mô hình dữ liệu dự kiến, chỉ tạo khi đã
chốt hợp đồng và luồng giao dịch.

#### 11.1. business_partners

Ý nghĩa: Đối tác kinh doanh hợp tác với GoWander.

    Field        Type / Constraint                     Ý nghĩa

    id               UUID · PK             ID đối tác

    name             VARCHAR(200) · NN     Tên pháp nhân/đối tác

    partner_typ      VARCHAR(50) · NN      MERCHANT, AFFILIATE, TICKETING...
e

    contact_ema      VARCHAR(320)          Email công việc
il

    contact_pho      VARCHAR(40)           Điện thoại công việc
ne

    status           VARCHAR(20) · NN      PENDING, ACTIVE, SUSPENDED

    created_at       TIMESTAMPTZ · NN      Ngày tạo

    updated_at       TIMESTAMPTZ · NN      Ngày sửa

Pattern: Relational Partner Master.

Thông tin đối tác không đồng nghĩa tài khoản cá nhân người dùng.

#### 11.2. partner_agreements

Ý nghĩa: Hợp đồng và các điều khoản hợp tác có hiệu lực theo thời gian.

    Field            Type / Constraint                 Ý nghĩa

    id                 UUID · PK                 ID thỏa thuận

    partner_id         UUID · FK partners · NN   Đối tác

    agreement_cod      VARCHAR(100) · UQ, NN     Mã thỏa thuận
e

    revenue_model      VARCHAR(40) · NN          COMMISSION, FIXED_FEE...

    commission_ra      NUMERIC(9,6)              Tỷ lệ hoa hồng nếu có
te

    currency_code      CHAR(3) · NN              Đồng tiền

    effective_fro      DATE · NN                 Bắt đầu
m

    effective_unt      DATE                       Hết hạn
il

    status             VARCHAR(20) · NN           DRAFT, ACTIVE, EXPIRED

    document_key       TEXT                       Tệp hợp đồng đã bảo vệ

    created_at         TIMESTAMPTZ · NN           Ngày tạo

Pattern: Versioned Agreement / Historical Terms.

Rule: Không ghi đè lịch sử hợp đồng đã có hiệu lực mà làm sai giao dịch trước đó.

#### 11.3. partner_referrals

Ý nghĩa: Ghi nhận lượt giới thiệu/chuyển đổi liên quan đối tác.

    Field           Type / Constraint                      Ý nghĩa

    id                   UUID · PK              ID giới thiệu

    partner_id           UUID · FK partners ·   Đối tác
NN

    agreement_id         UUID · FK              Hợp đồng áp dụng
agreements

    user_id              UUID · FK users        User nếu có cơ sở hợp lệ

    place_id             UUID · FK places       Place liên quan

    external_refere      VARCHAR(200)           Mã click/conversion từ provider
nce

    status               VARCHAR(30) · NN       CLICKED, PENDING, CONFIRMED,
REJECTED

    occurred_at          TIMESTAMPTZ · NN       Thời điểm

    confirmed_at         TIMESTAMPTZ            Ngày xác nhận

    metadata             JSONB · NN             Attribution có kiểm soát

Pattern: Attribution + Idempotency.

Rule: Unique external reference theo provider/partner và chính sách; một click không
mặc định là giao dịch thành công.

#### 11.4. revenue_transactions

Ý nghĩa: Khoản doanh thu GoWander có căn cứ ghi nhận.

    Field          Type / Constraint                   Ý nghĩa

    id                    UUID · PK              ID bản ghi

    partner_id            UUID · FK partners     Đối tác

    agreement_id          UUID · FK              Hợp đồng áp dụng
agreements

    referral_id           UUID · FK referrals    Nguồn chuyển đổi

    channel               VARCHAR(40) · NN         AFFILIATE, MERCHANT, ADS...

    external_referen      VARCHAR(200)             Mã giao dịch bên ngoài
ce

    gross_amount          NUMERIC(18,2) · NN       Gross revenue thuộc GoWander

    currency_code         CHAR(3) · NN             Đơn vị tiền

    status                VARCHAR(30) · NN         PENDING, CONFIRMED, VOIDED

    recognized_at         TIMESTAMPTZ              Mốc ghi nhận doanh thu

    created_at            TIMESTAMPTZ · NN         Ngày tạo

Pattern: Transactional Record + Idempotency.

Business Rule: Nếu GoWander chỉ hưởng hoa hồng, gross_amount phải phản ánh
phần được hưởng theo chính sách, không tự động là toàn bộ giá trị đơn hàng của đối
tác.

#### 11.5. revenue_adjustments

Ý nghĩa: Hoàn/giảm trừ/điều chỉnh doanh thu có lịch sử.

    Field               Type / Constraint                     Ý nghĩa

    id                       UUID · PK                        ID điều chỉnh

    revenue_transactio       UUID · FK                        Khoản gốc
    n_id                     revenue_transactions · NN

    adjustment_type          VARCHAR(40) · NN              REFUND, DISCOUNT,
CORRECTION...

    amount                   NUMERIC(18,2) · NN            Giá trị điều chỉnh có dấu
theo quy ước

    currency_code            CHAR(3) · NN                  Tiền tệ

    reason                   TEXT                          Lý do

    external_reference       VARCHAR(200)                  Mã chứng từ

    created_at               TIMESTAMPTZ · NN              Thời điểm

    created_by               UUID · FK users               Người ghi nếu thao tác nội
bộ

Pattern: Append-only Adjustment.

Rule: Định nghĩa rõ dấu của adjustment; không tính hoàn tiền hai lần. Không sửa
doanh thu gốc tùy tiện nếu thay đổi phải được lưu lịch sử.

#### 11.6. business_expenses

Ý nghĩa: Chi phí phục vụ đánh giá hoạt động kinh doanh.

    Field         Type / Constraint                 Ý nghĩa

    id               UUID · PK              ID chi phí

    partner_id       UUID · FK partners     Đối tác nếu liên quan

    expense_typ      VARCHAR(50) · NN       HOSTING, MARKETING, FEE...
e

    amount           NUMERIC(18,2) · NN     Giá trị chi phí không âm

    currency_co      CHAR(3) · NN           Tiền tệ
de

    incurred_on      DATE · NN              Ngày phát sinh

    description      TEXT                   Ghi chú

    reference_k      TEXT                   Tham chiếu chứng từ nếu có
ey

    created_at       TIMESTAMPTZ · NN       Ngày ghi nhận

Pattern: Expense Record.

Để tính Contribution Margin phải xác định chính xác nhóm chi phí biến đổi, không tự
trừ mọi chi phí vận hành bất kể bản chất.

#### 11.7. partner_settlements

Ý nghĩa: Đối soát khoản phải thu/phải trả hoặc thanh toán theo thỏa thuận.

    Field            Type / Constraint                    Ý nghĩa

    id                   UUID · PK               ID đối soát

    partner_id           UUID · FK partners ·    Đối tác
NN

    agreement_id         UUID · FK agreements    Hợp đồng

    period_start         DATE · NN               Đầu kỳ

    period_end           DATE · NN               Cuối kỳ

    settlement_amo       NUMERIC(18,2) · NN      Giá trị quyết toán theo hướng đã định
    unt                                          nghĩa

    currency_code        CHAR(3) · NN            Tiền tệ

    direction            VARCHAR(20) · NN        RECEIVABLE, PAYABLE

    status               VARCHAR(30) · NN        DRAFT, APPROVED, SETTLED

    settled_at           TIMESTAMPTZ             Ngày hoàn tất

    created_at           TIMESTAMPTZ · NN        Ngày lập

Pattern: Settlement Workflow.

Important: Trước khi triển khai production cần một liên kết có thể kiểm toán giữa
settlement và các revenue transactions/adjustments cấu thành, ví dụ bảng
settlement_items hoặc batch reference có cấu trúc. Bảng liên kết bổ sung chưa
tính trong danh mục 49 bảng vì quy trình settlement và nhà cung cấp chưa được
quyết định.

