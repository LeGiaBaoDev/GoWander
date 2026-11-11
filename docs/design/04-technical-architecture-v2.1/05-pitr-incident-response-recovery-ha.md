14. CONTINUOUS WAL ARCHIVING &
PITR
### 14.1. WAL

PostgreSQL sử dụng Write-Ahead Log để hỗ trợ tính bền vững và crash recovery.

Khi cấu hình WAL archiving, các WAL segments được lưu ra repository ngoài database.

### 14.2. PITR Requirements

Để có thể phục hồi đến thời điểm xác định, cần:

- ​ Base Backup hợp lệ.
- ​ WAL history đầy đủ đến recovery target.
- ​ Repository truy cập được.

- ​ Thông tin timeline phù hợp.
- ​ Phiên bản/cấu hình PostgreSQL tương thích.
- ​ Quy trình kiểm chứng kết quả.

Không chỉ có WAL mà thiếu base backup.

Không chỉ có Full Backup hằng ngày mà mong khôi phục chính xác đến trước một thao tác
xóa nhầm.

### 14.3. Example Recovery

Ví dụ chỉ dành cho recovery host cách ly, với PostgreSQL đã dừng và data directory đã
được chuẩn bị cho recovery:

None‎
pgbackrest \
--stanza=gowander \
--type=time \
--target="2026-11-11 01:20:00+07" \
restore

Cú pháp và các tùy chọn liên quan phải được xác nhận theo phiên bản pgBackRest đã
khóa, backup set, recovery timeline và cấu hình môi trường.

Không chạy lệnh này trực tiếp trên production chỉ vì thấy database bất thường.

Recovery Target phải nằm trong phạm vi thực sự có thể phục hồi từ các backup và WAL
được giữ.

### 14.4. PITR Example Scenario

Một Admin vô tình xóa dữ liệu lúc 14:05.

Hệ thống phát hiện hoặc nhận báo cáo lúc 14:09.

Các bước đúng:

1. Ghi nhận và cô lập sự cố.
2. Xác định chính xác transaction/thời gian.
3. Đánh giá dữ liệu ghi sau thời điểm cần khôi phục.
4. Tạo một restore copy tại thời điểm trước sự cố.
5. Kiểm tra dữ liệu trước/sau.
6. Lựa chọn phục hồi chọn lọc hoặc toàn bộ.
7. Chỉ phục hồi production sau khi đã cân nhắc mất các ghi hợp lệ sau recovery target.

PITR toàn database về thời điểm trước xóa nhầm có thể làm mất những giao dịch hợp
lệ xảy ra sau thời điểm đó.

Vì vậy, với lỗi chỉ ảnh hưởng một tập dữ liệu, có thể phù hợp hơn nếu restore sang
database cách ly rồi xuất/nhập chọn lọc các bản ghi bị mất.

## 15. AUTOMATED INCIDENT DETECTION

### 15.1. Monitoring Signals

    Signal                     Source                           Meaning

    DB Connection           Application/Exporter          Không kết nối được
Failure

    DB Readiness            Health Check                  DB chưa sẵn sàng
Failure

    Disk Usage High         Host Metrics                  Nguy cơ hết dung lượng

    WAL Archive Failure     pg_stat_archiver / Backup     WAL có thể chưa được lưu
Logs

    Backup Job Failure      Scheduler/pgBackRest          Backup thất bại

    Backup Too Old          Backup Metadata               Bản backup mới nhất đã quá tuổi

    WAL Archive Lag         Archive Monitoring            RPO có nguy cơ vượt mục tiêu

    High Query Latency      PostgreSQL/App Metrics        Query chậm

    Data Anomaly            Domain Rules/Audit            Dữ liệu bất thường cần kiểm tra

    Restore Drill Failure   DR Pipeline                   Backup chưa chứng minh phục hồi
được

### 15.2. Alert Severity

INFO: Có backup thành công, scheduled maintenance.

WARNING: Backup trì hoãn, tăng latency, disk vượt ngưỡng cảnh báo.

CRITICAL: Không còn backup bảo đảm mục tiêu, WAL archive lỗi kéo dài, database không
truy cập được hoặc có nghi vấn mất dữ liệu.

### 15.3. Initial Alert Thresholds

Đề xuất thử nghiệm:

- ​ Disk Usage >80%: Warning.
- ​ Disk Usage >90%: Critical.
- ​ Backup job kết thúc thất bại: Immediate Alert.
- ​ Backup chưa có bản hoàn chỉnh mới trong khoảng dự kiến: Warning/Critical theo
SLA.
- ​ WAL Archive Lag vượt giới hạn RPO: Critical.
- ​ Database Readiness thất bại kéo dài: Incident.
- ​ Restore Drill thất bại: Critical Operations Alert.

Ngưỡng chính thức phải được hiệu chỉnh theo tốc độ tăng dữ liệu, quy mô ổ và mục tiêu
vận hành.

Không coi last_archived_time cũ là lỗi nếu database không phát sinh WAL đáng kể mà
chưa xem xét tình trạng archive thực tế.

## 16. AUTOMATED RESPONSE POLICY

### 16.1. Safe Automatic Actions

Hệ thống có thể tự động:

- ​   Gửi cảnh báo.
- ​   Tạo incident record.
- ​   Restart service theo giới hạn và điều kiện thích hợp.
- ​   Tạm dừng một background job gây lỗi.
- ​   Chặn deployment mới khi backup chưa đạt.
- ​   Thực hiện backup bổ sung khi repository khỏe và tác vụ phù hợp.
- ​   Khởi chạy isolated recovery test.
- ​   Cung cấp diagnostics.
- ​   Chuyển traffic/readiness sang trạng thái bảo trì nếu chính sách cho phép.

### 16.2. Actions Requiring Approval

Những hành động sau không tự động thực hiện chỉ dựa trên một cảnh báo:

- ​   Restore đè Production Database.
- ​   Delete Database.
- ​   Delete Backups.
- ​   Roll Back Data to Earlier Time.

- ​ Promote Standby khi chưa có HA controller được cấu hình và kiểm thử.
- ​ Execute Destructive Database Migration.
- ​ Reconcile tài chính hoặc Reward Ledger bằng cách sửa dữ liệu gốc.

### 16.3. Automated Response Matrix

    Incident               Automated Response            Human-controlled Recovery

    API mất DB connection         Retry có giới hạn, alert       Điều tra nếu kéo dài
ngắn hạn

    PostgreSQL process            Controlled restart nếu an      Restore nếu thực sự hỏng
    ngừng                         toàn

    Disk gần đầy                  Alert, stop unsafe jobs        Mở rộng disk/dọn theo runbook

    WAL archiving lỗi             Alert, bảo vệ retention        Sửa repository/credentials

    Backup job lỗi                Retry có kiểm soát             Điều tra nguyên nhân

    Suspected Data Deletion       Alert, audit, evidence         Isolated PITR, selective
    preservation                   recovery

    Corrupted Data Files          Isolate/alert                  Restore được phê duyệt

    Host Lost                     Alert, activate DR plan        Restore sang host mới hoặc
failover

    Bad Migration                 Stop rollout, isolate          Roll forward/back theo
migration plan

Không để script phản ứng tự động tạo vòng lặp restart vô hạn.

## 17. RECOVERY MODES

### 17.1. Mode A — Crash Recovery

PostgreSQL có thể tự dùng WAL còn lại để khôi phục trạng thái nhất quán sau crash.

Đây không phải full restore từ backup.

### 17.2. Mode B — Service Restart

Dành cho lỗi tiến trình/container khi dữ liệu trên disk vẫn hợp lệ.

Không thay đổi backup history.

### 17.3. Mode C — Logical Recovery

Khôi phục chọn lọc từ logical export hoặc isolated restore.

Phù hợp sự cố một số dữ liệu bị xóa/sửa nhầm.

### 17.4. Mode D — Full Physical Restore

Phục hồi database cluster từ backup vật lý.

Sử dụng khi mất/hỏng cluster hoặc cần xây dựng lại môi trường.

### 17.5. Mode E — Point-in-Time Recovery

Khôi phục database đến mốc trước sự cố, trong phạm vi WAL đủ.

### 17.6. Mode F — High Availability Failover

Chuyển Primary sang Standby khi có kiến trúc HA và điều kiện failover đáp ứng.

Không giải quyết lỗi xóa nhầm dữ liệu vì lỗi có thể đã được replicate sang Standby.

## 18. HIGH AVAILABILITY STRATEGY

### 18.1. MVP Decision

Core MVP không bắt buộc PostgreSQL HA cluster.

Đề xuất:

- ​   Một Primary PostgreSQL ổn định.
- ​   Backup/WAL off-host.
- ​   Monitoring.
- ​   Restart policy.
- ​   Recovery runbook.
- ​   Restore drills.

### 18.2. Future HA


Khi cần giảm downtime:

- ​   Primary/Standby replication.
- ​   Failover orchestration.
- ​   Patroni hoặc managed PostgreSQL HA.
- ​   Health-aware routing.
- ​   Split-brain protection.
- ​   Replication lag monitoring.

### 18.3. HA Limitations

Replication không thay thế backup.

Một câu lệnh xóa nhầm có thể được nhân bản sang Standby.

Replication bất đồng bộ có thể mất một số giao dịch gần nhất khi failover.

HA vẫn cần backup, PITR và disaster recovery.

