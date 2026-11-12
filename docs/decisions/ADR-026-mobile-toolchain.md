# ADR-026 — Khoá phiên bản công cụ mobile cho M03

- **Trạng thái:** Accepted cho PoC M03 · **Ngày:** 12-11-2026
- **Liên quan:** `PIPELINE.md` mục 7 (M03, task 1), ADR-025 (bản đồ nền), `docs/development/toolchain-versions.md`

## Quyết định

Dùng đúng bộ phiên bản mà **maplibre-compose v0.20.0** dùng để tự build (`gradle/libs.versions.toml` tại tag `v0.20.0`), để giảm rủi ro lệch phiên bản giữa thư viện bản đồ và Compose:

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Kotlin (KMP, Compose compiler) | 2.4.20 | Bản stable mới nhất là 2.4.21 (bản sửa lỗi); giữ 2.4.20 cho khớp thư viện |
| Compose Multiplatform | 1.12.1 | Stable mới nhất là 1.12.2; giữ 1.12.1 cho khớp thư viện |
| maplibre-compose | 0.20.0 | `org.maplibre.compose`; Android và iOS đang ở mức **Beta** |
| Android Gradle Plugin | 9.3.1 | Bố cục AGP 9: module KMP dùng `com.android.kotlin.multiplatform.library`, app Android ở module riêng |
| Gradle | 9.7.1 | Qua Gradle Wrapper, khoá `distributionSha256Sum` |
| Android SDK | compileSdk 37, minSdk 24, targetSdk 36 | Theo maplibre-compose; đã cài `android-37.0` |
| JDK | 21.0.8 (đã cài) | Android/iOS không cần JDK 25; chỉ desktop của maplibre-compose cần |
| Xcode | 27.0 (27A266a, đã cài) | Xem rủi ro bên dưới |
| iOS tối thiểu | 15.5 | Yêu cầu của maplibre-compose |

Khung project lấy từ template chính thức `Kotlin/KMP-App-Template` (Apache 2.0, nhánh `main` ngày 06-11-2026), bỏ desktop/web.

## Bằng chứng (kiểm tra ngày 12-11-2026)

- Maven Central `maven-metadata.xml`: maplibre-compose bản mới nhất 0.20.0 (10-10-2026); Compose Multiplatform stable mới nhất 1.12.2 (có 1.13.0-beta02); Kotlin stable mới nhất 2.4.21 (có 2.5.0-RC).
- Bảng tương thích Kotlin Multiplatform: Kotlin 2.4.20–2.4.21 hỗ trợ Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1, **Xcode 26.4**.
- maplibre-compose v0.20.0 dùng Gradle 9.7.1 (cao hơn mức 9.7.0 trong bảng) và vẫn build được ở upstream.

## Rủi ro

- **Xcode 27.0 nằm ngoài bảng tương thích chính thức** (bảng ghi 26.4). Máy chỉ có Xcode 27.0. Nếu Kotlin/Native lỗi khi build iOS, phương án là cài thêm Xcode 26.4 rồi chọn bằng `DEVELOPER_DIR`. Kết quả build thật ghi vào change record.
- maplibre-compose ở mức Beta: bản minor có thể đổi API. Khoá đúng 0.20.0, chỉ nâng có chủ đích.
- Ổ đĩa còn khoảng 12 GB; Kotlin/Native, cache Gradle và bản build iOS có thể chiếm vài GB.

## Cập nhật 12-11-2026 (sau khi build và chạy)

- Android Studio 2026.2 tự tạo `mobile/gradle/gradle-daemon-jvm.properties` (`toolchainVersion=25`), nên Gradle tự tải **Temurin JDK 25.0.3** (khoảng 425 MB, `~/.gradle/jdks/`) để chạy daemon. Giữ nguyên vì đó là mặc định của Android Studio; mã vẫn biên dịch ra bytecode JVM 11, và JDK 21 trên máy vẫn dùng được cho các việc khác.
- **Xcode 27.0 build được** framework Kotlin/Native và app iOS (`BUILD SUCCEEDED`), chạy được trên simulator iPhone 18 Pro iOS 27.0 (build cho iPhone 17 thành công nhưng cài lỗi vì ổ đĩa đầy). Rủi ro "Xcode ngoài bảng tương thích" chưa gây lỗi; vẫn theo dõi khi nâng Kotlin.
- Build cho `generic/platform=iOS Simulator` lỗi vì Xcode đòi thêm `x86_64` (máy Intel) mà Gradle không khai báo; build cho một simulator cụ thể (arm64) thì chạy. Không thêm target `iosX64` vì máy phát triển là Apple Silicon.
