-- ADR-039: Admin accounts with a login and a password instead of Google sign-in.

-- One login per operator. login is stored lower-case, so UNIQUE also means "no two logins differing only in case".
CREATE TABLE admin_credentials (
  user_id             UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  login               VARCHAR(40) NOT NULL UNIQUE CHECK (login ~ '^[a-z0-9][a-z0-9._-]{2,39}$'),
  password_hash       TEXT NOT NULL CHECK (password_hash LIKE '$argon2id$%'),
  must_change         BOOLEAN NOT NULL DEFAULT true, -- a temporary password from cmd/admin
  failed_attempts     INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
  locked_until        TIMESTAMPTZ,
  password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "Quên mật khẩu" links: only the SHA-256 of the link's token is stored; one use, 30 minutes.
CREATE TABLE admin_password_resets (
  token_hash BYTEA PRIMARY KEY CHECK (octet_length(token_hash) = 32),
  user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ
);
CREATE INDEX admin_password_resets_user_idx ON admin_password_resets (user_id, created_at);

GRANT SELECT, INSERT ON admin_credentials TO gowander_admin;
GRANT UPDATE (password_hash, must_change, failed_attempts, locked_until, password_changed_at) ON admin_credentials TO gowander_admin;
GRANT SELECT, INSERT, DELETE ON admin_password_resets TO gowander_admin;
GRANT UPDATE (used_at) ON admin_password_resets TO gowander_admin;

-- Google sign-in is gone: its links go with it (the users, their roles and the audit log stay).
DROP TABLE user_auth_identities;
