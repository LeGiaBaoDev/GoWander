## GOWANDER

DEVELOPMENT & IMPLEMENTATION
GUIDE
Version: 1.0 — Step-by-Step Development Roadmap

Project: GoWander — Move Smart. Explore More.

Primary Platform: Android / iOS

Administration: Web Admin

Mobile Stack: Kotlin Multiplatform + Compose

Backend Stack: Go Modular Monolith

Database: PostgreSQL + PostGIS

Routing Engine: OpenTripPlanner

Target Environment: macOS Development → Linux Production

Status: Not Started — Implementation Guide

## 01. PURPOSE & WORKING RULES

### 1.1. Mục đích

Tài liệu hướng dẫn toàn bộ quy trình dựng GoWander từ môi trường phát triển ban đầu đến
MVP có thể triển khai và vận hành.

Mỗi bước phải có:

- ​   Objective — Mục tiêu.
- ​   Prerequisites — Điều kiện đầu vào.
- ​   Implementation — Các việc cần làm.
- ​   Verification — Cách xác minh.
- ​   Deliverables — Đầu ra.
- ​   Exit Criteria — Điều kiện hoàn thành.

- ​ Troubleshooting — Cách xử lý khi thất bại.

Không đánh dấu hoàn thành khi chỉ tạo xong file, thư mục hoặc chạy thành công một lệnh
mà chưa chứng minh chức năng hoạt động.

### 1.2. Development Principles

Vertical Slice First: Hoàn thành luồng đi từ UI đến dữ liệu thật trước khi phát triển hàng loạt
module.

Data Before Routing: Không triển khai giao thông dựa trên dữ liệu chưa được xác minh.

Architecture Without Overengineering: Giữ module boundaries rõ ràng, không xây
microservices sớm.

Test As You Build: Viết test và kiểm chứng từng bước.

Document Actual Results: Ghi kết quả thực tế vào implementation log.

Backup Before Production: Không phát hành production chứa dữ liệu thật khi chưa có
backup và restore drill đạt yêu cầu.

### 1.3. Development Environment

    Tool                 Purpose

    Git                    Version Control

    GitHub                 Repository, PR, CI

    JDK                    Android/Kotlin tooling

    Android Studio         Android/KMP

    Xcode                  iOS build

    Go                     Backend

    Docker Desktop         Local infrastructure

    PostgreSQL/PostGI      Application Database
S

    OpenTripPlanner        Routing

    VS Code/IDE            Development

    Gradle                 Mobile builds

    curl/jq                API validation

    Python                  Data inspection scripts

    pgBackRest              Physical backups

Phiên bản thực tế cần được khóa sau khi kiểm tra compatibility giữa những thành phần.

## 02. PROJECT STRUCTURE

### 2.1. Recommended Repository

Sử dụng Monorepo để đơn giản hóa quá trình phát triển bởi một người.

None‎
gowander/
├── mobile/
├── backend/
├── admin/
├── infrastructure/
    │       ├── docker/
    │       ├── otp/
    │       ├── postgres/
    │       ├── backup/
    │       └── monitoring/
├── data/
    │       ├── sources/
    │       ├── raw/
    │       ├── processed/
    │       └── test-cases/
├── docs/
    │       ├── architecture/
    │       ├── development/
    │       ├── decisions/
    │       ├── research/
    │       └── runbooks/
├── scripts/
├── .github/
    │       └── workflows/
├── .gitignore

├── .env.example
└── README.md

### 2.2. Directory Responsibilities

    Directory                  Responsibility

    mobile           Android/iOS KMP Application

    backend          Go API & Business Logic

    admin            Admin Web

infrastructure   Docker, Database, OTP, Backup

    data             Transit datasets và dữ liệu thử
nghiệm

    docs             Technical Records, Guide, ADR

    scripts          Công cụ hỗ trợ development

    .github          CI/CD Workflows

Không commit dữ liệu nhạy cảm, credentials, database volumes hoặc backup thực tế vào
Git.

