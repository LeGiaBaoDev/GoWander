## 17. RELEASE CHECKLIST

Mỗi release cần kiểm tra:

- ​   Code review hoặc self-review có bằng chứng.
- ​   Tests pass.
- ​   Schema migrations pass.
- ​   API contract compatible.

- ​   No secrets committed.
- ​   No critical vulnerabilities unresolved.
- ​   DB backups healthy.
- ​   WAL archiving healthy.
- ​   Restore drill current.
- ​   OTP graph/source version documented.
- ​   Android/iOS target testing completed.
- ​   Monitoring and alerts active.
- ​   Rollback/Recovery plan documented.

## 18. IMPLEMENTATION LOG FORMAT

Dùng format thống nhất trong docs/development/implementation-log.md:

Step ID: Ví dụ GW-OTP-003

Date: YYYY-MM-DD

Objective: Mục tiêu.

Environment: OS, Tools, Versions.

Implementation: Lệnh chạy/Code đã tạo.

Result: PASS / FAIL / BLOCKED.

Evidence: Log, test result, screenshot, output.

Issues: Lỗi phát hiện.

Resolution: Cách xử lý.

Next Step: Bước tiếp theo.

Không chỉnh một kết quả FAIL thành PASS nếu chưa kiểm thử lại.

## 19. FIRST DEVELOPMENT MILESTONE

### Milestone M1 — Prove Real Routing

Hoàn thành:

1. Repository Foundation.
2. Pilot Region Selection.
3. Transit Data Verification.
4. OTP Graph Build.
5. GraphQL Routing.
6. Representative Route Tests.
7. OTP Performance Notes.

Definition of Done:

Có ít nhất một hành trình hợp lệ từ nguồn dữ liệu thực đã được phép sử dụng, có thể truy
vấn qua OTP và đối chiếu được với lịch/trạm của dataset.

Nếu dữ liệu bus/metro TP.HCM chưa được phép sử dụng hoặc chưa đủ, kết quả milestone
là BLOCKED hoặc SCOPE CHANGE, không sử dụng dữ liệu mô phỏng để đánh dấu hoàn
thành.

20. FINAL IMPLEMENTATION
PRINCIPLES
GoWander được triển khai theo trình tự:

Foundation → Data → Routing → Map → Backend → Database → Mobile Integration
→ Admin → Reliability → Pilot → Expansion.

Thành công của từng bước được chứng minh bằng đầu ra có thể kiểm tra.

Tài liệu kiến trúc được cập nhật khi kết quả triển khai khác giả định ban đầu.

Không xây những thành phần phức tạp nếu chưa có nhu cầu thực tế.

Không triển khai production chứa dữ liệu thật nếu chưa bảo vệ được dữ liệu và chưa thử
khôi phục.

GoWander — Move Smart. Explore More.


