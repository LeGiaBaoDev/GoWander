#!/usr/bin/env bash
# Dựng graph OpenTripPlanner 2.10.0 cho PoC M02, chạy lại được từ đầu.
#
# Các bước:
#   1. Cắt OSM Việt Nam theo khung hành lang Metro số 1 bằng osmium.
#   2. Đóng gói feed GTFS giả định thành zip có checksum ổn định.
#   3. Chép build-config.json vào thư mục làm việc.
#   4. Chạy OTP (image khoá theo digest, ADR-023) với --build --save trong container
#      không mạng, không root, bỏ hết capability; đo thời gian, bộ nhớ đỉnh (cgroup) và heap JVM (GC log).
#   5. Ghi manifest: trạng thái, phiên bản, checksum đầu vào/đầu ra, thời gian, bộ nhớ, số WARN/ERROR.
#
# Cách dùng: infrastructure/otp/scripts/build-graph.sh
# Biến môi trường (tuỳ chọn):
#   OSM_SOURCE     file .osm.pbf nguồn (mặc định: bản vietnam-*.osm.pbf mới nhất trong data/raw/osm/)
#   GTFS_DIR       thư mục GTFS (mặc định: data/assumed/hcmc-metro-l1-gtfs)
#   BBOX           minlon,minlat,maxlon,maxlat (mặc định: hành lang Metro số 1 + khoảng 2 km)
#   GRAPH_NAME     tên thư mục làm việc dưới infrastructure/otp/work/ (mặc định: metro-l1-assumed)
#   OTP_XMX        heap tối đa của JVM (mặc định: 4g)
#   OTP_MEM_LIMIT  giới hạn bộ nhớ container (mặc định: 6g)
#
# Kết quả: manifests/<GRAPH_NAME>.txt khi thành công; manifests/<GRAPH_NAME>.failed.txt khi thất bại
# (không ghi đè manifest của lần thành công trước).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OTP_DIR="${REPO_ROOT}/infrastructure/otp"
# shellcheck source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

GRAPH_NAME="${GRAPH_NAME:-metro-l1-assumed}"
GTFS_DIR="${GTFS_DIR:-${REPO_ROOT}/data/assumed/hcmc-metro-l1-gtfs}"
BBOX="${BBOX:-106.675,10.750,106.835,10.900}"
OTP_XMX="${OTP_XMX:-4g}"
OTP_MEM_LIMIT="${OTP_MEM_LIMIT:-6g}"
WORK_DIR="${OTP_DIR}/work/${GRAPH_NAME}"
MANIFEST_DIR="${OTP_DIR}/manifests"
MIN_OSM_BYTES=100000
REQUIRED_GTFS=(agency.txt stops.txt routes.txt trips.txt stop_times.txt)

# --- Hàm riêng của script này (hàm chung nằm trong lib.sh) ---------------
cpu_name() {
  sysctl -n machdep.cpu.brand_string 2>/dev/null \
    || awk -F': ' '/^model name/ {print $2; exit}' /proc/cpuinfo 2>/dev/null \
    || echo UNKNOWN
}
# Cột theo tên header của một file CSV GTFS đơn giản (không có dấu phẩy trong ô có ngoặc kép).
gtfs_field() {
  local file="$1" column="$2"
  [[ -f "${file}" ]] || return 0
  tr -d '\r' <"${file}" | awk -F, -v col="${column}" '
    NR==1 { for (i=1;i<=NF;i++) if ($i==col) idx=i; next }
    NR==2 && idx { print $idx; exit }'
}

# --- Kiểm tra công cụ và tham số ---------------------------------------------
for tool in docker osmium zip unzip awk; do
  command -v "${tool}" >/dev/null 2>&1 || die "thiếu công cụ '${tool}'"
done
command -v sha256sum >/dev/null 2>&1 || command -v shasum >/dev/null 2>&1 || die "thiếu sha256sum/shasum"
command -v md5sum >/dev/null 2>&1 || command -v md5 >/dev/null 2>&1 || die "thiếu md5sum/md5"
docker info >/dev/null 2>&1 || die "Docker daemon chưa chạy"
[[ "${GRAPH_NAME}" =~ ^[A-Za-z0-9._-]+$ ]] || die "GRAPH_NAME chỉ được chứa chữ, số, '.', '_' và '-'"
num='-?[0-9]+(\.[0-9]+)?'
[[ "${BBOX}" =~ ^${num},${num},${num},${num}$ ]] || die "BBOX phải có dạng minlon,minlat,maxlon,maxlat"
awk -F, '{ exit !($1<$3 && $2<$4 && $1>=-180 && $3<=180 && $2>=-90 && $4<=90) }' <<<"${BBOX}" \
  || die "BBOX sai thứ tự hoặc ngoài khoảng (cần minlon<maxlon, minlat<maxlat): ${BBOX}"

# --- Nguồn OSM và GTFS --------------------------------------------------------
if [[ -z "${OSM_SOURCE:-}" ]]; then
  OSM_SOURCE="$(find "${REPO_ROOT}/data/raw/osm" -maxdepth 1 -name 'vietnam-[0-9]*.osm.pbf' 2>/dev/null | sort | tail -n 1)"
fi
[[ -n "${OSM_SOURCE}" && -f "${OSM_SOURCE}" ]] \
  || die "không tìm thấy OSM nguồn; chạy infrastructure/otp/scripts/fetch-osm.sh trước"
if [[ -f "${OSM_SOURCE}.md5" ]]; then
  [[ "$(md5_of "${OSM_SOURCE}")" == "$(awk '{print $1}' "${OSM_SOURCE}.md5")" ]] \
    || die "MD5 của ${OSM_SOURCE} không khớp file .md5"
  log "MD5 nguồn OSM khớp $(basename "${OSM_SOURCE}").md5"
else
  warn "không có ${OSM_SOURCE}.md5; bỏ qua kiểm tra MD5"
fi
for f in "${REQUIRED_GTFS[@]}"; do
  [[ -s "${GTFS_DIR}/${f}" ]] || die "GTFS thiếu hoặc rỗng: ${GTFS_DIR}/${f}"
done
[[ -s "${GTFS_DIR}/calendar.txt" || -s "${GTFS_DIR}/calendar_dates.txt" ]] \
  || die "GTFS cần calendar.txt hoặc calendar_dates.txt trong ${GTFS_DIR}"

# --- Dọn dẹp khi thoát hoặc bị ngắt (Ctrl+C) ---------------------------------
cname="gowander-otp-build-$$"
stage=""
cleanup() {
  docker rm -f "${cname}" >/dev/null 2>&1 || true
  [[ -n "${stage}" ]] && rm -rf "${stage}"
  return 0
}
trap cleanup EXIT
trap 'warn "bị ngắt"; exit 130' INT TERM

# --- Thư mục làm việc (chỉ chứa file sinh ra, bị .gitignore chặn) -------------
rm -rf "${WORK_DIR}"
mkdir -p "${WORK_DIR}" "${MANIFEST_DIR}"

# --- 1. Cắt OSM ---------------------------------------------------------------
OSM_OUT="${WORK_DIR}/hcmc-metro-l1-corridor.osm.pbf"
log "Cắt OSM theo bbox ${BBOX}"
crop_start=$(date +%s)
osmium extract --bbox "${BBOX}" --strategy complete_ways --overwrite \
  --output "${OSM_OUT}" "${OSM_SOURCE}"
crop_seconds=$(( $(date +%s) - crop_start ))
osm_bytes="$(file_size "${OSM_OUT}")"
(( osm_bytes >= MIN_OSM_BYTES )) \
  || die "OSM sau khi cắt chỉ ${osm_bytes} byte (< ${MIN_OSM_BYTES}); bbox có nằm trong vùng của file nguồn không?"

# --- 2. Đóng gói GTFS (zip ổn định: thời gian file cố định, thứ tự cố định) ---
GTFS_OUT="${WORK_DIR}/hcmc-metro-l1-gtfs.zip"
stage="$(mktemp -d)"
find "${GTFS_DIR}" -maxdepth 1 -name '*.txt' ! -name 'README.txt' -exec cp {} "${stage}/" \;
find "${stage}" -type f -exec touch -t 202601010000 {} +
( cd "${stage}" && find . -type f -name '*.txt' | sed 's|^\./||' | LC_ALL=C sort | zip -X -q -@ "${GTFS_OUT}" )
gtfs_files="$(unzip -Z1 "${GTFS_OUT}" | LC_ALL=C sort | tr '\n' ' ')"
log "Đóng gói GTFS: ${gtfs_files}"

# --- 3. Cấu hình --------------------------------------------------------------
cp "${OTP_DIR}/config/build-config.json" "${WORK_DIR}/build-config.json" \
  || die "không chép được config/build-config.json"

# --- 4. Dựng graph ------------------------------------------------------------
# Bọc entrypoint gốc của image (/docker-entrypoint.sh) để sau khi OTP thoát đọc
# memory.peak của cgroup — bộ nhớ đỉnh thật của container, do kernel ghi lại.
BUILD_LOG="${WORK_DIR}/otp-build.log"
GC_LOG="${WORK_DIR}/gc.log"
CGROUP_PEAK="${WORK_DIR}/cgroup-memory-peak.txt"
java_opts="-Xmx${OTP_XMX} -Xlog:gc:file=/var/opentripplanner/gc.log:uptime"
log "Dựng graph bằng ${OTP_IMAGE} (Xmx=${OTP_XMX}, giới hạn container=${OTP_MEM_LIMIT}, không mạng, không root)"
build_start=$(date +%s)
set +e
docker run --rm --name "${cname}" \
  --user "$(id -u):$(id -g)" \
  --network none \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --memory "${OTP_MEM_LIMIT}" \
  -e JAVA_OPTS="${java_opts}" \
  -v "${WORK_DIR}:/var/opentripplanner" \
  --entrypoint bash \
  "${OTP_IMAGE}" \
  -c '/docker-entrypoint.sh "$@"; rc=$?; cat /sys/fs/cgroup/memory.peak >/var/opentripplanner/cgroup-memory-peak.txt 2>/dev/null; exit $rc' \
  otp --build --save --abortOnUnknownConfig >"${BUILD_LOG}" 2>&1 &
# Chạy nền rồi wait: wait bị tín hiệu (TERM/INT) cắt ngang ngay, nên trap dọn container chạy kịp.
docker_pid=$!
wait "${docker_pid}"
otp_exit=$?
set -e
build_seconds=$(( $(date +%s) - build_start ))

# Bộ nhớ đỉnh của container (MiB) từ cgroup v2; NA nếu không đọc được.
peak_mib="NA"
if [[ -s "${CGROUP_PEAK}" ]] && [[ "$(cat "${CGROUP_PEAK}")" =~ ^[0-9]+$ ]]; then
  peak_mib=$(( $(cat "${CGROUP_PEAK}") / 1048576 ))
else
  warn "không đọc được memory.peak của cgroup; peak_container_mem_mib=NA"
fi
# Heap JVM lớn nhất quan sát được tại các lần GC (giá trị "trước" trong "123M->45M(354M)").
heap_peak_mib="NA"
gc_events=0
if [[ -s "${GC_LOG}" ]]; then
  gc_events="$(grep -c 'Pause' "${GC_LOG}" || true)"
  heap_peak_mib="$(grep -oE '[0-9]+M->' "${GC_LOG}" | tr -d 'M->' | sort -n | tail -n 1)"
  [[ -n "${heap_peak_mib}" ]] || heap_peak_mib="NA"
fi
[[ "${heap_peak_mib}" != "NA" ]] || warn "không đọc được heap đỉnh từ GC log"

[[ -s "${BUILD_LOG}" ]] || warn "log OTP rỗng: ${BUILD_LOG}"
# Định dạng log OTP 2.10.0: "HH:MM:SS.mmm LEVEL [thread] ..."
warn_count="$(grep -Ec '^[0-9:.]+ WARN ' "${BUILD_LOG}" || true)"
error_count="$(grep -Ec '^[0-9:.]+ ERROR ' "${BUILD_LOG}" || true)"
oom_count="$(grep -c 'OutOfMemoryError' "${BUILD_LOG}" || true)"

GRAPH_OBJ="${WORK_DIR}/graph.obj"
status="ok"
if [[ "${otp_exit}" -ne 0 || ! -s "${GRAPH_OBJ}" ]]; then status="failed"; fi

# --- 5. Manifest --------------------------------------------------------------
otp_version="$(grep -m1 -oE 'Version: [0-9][^,]*' "${BUILD_LOG}" | sed 's/^Version: //' || true)"
MANIFEST="${WORK_DIR}/build-manifest.txt"
{
  echo "# Manifest dựng graph OTP — sinh bởi infrastructure/otp/scripts/build-graph.sh"
  echo "status=${status}"
  echo "graph_name=${GRAPH_NAME}"
  echo "built_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "otp_image=${OTP_IMAGE}"
  echo "otp_version=$(or_unknown otp_version "${otp_version}")"
  echo "osmium_version=$(or_unknown osmium_version "$(osmium --version 2>/dev/null | head -n 1 | sed 's/^osmium version //')")"
  echo "host=$(uname -sm) $(cpu_name)"
  echo "docker_engine=$(or_unknown docker_engine "$(docker version --format '{{.Server.Version}}' 2>/dev/null)")"
  echo "docker_mem_total_bytes=$(or_unknown docker_mem_total "$(docker info --format '{{.MemTotal}}' 2>/dev/null)")"
  echo "container_flags=--user $(id -u):$(id -g) --network none --cap-drop ALL --security-opt no-new-privileges --memory ${OTP_MEM_LIMIT}"
  echo "java_opts=-Xmx${OTP_XMX}"
  echo "osm_source=$(basename "${OSM_SOURCE}")"
  echo "osm_source_sha256=$(sha256_of "${OSM_SOURCE}")"
  echo "osm_bbox=${BBOX}"
  echo "osm_corridor_sha256=$(sha256_of "${OSM_OUT}")"
  echo "osm_corridor_bytes=${osm_bytes}"
  echo "gtfs_dir=${GTFS_DIR#"${REPO_ROOT}/"}"
  echo "gtfs_feed_version=$(or_unknown gtfs_feed_version "$(gtfs_field "${GTFS_DIR}/feed_info.txt" feed_version)")"
  echo "gtfs_files=${gtfs_files% }"
  echo "gtfs_zip_sha256=$(sha256_of "${GTFS_OUT}")"
  echo "build_config_sha256=$(sha256_of "${WORK_DIR}/build-config.json")"
  echo "crop_seconds=${crop_seconds}"
  echo "build_seconds=${build_seconds}"
  echo "otp_exit_code=${otp_exit}"
  echo "peak_container_mem_mib=${peak_mib}"
  echo "peak_jvm_heap_at_gc_mib=${heap_peak_mib}"
  echo "gc_pause_events=${gc_events}"
  echo "log_warn_lines=${warn_count}"
  echo "log_error_lines=${error_count}"
  echo "log_out_of_memory_lines=${oom_count}"
  if [[ -s "${GRAPH_OBJ}" ]]; then
    echo "graph_obj_bytes=$(file_size "${GRAPH_OBJ}")"
    echo "graph_obj_sha256=$(sha256_of "${GRAPH_OBJ}")"
  else
    echo "graph_obj_bytes=missing"
  fi
} >"${MANIFEST}"

if [[ "${status}" == "ok" ]]; then
  cp "${MANIFEST}" "${MANIFEST_DIR}/${GRAPH_NAME}.txt"
  manifest_out="${MANIFEST_DIR}/${GRAPH_NAME}.txt"
else
  cp "${MANIFEST}" "${MANIFEST_DIR}/${GRAPH_NAME}.failed.txt"
  manifest_out="${MANIFEST_DIR}/${GRAPH_NAME}.failed.txt"
fi

log "Thời gian dựng ${build_seconds}s, bộ nhớ đỉnh container ${peak_mib} MiB, heap JVM đỉnh ${heap_peak_mib} MiB, WARN=${warn_count}, ERROR=${error_count}"
log "Manifest: ${manifest_out#"${REPO_ROOT}/"}; log: ${BUILD_LOG#"${REPO_ROOT}/"}"
if [[ "${status}" != "ok" ]]; then
  tail -n 30 "${BUILD_LOG}" >&2
  if [[ "${otp_exit}" -eq 137 || "${oom_count}" -gt 0 ]]; then
    warn "có dấu hiệu hết bộ nhớ (mã thoát ${otp_exit}, OutOfMemoryError=${oom_count}); tăng OTP_MEM_LIMIT/OTP_XMX"
  fi
  die "OTP dựng graph thất bại (mã thoát ${otp_exit})"
fi
log "OK: ${GRAPH_OBJ#"${REPO_ROOT}/"}"
