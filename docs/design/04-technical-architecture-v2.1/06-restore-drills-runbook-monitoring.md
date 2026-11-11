19. BACKUP VERIFICATION & RESTORE
DRILLS
### 19.1. Verification Layers

Layer 1 — Job Result: Backup command hoàn thành.

Layer 2 — Repository Metadata: Backup set và WAL archive có thông tin đầy đủ.

Layer 3 — Isolated Restore: Có thể khởi động PostgreSQL từ backup.

Layer 4 — Application Validation: Migrations/schema, dữ liệu và queries hợp lệ.

Layer 5 — Functional Validation: Những workflow trọng yếu hoạt động.

### 19.2. Automated Restore Drill

Một automated restore drill thực hiện:

1. Tạo môi trường tạm thời cách ly.
2. Chọn backup/recovery target.
3. Restore database.
4. Khởi động PostgreSQL ở mạng riêng.
5. Chạy database integrity/schema checks.

6. Chạy sample business queries.
7. Kiểm tra row counts/relationships phù hợp.
8. Ghi RTO thực tế.
9. Ghi khả năng đạt recovery target.
10. Xóa môi trường thử nghiệm theo retention policy.

Không kết nối restored staging tới production payment, notifications hoặc external partner
callbacks.

### 19.3. Business-critical Checks

- ​   Users + Auth Identities nhất quán.
- ​   Places có tọa độ hợp lệ.
- ​   Posts/Reviews không mất quan hệ quan trọng.
- ​   Rewards Ledger không double-count.
- ​   Revenue Transactions/Adjustments cân đối.
- ​   Admin Audit Logs được giữ đúng chính sách.

### 19.4. Restore Acceptance

Một backup chỉ được xem là đã kiểm chứng phục hồi khi restore drill thực sự thành công.

Có backup file không đồng nghĩa chắc chắn phục hồi được.

## 20. BACKUP & RECOVERY DASHBOARD

### 20.1. Purpose

Admin Operations cần biết:

- ​   Có backup mới nhất không?
- ​   Backup có thành công không?
- ​   WAL còn hoạt động không?
- ​   Có thể phục hồi đến thời điểm nào?
- ​   Lần test restore gần nhất có đạt không?
- ​   Đang có rủi ro mất dữ liệu không?

### 20.2. Meaningful KPI

    KPI                            Meaning                         Action

    Latest Successful Backup      Độ tuổi backup hoàn chỉnh mới    Kiểm tra scheduler
    Age                           nhất

    Backup Success Rate           Tỷ lệ jobs thành công            Điều tra lỗi backup

    WAL Archive Health            Tình trạng archive               Bảo vệ PITR/RPO

    Recoverable Window            Phạm vi phục hồi đã kiểm chứng   Đánh giá retention

    Last Successful Restore       Khi nào restore thành công       Bắt đầu drill nếu quá
    Drill                                                          lâu

    Observed Recovery             Thời gian restore thực tế        So sánh RTO
Duration

    Backup Repository Usage       Dung lượng/chi phí               Điều chỉnh retention

Đây là Operational Dashboard, không phải Dashboard nghiệp vụ thứ bảy độc lập. Nó nằm
trong System Health của Admin.

### 20.3. Security

Chỉ Operations/System Admin được xem chi tiết backup.

Chỉ quyền đặc biệt mới được yêu cầu restore.

Backup credentials không hiển thị trên UI.

21. INCIDENT RESPONSE & DISASTER
RECOVERY RUNBOOK
### 21.1. Incident Lifecycle

Detect → Classify → Contain → Diagnose → Select Recovery Strategy → Recover →
Validate → Resume → Postmortem.

### 21.2. Incident Classification

SEV-1: Mất dữ liệu nghiêm trọng, database không thể phục vụ, nguy cơ bảo mật cao.

SEV-2: Một chức năng trọng yếu không khả dụng.

SEV-3: Chất lượng giảm, backup có cảnh báo nhưng dịch vụ còn hoạt động.

Mức độ cụ thể được điều chỉnh theo quy mô pilot và SLA.

### 21.3. Recovery Approval

Production restore cần:

- ​   Incident ID.
- ​   Lý do.
- ​   Người thực hiện.
- ​   Source Backup.
- ​   Recovery Target.
- ​   RPO Impact.
- ​   Expected Data Loss.
- ​   Rollback/Abort Plan.
- ​   Recovery Test Evidence.
- ​   Confirmation.

### 21.4. Post-recovery Verification

Kiểm tra:

- ​   PostgreSQL Readiness.
- ​   Go API Health.
- ​   Authentication.
- ​   Discover Queries.
- ​   PostGIS Queries.
- ​   Community Data.
- ​   Rewards.
- ​   Revenue.
- ​   OTP Integration.
- ​   Admin Audit.
- ​   Backup/WAL jobs sau recovery.

Không mở traffic đầy đủ khi chưa kiểm chứng những chức năng trọng yếu.

22. APPLICATION MONITORING &
OBSERVABILITY
### 22.1. Monitoring Layers

Infrastructure: CPU, RAM, disk, network.

Go API: Request rate, error rate, latency.

PostgreSQL: Connections, disk, query latency, locks, autovacuum, WAL archive.

OTP: Availability, routing latency, graph version, routing errors.

Background Jobs: Queue age, retry, failures.

Business: KPI quality và event tracking freshness.

Backup: Jobs, WAL, repository và restore drills.

### 22.2. Logging

Structured logs nên có:

- ​   Timestamp.
- ​   Service.
- ​   Environment.
- ​   Log Level.
- ​   Request ID.
- ​   Trace/Correlation ID khi có.
- ​   Error Code.
- ​   Operation.
- ​   Safe Metadata.

Không log access tokens, phone/email nguyên dạng khi không cần hoặc private journal
content.

### 22.3. Tracing

Đầu tiên sử dụng request IDs và structured logs.

Distributed tracing được triển khai khi cần điều tra cross-service latency như Go ↔ OTP.

### 22.4. Alert Quality

Tránh cảnh báo gây nhiễu.

Alert nên có:

- ​   Mức độ.
- ​   Sự cố ảnh hưởng.
- ​   Ngưỡng và thời gian duy trì.
- ​   Link dashboard.
- ​   Runbook.
- ​   Owner.
- ​   Trạng thái acknowledged/resolved.

