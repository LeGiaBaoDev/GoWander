10. DATABASE RELIABILITY
OBJECTIVES
### 10.1. Reliability Definitions

Availability: Database phục vụ request khi được yêu cầu.

Durability: Dữ liệu đã commit được bảo vệ theo cấu hình hệ thống.

RPO — Recovery Point Objective: Khoảng mất dữ liệu tối đa có thể chấp nhận khi khôi
phục.

RTO — Recovery Time Objective: Khoảng thời gian tối đa mục tiêu để khôi phục dịch vụ.

PITR — Point-in-Time Recovery: Phục hồi database đến thời điểm cụ thể từ backup và
WAL.

HA — High Availability: Duy trì hoặc khôi phục nhanh khả năng phục vụ khi một instance
thất bại.

### 10.2. Proposed Recovery Targets

    Environment             Proposed RPO       Proposed RTO

    Local Development          Không cam kết      Best effort

    Staging                    ≤24 giờ            ≤8 giờ

    Initial Production Pilot   ≤15 phút           ≤4 giờ

    Mature Production          Theo SLA đã chốt   Theo SLA đã chốt

Đây là mục tiêu thiết kế đề xuất, không phải khả năng đã đạt.

RPO 15 phút không chỉ dựa vào việc bật archive_timeout hoặc chạy backup hàng ngày.
Cần đo độ trễ upload WAL, trạng thái repository và kết quả PITR thực tế.

Nếu ngân sách hoặc hạ tầng không đáp ứng, phải điều chỉnh mục tiêu công khai trước khi
phát hành.

11. BACKUP STRATEGY —
PRODUCTION
### 11.1. Backup Requirements

GoWander cần hỗ trợ:

1. Full Backup.
2. Differential Backup.
3. Incremental Backup khi cần.
4. Continuous WAL Archiving.
5. Manual On-demand Backup.
6. Scheduled Backups.

7. Backup Before Critical Changes.
8. Backup Verification.
9. Offsite Backup Storage.
10. Point-in-Time Recovery.
11. Restore Testing.
12. Backup Failure Alerting.

### 11.2. Recommended Tool

pgBackRest là công cụ backup chính được đề xuất.

Lý do:

- ​    Hỗ trợ physical backup PostgreSQL.
- ​    Full, Differential và Incremental.
- ​    Restore theo backup set.
- ​    PITR với WAL archive.
- ​    Retention policies.
- ​    Repository metadata.
- ​    Hỗ trợ external storage.
- ​    Có thể tích hợp scheduler/monitoring.

pg_dump vẫn hữu ích cho logical export, selective recovery hoặc migration hỗ trợ, nhưng
không thay thế chiến lược physical backup + WAL cho PITR.

### 11.3. Proposed Schedule

    Job                       Lịch đề xuất                  Purpose

    Full Backup               Chủ nhật 02:00               Full recovery baseline

    Differential Backup       Các ngày còn lại 02:00       Giảm thời gian/khối lượng
backup

    WAL Archiving             Liên tục                     PITR

    Backup Health Check       Mỗi 5 phút                   Phát hiện backup/WAL bất
thường

    Repository Check          Hàng ngày                    Kiểm tra metadata và archive

    Automated Restore         Hàng tuần trên isolated      Chứng minh có thể phục hồi
    Drill                     staging

    Full DR Exercise          Hàng tháng hoặc hàng quý     Kiểm tra quy trình toàn hệ thống

    On-demand Backup          Theo thao tác có quyền       Trước thay đổi lớn

Múi giờ scheduler phải được cấu hình và ghi rõ; đề xuất sử dụng Asia/Ho_Chi_Minh cho lịch
vận hành. Các timestamp trong database tiếp tục sử dụng TIMESTAMPTZ.

Lịch này là baseline. Cần điều chỉnh theo dung lượng, chi phí và benchmark.

### 11.4. Retention Policy

Đề xuất ban đầu:

- ​   Giữ ít nhất ba chu kỳ Full Backup hoàn chỉnh trong repository chính.
- ​   Giữ WAL đủ cho khoảng PITR được yêu cầu.
- ​   Có một repository độc lập/offsite.
- ​   Giữ bản backup trước migration quan trọng theo chính sách riêng.
- ​   Không xóa backup đang được sử dụng bởi chain phục hồi.
- ​   Kiểm tra lifecycle rule của object storage để tránh xóa WAL quá sớm.

Retention theo số lượng Full Backup không đồng nghĩa luôn đảm bảo một số ngày PITR cố
định.

Phải kiểm tra thực tế info, WAL coverage và lịch expire.

### 11.5. Storage Protection

- ​   Không chỉ lưu backup trên cùng ổ với PostgreSQL.
- ​   Mã hóa backup khi phù hợp.
- ​   Dùng credentials riêng cho backup repository.
- ​   Giới hạn quyền xóa.
- ​   Bật bucket versioning/object protection khi được hỗ trợ và cấu hình tương thích.
- ​   Theo dõi dung lượng và chi phí.
- ​   Bảo vệ backup encryption key.
- ​   Kiểm tra cả khả năng truy cập repository từ recovery host.

## 12. BACKUP AUTOMATION

### 12.1. Scheduling Mechanism

pgBackRest không tự đóng vai trò scheduler đầy đủ.

GoWander sử dụng cron hoặc systemd timer.

Đề xuất production Linux: systemd timer cho mỗi loại backup, kết hợp logging và failure
notifications.

Cron vẫn là lựa chọn phù hợp khi môi trường đơn giản.

### 12.2. Example Backup Commands

Ví dụ stanza tên gowander:

None‎
pgbackrest --stanza=gowander --type=full backup
pgbackrest --stanza=gowander --type=diff backup
pgbackrest --stanza=gowander --type=incr backup

Các lệnh trên là ví dụ sau khi PostgreSQL, stanza, repository và WAL archiving đã được cấu
hình.

Không chạy trực tiếp trên production khi chưa kiểm chứng cấu hình và quyền truy cập.

### 12.3. Example Cron Schedule

None‎
# Full: Sunday, 02:00
0. 2 * * 0 /usr/bin/pgbackrest --stanza=gowander --type=full
backup

# Differential: Monday-Saturday, 02:00
0. 2 * * 1-6 /usr/bin/pgbackrest --stanza=gowander --type=diff
backup

Cần chạy dưới system user được cấp quyền phù hợp, cấu hình timezone, logging và
alerting.

Không đặt nhiều backup job ghi đè cùng repository mà thiếu cơ chế lock/scheduling.

### 12.4. Backup Job Results

Một backup job phải ghi lại:

- ​     Job ID.
- ​     Start Time.
- ​     End Time.
- ​     Backup Type.
- ​     Stanza.
- ​     Repository.
- ​     Result.

- ​   Backup Set ID.
- ​   Duration.
- ​   Error Code/Message.
- ​   Archive Health.
- ​   Verification Status.

Lịch sử tác vụ có thể được quản lý bởi scheduler/monitoring system; không bắt buộc tạo
bảng riêng trong 49-table inventory của database nghiệp vụ.

## 13. MANUAL BACKUP & RESTORE

### 13.1. Manual Backup

Admin có thẩm quyền có thể yêu cầu backup trước:

- ​   Migration quan trọng.
- ​   Deployment có thay đổi dữ liệu lớn.
- ​   Data Import.
- ​   Bulk Content Operations.
- ​   Điều chỉnh trạng thái dữ liệu nhạy cảm.

Manual backup phải có:

1. Authorization.
2. Reason.
3. Target Database/Environment.
4. Backup Type.
5. Execution Status.
6. Audit Log.
7. Result Verification.

### 13.2. Manual Restore

Manual Restore phải là thao tác có kiểm soát cao.

Không cung cấp nút Restore Production trực tiếp cho mọi Admin.

Quy trình:

1. Chọn incident.
2. Chọn backup/recovery target.
3. Xác nhận môi trường.
4. Kiểm tra chain backup và WAL.
5. Tạo isolated restore nếu cần.

6. Kiểm tra dữ liệu.
7. Phê duyệt recovery plan.
8. Đưa ứng dụng vào maintenance mode khi cần.
9. Thực hiện recovery.
10. Kiểm tra dữ liệu và chức năng.
11. Reopen traffic.
12. Ghi audit và postmortem.

### 13.3. Admin UI Scope

MVP có thể chỉ hỗ trợ:

- ​   Xem trạng thái backup.
- ​   Xem lần backup gần nhất.
- ​   Xem cảnh báo.
- ​   Yêu cầu backup thủ công qua runbook nội bộ.

Giai đoạn sau mới bổ sung:

- ​   Backup Job Management.
- ​   Restore Drill Initiation.
- ​   Recovery Point Selection.
- ​   Administrative Approval Workflow.

Không cần xây toàn bộ hệ thống backup engine bên trong Go Backend.

