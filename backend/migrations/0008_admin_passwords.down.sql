-- Restores the empty Google table of 0002; the identities that were in it are not restored.
CREATE TABLE user_auth_identities (
  id                         UUID PRIMARY KEY DEFAULT uuidv7(),
  user_id                    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  provider                   VARCHAR(30) NOT NULL CHECK (provider IN ('GOOGLE', 'APPLE', 'OIDC')),
  issuer                     VARCHAR(255) NOT NULL,
  provider_subject           VARCHAR(255) NOT NULL,
  email_at_provider          VARCHAR(320),
  email_verified_at_provider BOOLEAN NOT NULL,
  linked_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at               TIMESTAMPTZ,
  UNIQUE (issuer, provider_subject),
  UNIQUE (user_id, provider)
);
GRANT SELECT, INSERT, UPDATE ON user_auth_identities TO gowander_admin;
DROP TABLE admin_password_resets;
DROP TABLE admin_credentials;
