# OpenTripPlanner — dựng graph cho PoC M02

> **Chỉ là PoC kỹ thuật.** Graph dựng từ feed Metro số 1 **giả định** (ADR-020), không phải lịch chạy chính thức. Không dùng kết quả này để nói TP.HCM đã sẵn sàng cho định tuyến thật.

- Phiên bản: OpenTripPlanner **2.10.0**, image khoá theo digest (ADR-023):
  `opentripplanner/opentripplanner@sha256:8d54e5c589186707ee365417f2202dc878c451fa3001b8edff07019531100933`
- Java nằm sẵn trong image (OpenJDK 25.0.4), máy không cần cài JDK.

## Cấu trúc

| Đường dẫn | Trong Git? | Nội dung |
|---|---|---|
| `config/otp-config.json` | Có | Cấu hình chung khi chạy: bật `ActuatorAPI` (endpoint health và Prometheus) |
| `config/router-config.json` | Có | Cấu hình khi phục vụ: `server.apiProcessingTimeout` = 10 s |
| `config/build-config.json` | Có | Cấu hình dựng graph (múi giờ `Asia/Ho_Chi_Minh`, bật `dataImportReport` để có báo cáo HTML trong `report/`) |
| `scripts/lib.sh` | Có | Hàm dùng chung (log, checksum, `or_unknown`) và digest image OTP — các script khác `source` file này |
| `scripts/serve.sh` | Có | `start` / `status` / `stop`: chạy OTP phục vụ graph, kiểm tra checksum, chờ health, đo, ghi manifest phục vụ |
| `scripts/fetch-osm.sh` | Có | Tải `vietnam-YYMMDD.osm.pbf` của Geofabrik và kiểm tra MD5 vào `data/raw/osm/` |
| `scripts/build-graph.sh` | Có | Kiểm tra đầu vào, cắt OSM, đóng gói GTFS, chạy OTP `--build --save` trong container hạn chế quyền, đo thời gian và bộ nhớ, ghi manifest |
| `manifests/<graph>.txt` | Có | Manifest của lần dựng **thành công** gần nhất (`status=ok`): phiên bản, checksum đầu vào/đầu ra, thời gian, bộ nhớ, số dòng WARN/ERROR. Lần dựng lỗi ghi vào `<graph>.failed.txt`, không ghi đè bản tốt |
| `manifests/<graph>.serve.txt` | Có | Manifest của lần khởi động phục vụ gần nhất (`status=ok`); lần lỗi ghi `<graph>.serve.failed.txt` |
| `queries/plan-connection.graphql` | Có | Truy vấn tìm tuyến mẫu (`planConnection`) đã chạy được trên 2.10.0 |
| `queries/rc-01.variables.json` | Có | Biến cho RC-01 (Bến Thành → Bến xe Suối Tiên, thứ Ba 17-11-2026 09:00) |
| `work/<graph>/` | **Không** (`.gitignore`) | Thư mục gốc OTP: OSM đã cắt, GTFS zip, `build-config.json`, `graph.obj`, `report/`, `otp-build.log`, `gc.log`, `cgroup-memory-peak.txt` |
| `data/raw/osm/` | **Không** (`.gitignore`) | File OSM Việt Nam thô và file `.md5` |

## Quy tắc đầu vào của OTP 2.10.0 (đã kiểm tra trong image)

Đọc trực tiếp từ các lớp `BuildConfig`, `FileDataSourceRepository`, `OtpFileNames` trong image đã khoá digest, ngày 12-11-2026:

- OTP nhận **một thư mục gốc**. Trong Docker, entrypoint chạy `java $JAVA_OPTS ... /var/opentripplanner/ $@`, nên thư mục làm việc được mount vào `/var/opentripplanner`.
- Tên file cấu hình cố định: `otp-config.json`, `build-config.json`, `router-config.json`, `debug-ui-config.json`. Không có file nào thì OTP dùng giá trị mặc định.
- Mẫu tên file (mặc định trong `localFileNamePatterns`, không phân biệt hoa thường):
  - GTFS: tên chứa `gtfs`, dạng file zip hoặc thư mục → dùng `hcmc-metro-l1-gtfs.zip`.
  - NeTEx: tên chứa `netex`.
  - OSM: đuôi `.pbf`, `.osm` hoặc `.osm.xml` → dùng `hcmc-metro-l1-corridor.osm.pbf`.
  - DEM (độ cao): đuôi `.tif` hoặc `.tiff` (chưa dùng).
- Đầu ra: `graph.obj` (hoặc `streetGraph.obj` khi dùng `--buildStreet`); báo cáo lỗi dữ liệu ghi vào thư mục `report/` cạnh graph.
- Cờ dòng lệnh dùng: `--build --save --abortOnUnknownConfig` (cờ cuối làm OTP dừng nếu cấu hình có tham số lạ).

## Khung cắt OSM

`BBOX=106.675,10.750,106.835,10.900` (minlon, minlat, maxlon, maxlat): bao 14 ga trong `stops.txt` (vĩ độ 10.7708–10.8796, kinh độ 106.6976–106.8142) cộng khoảng 2 km mỗi phía để có đường đi bộ tới ga. Điểm ở Hà Nội (RC-09) nằm ngoài khung này. Cắt bằng `osmium extract --strategy complete_ways`, nên các tuyến đường cắt ngang biên được giữ trọn.

Dữ liệu OSM © OpenStreetMap contributors, ODbL 1.0, lấy qua Geofabrik.

## Chạy lại

Yêu cầu: Docker đang chạy, `osmium` (`brew install osmium-tool`), `zip`, `unzip`, `shasum` hoặc `sha256sum`, `md5` hoặc `md5sum`. Script chạy được trên macOS và Linux (chưa thử trên Linux).

```bash
infrastructure/otp/scripts/fetch-osm.sh 261111   # hoặc bỏ tham số để lấy bản mới nhất
infrastructure/otp/scripts/build-graph.sh
```

Biến tuỳ chọn: `OSM_SOURCE`, `GTFS_DIR`, `BBOX`, `GRAPH_NAME`, `OTP_XMX` (mặc định `4g`), `OTP_MEM_LIMIT` (mặc định `6g`). Script xoá và tạo lại `work/<GRAPH_NAME>/` mỗi lần chạy.

### Container và cách đo

- Container chạy với `--user <uid>:<gid> --network none --cap-drop ALL --security-opt no-new-privileges --memory 6g`: không root, không mạng (dựng graph không cần mạng), bỏ mọi capability.
- `peak_container_mem_mib`: đọc `/sys/fs/cgroup/memory.peak` ngay sau khi OTP thoát — bộ nhớ đỉnh thật của container do kernel ghi. (Cách cũ lấy mẫu `docker stats` chỉ có 3–4 mẫu mỗi lần dựng và báo thấp hơn thực tế: 639–702 MiB so với 732–930 MiB đo bằng `memory.peak`.)
- `peak_jvm_heap_at_gc_mib`: heap lớn nhất ghi trong GC log (`-Xlog:gc`) tại các lần GC; heap thật giữa hai lần GC có thể cao hơn một chút.
- `graph.obj` **không** giống nhau từng byte giữa các lần dựng dù đầu vào giống hệt; so sánh đầu vào bằng `osm_corridor_sha256`, `gtfs_zip_sha256`, `build_config_sha256`.
- Nhấn Ctrl+C hoặc gửi SIGTERM: script dừng với mã 130 và xoá container.

## Số đo nền (12-11-2026, 3 lần chạy, Apple M4 Pro, Docker 29.6.2 cấp 9,4 GB)

| Chỉ số | Lần 1 | Lần 2 | Lần 3 | Trung vị |
|---|---|---|---|---|
| Cắt OSM (s) | 2 | 2 | 2 | 2 |
| Dựng graph, gồm khởi động JVM (s) | 6 | 6 | 6 | 6 |
| Bộ nhớ đỉnh container (MiB) | 754 | 788 | 844 | 788 |
| Heap JVM đỉnh tại GC (MiB) | 417 | 416 | 479 | 417 |

Graph: `|V|=87,499 |E|=212,008`, 14 ga, 2 pattern; `graph.obj` khoảng 13,1 MB; 3 WARN, 0 ERROR.

## Lỗi dữ liệu đã biết (lần dựng 12-11-2026)

- `PrunedStopIsland` = 6: ga L1-04, L1-05, L1-09, L1-10, L1-11, L1-14 bị gắn vào "đảo" đường đi bộ nhỏ (4–12 đỉnh, có thể là lối trong nhà ga trên cao), OTP xoá đảo và **gỡ liên kết** ga khỏi đảo đó. Có thể làm hỏng đoạn đi bộ tới/từ các ga này (ảnh hưởng RC-01, RC-02, RC-05). Kiểm tra ở task 5; hướng sửa: dời toạ độ ga ra lối lên xuống, thêm cửa ga (`location_type=2`) hoặc chỉnh `islandPruning`.
- `StopNotLinkedForTransfers` = 13: các ga cách nhau quá xa để có chuyển tiếp đi bộ giữa ga; bình thường với một tuyến đơn.
- `Limiting transit service days to [2025-11-12, 2029-11-13)`: khoảng mặc định quanh ngày dựng; feed (01-06 → 31-12-2026) nằm trong khoảng này.

## Chạy phục vụ (task 3)

```bash
infrastructure/otp/scripts/serve.sh start    # mặc định cổng 8088 (8080 hay bị app khác dùng); đổi bằng OTP_PORT
infrastructure/otp/scripts/serve.sh status
infrastructure/otp/scripts/serve.sh stop
```

- `start` chỉ chạy khi `graph.obj` khớp `graph_obj_sha256` trong `manifests/<graph>.txt` có `status=ok`; không khớp thì báo dựng lại.
- Container: không root, `--cap-drop ALL`, `no-new-privileges`, giới hạn 3 GB, `-Xmx2g`; thư mục graph mount **chỉ đọc**; cổng chỉ mở ở `127.0.0.1`.
- Sẵn sàng khi `GET /otp/actuators/health` trả 200 (`{"status":"UP"}`), rồi kiểm tra `POST /otp/gtfs/v1` với `{ __typename }` phải trả `QueryType`; một trong hai lỗi thì dừng container và ghi `.serve.failed.txt`.
- Endpoint đã thấy trả 200 (12-11-2026): `/otp/actuators/health`, `/otp/actuators/prometheus`, `/otp/gtfs/v1`, Debug UI ở `/`.
- `router-config.json`: OTP 2.10.0 **không** nhận `server.streetRoutingTimeout` (có trong mã nhưng `--abortOnUnknownConfig` báo "Unexpected config parameter"); chỉ dùng `server.apiProcessingTimeout`.

Số đo khởi động (3 lần, cùng máy, graph dựng 14:58):

| Chỉ số | Lần 1 | Lần 2 | Lần 3 | Trung vị |
|---|---|---|---|---|
| `docker run` → health 200 (ms) | 1987 | 1659 | 1637 | 1659 |
| Bộ nhớ đỉnh container (MiB) | 520 | 506 | 520 | 520 |
| Heap JVM đỉnh tại GC (MiB) | 202 | 166 | 196 | 196 |

WARN khi phục vụ (2–3 dòng, 0 ERROR): `FEATURES THAT SHOULD BE TURNED OFF IN PRODUCTION: CostlyAssertions`, và cảnh báo Micrometer đăng ký trùng meter `graph_build_issues_total` khi bật `ActuatorAPI` (không chặn). Cảnh báo `Cannot create Raptor data` lúc dựng graph **không** xuất hiện khi phục vụ: log ghi `Creating transit layer for Raptor routing ... Mapping complete`.

## API GraphQL của OTP 2.10.0 (task 4)

- Endpoint: `POST /otp/gtfs/v1`, body JSON `{"query": ..., "variables": ...}`.
- Schema: `/app/resources/org/opentripplanner/apis/gtfs/schema.graphqls` trong image (5 610 dòng, sha256 `ee5c42d5fd6346b6062d057030f8cb2062517b31e93add66ca5a92cae247aac4`). Introspection của server khớp file này ở phần `planConnection`.
- **Tìm tuyến dùng `planConnection`** (14 tham số: `origin`, `destination`, `dateTime`, `first`, `last`, `after`, `before`, `modes`, `preferences`, `itineraryFilter`, `searchWindow`, `via`, `flex`, `locale`). `plan` đã **deprecated** ("Use `planConnection` instead") — không dùng.
- Kết quả: `edges[].node` là `Itinerary` (`start`, `end`, `duration`, `walkDistance`, `numberOfTransfers`, `legs`); mỗi `Leg` có `mode`, `start/end { scheduledTime estimated { time } }`, `from/to`, `route`, `legGeometry { points }` (polyline mã hoá).
- Nhãn lịch/thời gian thực: `start.estimated` = `null` nghĩa là chỉ có lịch. Các trường `realTime`, `realtimeState`, `startTime`, `endTime` đã deprecated.
- Lỗi — **luôn HTTP 200**, phải đọc nội dung:
  - Lỗi tìm tuyến nằm ở `planConnection.routingErrors[]` với `code` thuộc `RoutingErrorCode`: `LOCATION_NOT_FOUND`, `NO_DIRECT_MODE_CONNECTION`, `NO_STOPS_IN_RANGE`, `NO_TRANSIT_CONNECTION`, `NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW`, `OUTSIDE_BOUNDS`, `OUTSIDE_SERVICE_PERIOD`, … và `inputField` (`FROM`/`TO`/`DATE_TIME`).
  - Đầu vào sai (ví dụ vĩ độ 95) trả `errors[]` cấp GraphQL (`DataFetchingException`), không có `routingErrors` → Go API phải tự kiểm tra đầu vào và trả `INVALID_REQUEST`.

Thử ngày 12-11-2026 (server từ `serve.sh`):

| Case | Kết quả |
|---|---|
| RC-01 Bến Thành → Bến xe Suối Tiên, thứ Ba 09:00 | HTTP 200, 0,29 s; 3 hành trình `SUBWAY L1`, 0 chuyển tuyến, trên tàu 1 771 s (29 phút 31 giây — khớp kỳ vọng), `estimated = null` (theo lịch), geometry 14 điểm |
| RC-09 điểm đến Hà Nội | HTTP 200; `routingErrors: OUTSIDE_BOUNDS, inputField TO`; 0 hành trình |
| RC-10 vĩ độ 95 | HTTP 200; `errors: "The latitude is not in range[-90.0, 90.0]: 95.0"` |

Cần xem ở task 5: với chuyến theo tần suất (`frequencies.txt`, `exact_times=0`), 3 hành trình RC-01 cách nhau đúng 1 phút (09:12:55, 09:13:55, 09:14:55) và chuyến đầu chờ 775 s, lớn hơn headway 655 s mà RC-01 kỳ vọng. Có thể OTP cộng trọn headway vào mỗi mốc tìm kiếm theo phút (cách tính bi quan); chưa kiểm chứng.

Chạy lại:

```bash
infrastructure/otp/scripts/serve.sh start
python3 -I -c 'import json;print(json.dumps({"query":open("infrastructure/otp/queries/plan-connection.graphql").read(),"variables":json.load(open("infrastructure/otp/queries/rc-01.variables.json"))}))' \
  | curl -s -H 'Content-Type: application/json' --data @- http://127.0.0.1:8088/otp/gtfs/v1
infrastructure/otp/scripts/serve.sh stop
```

## Chưa làm (các task sau của M02)

- Task 5–6: chạy `data/test-cases/route-cases.csv`, đo độ trễ tìm tuyến.
