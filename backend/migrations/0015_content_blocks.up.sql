-- Rich text for stories (ADR-041): Editor.js blocks, stored as JSON, with the plain text kept in `content` for search and
-- for app versions that do not render blocks yet.
ALTER TABLE historical_stories ADD COLUMN blocks JSONB;
GRANT UPDATE (blocks) ON historical_stories TO gowander_admin;
