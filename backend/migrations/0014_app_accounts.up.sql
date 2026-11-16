-- App accounts (ADR-040): a separate world from the Admin tables. The public API role writes here; it must never be
-- able to read admin_credentials or user_contacts.
CREATE TABLE app_users (
  id                UUID PRIMARY KEY DEFAULT uuidv7(),
  display_name      VARCHAR(100) NOT NULL CHECK (btrim(display_name) <> ''),
  -- Sign-in name: 3–30 characters, starts with a letter, lower case on disk.
  username          VARCHAR(30) UNIQUE CHECK (username ~ '^[a-z][a-z0-9._]{2,29}$'),
  email             VARCHAR(254) UNIQUE,          -- lower(btrim()); NULL only for deleted accounts
  email_verified_at TIMESTAMPTZ,
  phone             VARCHAR(16) UNIQUE,           -- E.164, optional
  phone_verified_at TIMESTAMPTZ,                  -- NULL until SMS exists (AUTH-5)
  password_hash     TEXT,                         -- argon2id; NULL for Google-only accounts
  google_sub        VARCHAR(255) UNIQUE,
  status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
  failed_logins     INT NOT NULL DEFAULT 0,
  locked_until      TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at     TIMESTAMPTZ,
  CHECK (email IS NULL OR email = lower(btrim(email))),
  -- An account must keep a way to sign in, unless it has been deleted.
  CHECK (status = 'DELETED' OR email IS NOT NULL OR google_sub IS NOT NULL)
);

-- Sessions: only the SHA-256 of the bearer token is stored (ADR-040 §3.4).
CREATE TABLE app_sessions (
  token_hash   BYTEA PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  device_label VARCHAR(100) NOT NULL DEFAULT '',   -- "iPhone", "Android": a label, never a device id
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX app_sessions_user_idx ON app_sessions (user_id);

-- Six-digit codes for verifying an email and for resetting a password: hashed, one use, five tries.
CREATE TABLE app_codes (
  id         UUID PRIMARY KEY DEFAULT uuidv7(),
  user_id    UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  purpose    VARCHAR(20) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
  code_hash  BYTEA NOT NULL,
  attempts   INT NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX app_codes_user_idx ON app_codes (user_id, purpose, created_at DESC);

-- A feedback sent while signed in belongs to the account (name and email come from it, not from the form).
ALTER TABLE feedback_reports ADD COLUMN app_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL;
CREATE INDEX feedback_reports_app_user_idx ON feedback_reports (app_user_id);

-- Public API: it creates accounts, sessions and codes, and clears the sender identity of a deleted account's feedback.
GRANT SELECT, INSERT ON app_users TO gowander_app;
GRANT UPDATE (display_name, username, email, email_verified_at, phone, phone_verified_at, password_hash, google_sub,
              status, failed_logins, locked_until, last_login_at, updated_at) ON app_users TO gowander_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON app_sessions TO gowander_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON app_codes TO gowander_app;
GRANT INSERT (app_user_id) ON feedback_reports TO gowander_app;
-- Deleting an account clears the link and the name: the WHERE clause needs SELECT on that one column, nothing else.
GRANT SELECT (app_user_id), UPDATE (app_user_id, sender_name) ON feedback_reports TO gowander_app;

-- Admin: reads the list of app users and may suspend one; never sees passwords, codes or sessions.
GRANT SELECT (id, display_name, username, email, email_verified_at, phone, phone_verified_at, status, created_at,
              last_login_at) ON app_users TO gowander_admin;
GRANT UPDATE (status, updated_at) ON app_users TO gowander_admin;
