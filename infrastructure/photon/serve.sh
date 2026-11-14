#!/usr/bin/env bash
# Self-hosted Photon geocoder for GoWander (address search, OSM data ODbL). See README.md.
#   ./serve.sh start | status | stop
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
W="$HERE/work"
VERSION=0.7.4
JAR="$W/photon-$VERSION.jar"
JAR_SHA256=5cf6effb274504c1d2d088efe7c7a10fbd1e30de3215a260451f68cc57186601   # GitHub release komoot/photon 0.7.4, fetched 14-11-2026
DB=photon-db-vn-250720.tar.bz2
DB_MD5=01fe9f1e111556204b4799830c55f936                                        # graphhopper.com extract, 21-07-2025
PORT="${PHOTON_PORT:-2322}"
PIDFILE="$W/photon.pid"

fetch() {
  mkdir -p "$W"
  [[ -f "$JAR" ]] || curl -sfL -o "$JAR" "https://github.com/komoot/photon/releases/download/$VERSION/photon-$VERSION.jar"
  [[ "$(shasum -a 256 "$JAR" | cut -d' ' -f1)" == "$JAR_SHA256" ]] || { echo "SHA-256 của $JAR không khớp — dừng" >&2; exit 1; }
  if [[ ! -d "$W/photon_data" ]]; then
    [[ -f "$W/$DB" ]] || curl -sfL -o "$W/$DB" "https://download1.graphhopper.com/public/extracts/by-country-code/vn/$DB"
    [[ "$(md5 -q "$W/$DB" 2>/dev/null || md5sum "$W/$DB" | cut -d' ' -f1)" == "$DB_MD5" ]] || { echo "MD5 của $DB không khớp — dừng" >&2; exit 1; }
    tar -xjf "$W/$DB" -C "$W"
  fi
}

case "${1:-}" in
  start)
    fetch
    if [[ -f "$PIDFILE" ]] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then echo "Photon đang chạy (pid $(cat "$PIDFILE"))"; exit 0; fi
    (cd "$W" && nohup java -Xmx1g -jar "$JAR" -data-dir "$W" -listen-ip 127.0.0.1 -listen-port "$PORT" > "$W/photon.log" 2>&1 & echo $! > "$PIDFILE")
    for _ in $(seq 1 60); do
      curl -sf "http://127.0.0.1:$PORT/api?q=ben+thanh&limit=1" >/dev/null && { echo "Photon sẵn sàng: http://127.0.0.1:$PORT"; exit 0; }
      sleep 2
    done
    echo "Photon chưa trả lời sau 120 s; xem $W/photon.log" >&2; exit 1 ;;
  status) curl -sf -o /dev/null -w "Photon HTTP %{http_code}\n" "http://127.0.0.1:$PORT/api?q=ben+thanh&limit=1" || echo "Photon không chạy" ;;
  stop) [[ -f "$PIDFILE" ]] && kill "$(cat "$PIDFILE")" 2>/dev/null; rm -f "$PIDFILE"; echo "Đã dừng Photon" ;;
  *) sed -n '2,3p' "$0" >&2; exit 2 ;;
esac
