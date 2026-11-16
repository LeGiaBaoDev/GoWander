-- Visits (ADR-038, amended 16-11-2026): one count per day of app starts. The app sends nothing but the request;
-- no id, no location. Areas in admin_areas are now provinces (data/areas/vn-provinces.sql).
CREATE TABLE usage_app_opens (
  day   DATE PRIMARY KEY,
  opens INTEGER NOT NULL CHECK (opens > 0)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON usage_app_opens TO gowander_app;
GRANT SELECT ON usage_app_opens TO gowander_admin;
