#!/usr/bin/env bash
# Tải bản trích xuất OSM Việt Nam có ghi ngày của Geofabrik (ODbL 1.0) và kiểm tra MD5.
# File thô nằm ở data/raw/osm/ (đã bị .gitignore chặn), không đưa vào Git.
#
# Cách dùng: infrastructure/otp/scripts/fetch-osm.sh [YYMMDD]
#   Không truyền ngày: lấy bản mà vietnam-latest.osm.pbf đang trỏ tới.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OSM_DIR="${REPO_ROOT}/data/raw/osm"
BASE_URL="https://download.geofabrik.de/asia"

stamp="${1:-}"
if [[ -z "${stamp}" ]]; then
  location="$(curl -fsSI "${BASE_URL}/vietnam-latest.osm.pbf" | tr -d '\r' | awk 'tolower($1)=="location:" {print $2}')"
  stamp="$(basename "${location}" | sed -E 's/^vietnam-([0-9]{6})\.osm\.pbf$/\1/')"
  if [[ ! "${stamp}" =~ ^[0-9]{6}$ ]]; then
    echo "Không xác định được ngày của bản vietnam-latest (Location: '${location}')." >&2
    exit 1
  fi
fi
if [[ ! "${stamp}" =~ ^[0-9]{6}$ ]]; then
  echo "Ngày phải có dạng YYMMDD, nhận được '${stamp}'." >&2
  exit 2
fi

file="vietnam-${stamp}.osm.pbf"
mkdir -p "${OSM_DIR}"
cd "${OSM_DIR}"

curl -fsSL -o "${file}.md5" "${BASE_URL}/${file}.md5"
expected="$(awk '{print $1}' "${file}.md5")"

if [[ -f "${file}" ]] && [[ "$(md5 -r "${file}" | awk '{print $1}')" == "${expected}" ]]; then
  echo "Đã có ${file}, MD5 khớp; bỏ qua tải lại."
else
  curl -fSL --retry 3 -o "${file}.part" "${BASE_URL}/${file}"
  mv "${file}.part" "${file}"
fi

actual="$(md5 -r "${file}" | awk '{print $1}')"
if [[ "${expected}" != "${actual}" ]]; then
  echo "MD5 không khớp cho ${file}: mong đợi ${expected}, thực tế ${actual}." >&2
  exit 1
fi
echo "OK ${OSM_DIR}/${file} md5=${actual}"
