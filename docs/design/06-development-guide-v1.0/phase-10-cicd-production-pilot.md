13. PHASE 10 — CI/CD & PRODUCTION
PILOT
### Step 10.1 — CI

- ​   Formatting/Lint.
- ​   Unit Tests.
- ​   Integration Tests.
- ​   Migration Tests.
- ​   Build.
- ​   OpenAPI Contract Checks.
- ​   Basic Security Checks.

### Step 10.2 — Staging

Staging phải kiểm tra các đường đi chính:

- ​   Discover.
- ​   Routing.
- ​   Database.
- ​   Admin.
- ​   Backup.
- ​   Auth nếu được phát hành.

### Step 10.3 — Deployment Gate

Chỉ triển khai khi:

- ​   Core E2E đạt.
- ​   Data Sources hợp lệ.
- ​   OTP Graph đã kiểm tra.
- ​   Map Android/iOS đã đạt PoC.
- ​   Database migrations thành công.
- ​   Admin RBAC hoạt động.
- ​   Backup/WAL khỏe.
- ​   Restore Drill đạt.
- ​   Monitoring có tín hiệu.

### Step 10.4 — Pilot Release

Phạm vi:

- ​ Khu vực TP.HCM giới hạn.
- ​ Bus/Metro/Walking được hỗ trợ.

- ​ 30–50 Places dự kiến.
- ​ 10–15 Historical Stories dự kiến.
- ​ Feedback và event tracking phù hợp.

### Step 10.5 — Measure & Improve

Theo dõi:

- ​   Route Found Rate.
- ​   Routing Failure Rate.
- ​   P95 Latency.
- ​   Discover-to-Route Conversion.
- ​   Content Quality.
- ​   Feedback.
- ​   Critical Incidents.

Không gọi pilot thành công chỉ vì có nhiều lượt mở app.

