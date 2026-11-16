-- Photos (ADR-035, M07): the Admin uploads them; the files live in storage (R2 in production), the database keeps the
-- key ("img/<hash>") with credit and licence, which the app shows next to every photo.

ALTER TABLE food_items
  ADD COLUMN image_credit  TEXT,
  ADD COLUMN image_license TEXT,
  ADD CONSTRAINT food_items_image_credit_check CHECK (image_key IS NULL OR (btrim(coalesce(image_credit, '')) <> '' AND btrim(coalesce(image_license, '')) <> ''));

ALTER TABLE historical_stories
  ADD COLUMN image_key     TEXT,
  ADD COLUMN image_credit  TEXT,
  ADD COLUMN image_license TEXT,
  ADD CONSTRAINT historical_stories_image_credit_check CHECK (image_key IS NULL OR (btrim(coalesce(image_credit, '')) <> '' AND btrim(coalesce(image_license, '')) <> ''));

GRANT INSERT, DELETE ON place_media TO gowander_admin;
GRANT UPDATE (alt_text, source_credit, license_info, sort_order, is_cover) ON place_media TO gowander_admin;
GRANT UPDATE (image_key, image_credit, image_license) ON food_items, historical_stories TO gowander_admin;
