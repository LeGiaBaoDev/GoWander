11. PHASE 8 — DATABASE BACKUP &
RECOVERY
### 11.1. Objective

Bảo vệ dữ liệu trước khi vận hành Production Pilot.

### Step 8.1 — Define Recovery Targets

Đề xuất ban đầu cho Production Pilot:

- ​ RPO: mục tiêu tối đa 15 phút.
- ​ RTO: mục tiêu tối đa 4 giờ.

Chưa xem đây là SLA cho đến khi có kết quả restore drill thực tế.

### Step 8.2 — Install and Configure pgBackRest

Cấu hình:

- ​     PostgreSQL Cluster/Stanza.
- ​     Backup Repository.
- ​     WAL Archiving.
- ​     Retention.
- ​     Credentials.
- ​     Encryption.
- ​     External Storage.

### Step 8.3 — Validate Backup Configuration

Sau khi tạo stanza:

None‎
pgbackrest --stanza=gowander stanza-create

pgbackrest --stanza=gowander check

Chạy lệnh trong đúng môi trường đã cấu hình và với quyền phù hợp.

### Step 8.4 — Perform Initial Full Backup


None‎
pgbackrest --stanza=gowander --type=full backup

pgbackrest --stanza=gowander info

### Step 8.5 — Configure Scheduled Backups

Đề xuất:

- ​     Chủ nhật: Full Backup.
- ​     Thứ hai–thứ bảy: Differential Backup.
- ​     WAL: Continuous Archiving.
- ​     Backup Health Check: thường xuyên.
- ​     Weekly Restore Drill.

Cron hoặc systemd timer là scheduler; pgBackRest không tự thay thế scheduler.

### Step 8.6 — Configure Manual Backup

Admin/Operator được quyền có thể yêu cầu backup có:

- ​     Reason.
- ​     Environment.
- ​     Backup Type.
- ​     Execution Log.
- ​     Result.
- ​     Audit.

### Step 8.7 — Perform Isolated Restore Test

Tạo PostgreSQL test cluster độc lập.

Không restore đè trực tiếp lên production.

### Step 8.8 — PITR Test

1. Tạo dữ liệu thử.
2. Full Backup.
3. Tạo thêm WAL records.
4. Ghi nhận recovery target.
5. Tạo thay đổi gây lỗi thử nghiệm.
6. Restore đến thời điểm trước thay đổi lỗi.
7. Khởi động cluster cách ly.
8. So sánh dữ liệu.

9. Đo Recovery Duration.

### Step 8.9 — Alerts

Cảnh báo khi:

- ​   Backup thất bại.
- ​   Backup quá tuổi.
- ​   WAL archive lỗi.
- ​   Repository không khả dụng.
- ​   Disk gần đầy.
- ​   Restore Drill thất bại.

### Exit Criteria

Phải có bằng chứng:

- ​   Full backup thành công.
- ​   Backup theo lịch thành công.
- ​   WAL archiving hoạt động.
- ​   Manual backup hoạt động.
- ​   Isolated restore thành công.
- ​   PITR thành công.
- ​   Alert thử nghiệm hoạt động.
- ​   Đo được RPO/RTO thực tế.

Không tự restore production chỉ vì DB health check thất bại.

