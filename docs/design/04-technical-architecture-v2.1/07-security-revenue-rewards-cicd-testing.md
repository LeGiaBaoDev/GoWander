23. API SECURITY & APPLICATION
PROTECTION
### 23.1. API Security

- ​   HTTPS.
- ​   JWT/OIDC Validation.
- ​   Authorization tại Backend.
- ​   Input Validation.
- ​   Parameterized SQL.
- ​   Rate Limiting.
- ​   CORS Policy phù hợp.
- ​   Request Size Limits.
- ​   Safe Error Responses.
- ​   Secrets Management.
- ​   Sensitive-field Redaction.

### 23.2. Admin Security

- ​   RBAC.
- ​   Audit.
- ​   MFA cho tài khoản có quyền cao khi provider hỗ trợ.
- ​   Session Expiration.
- ​   Reauthentication cho thao tác nguy hiểm.
- ​   Least Privilege.
- ​   Không dùng shared admin accounts.

### 23.3. Database Security

- ​   Tài khoản DB của API không phải superuser.
- ​   Backup user có quyền riêng.
- ​   Migration user được phân tách khi phù hợp.
- ​   Giới hạn network access.
- ​   Không mở PostgreSQL trực tiếp ra Internet.
- ​   Backup repository credentials được bảo vệ.

### 23.4. Personal Data

Bảo vệ:

- ​ Authentication Identities.

- ​   Emails/Phones.
- ​   Location.
- ​   Private Exploration.
- ​   Journal Media.
- ​   Rewards.
- ​   Business Agreements.
- ​   Revenue Records.

24. REVENUE & BUSINESS TECHNICAL
DESIGN
### 24.1. Business Domain

Khi thương mại hóa:

- ​   Partners.
- ​   Agreements.
- ​   Referrals.
- ​   Revenue Transactions.
- ​   Adjustments.
- ​   Expenses.
- ​   Settlements.

### 24.2. Business Data Flow

Partner Agreement → Referral → Verified Conversion → Revenue Record → Adjustment →
Settlement → Dashboard.

### 24.3. Integrity Rules

- ​ Không dùng click như giao dịch đã thanh toán.
- ​ Không ghi toàn bộ Partner Transaction Value thành GoWander Revenue nếu chỉ
hưởng commission.
- ​ Không tạo duplicate transaction khi provider callback lặp lại.
- ​ Không sửa audit và revenue history làm mất chứng từ.
- ​ Phân biệt tiền tệ.
- ​ Kiểm tra đối soát trước khi chốt báo cáo.

### 24.4. Dashboard Metrics

- ​ Gross Revenue.

- ​   Net Revenue.
- ​   Revenue by Channel.
- ​   Partner Conversion.
- ​   Contribution Margin.
- ​   Unsettled Amount.

Mọi công thức cần thống nhất với business agreements và chính sách ghi nhận doanh thu.

## 25. REWARDS TECHNICAL DESIGN

### 25.1. Points Ledger

points_ledger là nguồn ghi lịch sử.

Không tự động thay đổi số dư bằng phép cập nhật tùy ý thiếu transaction.

### 25.2. Idempotency

Một đóng góp không được cấp điểm trùng.

Sử dụng event key hoặc business unique constraint phù hợp.

### 25.3. Approval Flow

Contribution → Validation → Moderation → Eligibility → Reward Grant → Ledger.

Không tự động cấp điểm cho:

- ​   Follow.
- ​   Like.
- ​   Positive Review.
- ​   Self-declared Visited.
- ​   Duplicate Content.

## 26. CI/CD & RELEASE ENGINEERING

### 26.1. Pipeline

1. Lint/Static Analysis.

2. Unit Tests.
3. Database Migration Tests.
4. Integration Tests.
5. Build.
6. Container Image Scan khi khả dụng.
7. Staging Deployment.
8. Smoke Tests.
9. Backup/Readiness Gate.
10. Production Deployment có kiểm soát.
11. Post-deploy Health Checks.

### 26.2. Migration Safety

Đối với destructive migration:

- ​   Có migration review.
- ​   Có backup/recovery plan.
- ​   Thực hiện Expand–Migrate–Contract khi cần.
- ​   Kiểm tra backup age.
- ​   Kiểm tra WAL archive.
- ​   Không deploy khi DR health critical.
- ​   Có kế hoạch ứng dụng rollback.

### 26.3. Release Artifacts

- ​   Mobile Version.
- ​   Backend Version.
- ​   Database Migration Version.
- ​   OTP Graph Version.
- ​   GTFS/OSM Source Version.
- ​   Admin Web Version.

Phải truy vết phiên bản khi xảy ra sự cố.

## 27. TESTING STRATEGY

### 27.1. Mobile

- ​   ViewModel/Use Case Tests.
- ​   Android/iOS UI Tests.
- ​   GPS Permission Tests.
- ​   Map Integration Tests.
- ​   Networking/Error Recovery.

### 27.2. Backend

- ​   Domain Unit Tests.
- ​   Repository Integration Tests.
- ​   API Contract Tests.
- ​   Authentication Tests.
- ​   RBAC Tests.
- ​   Concurrency Tests.

### 27.3. Database

- ​   FK/Unique/Check Validation.
- ​   Migration Tests.
- ​   SQL Query Tests.
- ​   Spatial Query Tests.
- ​   Transaction Tests.

### 27.4. Backup/Recovery

- ​   Full Backup.
- ​   Differential Restore.
- ​   PITR.
- ​   Manual Backup.
- ​   Backup Failure Alert.
- ​   WAL Archive Failure Alert.
- ​   Corrupted Data Scenario.
- ​   Isolated Restore.
- ​   Production Restore Runbook Simulation.

### 27.5. Business

- ​   Reward Idempotency.
- ​   Revenue Idempotency.
- ​   Refund/Adjustment.
- ​   Settlement Reconciliation.
- ​   Audit Integrity.

