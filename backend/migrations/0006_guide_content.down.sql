REVOKE UPDATE (title, summary, content, try_this, sources, source_url, status, published_at, sort_order, edited_at, updated_at)
  ON historical_stories FROM gowander_admin;
REVOKE UPDATE (name, name_en, summary, description, shops, sources, status, sort_order, edited_at, updated_at) ON food_items FROM gowander_admin;
REVOKE INSERT ON food_items, historical_stories FROM gowander_admin;
-- Stories that only have a written source cannot satisfy the old constraint: they go with the rollback.
DELETE FROM historical_stories WHERE source_url IS NULL OR btrim(source_url) = '';
ALTER TABLE historical_stories
  DROP CONSTRAINT historical_stories_source_check,
  ALTER COLUMN source_url SET NOT NULL,
  ADD CONSTRAINT historical_stories_source_url_check CHECK (btrim(source_url) <> ''),
  DROP COLUMN edited_at, DROP COLUMN sort_order, DROP COLUMN sources, DROP COLUMN try_this, DROP COLUMN slug;
ALTER TABLE food_items DROP COLUMN edited_at, DROP COLUMN sort_order, DROP COLUMN sources, DROP COLUMN shops, DROP COLUMN summary;
