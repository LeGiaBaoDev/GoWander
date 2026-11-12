-- M05 Discover tables (ADR-028 §3, docs/design/05-database-design-v1.2/03-discover-tables.md).
-- Runs as gowander_owner; search_path = gowander, public. Extensions are created by infrastructure/postgres/init.

-- Lower-case, accent-free text for search: "Bến Thành" -> "ben thanh". IMMUTABLE so it can back an index.
CREATE FUNCTION search_text(t text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
  RETURN lower(public.unaccent('public.unaccent'::regdictionary, t));

CREATE TABLE place_categories (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  parent_id  BIGINT REFERENCES place_categories (id) ON DELETE RESTRICT,
  name       VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
  name_en    VARCHAR(100),
  slug       VARCHAR(100) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_active  BOOLEAN NOT NULL DEFAULT true,
  CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX place_categories_parent_idx ON place_categories (parent_id);

CREATE TABLE places (
  id                  UUID PRIMARY KEY DEFAULT uuidv7(),
  name                VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
  name_en             VARCHAR(200),
  slug                VARCHAR(220) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  short_description   VARCHAR(500),
  location            GEOGRAPHY(POINT, 4326) NOT NULL,
  address             TEXT,
  primary_category_id BIGINT REFERENCES place_categories (id) ON DELETE SET NULL,
  status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
  -- Provenance (ADR-028 §4): every place is traceable to its source and licence.
  source              VARCHAR(20) NOT NULL CHECK (source IN ('OSM', 'CURATED', 'FIXTURE')),
  source_ref          TEXT NOT NULL CHECK (btrim(source_ref) <> ''),
  source_url          TEXT,
  license             TEXT NOT NULL CHECK (btrim(license) <> ''),
  attribution         TEXT NOT NULL CHECK (btrim(attribution) <> ''),
  search_doc          TEXT GENERATED ALWAYS AS (search_text(name || ' ' || coalesce(name_en, ''))) STORED,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source, source_ref)
);
CREATE INDEX places_location_gix ON places USING GIST (location);
CREATE INDEX places_published_name_idx ON places (name, id) WHERE status = 'PUBLISHED';
CREATE INDEX places_primary_category_idx ON places (primary_category_id);
CREATE INDEX places_search_trgm_idx ON places USING GIN (search_doc public.gin_trgm_ops);

CREATE TABLE place_category_links (
  place_id    UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES place_categories (id) ON DELETE RESTRICT,
  PRIMARY KEY (place_id, category_id)
);
CREATE INDEX place_category_links_category_idx ON place_category_links (category_id, place_id);

CREATE TABLE place_media (
  id            UUID PRIMARY KEY DEFAULT uuidv7(),
  place_id      UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
  storage_key   TEXT NOT NULL CHECK (btrim(storage_key) <> ''),
  media_type    VARCHAR(30) NOT NULL CHECK (media_type IN ('IMAGE')),
  alt_text      VARCHAR(300),
  source_credit TEXT NOT NULL CHECK (btrim(source_credit) <> ''),
  license_info  TEXT NOT NULL CHECK (btrim(license_info) <> ''),
  sort_order    INTEGER NOT NULL DEFAULT 0,
  is_cover      BOOLEAN NOT NULL DEFAULT false,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX place_media_place_idx ON place_media (place_id, sort_order);
CREATE UNIQUE INDEX place_media_one_cover_idx ON place_media (place_id) WHERE is_cover;

CREATE TABLE food_items (
  id          UUID PRIMARY KEY DEFAULT uuidv7(),
  name        VARCHAR(150) NOT NULL CHECK (btrim(name) <> ''),
  name_en     VARCHAR(150),
  slug        VARCHAR(170) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  description TEXT,
  image_key   TEXT,
  status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE place_foods (
  place_id      UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
  food_id       UUID NOT NULL REFERENCES food_items (id) ON DELETE CASCADE,
  price_min     NUMERIC(12, 2) CHECK (price_min >= 0),
  price_max     NUMERIC(12, 2) CHECK (price_max >= 0),
  currency_code CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency_code ~ '^[A-Z]{3}$'),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (place_id, food_id),
  CHECK (price_min IS NULL OR price_max IS NULL OR price_min <= price_max)
);
CREATE INDEX place_foods_food_idx ON place_foods (food_id);

CREATE TABLE historical_stories (
  id            UUID PRIMARY KEY DEFAULT uuidv7(),
  place_id      UUID REFERENCES places (id) ON DELETE SET NULL,
  title         VARCHAR(220) NOT NULL CHECK (btrim(title) <> ''),
  summary       VARCHAR(500),
  content       TEXT NOT NULL CHECK (btrim(content) <> ''),
  -- ADR-022: vi and en first, zh and ko later.
  language_code VARCHAR(10) NOT NULL CHECK (language_code IN ('vi', 'en', 'zh', 'ko')),
  -- story_sources is not built yet (conditional), but a story never ships without a source.
  source_url    TEXT NOT NULL CHECK (btrim(source_url) <> ''),
  status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
  published_at  TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX historical_stories_place_idx ON historical_stories (place_id);

-- The runtime role reads Discover tables only (ADR-028 §2). New tables need their own GRANT.
GRANT SELECT ON place_categories, places, place_category_links, place_media, food_items, place_foods, historical_stories
  TO gowander_app;
