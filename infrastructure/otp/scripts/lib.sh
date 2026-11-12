# Hàm dùng chung cho các script trong infrastructure/otp/scripts/ (nạp bằng `source`).
# Chạy được trên cả macOS (BSD) và Linux (GNU).
# shellcheck shell=bash

log()  { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')" "$*"; }
warn() { log "CẢNH BÁO: $*" >&2; }
die()  { log "LỖI: $*" >&2; exit 1; }

file_size() { stat -f %z "$1" 2>/dev/null || stat -c %s "$1"; }
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}
md5_of() {
  if command -v md5sum >/dev/null 2>&1; then md5sum "$1" | awk '{print $1}'
  else md5 -r "$1" | awk '{print $1}'; fi
}

# Giá trị rỗng thì ghi UNKNOWN và cảnh báo, không để chuỗi rỗng lọt vào manifest.
or_unknown() {
  local name="$1" value="$2"
  if [[ -z "${value}" ]]; then warn "không đọc được ${name}"; echo UNKNOWN; else echo "${value}"; fi
}

# Image OTP 2.10.0 khoá theo digest (ADR-023). Đổi phiên bản: sửa ở đây và cập nhật ADR-023.
# shellcheck disable=SC2034  # dùng trong build-graph.sh và serve.sh sau khi source
OTP_IMAGE="opentripplanner/opentripplanner@sha256:8d54e5c589186707ee365417f2202dc878c451fa3001b8edff07019531100933"
