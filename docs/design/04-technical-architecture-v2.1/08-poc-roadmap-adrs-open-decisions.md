28. TECHNICAL POC & VALIDATION
PLAN

### 28.1. Required PoCs

    PoC                         Acceptance

Data Feasibility   GTFS/OSM valid and licensed

    Routing            OTP returns correct representative
journeys

    Map                Android/iOS maps, markers, polyline, GPS

    API                KMP → Go → OTP

    PostgreSQL         Migrations & representative queries

    Identity           Google Login/Account Linking safe

    Admin              RBAC & Audit enforced

    Backup             Full/Diff Backup works

    PITR               Restore to target on isolated host

    Monitoring         Alerts correctly detect simulated failures

    Recovery           RPO/RTO measured in drill

### 28.2. Release Gate

Không tuyên bố production-ready nếu:

- ​   Chưa có source dữ liệu transit hợp lệ.
- ​   Routing chưa kiểm chứng.
- ​   iOS/Android map chưa hoạt động.
- ​   Database backup chưa kiểm tra restore.
- ​   Production secrets chưa bảo vệ.
- ​   Admin API chưa phân quyền.
- ​   Dữ liệu doanh thu chưa có nguồn đáng tin cậy khi kích hoạt monetization.

## 29. IMPLEMENTATION ROADMAP

### Stage 0 — Architecture Foundation

- ​ Repository & CI.
- ​ OpenAPI skeleton.

- ​   Database/PostGIS.
- ​   Go modular structure.
- ​   Docker Compose.
- ​   Logs & Health Checks.

### Stage 1 — Data & Routing PoC

- ​   GTFS/OSM verification.
- ​   OTP Graph Build.
- ​   Routing Adapter.
- ​   Route Test Cases.

### Stage 2 — Mobile Map PoC

- ​   KMP Map.
- ​   Markers.
- ​   Polylines.
- ​   GPS.
- ​   Route Rendering Android/iOS.

### Stage 3 — Core Mobility + Discover

- ​   Route Planning.
- ​   Route Comparison.
- ​   Place Search.
- ​   Food/Story Details.
- ​   Discover-to-Route.

### Stage 4 — Core Admin & Operations

- ​   Content Management.
- ​   Transit Monitoring.
- ​   Feedback.
- ​   Basic Dashboard.
- ​   RBAC & Audit.

### Stage 5 — Backup & Production Reliability

- ​   pgBackRest.
- ​   Scheduled Backups.
- ​   WAL Archive.
- ​   External Repository.
- ​   Backup Alerts.
- ​   Manual Backup Runbook.
- ​   PITR Drill.

- ​ Restore Verification.

Backup Foundation cần hoàn tất trước Production Pilot có dữ liệu thật; việc lập kế hoạch ở
Stage 5 không có nghĩa được phép chạy production trước khi có backup.

### Stage 6 — Pilot

- ​   Functional E2E.
- ​   Deployment.
- ​   Monitoring.
- ​   Feedback Collection.
- ​   Incident Response Tests.

### Stage 7 — Account, Exploration & Community

- ​   Google Sign-In.
- ​   Profile.
- ​   Exploration.
- ​   Reviews/Posts.
- ​   Moderation.

### Stage 8 — Rewards & Business

- ​   Points Ledger.
- ​   Contributions.
- ​   Partner Integration.
- ​   Revenue.
- ​   Settlements.

### Stage 9 — Scaling

- ​   Additional Read Models.
- ​   Cache where needed.
- ​   Advanced Monitoring.
- ​   HA evaluation.
- ​   Advanced Recommendations.

30. TECHNICAL ARCHITECTURE
DECISIONS

    ADR                     Decision                              Status

    ADR-001     KMP + Compose                         Selected

    ADR-002     Go Modular Monolith                   Selected

    ADR-003     PostgreSQL + PostGIS                  Selected

    ADR-004     OTP via GraphQL Adapter               Pending PoC

    ADR-005     MapLibre Compose                      Pending PoC

    ADR-006     OIDC-based Authentication             Proposed

    ADR-007     Admin Web + RBAC                      Selected Approach

    ADR-008     Versioned SQL Migrations              Required

    ADR-009     pgBackRest for Physical Backup        Proposed

    ADR-010     WAL Archiving + PITR                  Required for Production Pilot Target

    ADR-011     Scheduled + Manual Backup             Required

    ADR-012     Automated Restore Drills              Required

    ADR-013     No Blind Auto Restore to Production   Accepted

    ADR-014     External Backup Repository            Required

    ADR-015     Automated Alerts                      Required

    ADR-016     HA/Patroni                            Deferred

    ADR-017     Decision-oriented Dashboards          Selected

    ADR-018     Revenue/Settlement Separation         Selected Design

    ADR-019     No Premature Microservices            Accepted

## 31. OPEN TECHNICAL DECISIONS

Trước khi triển khai cần chốt:

1. PostgreSQL version và PostGIS compatibility.
2. pgBackRest version và repository configuration.
3. S3 provider hay backup host riêng.
4. Encryption key management.
5. Scheduler cron hay systemd timer.

6. RPO/RTO có phù hợp ngân sách thực tế không.
7. Số chu kỳ Full Backup và thời hạn WAL.
8. Alert channel và incident ownership.
9. Monitoring stack managed hay self-hosted.
10. Recovery environment provisioning.
11. Backup object versioning/immutability compatibility.
12. Go API deployment model.
13. Identity Provider và Apple Sign-In.
14. Admin Web frontend framework.
15. OTP Graph Versioning.
16. MapLibre Android/iOS PoC.
17. Business contract and payment integration.
18. Revenue recognition rules.
19. Security/privacy retention policy.

Các quyết định này sẽ được ghi trong ADRs và tài liệu triển khai.

32. RESEARCH SOURCES &
IMPLEMENTATION REFERENCES
Những nguồn kỹ thuật chính thức cần được tiếp tục sử dụng khi triển khai:

PostgreSQL:

- ​   https://www.postgresql.org/docs/current/
- ​   https://www.postgresql.org/docs/current/continuous-archiving.html
- ​   https://www.postgresql.org/docs/current/monitoring-stats.html
- ​   https://www.postgresql.org/docs/current/warm-standby.html
- ​   https://www.postgresql.org/docs/current/app-pgdump.html

pgBackRest:

- ​ https://pgbackrest.org/user-guide.html
- ​ https://pgbackrest.org/configuration.html
- ​ https://github.com/pgbackrest/pgbackrest

Routing:

- ​ https://github.com/opentripplanner/OpenTripPlanner
- ​ https://docs.opentripplanner.org/en/latest/

Map:

- ​ https://maplibre.org/maplibre-compose/

Authentication:

- ​ https://developers.google.com/identity/openid-connect/openid-connect
- ​ https://developer.apple.com/app-store/review/guidelines/

High Availability:

- ​ https://github.com/patroni/patroni

Các tài liệu phải được kiểm tra lại với phiên bản dependency đã khóa khi chuẩn bị
production.

## 33. CONCLUSION

GoWander sử dụng kiến trúc:

KMP Mobile + Admin Web → Go Modular Monolith → PostgreSQL/PostGIS, kết hợp
OpenTripPlanner cho giao thông đa phương thức.

Production Reliability được xây quanh:

Monitor → Detect → Alert → Contain → Recover → Verify.

Database Recovery được thiết kế theo nhiều lớp:

Scheduled Backup + Manual Backup + Continuous WAL Archiving + PITR + Offsite
Storage + Restore Drills.

Không dùng tự động restore production mù quáng để phản ứng với mọi lỗi.

Mục tiêu của Technical v2.1 là một hệ thống có thể xây dựng, vận hành, kiểm chứng và
phục hồi được, không chỉ một sơ đồ kiến trúc đẹp.

GoWander — Move Smart. Explore More.

---

