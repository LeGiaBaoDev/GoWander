-- Wards and communes of Ho Chi Minh City (owner, 16-11-2026): the Admin's "Khách đi đâu" map colours each ward by the
-- searches ending in it. Shapes only, from OSM (ODbL), loaded from data/areas/hcm-wards.sql; counting stays on the
-- ~500 m destination cells (usage_dest_cells), which the map groups by ward.
CREATE TABLE city_wards (
  code    VARCHAR(20) PRIMARY KEY,
  name    VARCHAR(150) NOT NULL CHECK (btrim(name) <> ''),
  geom    geometry(MultiPolygon, 4326) NOT NULL,
  source  VARCHAR(20) NOT NULL DEFAULT 'OSM',
  license VARCHAR(50) NOT NULL DEFAULT 'ODbL-1.0'
);
CREATE INDEX city_wards_geom_idx ON city_wards USING gist (geom);

GRANT SELECT ON city_wards TO gowander_admin;
