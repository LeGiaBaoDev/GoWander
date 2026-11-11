## GOWANDER

### DATABASE DESIGN & DATA MODELING

Version: 1.2 — Complete Logical Data Dictionary, Administration & Revenue
Extension

Engine: PostgreSQL + PostGIS

Backend: Go Modular Monolith

Architecture: Relational-first + Selective Data Modeling Patterns

Tables: 49 proposed tables / 9 data domains

Status: Proposed Logical Design — SQL DDL & Migration Validation Pending

### 01. DATABASE DESIGN OVERVIEW

GoWander sử dụng một PostgreSQL database chính với PostGIS để hỗ trợ:

- ​   Identity & Authentication.
- ​   Discover & Content.
- ​   Mobility Metadata.
- ​   Exploration.
- ​   Community & Moderation.
- ​   Rewards.
- ​   Supporting Operations.
- ​   Administration & Access.
- ​   Business & Monetization.

Routing graph được quản lý bởi OpenTripPlanner, không sao chép toàn bộ
GTFS/OSM vào database nghiệp vụ.

#### 1.1. Database Objectives

1. Đảm bảo dữ liệu đúng và nhất quán.
2. Truy vấn địa lý hiệu quả.
3. Bảo vệ identity, email và phone.
4. Hỗ trợ Content/Moderation Admin.
5. Cấp và đối soát Rewards.
6. Lưu nguồn dữ liệu doanh thu đáng tin cậy.
7. Hỗ trợ KPI có công thức và nguồn.

8. Giảm payload Mobile qua projection/pagination.
9. Mở rộng từng bước mà không thiết kế quá mức.

#### 1.2. Data Design Conventions

    Convention                            Quyết định

    UUID                        Kiểu khóa cho thực thể nghiệp vụ chính

    UUIDv7                      Ưu tiên sinh tại application khi được kiểm
chứng

    BIGINT                      Danh mục/quy tắc cấu hình nhỏ khi phù hợp

    TIMESTAMPTZ                 Mốc thời gian hệ thống

    DATE                        Ngày do user khai báo hoặc ngày nghiệp vụ

    NUMERIC(18,2)               Giá trị tiền theo đơn vị tiền tệ

    CHAR(3)                     Mã tiền ISO 4217

    JSONB                       Metadata linh hoạt có validation

    GEOGRAPHY(POINT,4326)       Tọa độ điểm theo WGS84

    FK                          Bảo đảm quan hệ dữ liệu khi có thể

    UNIQUE                      Chống trùng theo quy tắc nghiệp vụ

    CHECK                         Kiểm tra giá trị và trạng thái

    NULL                          Chỉ cho phép khi trường thực sự tùy chọn

Quy ước đọc Data Dictionary: PK = Primary Key; FK = Foreign Key; UQ = Unique; NN =
NOT NULL; DEF = Default. Trường không ghi NN là nullable trừ khi quy định cụ thể
trong physical schema.

Những giá trị tiền trong bảng phải được xác định rõ là số tiền theo đơn vị tiền tệ
được lưu, không mặc định đều là VND hoặc doanh thu thực nhận.

### 02. TABLE INVENTORY

    Domain                                    Bảng                                 Số
lượng

    Identity       users, user_auth_identities, user_contacts, user_profiles,      5
user_preferences

    Discover       places, place_details, place_categories,                        11
place_category_links, place_media, food_items,
place_foods, historical_stories, story_sources, trip_ideas,
trip_idea_places

    Mobility       transit_data_sources, transit_data_versions,                    3
transit_stop_refs

    Exploration    user_place_status, exploration_entries, journal_media,          5
exploration_collections, collection_places

    Community      posts, post_media, reviews, user_follows, post_likes,           8
post_comments, content_reports, moderation_actions

    Rewards         points_ledger, reward_rules, reward_grants                  3

    Supporting      feedback_reports, notifications, usage_events,              4
outbox_events

    Admin &         roles, user_roles, admin_audit_logs                         3
Access

    Business &      business_partners, partner_agreements, partner_referrals,   7
    Revenue         revenue_transactions, revenue_adjustments,
business_expenses, partner_settlements

    Total                                                                       49

Core Foundation: Các bảng thực sự cần cho Mobility, Discover và Feedback.

Account/Community: Triển khai khi phát hành account, Exploration, Community,
Rewards.

Admin: Triển khai theo phạm vi vận hành từ MVP.

Business: Thiết kế trước nhưng chỉ triển khai khi thương mại hóa có hợp đồng và
giao dịch thật.

Conditional: place_details, story_sources, transit_stop_refs,
reward_rules, reward_grants, outbox_events và một số bảng khác chỉ tạo nếu
nhu cầu phát sinh.

