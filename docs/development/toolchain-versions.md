# Phiên bản công cụ trên máy phát triển

Ghi nhận ngày **11-11-2026** bằng cách chạy lệnh trực tiếp trên máy phát triển chính. Đây là phiên bản đang cài, chưa phải phiên bản đã khoá cho dự án; việc khoá phiên bản thực hiện ở từng milestone sau khi kiểm tra tương thích.

## Đã có

| Công cụ | Phiên bản | Lệnh kiểm tra |
|---|---|---|
| macOS | 27.0.1 (26A434), arm64 | `sw_vers`, `uname -m` |
| Git | 2.54.0 (Apple Git-157) | `git --version` |
| Go | 1.27.4 darwin/arm64 | `go version` |
| JDK | Java 21.0.8 LTS (HotSpot 64-bit) | `java -version` |
| Docker Engine | 29.6.2 (daemon đang chạy) | `docker --version`, `docker info` |
| Docker Compose | v5.3.1 | `docker compose version` |
| Python | 3.15.2 | `python3 --version` |
| Xcode | 27.0 (27A266a) | `xcodebuild -version` |
| iOS Simulator runtime | iOS 26.3, iOS 27.0 | `xcrun simctl list runtimes` |
| curl | 8.7.1 | `curl --version` |
| jq | 1.7.1 | `jq --version` |
| Node.js | v20.20.2 | `node --version` |
| GitHub CLI | 2.103.3 | `gh --version` |
| Android Studio | 2026.2 (cài 12-11-2026) | `defaults read "/Applications/Android Studio.app/Contents/Info.plist" CFBundleShortVersionString` |
| Android SDK | platform `android-37.0`, build-tools 36.0.0, emulator 37.2.13.0; AVD `Medium_Phone_API_37.0` (bộ nhớ trong đặt 4 GB vì ổ đĩa đầy) | `ls ~/Library/Android/sdk/platforms`, `emulator -version` |

## Chưa có

| Công cụ | Tình trạng | Ảnh hưởng |
|---|---|---|
| Gradle (cài toàn cục) | Không có lệnh `gradle` | Không chặn: dự án KMP dùng Gradle Wrapper |
| Kotlin CLI | Không có lệnh `kotlin` | Không chặn: Gradle tự tải Kotlin compiler |
| pgBackRest | Không có lệnh `pgbackrest` | Không chặn tới M08; dự kiến chạy trên Linux/container |

## Repository

- Remote `origin`: `git@github.com:LeGiaBaoDev/GoWander.git`
- Tên Go module ở M04 sẽ lấy theo remote này: `github.com/LeGiaBaoDev/GoWander/backend` (chưa khởi tạo).

## Chưa kiểm chứng

- Chưa build thử dự án Go nào.
- Cập nhật 12-11-2026 (M03): app `mobile/` build và chạy được trên Android emulator API 37 và iOS simulator iOS 27.0 với JDK 21, Kotlin 2.4.20, Compose Multiplatform 1.12.1 và **Xcode 27.0** (dù bảng tương thích Kotlin chỉ ghi Xcode 26.4). Phiên bản khoá: ADR-026.
- Ổ đĩa máy phát triển thường chỉ còn khoảng 10 GB trống; emulator, simulator và Kotlin/Native từng làm đầy ổ (12-11-2026).
