#!/bin/bash
# Runs once as the postgres superuser when the data volume is created (ADR-028 §2).
# The only place that needs superuser: extensions, the two application roles, and the test database.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username postgres --dbname "$POSTGRES_DB" \
  -v owner_pw="$GOWANDER_OWNER_PASSWORD" -v app_pw="$GOWANDER_APP_PASSWORD" -v test_db="${POSTGRES_DB}_test" <<'SQL'
CREATE ROLE gowander_owner LOGIN PASSWORD :'owner_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE gowander_app   LOGIN PASSWORD :'app_pw'   NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE gowander_owner SET search_path = gowander, public;
ALTER ROLE gowander_app   SET search_path = gowander, public;
ALTER ROLE gowander_app   SET statement_timeout = '5s';
-- Integration tests only ever touch this database (backend/README.md).
CREATE DATABASE :"test_db";
SQL

# Same layout in the development and the test database.
for db in "$POSTGRES_DB" "${POSTGRES_DB}_test"; do
  psql -v ON_ERROR_STOP=1 --username postgres --dbname "$db" -v db="$db" <<'SQL'
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- name search index
CREATE EXTENSION IF NOT EXISTS unaccent;  -- "ben thanh" finds "Bến Thành"

REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO gowander_owner, gowander_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA gowander AUTHORIZATION gowander_owner;
GRANT USAGE ON SCHEMA gowander TO gowander_app;
-- No default privileges: each migration GRANTs exactly the tables the public API may read (ADR-028 §2),
-- so future admin or user tables are never readable by the runtime role by accident.
SQL
done
