REVOKE UPDATE (image_key, image_credit, image_license) ON food_items, historical_stories FROM gowander_admin;
REVOKE UPDATE (alt_text, source_credit, license_info, sort_order, is_cover) ON place_media FROM gowander_admin;
REVOKE INSERT, DELETE ON place_media FROM gowander_admin;
ALTER TABLE historical_stories DROP COLUMN image_license, DROP COLUMN image_credit, DROP COLUMN image_key;
ALTER TABLE food_items DROP CONSTRAINT food_items_image_credit_check, DROP COLUMN image_license, DROP COLUMN image_credit;
