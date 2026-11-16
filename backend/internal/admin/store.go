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

// PasswordLogin checks a login and password (ADR-039). Every refusal is ErrNotAllowed, whatever the reason, and an
// unknown login costs one hash like a wrong password. Five wrong passwords in a row lock the account for 15 minutes.
func (s *PG) PasswordLogin(ctx context.Context, rawLogin, password, requestID string) (string, error) {
	var userID, hash string
	var locked bool
	err := s.Pool.QueryRow(ctx, `SELECT user_id::text, password_hash, coalesce(locked_until > now(), false) FROM admin_credentials WHERE login = $1`,
		NormalizeLogin(rawLogin)).Scan(&userID, &hash, &locked)
	if errors.Is(err, pgx.ErrNoRows) {
		CheckPassword(dummyHash, password)
		return "", ErrNotAllowed
	}
	if err != nil {
		return "", err
	}
	if locked {
		CheckPassword(dummyHash, password)
		return "", ErrNotAllowed
	}
	if !CheckPassword(hash, password) {
		if _, err := s.Pool.Exec(ctx, `UPDATE admin_credentials SET
			locked_until = CASE WHEN failed_attempts + 1 >= $2 THEN now() + $3::interval ELSE locked_until END,
			failed_attempts = CASE WHEN failed_attempts + 1 >= $2 THEN 0 ELSE failed_attempts + 1 END
			WHERE user_id = $1`, userID, maxFailedLogins, lockFor.String()); err != nil {
			return "", err
		}
		_ = audit(ctx, s.Pool, AuditEntry{Action: "admin.login_failed", EntityType: "user", EntityID: userID, RequestID: requestID})
		return "", ErrNotAllowed
	}
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return "", err
	}
	defer tx.Rollback(ctx)
	var active bool
	if err := tx.QueryRow(ctx, `SELECT status = 'ACTIVE' AND EXISTS (`+strings.Replace(activeRoles, " ORDER BY r.id", "", 1)+`) FROM users WHERE id = $1`, userID).Scan(&active); err != nil {
		return "", err
	}
	if !active {
		return "", ErrNotAllowed
	}
	if _, err := tx.Exec(ctx, `UPDATE admin_credentials SET failed_attempts = 0, locked_until = NULL WHERE user_id = $1`, userID); err != nil {
		return "", err
	}
	if _, err := tx.Exec(ctx, `UPDATE users SET last_login_at = now(), updated_at = now() WHERE id = $1`, userID); err != nil {
		return "", err
	}
	if err := audit(ctx, tx, AuditEntry{ActorID: userID, Action: "admin.login", EntityType: "user", EntityID: userID, RequestID: requestID}); err != nil {
		return "", err
	}
	return userID, tx.Commit(ctx)
}

const (
	maxFailedLogins = 5
	lockFor         = 15 * time.Minute
	resetValidFor   = 30 * time.Minute
	resetEvery      = 2 * time.Minute // at most one "quên mật khẩu" email per account in this time
)

// setPassword stores a new hash, unlocks the account, and signs the person out of every session except keepToken.
func setPassword(ctx context.Context, tx pgx.Tx, userID, password string, mustChange bool, keepToken string) error {
	if _, err := tx.Exec(ctx, `UPDATE admin_credentials SET password_hash = $2, must_change = $3, failed_attempts = 0, locked_until = NULL,
		password_changed_at = now() WHERE user_id = $1`, userID, HashPassword(password), mustChange); err != nil {
		return err
	}
	_, err := tx.Exec(ctx, `DELETE FROM admin_sessions WHERE user_id = $1 AND token_hash <> $2`, userID, tokenHash(keepToken))
	return err
}

// ChangePassword: the signed-in operator replaces their password (also the forced change after a temporary one).
func (s *PG) ChangePassword(ctx context.Context, userID, current, next, keepToken, requestID string) error {
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var login, hash string
		if err := tx.QueryRow(ctx, `SELECT login, password_hash FROM admin_credentials WHERE user_id = $1 FOR UPDATE`, userID).Scan(&login, &hash); err != nil {
			return err
		}
		if !CheckPassword(hash, current) {
			return bad("mật khẩu hiện tại không đúng")
		}
		if current == next {
			return bad("mật khẩu mới phải khác mật khẩu hiện tại")
		}
		if err := validatePassword(login, next); err != nil {
			return err
		}
		if err := setPassword(ctx, tx, userID, next, false, keepToken); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{ActorID: userID, Action: "admin.password_change", EntityType: "user", EntityID: userID, RequestID: requestID})
	})
}

// StartPasswordReset makes a one-time link token for a login and returns where to email it. ErrNotFound covers an
// unknown login, an account without email, an inactive account and a request within resetEvery of the last one: the
// caller answers the same in every case.
func (s *PG) StartPasswordReset(ctx context.Context, rawLogin, requestID string) (email, token string, err error) {
	err = pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var userID string
		var recent bool
		err := tx.QueryRow(ctx, `SELECT a.user_id::text, coalesce(c.email, ''),
			EXISTS (SELECT 1 FROM admin_password_resets r WHERE r.user_id = a.user_id AND r.created_at > now() - $2::interval)
			FROM admin_credentials a JOIN users u ON u.id = a.user_id LEFT JOIN user_contacts c ON c.user_id = a.user_id
			WHERE a.login = $1 AND u.status = 'ACTIVE'`, NormalizeLogin(rawLogin), resetEvery.String()).Scan(&userID, &email, &recent)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && (email == "" || recent)) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		token = randomToken()
		// One live link per account: a new request replaces the old one.
		if _, err := tx.Exec(ctx, `DELETE FROM admin_password_resets WHERE user_id = $1 AND used_at IS NULL`, userID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO admin_password_resets (token_hash, user_id, expires_at) VALUES ($1, $2, now() + $3::interval)`,
			tokenHash(token), userID, resetValidFor.String()); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{Action: "admin.password_reset_requested", EntityType: "user", EntityID: userID, RequestID: requestID})
	})
	if err != nil {
		return "", "", err
	}
	return email, token, nil
}

// ResetPassword uses a link token once, sets the new password and signs the account out everywhere.
func (s *PG) ResetPassword(ctx context.Context, token, next, requestID string) error {
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var userID, login string
		err := tx.QueryRow(ctx, `SELECT r.user_id::text, a.login FROM admin_password_resets r JOIN admin_credentials a ON a.user_id = r.user_id
			WHERE r.token_hash = $1 AND r.used_at IS NULL AND r.expires_at > now() FOR UPDATE OF r`, tokenHash(token)).Scan(&userID, &login)
		if errors.Is(err, pgx.ErrNoRows) {
			return bad("link đặt lại mật khẩu đã dùng hoặc đã hết hạn, hãy yêu cầu link mới")
		}
		if err != nil {
			return err
		}
		if err := validatePassword(login, next); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE admin_password_resets SET used_at = now() WHERE token_hash = $1`, tokenHash(token)); err != nil {
			return err
		}
		if err := setPassword(ctx, tx, userID, next, false, ""); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{ActorID: userID, Action: "admin.password_reset", EntityType: "user", EntityID: userID, RequestID: requestID})
	})
}

// CreateAccount gives an operator a login and a temporary password (cmd/admin). An existing user with that contact
// email (the owner, who signed in with Google before ADR-039) keeps their id, roles and audit history; otherwise a
// user is created with the role. The temporary password is returned once and must be changed at the first sign-in.
func (s *PG) CreateAccount(ctx context.Context, rawLogin, rawEmail, name, role string) (userID, temporary string, err error) {
	login, email := NormalizeLogin(rawLogin), NormalizeEmail(rawEmail)
	switch {
	case login == "":
		return "", "", bad("mã tài khoản dài 3–40 ký tự, chỉ gồm a-z, 0-9, dấu chấm, gạch dưới, gạch ngang")
	case email == "":
		return "", "", bad("email không hợp lệ (dùng để nhận link quên mật khẩu)")
	case !slices.Contains(AllRoles, role):
		return "", "", bad("vai trò không hợp lệ")
	}
	temporary = TemporaryPassword()
	err = pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var taken bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM admin_credentials WHERE login = $1)`, login).Scan(&taken); err != nil {
			return err
		}
		if taken {
			return bad("mã tài khoản %q đã có người dùng", login)
		}
		err := tx.QueryRow(ctx, `SELECT user_id::text FROM user_contacts WHERE email_normalized = $1`, email).Scan(&userID)
		if errors.Is(err, pgx.ErrNoRows) {
			if err := tx.QueryRow(ctx, `INSERT INTO users (display_name) VALUES ($1) RETURNING id::text`, nullable(strings.TrimSpace(name))).Scan(&userID); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO user_contacts (user_id, email, email_normalized, email_verified_at) VALUES ($1, $2, $2, now())`, userID, email); err != nil {
				return err
			}
		} else if err != nil {
			return err
		}
		var hasLogin bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM admin_credentials WHERE user_id = $1)`, userID).Scan(&hasLogin); err != nil {
			return err
		}
		if hasLogin {
			return bad("người có email này đã có tài khoản; dùng reset-password")
		}
		if _, err := tx.Exec(ctx, `INSERT INTO user_roles (user_id, role_id) SELECT $1, id FROM roles WHERE code = $2 ON CONFLICT DO NOTHING`, userID, role); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO admin_credentials (user_id, login, password_hash) VALUES ($1, $2, $3)`, userID, login, HashPassword(temporary)); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{Action: "account.create", EntityType: "user", EntityID: userID, Metadata: map[string]any{"login": login, "role": role}})
	})
	if err != nil {
		return "", "", err
	}
	return userID, temporary, nil
}

// SetTemporaryPassword (cmd/admin reset-password) replaces a forgotten password when email is not available.
func (s *PG) SetTemporaryPassword(ctx context.Context, rawLogin string) (string, error) {
	temporary := TemporaryPassword()
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var userID string
		err := tx.QueryRow(ctx, `SELECT user_id::text FROM admin_credentials WHERE login = $1 FOR UPDATE`, NormalizeLogin(rawLogin)).Scan(&userID)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if err := setPassword(ctx, tx, userID, temporary, true, ""); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{Action: "account.temporary_password", EntityType: "user", EntityID: userID})
	})
	return temporary, err
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
	err := s.Pool.QueryRow(ctx, `SELECT s.user_id::text, s.csrf_token, s.expires_at, coalesce(c.email, ''), coalesce(u.display_name, ''), a.login, a.must_change
		  FROM admin_sessions s JOIN users u ON u.id = s.user_id JOIN admin_credentials a ON a.user_id = u.id LEFT JOIN user_contacts c ON c.user_id = u.id
		 WHERE s.token_hash = $1 AND s.expires_at > now() AND u.status = 'ACTIVE'`, tokenHash(token)).Scan(&se.UserID, &se.CSRF, &se.ExpiresAt, &se.Email, &se.Name, &se.Login, &se.MustChange)
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
