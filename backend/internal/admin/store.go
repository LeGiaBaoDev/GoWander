package admin

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/mail"
	"slices"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// PG is the PostgreSQL Store, connected as the role gowander_admin (ADR-037).
type PG struct {
	Pool *pgxpool.Pool
}

func (s *PG) Ready(ctx context.Context) error { return s.Pool.Ping(ctx) }

// NormalizeEmail lower-cases and trims an email and checks it is a plain address; "" when it is not.
func NormalizeEmail(raw string) string {
	e := strings.ToLower(strings.TrimSpace(raw))
	if a, err := mail.ParseAddress(e); err != nil || a.Address != e || len(e) > 320 {
		return ""
	}
	return e
}

func randomToken() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		panic(err) // crypto/rand does not fail on supported platforms
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

func tokenHash(token string) []byte {
	h := sha256.Sum256([]byte(token))
	return h[:]
}

// activeRoles is the SQL for the role codes a user holds now (unexpired, active role).
const activeRoles = `SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id
 WHERE ur.user_id = $1 AND r.is_active AND (ur.expires_at IS NULL OR ur.expires_at > now()) ORDER BY r.id`

func (s *PG) Login(ctx context.Context, id Identity, bootstrapEmail, requestID string) (string, error) {
	email := NormalizeEmail(id.Email)
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return "", err
	}
	defer tx.Rollback(ctx)

	var userID string
	err = tx.QueryRow(ctx, `SELECT user_id::text FROM user_auth_identities WHERE issuer = $1 AND provider_subject = $2`, id.Issuer, id.Subject).Scan(&userID)
	switch {
	case err == nil:
		// already linked: the Google subject decides, the email may have changed at Google
	case !errors.Is(err, pgx.ErrNoRows):
		return "", err
	case email == "" || !id.EmailVerified:
		return "", ErrNotAllowed
	default:
		// First sign-in: an email a role was granted to. Never merge into an account that already has another Google login.
		err = tx.QueryRow(ctx, `SELECT c.user_id::text FROM user_contacts c
			 WHERE c.email_normalized = $1 AND NOT EXISTS (SELECT 1 FROM user_auth_identities i WHERE i.user_id = c.user_id AND i.provider = 'GOOGLE')`, email).Scan(&userID)
		if errors.Is(err, pgx.ErrNoRows) {
			// Bootstrap: the configured email becomes SYSTEM_ADMIN while nobody holds any role.
			var anyRole bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM user_roles)`).Scan(&anyRole); err != nil {
				return "", err
			}
			if anyRole || bootstrapEmail == "" || email != bootstrapEmail {
				return "", ErrNotAllowed
			}
			if err := tx.QueryRow(ctx, `INSERT INTO users (display_name) VALUES ($1) RETURNING id::text`, nullable(id.Name)).Scan(&userID); err != nil {
				return "", err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO user_contacts (user_id, email, email_normalized, email_verified_at) VALUES ($1, $2, $2, now())`, userID, email); err != nil {
				return "", err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO user_roles (user_id, role_id) SELECT $1, id FROM roles WHERE code = $2`, userID, RoleSystemAdmin); err != nil {
				return "", err
			}
			if err := audit(ctx, tx, AuditEntry{ActorID: userID, Action: "admin.bootstrap", EntityType: "user", EntityID: userID, RequestID: requestID, Metadata: map[string]any{"role": RoleSystemAdmin}}); err != nil {
				return "", err
			}
		} else if err != nil {
			return "", err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO user_auth_identities (user_id, provider, issuer, provider_subject, email_at_provider, email_verified_at_provider)
			VALUES ($1, 'GOOGLE', $2, $3, $4, true)`, userID, id.Issuer, id.Subject, email); err != nil {
			return "", err
		}
		if _, err := tx.Exec(ctx, `UPDATE user_contacts SET email_verified_at = coalesce(email_verified_at, now()), updated_at = now() WHERE user_id = $1`, userID); err != nil {
			return "", err
		}
	}

	var active bool
	if err := tx.QueryRow(ctx, `SELECT status = 'ACTIVE' AND EXISTS (`+strings.Replace(activeRoles, " ORDER BY r.id", "", 1)+`) FROM users WHERE id = $1`, userID).Scan(&active); err != nil {
		return "", err
	}
	if !active {
		return "", ErrNotAllowed
	}
	if _, err := tx.Exec(ctx, `UPDATE users SET last_login_at = now(), display_name = coalesce(display_name, $2), updated_at = now() WHERE id = $1`, userID, nullable(id.Name)); err != nil {
		return "", err
	}
	if _, err := tx.Exec(ctx, `UPDATE user_auth_identities SET last_used_at = now(), email_at_provider = $3 WHERE issuer = $1 AND provider_subject = $2`, id.Issuer, id.Subject, nullable(email)); err != nil {
		return "", err
	}
	if err := audit(ctx, tx, AuditEntry{ActorID: userID, Action: "admin.login", EntityType: "user", EntityID: userID, RequestID: requestID}); err != nil {
		return "", err
	}
	return userID, tx.Commit(ctx)
}

func (s *PG) CreateSession(ctx context.Context, userID string, ttl time.Duration) (string, error) {
	token := randomToken()
	_, err := s.Pool.Exec(ctx, `INSERT INTO admin_sessions (token_hash, user_id, csrf_token, expires_at) VALUES ($1, $2, $3, now() + $4::interval)`,
		tokenHash(token), userID, randomToken(), ttl.String())
	if err != nil {
		return "", err
	}
	// Expired sessions are swept here rather than by a job: a few rows per operator at most.
	_, _ = s.Pool.Exec(ctx, `DELETE FROM admin_sessions WHERE expires_at < now()`)
	return token, nil
}

func (s *PG) Session(ctx context.Context, token string) (Session, error) {
	var se Session
	err := s.Pool.QueryRow(ctx, `SELECT s.user_id::text, s.csrf_token, s.expires_at, coalesce(c.email, ''), coalesce(u.display_name, '')
		  FROM admin_sessions s JOIN users u ON u.id = s.user_id LEFT JOIN user_contacts c ON c.user_id = u.id
		 WHERE s.token_hash = $1 AND s.expires_at > now() AND u.status = 'ACTIVE'`, tokenHash(token)).Scan(&se.UserID, &se.CSRF, &se.ExpiresAt, &se.Email, &se.Name)
	if errors.Is(err, pgx.ErrNoRows) {
		return Session{}, ErrNotAllowed
	}
	if err != nil {
		return Session{}, err
	}
	rows, err := s.Pool.Query(ctx, activeRoles, se.UserID)
	if err != nil {
		return Session{}, err
	}
	if se.Roles, err = pgx.CollectRows(rows, pgx.RowTo[string]); err != nil {
		return Session{}, err
	}
	if len(se.Roles) == 0 { // every role was revoked: the session no longer opens anything
		return Session{}, ErrNotAllowed
	}
	return se, nil
}

func (s *PG) DeleteSession(ctx context.Context, token string) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM admin_sessions WHERE token_hash = $1`, tokenHash(token))
	return err
}

func (s *PG) Operators(ctx context.Context) ([]Operator, error) {
	rows, err := s.Pool.Query(ctx, `SELECT u.id::text, coalesce(c.email, ''), u.status, u.last_login_at,
		       coalesce(array_agg(r.code ORDER BY r.id) FILTER (WHERE r.code IS NOT NULL), '{}')
		  FROM users u
		  JOIN user_roles ur ON ur.user_id = u.id
		  LEFT JOIN roles r ON r.id = ur.role_id AND r.is_active AND (ur.expires_at IS NULL OR ur.expires_at > now())
		  LEFT JOIN user_contacts c ON c.user_id = u.id
		 GROUP BY u.id, c.email, u.status, u.last_login_at
		 ORDER BY c.email`)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Operator, error) {
		var o Operator
		return o, r.Scan(&o.UserID, &o.Email, &o.Status, &o.LastLogin, &o.Roles)
	})
}

func validRole(role string) bool { return slices.Contains(AllRoles, role) }

func (s *PG) Grant(ctx context.Context, actorID, rawEmail, role, requestID string) error {
	email := NormalizeEmail(rawEmail)
	if email == "" || !validRole(role) {
		return ErrBadInput
	}
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	var userID string
	err = tx.QueryRow(ctx, `SELECT user_id::text FROM user_contacts WHERE email_normalized = $1`, email).Scan(&userID)
	if errors.Is(err, pgx.ErrNoRows) {
		// A person who has never signed in: the account waits for their first Google sign-in with this email.
		if err := tx.QueryRow(ctx, `INSERT INTO users DEFAULT VALUES RETURNING id::text`).Scan(&userID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO user_contacts (user_id, email, email_normalized) VALUES ($1, $2, $2)`, userID, email); err != nil {
			return err
		}
	} else if err != nil {
		return err
	}
	tag, err := tx.Exec(ctx, `INSERT INTO user_roles (user_id, role_id, assigned_by) SELECT $1, id, $3 FROM roles WHERE code = $2 ON CONFLICT DO NOTHING`, userID, role, actorID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 1 {
		if err := audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "role.grant", EntityType: "user", EntityID: userID, RequestID: requestID, Metadata: map[string]any{"role": role}}); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

func (s *PG) Revoke(ctx context.Context, actorID, userID, role, requestID string) error {
	if !validRole(role) {
		return ErrBadInput
	}
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	if role == RoleSystemAdmin {
		// Serialise concurrent revokes of the last system admins, then refuse to remove the last one.
		// A transaction-scoped advisory lock: needs no write privilege on roles, released at commit or rollback.
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(hashtext('gowander.system_admin_revoke'))`); err != nil {
			return err
		}
		var others int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id JOIN users u ON u.id = ur.user_id
			 WHERE r.code = $1 AND ur.user_id <> $2 AND u.status = 'ACTIVE' AND (ur.expires_at IS NULL OR ur.expires_at > now())`, RoleSystemAdmin, userID).Scan(&others); err != nil {
			return err
		}
		if others == 0 {
			return ErrLastSystemAdmin
		}
	}
	tag, err := tx.Exec(ctx, `DELETE FROM user_roles WHERE user_id = $1 AND role_id = (SELECT id FROM roles WHERE code = $2)`, userID, role)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 1 {
		if err := audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "role.revoke", EntityType: "user", EntityID: userID, RequestID: requestID, Metadata: map[string]any{"role": role}}); err != nil {
			return err
		}
		// A person with no role left is signed out everywhere at once.
		if _, err := tx.Exec(ctx, `DELETE FROM admin_sessions WHERE user_id = $1 AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = $1)`, userID); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

func (s *PG) Audit(ctx context.Context, e AuditEntry) error { return audit(ctx, s.Pool, e) }

// audit writes one log line; db is the pool or the open transaction of the change it records.
func audit(ctx context.Context, db interface {
	Exec(context.Context, string, ...any) (pgconn.CommandTag, error)
}, e AuditEntry) error {
	meta, err := json.Marshal(e.Metadata)
	if err != nil || e.Metadata == nil {
		meta = []byte("{}")
	}
	_, err = db.Exec(ctx, `INSERT INTO admin_audit_logs (actor_id, action, entity_type, entity_id, metadata, request_id) VALUES ($1, $2, $3, $4, $5, $6)`,
		nullable(e.ActorID), e.Action, e.EntityType, nullable(e.EntityID), meta, nullable(e.RequestID))
	return err
}

func (s *PG) AuditLog(ctx context.Context, limit int) ([]AuditEntry, error) {
	rows, err := s.Pool.Query(ctx, `SELECT coalesce(a.actor_id::text, ''), coalesce(u.display_name, c.email, ''), a.action, a.entity_type,
		       coalesce(a.entity_id, ''), a.metadata, coalesce(a.request_id, ''), a.created_at
		  FROM admin_audit_logs a LEFT JOIN users u ON u.id = a.actor_id LEFT JOIN user_contacts c ON c.user_id = a.actor_id
		 ORDER BY a.created_at DESC LIMIT $1`, limit)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (AuditEntry, error) {
		var e AuditEntry
		var meta []byte
		err := r.Scan(&e.ActorID, &e.ActorName, &e.Action, &e.EntityType, &e.EntityID, &meta, &e.RequestID, &e.At)
		if err == nil {
			_ = json.Unmarshal(meta, &e.Metadata)
		}
		return e, err
	})
}

func nullable(s string) any {
	if s == "" {
		return nil
	}
	return s
}
