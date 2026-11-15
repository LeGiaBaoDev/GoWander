#!/bin/bash
# Runs once as the postgres superuser when the data volume is created (ADR-037, M07).
# The Admin pages write content and admin tables; the public API keeps the read-only role gowander_app.
# Existing volumes: run `./db.sh admin-role` once instead (same SQL).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username postgres --dbname "$POSTGRES_DB" -v admin_pw="$GOWANDER_ADMIN_PASSWORD" <<'SQL'
CREATE ROLE gowander_admin LOGIN PASSWORD :'admin_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE gowander_admin SET search_path = gowander, public;
ALTER ROLE gowander_admin SET statement_timeout = '10s';
SQL
for db in "$POSTGRES_DB" "${POSTGRES_DB}_test"; do
  psql -v ON_ERROR_STOP=1 --username postgres --dbname "$db" -v db="$db" <<'SQL'
GRANT CONNECT ON DATABASE :"db" TO gowander_admin;
GRANT USAGE ON SCHEMA gowander TO gowander_admin;
-- Table privileges come from the migrations (GRANT … TO gowander_admin), like for gowander_app.
SQL
done
