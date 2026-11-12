#!/usr/bin/env bash
# Dựng graph OpenTripPlanner 2.10.0 cho PoC M02, chạy lại được từ đầu.
#
# Các bước:
#   1. Cắt OSM Việt Nam theo khung hành lang Metro số 1 bằng osmium.
#   2. Đóng gói feed GTFS giả định thành zip có checksum ổn định.
#   3. Chép build-config.json vào thư mục làm việc.
#   4. Chạy OTP (image khoá theo digest, ADR-023) với --build --save, đo thời gian và bộ nhớ.
#   5. Ghi manifest: phiên bản, checksum đầu vào/đầu ra, thời gian, bộ nhớ đỉnh, số WARN/ERROR.
#
# Cách dùng: infrastructure/otp/scripts/build-graph.sh
# Biến môi trường (tuỳ chọn):
#   OSM_SOURCE     file .osm.pbf nguồn (mặc định: bản vietnam-*.osm.pbf mới nhất trong data/raw/osm/)
#   GTFS_DIR       thư mục GTFS (mặc định: data/assumed/hcmc-metro-l1-gtfs)
#   BBOX           minlon,minlat,maxlon,maxlat (mặc định: hành lang Metro số 1 + khoảng 2 km)
#   GRAPH_NAME     tên thư mục làm việc dưới infrastructure/otp/work/ (mặc định: metro-l1-assumed)
#   OTP_XMX        heap tối đa của JVM (mặc định: 4g)
#   OTP_MEM_LIMIT  giới hạn bộ nhớ container (mặc định: 6g)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OTP_DIR="${REPO_ROOT}/infrastructure/otp"

OTP_IMAGE="opentripplanner/opentripplanner@sha256:8d54e5c589186707ee365417f2202dc878c451fa3001b8edff07019531100933"
GRAPH_NAME="${GRAPH_NAME:-metro-l1-assumed}"
GTFS_DIR="${GTFS_DIR:-${REPO_ROOT}/data/assumed/hcmc-metro-l1-gtfs}"
BBOX="${BBOX:-106.675,10.750,106.835,10.900}"
OTP_XMX="${OTP_XMX:-4g}"
OTP_MEM_LIMIT="${OTP_MEM_LIMIT:-6g}"
WORK_DIR="${OTP_DIR}/work/${GRAPH_NAME}"
MANIFEST_DIR="${OTP_DIR}/manifests"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')" "$*"; }
die() { log "LỖI: $*" >&2; exit 1; }
sha256() { shasum -a 256 "$1" | awk '{print $1}'; }

# --- Kiểm tra công cụ ---------------------------------------------------------
for tool in docker osmium zip shasum md5 awk; do
  command -v "${tool}" >/dev/null 2>&1 || die "thiếu công cụ '${tool}'"
done
docker info >/dev/null 2>&1 || die "Docker daemon chưa chạy"
[[ "${GRAPH_NAME}" =~ ^[A-Za-z0-9._-]+$ ]] || die "GRAPH_NAME chỉ được chứa chữ, số, '.', '_' và '-'"
[[ "${BBOX}" =~ ^[0-9.]+,[0-9.]+,[0-9.]+,[0-9.]+$ ]] || die "BBOX phải có dạng minlon,minlat,maxlon,maxlat"

# --- Nguồn OSM ----------------------------------------------------------------
if [[ -z "${OSM_SOURCE:-}" ]]; then
  OSM_SOURCE="$(find "${REPO_ROOT}/data/raw/osm" -maxdepth 1 -name 'vietnam-[0-9]*.osm.pbf' 2>/dev/null | sort | tail -n 1)"
fi
[[ -n "${OSM_SOURCE}" && -f "${OSM_SOURCE}" ]] \
  || die "không tìm thấy OSM nguồn; chạy infrastructure/otp/scripts/fetch-osm.sh trước"
if [[ -f "${OSM_SOURCE}.md5" ]]; then
  [[ "$(md5 -r "${OSM_SOURCE}" | awk '{print $1}')" == "$(awk '{print $1}' "${OSM_SOURCE}.md5")" ]] \
    || die "MD5 của ${OSM_SOURCE} không khớp file .md5"
  log "MD5 nguồn OSM khớp $(basename "${OSM_SOURCE}").md5"
fi
[[ -f "${GTFS_DIR}/stops.txt" ]] || die "không thấy ${GTFS_DIR}/stops.txt"

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

# --- 2. Đóng gói GTFS (zip ổn định: thời gian file cố định, thứ tự cố định) ---
GTFS_OUT="${WORK_DIR}/hcmc-metro-l1-gtfs.zip"
stage="$(mktemp -d)"
trap 'rm -rf "${stage}"' EXIT
find "${GTFS_DIR}" -maxdepth 1 -name '*.txt' ! -name 'README.txt' -exec cp {} "${stage}/" \;
find "${stage}" -type f -exec touch -t 202601010000 {} +
( cd "${stage}" && find . -type f -name '*.txt' | sed 's|^\./||' | LC_ALL=C sort | zip -X -q -@ "${GTFS_OUT}" )
log "Đóng gói GTFS: $(unzip -Z1 "${GTFS_OUT}" | tr '\n' ' ')"

# --- 3. Cấu hình --------------------------------------------------------------
cp "${OTP_DIR}/config/build-config.json" "${WORK_DIR}/build-config.json"

# --- 4. Dựng graph ------------------------------------------------------------
BUILD_LOG="${WORK_DIR}/otp-build.log"
MEM_SAMPLES="${WORK_DIR}/memory-samples.txt"
cname="gowander-otp-build-$$"
log "Dựng graph bằng ${OTP_IMAGE} (Xmx=${OTP_XMX}, giới hạn container=${OTP_MEM_LIMIT})"
build_start=$(date +%s)
docker run --rm --name "${cname}" \
  --memory "${OTP_MEM_LIMIT}" \
  -e JAVA_OPTS="-Xmx${OTP_XMX}" \
  -v "${WORK_DIR}:/var/opentripplanner" \
  "${OTP_IMAGE}" --build --save --abortOnUnknownConfig >"${BUILD_LOG}" 2>&1 &
docker_pid=$!

: >"${MEM_SAMPLES}"
while kill -0 "${docker_pid}" 2>/dev/null; do
  docker stats --no-stream --format '{{.MemUsage}}' "${cname}" 2>/dev/null >>"${MEM_SAMPLES}" || true
  sleep 1
done
set +e
wait "${docker_pid}"
otp_exit=$?
set -e
build_seconds=$(( $(date +%s) - build_start ))

# Bộ nhớ đỉnh của container (MiB), lấy từ các mẫu "1.23GiB / 6GiB" của docker stats.
peak_mib="$(awk '
  {
    v=$1; n=v; sub(/[A-Za-z]+$/, "", n); u=v; sub(/^[0-9.]+/, "", u);
    m = (u=="GiB") ? n*1024 : (u=="MiB") ? n : (u=="KiB") ? n/1024 : (u=="B") ? n/1048576 : 0;
    if (m>peak) peak=m
  }
  END { printf "%.0f", peak }' "${MEM_SAMPLES}")"
mem_sample_count="$(grep -c . "${MEM_SAMPLES}" || true)"
warn_count="$(grep -c ' WARN ' "${BUILD_LOG}" || true)"
error_count="$(grep -c ' ERROR ' "${BUILD_LOG}" || true)"

# --- 5. Manifest --------------------------------------------------------------
GRAPH_OBJ="${WORK_DIR}/graph.obj"
MANIFEST="${WORK_DIR}/build-manifest.txt"
{
  echo "# Manifest dựng graph OTP — sinh bởi infrastructure/otp/scripts/build-graph.sh"
  echo "graph_name=${GRAPH_NAME}"
  echo "built_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "otp_image=${OTP_IMAGE}"
  echo "otp_version=$(docker run --rm "${OTP_IMAGE}" --version 2>/dev/null | head -n 1)"
  echo "osmium_version=$(osmium --version | head -n 1)"
  echo "host=$(uname -sm) $(sysctl -n machdep.cpu.brand_string 2>/dev/null || true)"
  echo "docker_engine=$(docker version --format '{{.Server.Version}}')"
  echo "docker_mem_total=$(docker info --format '{{.MemTotal}}')"
  echo "java_opts=-Xmx${OTP_XMX}"
  echo "container_mem_limit=${OTP_MEM_LIMIT}"
  echo "osm_source=$(basename "${OSM_SOURCE}")"
  echo "osm_source_sha256=$(sha256 "${OSM_SOURCE}")"
  echo "osm_bbox=${BBOX}"
  echo "osm_corridor_sha256=$(sha256 "${OSM_OUT}")"
  echo "osm_corridor_bytes=$(stat -f %z "${OSM_OUT}")"
  echo "gtfs_dir=${GTFS_DIR#"${REPO_ROOT}/"}"
  echo "gtfs_feed_version=$(awk -F, 'NR==2 {print $NF}' "${GTFS_DIR}/feed_info.txt" 2>/dev/null || true)"
  echo "gtfs_zip_sha256=$(sha256 "${GTFS_OUT}")"
  echo "build_config_sha256=$(sha256 "${WORK_DIR}/build-config.json")"
  echo "crop_seconds=${crop_seconds}"
  echo "build_seconds=${build_seconds}"
  echo "otp_exit_code=${otp_exit}"
  echo "peak_container_mem_mib=${peak_mib}"
  echo "mem_samples=${mem_sample_count}"
  echo "log_warn_lines=${warn_count}"
  echo "log_error_lines=${error_count}"
  if [[ -f "${GRAPH_OBJ}" ]]; then
    echo "graph_obj_bytes=$(stat -f %z "${GRAPH_OBJ}")"
    echo "graph_obj_sha256=$(sha256 "${GRAPH_OBJ}")"
  else
    echo "graph_obj_bytes=missing"
  fi
} >"${MANIFEST}"
cp "${MANIFEST}" "${MANIFEST_DIR}/${GRAPH_NAME}.txt"

log "Thời gian dựng ${build_seconds}s, bộ nhớ đỉnh ${peak_mib} MiB, WARN=${warn_count}, ERROR=${error_count}"
log "Manifest: ${MANIFEST_DIR#"${REPO_ROOT}/"}/${GRAPH_NAME}.txt; log: ${BUILD_LOG#"${REPO_ROOT}/"}"
if [[ "${otp_exit}" -ne 0 || ! -f "${GRAPH_OBJ}" ]]; then
  tail -n 30 "${BUILD_LOG}" >&2
  [[ "${otp_exit}" -eq 137 ]] && log "Mã thoát 137: có thể container bị giết vì hết bộ nhớ (tăng OTP_MEM_LIMIT/OTP_XMX)." >&2
  die "OTP dựng graph thất bại (mã thoát ${otp_exit})"
fi
log "OK: ${GRAPH_OBJ#"${REPO_ROOT}/"}"
