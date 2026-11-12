#!/usr/bin/env bash
# Local PostgreSQL/PostGIS for GoWander (ADR-028).
#   ./db.sh up        start (creates the volume and runs init/ on first start), wait until healthy
#   ./db.sh status    container state
#   ./db.sh psql [owner|app|postgres]   open psql inside the container (default: app)
#   ./db.sh seed FILE load a seed SQL file as the owner role (run migrations first)
#   ./db.sh down      stop, keep data
#   ./db.sh reset --yes-delete-data     stop and DELETE the volume (all local data)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
[[ -f "$ENV_FILE" ]] || { echo "Thiếu $ENV_FILE — chép từ .env.example rồi đặt mật khẩu" >&2; exit 1; }
dc() { docker compose -f "$HERE/compose.yaml" --env-file "$ENV_FILE" "$@"; }
db_name() { grep -E '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2-; }

case "${1:-}" in
  up)
    dc up -d
    for _ in $(seq 1 60); do
      port="$(grep -E '^POSTGRES_PORT=' "$ENV_FILE" | cut -d= -f2- || true)"
      [[ "$(docker inspect -f '{{.State.Health.Status}}' gowander-postgres 2>/dev/null)" == healthy ]] && { echo "PostgreSQL sẵn sàng tại 127.0.0.1:${port:-5432}"; exit 0; }
      sleep 2
    done
    echo "PostgreSQL chưa healthy sau 120 s; xem: docker logs gowander-postgres" >&2; exit 1 ;;
  status) dc ps ;;
  psql)
    case "${2:-app}" in
      owner) user=gowander_owner ;; app) user=gowander_app ;; postgres) user=postgres ;;
      *) echo "role: owner | app | postgres" >&2; exit 2 ;;
    esac
    shift $(( $# >= 2 ? 2 : 1 ))
    exec docker exec -it gowander-postgres psql -U "$user" -d "$(db_name)" "$@" ;;
  seed)
    [[ -f "${2:-}" ]] || { echo "dùng: ./db.sh seed data/discover/seed.sql" >&2; exit 2; }
    docker exec -i gowander-postgres psql -q -v ON_ERROR_STOP=1 -U gowander_owner -d "$(db_name)" < "$2"
    echo "Đã nạp $2" ;;
  down) dc down ;;
  reset)
    [[ "${2:-}" == --yes-delete-data ]] || { echo "Xoá toàn bộ dữ liệu local: chạy ./db.sh reset --yes-delete-data" >&2; exit 2; }
    dc down -v ;;
  *) sed -n '2,9p' "$0" >&2; exit 2 ;;
esac
