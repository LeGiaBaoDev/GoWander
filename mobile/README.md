# GoWander mobile (Kotlin Multiplatform + Compose)

App Android + iOS dùng chung một codebase Compose Multiplatform. Quyết định: [ADR-026](../docs/decisions/ADR-026-mobile-toolchain.md) (toolchain), [ADR-025](../docs/decisions/ADR-025-map-tiles.md) (bản đồ nền), [ADR-029](../docs/decisions/ADR-029-mobile-api-client.md) (gọi API, trạng thái màn hình).

> Lịch tàu là **dữ liệu giả định** (ADR-020): kết quả tìm tuyến luôn có nhãn DEMO.

## Hai luồng (M06)

- **Bản đồ:** điểm đi = vị trí GPS (hoặc nhấn giữ khi chọn "Nhấn giữ để đặt điểm đi"), điểm đến = nhấn giữ bản đồ hoặc "Chỉ đường" từ Khám phá → **Tìm tuyến** → chọn phương án → bản đồ vẽ chặng (metro nét liền, đi bộ nét đứt) và danh sách chặng.
- **Khám phá:** danh mục, tìm theo tên (không dấu, từ 2 ký tự), danh sách phân trang, chi tiết địa điểm có nguồn + giấy phép, nút **Chỉ đường tới đây**.

## Chạy trên máy ảo

Cần chạy trước trên máy Mac: database (`infrastructure/postgres/README.md`), OTP (`infrastructure/otp/scripts/serve.sh start`), và API Go:

```bash
cd backend && set -a && . ../.env && set +a && go run ./cmd/api   # lắng nghe 127.0.0.1:8090
```

| Nền tảng | Địa chỉ API app dùng | Cách chạy |
|---|---|---|
| Android emulator | `http://10.0.2.2:8090` (= 127.0.0.1 của máy Mac) | Android Studio ▶, hoặc `./gradlew :androidApp:assembleDebug` rồi `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| iOS simulator | `http://127.0.0.1:8090` | Mở `iosApp/iosApp.xcodeproj` trong Xcode ▶ (chọn một simulator cụ thể, không chọn "Any iOS Simulator") |

- HTTP không mã hoá chỉ được phép tới đúng các địa chỉ đó: Android chỉ trong bản **debug** (`androidApp/src/debug/`), iOS qua `NSAllowsLocalNetworking`.
- Vị trí giả: Android `adb emu geo fix <lon> <lat>`; iOS `xcrun simctl location booted set <lat>,<lon>`.
- Thiết bị thật **chưa hỗ trợ** (cần HTTPS và máy chủ).

## Kiểm thử

```bash
cd mobile
./gradlew :shared:testAndroidHostTest      # API client (MockEngine + JSON thật của API), polyline, ViewModel
./gradlew :shared:iosSimulatorArm64Test    # cùng bộ test trên iOS
```

Bằng chứng chạy đầu–cuối: `docs/evidence/m06/`.

## Cấu trúc (`shared/src/commonMain/kotlin/com/gowander/`)

```
App.kt                 2 tab (Bản đồ, Khám phá), một HttpClient dùng chung, Back của Android
api/                   DTO theo openapi.yaml, Ktor client, ApiError (Server | Network | Unexpected)
route/                 RouteViewModel, màn Bản đồ, giải mã polyline, GeoJSON
discover/              DiscoverViewModel, danh sách + chi tiết
ui/                    màu Design Kit v4, component, Strings (mọi chữ hiển thị)
```
