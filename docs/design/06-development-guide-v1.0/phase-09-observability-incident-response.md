12. PHASE 9 — OBSERVABILITY &
INCIDENT RESPONSE
### Step 9.1 — Collect Metrics

- ​   API requests and latency.
- ​   Routing errors.
- ​   PostgreSQL readiness.
- ​   Disk/CPU/RAM.
- ​   WAL archive.
- ​   Backup status.
- ​   OTP health.

### Step 9.2 — Define Alerts

Mỗi alert có:

- ​   Severity.
- ​   Trigger.
- ​   Duration.
- ​   Runbook Link.
- ​   Owner.
- ​   Escalation.

### Step 9.3 — Define Incident Classes

- ​   Service unavailable.
- ​   Data source issue.
- ​   Routing graph issue.
- ​   Database corruption.
- ​   Backup failure.
- ​   Suspected data deletion.
- ​   Unauthorized access.

### Step 9.4 — Recovery Workflow

Detect → Contain → Diagnose → Choose Strategy → Isolated Recovery → Approval →
Production Recovery → Verify.

### Step 9.5 — Test Failure Scenarios

- ​   Stop OTP.
- ​   Stop Go API.
- ​   Stop PostgreSQL in staging.
- ​   Fill test disk near alert threshold.
- ​   Fail a backup job.
- ​   Break WAL archive in test.
- ​   Simulate accidental data deletion.

Không thử phá hủy dữ liệu trên production để kiểm tra automation.

### Exit Criteria

Các failure scenarios có cảnh báo và quy trình khôi phục phù hợp.

