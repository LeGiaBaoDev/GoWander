-- M07 slice 3: dishes and stories of the region guide move from the app bundle to the server, edited in the Admin.
-- The fields follow what the app shows (content/GuideHcm.kt).

ALTER TABLE food_items
  ADD COLUMN summary    VARCHAR(300),
  -- Suggested shops, one per line "Name | Address". shortcut: free text until the Admin can add places; then link
  -- them through place_foods.
  ADD COLUMN shops      TEXT,
  ADD COLUMN sources    TEXT,
  ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN edited_at  TIMESTAMPTZ;

ALTER TABLE historical_stories
  ADD COLUMN slug       VARCHAR(170) UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  ADD COLUMN try_this   TEXT,
  -- Sources are often a publisher's name, not one URL ("Cục Du lịch Quốc gia Việt Nam (vietnam.travel)").
  ADD COLUMN sources    TEXT,
  ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN edited_at  TIMESTAMPTZ,
  ALTER COLUMN source_url DROP NOT NULL,
  DROP CONSTRAINT historical_stories_source_url_check,
  -- Still never published without a source: a URL or a written source.
  ADD CONSTRAINT historical_stories_source_check CHECK (btrim(coalesce(source_url, '') || coalesce(sources, '')) <> '');

GRANT INSERT ON food_items, historical_stories TO gowander_admin;
GRANT UPDATE (name, name_en, summary, description, shops, sources, status, sort_order, edited_at, updated_at) ON food_items TO gowander_admin;
GRANT UPDATE (title, summary, content, try_this, sources, source_url, status, published_at, sort_order, edited_at, updated_at)
  ON historical_stories TO gowander_admin;
