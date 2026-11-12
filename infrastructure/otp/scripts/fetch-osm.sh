#!/usr/bin/env bash
# Tải bản trích xuất OSM Việt Nam có ghi ngày của Geofabrik (ODbL 1.0) và kiểm tra MD5.
# File thô nằm ở data/raw/osm/ (đã bị .gitignore chặn), không đưa vào Git.
#
# Cách dùng: infrastructure/otp/scripts/fetch-osm.sh [YYMMDD]
#   Không truyền ngày: lấy bản mà vietnam-latest.osm.pbf đang trỏ tới.
#
# Lưu ý: file .md5 lấy cùng máy chủ với file dữ liệu, nên MD5 chỉ phát hiện file hỏng/tải dở,
# không chứng minh nguồn gốc.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OSM_DIR="${REPO_ROOT}/data/raw/osm"
BASE_URL="https://download.geofabrik.de/asia"
# Hết 20 s không kết nối được, hoặc tốc độ < 10 kB/s trong 60 s, thì dừng thay vì treo.
CURL_OPTS=(--fail --show-error --connect-timeout 20 --speed-limit 10000 --speed-time 60)

# shellcheck source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

stamp="${1:-}"
if [[ -z "${stamp}" ]]; then
  headers="$(curl "${CURL_OPTS[@]}" --silent --head "${BASE_URL}/vietnam-latest.osm.pbf")" \
    || die "không lấy được header của ${BASE_URL}/vietnam-latest.osm.pbf"
  location="$(tr -d '\r' <<<"${headers}" | awk 'tolower($1)=="location:" {print $2; exit}')"
  stamp="$(basename "${location:-none}" | sed -E 's/^vietnam-([0-9]{6})\.osm\.pbf$/\1/')"
  [[ "${stamp}" =~ ^[0-9]{6}$ ]] \
    || die "không xác định được ngày của bản vietnam-latest (Location: '${location:-}')"
fi
[[ "${stamp}" =~ ^[0-9]{6}$ ]] || { echo "Ngày phải có dạng YYMMDD, nhận được '${stamp}'." >&2; exit 2; }

file="vietnam-${stamp}.osm.pbf"
mkdir -p "${OSM_DIR}"
cd "${OSM_DIR}"

curl "${CURL_OPTS[@]}" --location --silent -o "${file}.md5.part" "${BASE_URL}/${file}.md5" \
  || die "không tải được ${file}.md5 (bản ${stamp} có tồn tại trên Geofabrik không?)"
expected="$(awk 'NR==1 {print $1}' "${file}.md5.part")"
[[ "${expected}" =~ ^[0-9a-f]{32}$ ]] || { rm -f "${file}.md5.part"; die "file ${file}.md5 không chứa MD5 hợp lệ"; }
mv "${file}.md5.part" "${file}.md5"

if [[ -f "${file}" ]] && [[ "$(md5_of "${file}")" == "${expected}" ]]; then
  echo "Đã có ${file}, MD5 khớp; bỏ qua tải lại."
else
  curl "${CURL_OPTS[@]}" --location --retry 3 -o "${file}.part" "${BASE_URL}/${file}" \
    || { rm -f "${file}.part"; die "tải ${file} thất bại"; }
  mv "${file}.part" "${file}"
fi

actual="$(md5_of "${file}")"
[[ "${expected}" == "${actual}" ]] || die "MD5 không khớp cho ${file}: mong đợi ${expected}, thực tế ${actual}"
echo "OK ${OSM_DIR}/${file} md5=${actual}"
