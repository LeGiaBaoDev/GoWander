-- Images inside a story (ADR-041 §4): uploaded through the Admin like every other photo (media.Process re-encodes and
-- strips EXIF, ADR-035), with the credit and licence the owner must give. The block JSON keeps only the key.
CREATE TABLE content_images (
  key        TEXT PRIMARY KEY,
  alt        VARCHAR(300) NOT NULL DEFAULT '',
  credit     VARCHAR(200) NOT NULL CHECK (btrim(credit) <> ''),
  license    VARCHAR(40)  NOT NULL CHECK (btrim(license) <> ''),
  created_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- The Admin uploads and removes them; the public API only needs to name them when rendering a story.
GRANT SELECT, INSERT, DELETE ON content_images TO gowander_admin;
GRANT SELECT (key, alt, credit, license) ON content_images TO gowander_app;
