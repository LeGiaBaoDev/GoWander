#!/usr/bin/env bash
# Fresh, disposable PostGIS + Mailpit; never reads .env or touches the dev volume.
set -euo pipefail
ci_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
ci_project="gowander-ci-$$"
ci_compose=(docker compose --env-file /dev/null --project-name "$ci_project" -f "$ci_root/scripts/ci/compose.yaml")
cleanup() {
  ci_result=$?
  trap - EXIT
  if (( ci_result != 0 )); then
    "${ci_compose[@]}" logs --no-color || true
  fi
  # Only this invocation's project; tmpfs data disappears with these containers.
  if ! "${ci_compose[@]}" down --volumes --remove-orphans; then
    echo "CI dependency cleanup failed for $ci_project" >&2
    ci_result=1
  fi
  exit "$ci_result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"${ci_compose[@]}" up -d --wait --wait-timeout 180
export MIGRATE_DATABASE_URL="postgres://gowander_owner:ci-owner-only@127.0.0.1:${CI_DB_PORT:-55432}/gowander_ci_test?sslmode=disable"
export DATABASE_URL="postgres://gowander_app:ci-app-only@127.0.0.1:${CI_DB_PORT:-55432}/gowander_ci_test?sslmode=disable"
export ADMIN_DATABASE_URL="postgres://gowander_admin:ci-admin-only@127.0.0.1:${CI_DB_PORT:-55432}/gowander_ci_test?sslmode=disable"
export MAILPIT_URL=http://127.0.0.1:8025
cd "$ci_root/backend"
mkdir -p tmp/ci
# OTP is a separate gate: ./cmd/api TestRouteCases needs a verified graph.
go test -tags integration -count=1 -p 1 -json ./internal/... | tee tmp/ci/integration.jsonl
