#!/usr/bin/env bash
# Chạy OpenTripPlanner 2.10.0 phục vụ graph đã dựng bởi build-graph.sh (PoC M02, task 3).
#
# Cách dùng:
#   infrastructure/otp/scripts/serve.sh start    # kiểm tra graph, khởi động, chờ sẵn sàng, đo, ghi manifest
#   infrastructure/otp/scripts/serve.sh status   # container có chạy không, health trả gì
#   infrastructure/otp/scripts/serve.sh stop     # lưu log rồi dừng và xoá container
# Biến môi trường (tuỳ chọn):
#   GRAPH_NAME     thư mục dưới infrastructure/otp/work/ (mặc định: metro-l1-assumed)
#   OTP_PORT       cổng trên máy, chỉ mở ở 127.0.0.1 (mặc định: 8088; 8080 hay bị app khác dùng)
#   OTP_XMX        heap tối đa của JVM (mặc định: 2g)
#   OTP_MEM_LIMIT  giới hạn bộ nhớ container (mặc định: 3g)
#   START_TIMEOUT  số giây tối đa chờ OTP sẵn sàng (mặc định: 120)
#
# Kết quả của "start": manifests/<GRAPH_NAME>.serve.txt khi thành công,
# manifests/<GRAPH_NAME>.serve.failed.txt khi thất bại (container bị dọn).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OTP_DIR="${REPO_ROOT}/infrastructure/otp"
# shellcheck source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

GRAPH_NAME="${GRAPH_NAME:-metro-l1-assumed}"
OTP_PORT="${OTP_PORT:-8088}"
OTP_XMX="${OTP_XMX:-2g}"
OTP_MEM_LIMIT="${OTP_MEM_LIMIT:-3g}"
START_TIMEOUT="${START_TIMEOUT:-120}"
WORK_DIR="${OTP_DIR}/work/${GRAPH_NAME}"
LOG_DIR="${WORK_DIR}/serve-logs"
MANIFEST_DIR="${OTP_DIR}/manifests"
BUILD_MANIFEST="${MANIFEST_DIR}/${GRAPH_NAME}.txt"
CNAME="gowander-otp-serve-${GRAPH_NAME}"
BASE_URL="http://127.0.0.1:${OTP_PORT}"
HEALTH_URL="${BASE_URL}/otp/actuators/health"
GRAPHQL_URL="${BASE_URL}/otp/gtfs/v1"

manifest_value() { awk -F= -v k="$1" '$1==k {sub(/^[^=]*=/, ""); print; exit}' "$2"; }
now_ms() { local t="${EPOCHREALTIME/[.,]/}"; echo $(( t / 1000 )); }   # cần bash ≥ 5
is_running() { [[ "$(docker inspect -f '{{.State.Running}}' "${CNAME}" 2>/dev/null || true)" == "true" ]]; }
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/${OTP_PORT}") 2>/dev/null; }
http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$@" || true; }

preflight() {
  [[ -n "${EPOCHREALTIME:-}" ]] || die "cần bash ≥ 5 (để đo thời gian theo mili giây)"
  for tool in docker curl awk; do
    command -v "${tool}" >/dev/null 2>&1 || die "thiếu công cụ '${tool}'"
  done
  command -v sha256sum >/dev/null 2>&1 || command -v shasum >/dev/null 2>&1 || die "thiếu sha256sum/shasum"
  docker info >/dev/null 2>&1 || die "Docker daemon chưa chạy"
  [[ "${GRAPH_NAME}" =~ ^[A-Za-z0-9._-]+$ ]] || die "GRAPH_NAME chỉ được chứa chữ, số, '.', '_' và '-'"
  if ! [[ "${OTP_PORT}" =~ ^[0-9]+$ ]] || (( OTP_PORT < 1024 || OTP_PORT > 65535 )); then
    die "OTP_PORT phải là số từ 1024 đến 65535"
  fi
  [[ "${START_TIMEOUT}" =~ ^[0-9]+$ ]] || die "START_TIMEOUT phải là số giây"
}

# --- start --------------------------------------------------------------------
cmd_start() {
  preflight
  is_running && die "container ${CNAME} đang chạy; chạy 'serve.sh stop' trước"
  docker rm -f "${CNAME}" >/dev/null 2>&1 || true   # dọn container cũ đã dừng (nếu có)
  port_in_use && die "cổng ${OTP_PORT} trên 127.0.0.1 đang bị dùng; đặt OTP_PORT khác"

  # 1. Graph phải là đúng graph đã ghi trong manifest dựng thành công.
  [[ -f "${BUILD_MANIFEST}" ]] || die "không thấy ${BUILD_MANIFEST#"${REPO_ROOT}/"}; chạy build-graph.sh trước"
  [[ "$(manifest_value status "${BUILD_MANIFEST}")" == "ok" ]] || die "manifest dựng graph không có status=ok"
  local graph="${WORK_DIR}/graph.obj"
  [[ -s "${graph}" ]] || die "không thấy ${graph#"${REPO_ROOT}/"}; chạy build-graph.sh trước"
  local expected actual
  expected="$(manifest_value graph_obj_sha256 "${BUILD_MANIFEST}")"
  actual="$(sha256_of "${graph}")"
  [[ -n "${expected}" && "${expected}" == "${actual}" ]] \
    || die "graph.obj không khớp manifest (manifest ${expected:-trống}, thực tế ${actual}); chạy lại build-graph.sh"
  log "graph.obj khớp manifest dựng graph (${actual:0:12}…)"

  # 2. Cấu hình chạy nằm cạnh graph (OTP đọc theo tên file trong thư mục gốc).
  cp "${OTP_DIR}/config/otp-config.json" "${OTP_DIR}/config/router-config.json" "${WORK_DIR}/" \
    || die "không chép được otp-config.json/router-config.json"
  rm -rf "${LOG_DIR}"
  mkdir -p "${LOG_DIR}" "${MANIFEST_DIR}"

  # 3. Khởi động. Thư mục graph mount chỉ đọc; log GC ghi vào thư mục riêng.
  trap 'warn "bị ngắt; dọn container"; docker rm -f "${CNAME}" >/dev/null 2>&1 || true; exit 130' INT TERM
  log "Khởi động ${CNAME} trên ${BASE_URL} (Xmx=${OTP_XMX}, giới hạn=${OTP_MEM_LIMIT})"
  local t0 t_ready startup_ms
  t0="$(now_ms)"
  docker run -d --name "${CNAME}" \
    --user "$(id -u):$(id -g)" \
    --cap-drop ALL \
    --security-opt no-new-privileges \
    --memory "${OTP_MEM_LIMIT}" \
    -p "127.0.0.1:${OTP_PORT}:8080" \
    -e JAVA_OPTS="-Xmx${OTP_XMX} -Xlog:gc:file=/var/otp-logs/gc.log:uptime" \
    -v "${WORK_DIR}:/var/opentripplanner:ro" \
    -v "${LOG_DIR}:/var/otp-logs" \
    "${OTP_IMAGE}" --load --serve --abortOnUnknownConfig >/dev/null \
    || die "docker run thất bại"

  # 4. Chờ health trả 200, hoặc container chết, hoặc hết giờ.
  local code="000" deadline=$(( t0 + START_TIMEOUT * 1000 ))
  while :; do
    code="$(http_code "${HEALTH_URL}")"
    [[ "${code}" == "200" ]] && break
    if ! is_running; then fail_start "container dừng trước khi sẵn sàng"; fi
    (( $(now_ms) < deadline )) || fail_start "quá ${START_TIMEOUT}s mà health chưa trả 200 (mã cuối: ${code})"
    sleep 0.25
  done
  t_ready="$(now_ms)"
  startup_ms=$(( t_ready - t0 ))
  trap - INT TERM

  # 5. Kiểm tra nhanh GraphQL (chưa tìm tuyến — việc của task 4/5).
  local gql gql_typename
  gql="$(curl -s --max-time 5 -H 'Content-Type: application/json' \
    -d '{"query":"{ __typename }"}' "${GRAPHQL_URL}" || true)"
  gql_typename="$(sed -nE 's/.*"__typename" *: *"([^"]+)".*/\1/p' <<<"${gql}")"
  [[ -n "${gql_typename}" ]] || fail_start "GraphQL ${GRAPHQL_URL} không trả __typename (phản hồi: ${gql:0:200})"

  # 6. Bộ nhớ (cgroup v2) và số đo từ log.
  local mem_peak mem_now heap_peak
  mem_peak="$(docker exec "${CNAME}" cat /sys/fs/cgroup/memory.peak 2>/dev/null || true)"
  mem_now="$(docker exec "${CNAME}" cat /sys/fs/cgroup/memory.current 2>/dev/null || true)"
  if [[ "${mem_peak}" =~ ^[0-9]+$ ]]; then mem_peak=$(( mem_peak / 1048576 )); else warn "không đọc được memory.peak"; mem_peak=NA; fi
  if [[ "${mem_now}" =~ ^[0-9]+$ ]]; then mem_now=$(( mem_now / 1048576 )); else warn "không đọc được memory.current"; mem_now=NA; fi
  heap_peak="$(grep -oE '[0-9]+M->' "${LOG_DIR}/gc.log" 2>/dev/null | tr -d 'M->' | sort -n | tail -n 1 || true)"
  [[ -n "${heap_peak}" ]] || { warn "không đọc được heap từ GC log"; heap_peak=NA; }

  local serve_log="${LOG_DIR}/otp-serve.log"
  docker logs "${CNAME}" >"${serve_log}" 2>&1 || warn "không lấy được docker logs"
  local warn_count error_count raptor_warn otp_version
  warn_count="$(grep -Ec '^[0-9:.]+ WARN ' "${serve_log}" || true)"
  error_count="$(grep -Ec '^[0-9:.]+ ERROR ' "${serve_log}" || true)"
  raptor_warn="$(grep -c 'Cannot create Raptor data' "${serve_log}" || true)"
  otp_version="$(grep -m1 -oE 'Version: [0-9][^,]*' "${serve_log}" | sed 's/^Version: //' || true)"

  # 7. Manifest.
  local manifest="${MANIFEST_DIR}/${GRAPH_NAME}.serve.txt"
  {
    echo "# Manifest phục vụ OTP — sinh bởi infrastructure/otp/scripts/serve.sh start"
    echo "status=ok"
    echo "graph_name=${GRAPH_NAME}"
    echo "started_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
    echo "otp_image=${OTP_IMAGE}"
    echo "otp_version=$(or_unknown otp_version "${otp_version}")"
    echo "graph_obj_sha256=${actual}"
    echo "graph_built_at=$(manifest_value built_at "${BUILD_MANIFEST}")"
    echo "osm_corridor_sha256=$(manifest_value osm_corridor_sha256 "${BUILD_MANIFEST}")"
    echo "gtfs_zip_sha256=$(manifest_value gtfs_zip_sha256 "${BUILD_MANIFEST}")"
    echo "gtfs_feed_version=$(manifest_value gtfs_feed_version "${BUILD_MANIFEST}")"
    echo "otp_config_sha256=$(sha256_of "${OTP_DIR}/config/otp-config.json")"
    echo "router_config_sha256=$(sha256_of "${OTP_DIR}/config/router-config.json")"
    echo "container_flags=--user $(id -u):$(id -g) --cap-drop ALL --security-opt no-new-privileges --memory ${OTP_MEM_LIMIT} -p 127.0.0.1:${OTP_PORT}:8080 graph:ro"
    echo "java_opts=-Xmx${OTP_XMX}"
    echo "startup_ms=${startup_ms}"
    echo "health_url=/otp/actuators/health"
    echo "health_http=${code}"
    echo "graphql_url=/otp/gtfs/v1"
    echo "graphql_typename=$(or_unknown graphql_typename "${gql_typename}")"
    echo "container_mem_peak_mib=${mem_peak}"
    echo "container_mem_after_ready_mib=${mem_now}"
    echo "peak_jvm_heap_at_gc_mib=${heap_peak}"
    echo "log_warn_lines=${warn_count}"
    echo "log_error_lines=${error_count}"
    echo "log_raptor_data_warning=${raptor_warn}"
  } >"${manifest}"

  log "Sẵn sàng sau ${startup_ms} ms; bộ nhớ đỉnh ${mem_peak} MiB, sau khi sẵn sàng ${mem_now} MiB; WARN=${warn_count}, ERROR=${error_count}"
  log "Manifest: ${manifest#"${REPO_ROOT}/"}"
  log "Health: ${HEALTH_URL} · GraphQL: ${GRAPHQL_URL} · Debug UI: ${BASE_URL}/"
}

fail_start() {
  local reason="$1"
  docker logs "${CNAME}" >"${LOG_DIR}/otp-serve.log" 2>&1 || true
  {
    echo "# Manifest phục vụ OTP — lần khởi động THẤT BẠI"
    echo "status=failed"
    echo "graph_name=${GRAPH_NAME}"
    echo "failed_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
    echo "reason=${reason}"
    echo "container_exit_code=$(docker inspect -f '{{.State.ExitCode}}' "${CNAME}" 2>/dev/null || echo UNKNOWN)"
    echo "container_oom_killed=$(docker inspect -f '{{.State.OOMKilled}}' "${CNAME}" 2>/dev/null || echo UNKNOWN)"
  } >"${MANIFEST_DIR}/${GRAPH_NAME}.serve.failed.txt"
  tail -n 30 "${LOG_DIR}/otp-serve.log" >&2 || true
  docker rm -f "${CNAME}" >/dev/null 2>&1 || true
  die "${reason}; xem ${LOG_DIR#"${REPO_ROOT}/"}/otp-serve.log"
}

# --- stop / status --------------------------------------------------------------
cmd_stop() {
  if docker inspect "${CNAME}" >/dev/null 2>&1; then
    mkdir -p "${LOG_DIR}"
    docker logs "${CNAME}" >"${LOG_DIR}/otp-serve.log" 2>&1 || warn "không lấy được docker logs"
    docker rm -f "${CNAME}" >/dev/null
    log "Đã dừng và xoá ${CNAME}; log ở ${LOG_DIR#"${REPO_ROOT}/"}/otp-serve.log"
  else
    log "Không có container ${CNAME}"
  fi
}

cmd_status() {
  if is_running; then
    log "${CNAME} đang chạy; health ${HEALTH_URL} → HTTP $(http_code "${HEALTH_URL}")"
  else
    log "${CNAME} không chạy"
    return 3
  fi
}

case "${1:-}" in
  start)  cmd_start ;;
  stop)   cmd_stop ;;
  status) cmd_status ;;
  *) echo "Cách dùng: $(basename "$0") start|stop|status" >&2; exit 2 ;;
esac
