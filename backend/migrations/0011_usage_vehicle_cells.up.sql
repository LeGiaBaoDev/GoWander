-- ADR-038 update (owner, 16-11-2026): lines split by vehicle (bus / metro / boat), and destinations counted per
-- ~500 m square so the Admin can show where people go (hot spots for affiliate shops). A cell is
-- (floor(lat / 0.0045), floor(lon / 0.0045)): 0.0045° is 500 m north-south, ~490 m east-west at 10.8° N.
ALTER TABLE usage_bus_lines ADD COLUMN vehicle VARCHAR(10) NOT NULL DEFAULT 'BUS' CHECK (vehicle IN ('BUS', 'METRO', 'BOAT'));

CREATE TABLE usage_dest_cells (
  day   DATE NOT NULL,
  lat_i INTEGER NOT NULL,
  lon_i INTEGER NOT NULL,
  plans INTEGER NOT NULL CHECK (plans > 0),
  PRIMARY KEY (day, lat_i, lon_i)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON usage_dest_cells TO gowander_app;
GRANT SELECT ON usage_dest_cells TO gowander_admin;
