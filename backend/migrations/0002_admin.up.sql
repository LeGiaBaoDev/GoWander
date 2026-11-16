-- M07 / ADR-037: operators, their Google sign-in, roles, sessions and an append-only audit log.
-- Tables follow docs/design/05-database-design-v1.2 (02-identity, 06-admin). The public API role gowander_app gets no
-- privilege here: none of these tables is readable through the public API.

CREATE TABLE users (
  id             UUID PRIMARY KEY DEFAULT uuidv7(),
  status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
  display_name   VARCHAR(120),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at  TIMESTAMPTZ,
  deactivated_at TIMESTAMPTZ
);

-- One row per external sign-in. Accounts are never merged by email (design 3.2): a second Google subject with the same
-- email is refused, not linked.
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

-- Private contact data, kept apart from the account (design 3.3). For operators: the email a role was granted to.
CREATE TABLE user_contacts (
  user_id           UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  email             VARCHAR(320),
  email_normalized  VARCHAR(320) UNIQUE,
  email_verified_at TIMESTAMPTZ,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (email_normalized IS NULL OR email_normalized = lower(btrim(email_normalized)))
);

CREATE TABLE roles (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code        VARCHAR(50) NOT NULL UNIQUE,
  name        VARCHAR(100) NOT NULL,
  description TEXT,
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO roles (code, name, description) VALUES
  ('CONTENT_EDITOR', 'Biên tập nội dung', 'Sửa, xuất bản, ẩn địa điểm, món ăn, câu chuyện, cẩm nang, hành trình.'),
  ('MODERATOR', 'Kiểm duyệt', 'Xử lý báo sai và nội dung người dùng gửi; không sửa nội dung biên tập.'),
  ('OPERATIONS_ADMIN', 'Vận hành', 'Nguồn dữ liệu giao thông, sự cố tìm tuyến, bảng số liệu vận hành.'),
  ('BUSINESS_ADMIN', 'Kinh doanh', 'Đối tác và doanh thu (chưa dùng ở M07).'),
  ('SYSTEM_ADMIN', 'Quản trị hệ thống', 'Cấp và thu hồi vai trò, xem nhật ký thao tác.');

CREATE TABLE user_roles (
  user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  role_id     BIGINT NOT NULL REFERENCES roles (id),
  assigned_by UUID REFERENCES users (id) ON DELETE SET NULL, -- NULL: the bootstrap SYSTEM_ADMIN
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ,
  PRIMARY KEY (user_id, role_id)
);

-- Server-side sessions: only the SHA-256 of the cookie value is stored, so a database dump cannot be replayed.
CREATE TABLE admin_sessions (
  token_hash   BYTEA PRIMARY KEY CHECK (octet_length(token_hash) = 32),
  user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  csrf_token   VARCHAR(64) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX admin_sessions_user_idx ON admin_sessions (user_id);

-- Append-only: the Admin role may INSERT and SELECT, never UPDATE or DELETE (design 10.3). entity_id is text so
-- BIGINT ids (categories) and UUIDs (places) both fit. metadata holds no private contact data.
CREATE TABLE admin_audit_logs (
  id          UUID PRIMARY KEY DEFAULT uuidv7(),
  actor_id    UUID REFERENCES users (id) ON DELETE SET NULL,
  action      VARCHAR(100) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id   TEXT,
  metadata    JSONB NOT NULL DEFAULT '{}'::jsonb,
  request_id  VARCHAR(64),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX admin_audit_logs_created_idx ON admin_audit_logs (created_at DESC);

GRANT SELECT, INSERT, UPDATE ON users, user_auth_identities, user_contacts TO gowander_admin;
GRANT SELECT ON roles TO gowander_admin;
GRANT SELECT, INSERT, DELETE ON user_roles TO gowander_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON admin_sessions TO gowander_admin;
GRANT SELECT, INSERT ON admin_audit_logs TO gowander_admin;
