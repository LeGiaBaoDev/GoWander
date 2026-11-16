-- M07 slice 2: the Admin edits places and categories. edited_at marks rows an editor changed, so a re-import of the
-- source (scripts/build_discover_seed.py) leaves them alone instead of overwriting the editor's work.
ALTER TABLE places ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE place_categories ADD COLUMN edited_at TIMESTAMPTZ;

-- Column-level grants: provenance (source, source_ref, licence, attribution), slug and location stay import-only.
GRANT UPDATE (name, name_en, short_description, address, primary_category_id, status, edited_at, updated_at) ON places TO gowander_admin;
GRANT UPDATE (name, name_en, sort_order, is_active, edited_at) ON place_categories TO gowander_admin;
GRANT SELECT, INSERT, DELETE ON place_category_links TO gowander_admin;
