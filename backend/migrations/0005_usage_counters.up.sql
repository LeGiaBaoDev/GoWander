-- Usage counters (ADR-038): daily aggregates only. No coordinates, IPs or device ids are ever stored.

-- Wards/communes of Ho Chi Minh City (commune level after the 7-2025 merger), from OSM (ODbL). Loaded by
-- scripts/build_area_seed.py; code = OSM relation, e.g. 'r1234'.
CREATE TABLE admin_areas (
  code    VARCHAR(20) PRIMARY KEY,
  name    VARCHAR(150) NOT NULL CHECK (btrim(name) <> ''),
  geom    geometry(MultiPolygon, 4326) NOT NULL,
  source  VARCHAR(20) NOT NULL DEFAULT 'OSM',
  license VARCHAR(50) NOT NULL DEFAULT 'ODbL-1.0'
);
CREATE INDEX admin_areas_geom_idx ON admin_areas USING gist (geom);

-- area_code '' = outside every known ward. FROM and TO are counted separately on purpose: an origin-destination pair
-- repeated daily is someone's home and workplace.
CREATE TABLE usage_route_plans (
  day       DATE NOT NULL,
  mode      VARCHAR(10) NOT NULL CHECK (mode IN ('WALK', 'TRANSIT', 'BUS', 'CAR', 'BICYCLE')),
  side      VARCHAR(4) NOT NULL CHECK (side IN ('FROM', 'TO')),
  area_code VARCHAR(20) NOT NULL DEFAULT '',
  plans     INTEGER NOT NULL CHECK (plans > 0),
  PRIMARY KEY (day, mode, side, area_code)
);

-- A line counts once per successful search that suggested it; area = origin ward.
CREATE TABLE usage_bus_lines (
  day       DATE NOT NULL,
  line_id   VARCHAR(100) NOT NULL,
  line_name VARCHAR(200) NOT NULL,
  area_code VARCHAR(20) NOT NULL DEFAULT '',
  suggested INTEGER NOT NULL CHECK (suggested > 0),
  PRIMARY KEY (day, line_id, area_code)
);

-- Searches whose destination is within 30 m of a published place. No FK: counters must not block deleting a place.
CREATE TABLE usage_place_routes (
  day      DATE NOT NULL,
  place_id UUID NOT NULL,
  plans    INTEGER NOT NULL CHECK (plans > 0),
  PRIMARY KEY (day, place_id)
);

-- The public API writes the counters (and deletes rows past the 25-month retention); content stays read-only for it.
GRANT SELECT ON admin_areas TO gowander_app, gowander_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON usage_route_plans, usage_bus_lines, usage_place_routes TO gowander_app;
GRANT SELECT ON usage_route_plans, usage_bus_lines, usage_place_routes TO gowander_admin;
