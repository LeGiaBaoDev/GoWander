REVOKE SELECT, INSERT, DELETE ON place_category_links FROM gowander_admin;
REVOKE UPDATE (name, name_en, sort_order, is_active, edited_at) ON place_categories FROM gowander_admin;
REVOKE UPDATE (name, name_en, short_description, address, primary_category_id, status, edited_at, updated_at) ON places FROM gowander_admin;
ALTER TABLE place_categories DROP COLUMN edited_at;
ALTER TABLE places DROP COLUMN edited_at;
