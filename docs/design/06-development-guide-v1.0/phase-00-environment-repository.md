03. PHASE 0 — ENVIRONMENT &
REPOSITORY
### Step 0.1 — Check Local Tools

Kiểm tra:

None‎
git --version
go version
docker --version
docker compose version

java -version
python3 --version

Kiểm tra thêm Android Studio, Xcode và simulators thông qua các công cụ tương ứng.

Expected Result:

- ​     Git hoạt động.
- ​     Docker daemon khởi động được.
- ​     Go cài đặt thành công.
- ​     JDK hỗ trợ toolchain đã chọn.
- ​     Android tooling hợp lệ.
- ​     Xcode build được iOS app trên macOS phù hợp.

### Step 0.2 — Initialize Repository

None‎
mkdir gowander
cd gowander

git init
git branch -M main

mkdir -p mobile backend admin
mkdir -p infrastructure/{docker,otp,postgres,backup,monitoring}
mkdir -p data/{sources,raw,processed,test-cases}
mkdir -p
docs/{architecture,development,decisions,research,runbooks}
mkdir -p scripts .github/workflows

touch README.md .gitignore .env.example

Tạo .gitignore phù hợp để loại trừ ít nhất:

None‎
.env
.env.*
!.env.example

.DS_Store

.idea/
.gradle/
build/
**/build/

data/raw/
data/processed/
*.osm.pbf
*.gtfs.zip
graph.obj

*.bak
*.backup
*.dump

Đây là danh sách tối thiểu, cần bổ sung .gitignore theo các công cụ chính thức của
Android/Kotlin, Go và Admin framework.

### Step 0.3 — README

README phải có:

- ​     Project description.
- ​     Architecture summary.
- ​     Prerequisites.
- ​     Local startup.
- ​     Development commands.
- ​     Environment variables.
- ​     Testing instructions.
- ​     Source references.

### Step 0.4 — First Commit

None‎
git add .
git commit -m "chore: initialize GoWander monorepo"

Khi repository đã được tạo trên GitHub, thêm remote theo URL thực tế rồi push.

### Exit Criteria


- ​   Repository tồn tại.
- ​   Folder structure rõ ràng.
- ​   Không có secrets trong Git.
- ​   Git commit đầu tiên thành công.
- ​   README có hướng dẫn cơ bản.

Status: NOT STARTED.

