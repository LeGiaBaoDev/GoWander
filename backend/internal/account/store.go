package account

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/passwords"
)

// PG is the Store on the public API pool (role gowander_app).
type PG struct{ Pool *pgxpool.Pool }

// userColumns is the only shape an account is read in: never a hash, a code or a session token.
const userColumns = `id::text, display_name, coalesce(username,''), coalesce(email,''), email_verified_at IS NOT NULL,
       coalesce(phone,''), phone_verified_at IS NOT NULL, password_hash IS NOT NULL, google_sub IS NOT NULL`

func scanUser(row pgx.Row) (User, error) {
	var u User
	err := row.Scan(&u.ID, &u.Name, &u.Username, &u.Email, &u.EmailVerified, &u.Phone, &u.PhoneVerified, &u.HasPassword, &u.Google)
	return u, err
}

func tokenHash(token string) []byte {
	h := sha256.Sum256([]byte(token))
	return h[:]
}

func randomToken() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		panic(err) // crypto/rand does not fail on supported platforms
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}

// timestamp is a nullable time from the database.
type timestamp = *time.Time

// Register creates a password account and its first session, and returns the code to email for verification.
func (s *PG) Register(ctx context.Context, in RegisterInput) (Session, User, string, error) {
	name, err := ValidateName(in.Name)
	if err != nil {
		return Session{}, User{}, "", err
	}
	username := NormalizeUsername(in.Username)
	if username == "" {
		return Session{}, User{}, "", bad("Tên đăng nhập 3–30 ký tự, bắt đầu bằng chữ, chỉ gồm a-z 0-9 . _")
	}
	email := NormalizeEmail(in.Email)
	if email == "" {
		return Session{}, User{}, "", bad("Email chưa đúng, bạn kiểm tra lại nhé.")
	}
	phone := ""
	if strings.TrimSpace(in.Phone) != "" {
		if phone = NormalizePhone(in.Phone); phone == "" {
			return Session{}, User{}, "", bad("Số điện thoại chưa đúng, bạn kiểm tra lại nhé.")
		}
	}
	if err := ValidatePassword(in.Password, username, email); err != nil {
		return Session{}, User{}, "", err
	}

	var session Session
	var user User
	var code string
	err = pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var taken bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM app_users
			 WHERE email = $1 OR username = $2 OR ($3 <> '' AND phone = $3))`, email, username, phone).Scan(&taken); err != nil {
			return err
		}
		if taken {
			return ErrAccountExists
		}
		if err := tx.QueryRow(ctx, `INSERT INTO app_users (display_name, username, email, phone, password_hash)
			VALUES ($1, $2, $3, NULLIF($4,''), $5) RETURNING id::text`,
			name, username, email, phone, passwords.Hash(in.Password)).Scan(&user.ID); err != nil {
			if isUniqueViolation(err) {
				return ErrAccountExists
			}
			return err
		}
		code = newCode()
		if err := insertCode(ctx, tx, user.ID, VerifyEmail, code); err != nil {
			return err
		}
		if session, err = createSession(ctx, tx, user.ID, in.Device); err != nil {
			return err
		}
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, user.ID))
		return err
	})
	if err != nil {
		return Session{}, User{}, "", err
	}
	return session, user, code, nil
}

// UsernameAvailable reports whether a well-formed sign-in name is free.
func (s *PG) UsernameAvailable(ctx context.Context, raw string) (bool, error) {
	username := NormalizeUsername(raw)
	if username == "" {
		return false, nil
	}
	var taken bool
	err := s.Pool.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM app_users WHERE username = $1)`, username).Scan(&taken)
	return !taken, err
}

// insertCode stores one hashed code. An older live code of the same purpose stops working, but its row stays: the
// hourly limit counts every code ever sent, not only the ones still open.
func insertCode(ctx context.Context, tx pgx.Tx, userID, purpose, code string) error {
	if _, err := tx.Exec(ctx, `UPDATE app_codes SET used_at = now() WHERE user_id = $1 AND purpose = $2 AND used_at IS NULL`, userID, purpose); err != nil {
		return err
	}
	_, err := tx.Exec(ctx, `INSERT INTO app_codes (user_id, purpose, code_hash, expires_at) VALUES ($1, $2, $3, now() + $4::interval)`,
		userID, purpose, codeHash(code), CodeTTL.String())
	return err
}

func createSession(ctx context.Context, tx pgx.Tx, userID, device string) (Session, error) {
	token := randomToken()
	var expires time.Time
	if err := tx.QueryRow(ctx, `INSERT INTO app_sessions (token_hash, user_id, device_label, expires_at)
		VALUES ($1, $2, $3, now() + $4::interval) RETURNING expires_at`,
		tokenHash(token), userID, deviceLabel(device), SessionTTL.String()).Scan(&expires); err != nil {
		return Session{}, err
	}
	// Expired sessions are swept here rather than by a job: a few rows per person at most.
	_, _ = tx.Exec(ctx, `DELETE FROM app_sessions WHERE expires_at < now()`)
	return Session{Token: token, ExpiresAt: expires}, nil
}

// IssueCode makes a new six-digit code at most once a minute and five times an hour per account.
func (s *PG) IssueCode(ctx context.Context, userID, purpose string) (string, error) {
	if purpose != VerifyEmail && purpose != ResetPassword {
		return "", bad("Mục đích gửi mã không hợp lệ.")
	}
	var code string
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var recent int
		var last *time.Time
		if err := tx.QueryRow(ctx, `SELECT count(*), max(created_at) FROM app_codes
			WHERE user_id = $1 AND created_at > now() - interval '1 hour'`, userID).Scan(&recent, &last); err != nil {
			return err
		}
		if recent >= CodePerHour || (last != nil && time.Since(*last) < CodeEvery) {
			return ErrRateLimited
		}
		code = newCode()
		return insertCode(ctx, tx, userID, purpose, code)
	})
	if err != nil {
		return "", err
	}
	return code, nil
}

// verifyCode checks and consumes the live code of a purpose. A wrong code is reported as ok=false after the wasted
// attempt is written, so the caller must COMMIT before answering: guessing is limited to five tries.
func verifyCode(ctx context.Context, tx pgx.Tx, userID, purpose, code string) (ok bool, err error) {
	var hash []byte
	var attempts int
	err = tx.QueryRow(ctx, `SELECT code_hash, attempts FROM app_codes
		WHERE user_id = $1 AND purpose = $2 AND used_at IS NULL AND expires_at > now()
		ORDER BY created_at DESC LIMIT 1 FOR UPDATE`, userID, purpose).Scan(&hash, &attempts)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	if err != nil || attempts >= CodeAttempts {
		return false, err
	}
	if subtle.ConstantTimeCompare(hash, codeHash(strings.TrimSpace(code))) != 1 {
		_, err = tx.Exec(ctx, `UPDATE app_codes SET attempts = attempts + 1 WHERE user_id = $1 AND purpose = $2 AND used_at IS NULL`, userID, purpose)
		return false, err
	}
	_, err = tx.Exec(ctx, `UPDATE app_codes SET used_at = now() WHERE user_id = $1 AND purpose = $2 AND used_at IS NULL`, userID, purpose)
	return err == nil, err
}

// VerifyEmail consumes the verification code and marks the address verified.
func (s *PG) VerifyEmail(ctx context.Context, userID, code string) (User, error) {
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return User{}, err
	}
	defer tx.Rollback(ctx)
	ok, err := verifyCode(ctx, tx, userID, VerifyEmail, code)
	if err != nil {
		return User{}, err
	}
	if !ok { // the spent attempt must survive: commit before saying no
		if err := tx.Commit(ctx); err != nil {
			return User{}, err
		}
		return User{}, ErrCodeInvalid
	}
	if _, err := tx.Exec(ctx, `UPDATE app_users SET email_verified_at = now(), updated_at = now() WHERE id = $1`, userID); err != nil {
		return User{}, err
	}
	user, err := scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, userID))
	if err != nil {
		return User{}, err
	}
	return user, tx.Commit(ctx)
}

// Login signs in with email, phone or username. Every refusal is ErrLoginFailed, except a locked account, whose
// reopen time the app shows (423).
func (s *PG) Login(ctx context.Context, identifier, password, device string) (Session, User, error) {
	kind, value := Identify(identifier)
	if kind == KindUnknown {
		passwords.Check(passwords.Dummy, password)
		return Session{}, User{}, ErrLoginFailed
	}
	where := map[Kind]string{KindEmail: "email = $1", KindPhone: "phone = $1", KindUsername: "username = $1"}[kind]

	u, err := scanUser(s.Pool.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE `+where, value))
	if errors.Is(err, pgx.ErrNoRows) {
		passwords.Check(passwords.Dummy, password)
		return Session{}, User{}, ErrLoginFailed
	}
	if err != nil {
		return Session{}, User{}, err
	}
	var hash string
	var failed int
	var locked timestamp
	var status string
	if err := s.Pool.QueryRow(ctx, `SELECT coalesce(password_hash,''), failed_logins, locked_until, status FROM app_users WHERE id = $1`, u.ID).
		Scan(&hash, &failed, &locked, &status); err != nil {
		return Session{}, User{}, err
	}
	now := time.Now()
	switch {
	case status != "ACTIVE", kind == KindPhone && !u.EmailVerified, hash == "":
		// A phone may only sign in once the email is verified (§3.2): a stranger may have registered the number.
		passwords.Check(passwords.Dummy, password)
		return Session{}, User{}, ErrLoginFailed
	case locked != nil && locked.After(now):
		passwords.Check(passwords.Dummy, password)
		return Session{}, User{}, LockedError{Until: *locked}
	}
	if !passwords.Check(hash, password) {
		count := failed
		if locked != nil { // the lock ran out: count from one again
			count = 0
		}
		count++
		var until any
		if count >= MaxFailedLogin {
			until = now.Add(LockFor)
			count = 0
		}
		if _, err := s.Pool.Exec(ctx, `UPDATE app_users SET failed_logins = $2, locked_until = $3, updated_at = now() WHERE id = $1`, u.ID, count, until); err != nil {
			return Session{}, User{}, err
		}
		return Session{}, User{}, ErrLoginFailed
	}

	var session Session
	err = pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `UPDATE app_users SET failed_logins = 0, locked_until = NULL, last_login_at = now(), updated_at = now() WHERE id = $1`, u.ID); err != nil {
			return err
		}
		var err error
		session, err = createSession(ctx, tx, u.ID, device)
		return err
	})
	return session, u, err
}

// GoogleLogin signs in with a verified Google identity, creating the account on the first visit. A Google address that
// already belongs to a password account is not merged silently: ErrLinkRequired.
func (s *PG) GoogleLogin(ctx context.Context, id GoogleIdentity, device string) (Session, User, bool, error) {
	if id.Sub == "" || id.Email == "" || !id.EmailVerified {
		return Session{}, User{}, false, ErrGoogleToken
	}
	var session Session
	var user User
	created := false
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var id2, status string
		err := tx.QueryRow(ctx, `SELECT id::text, status FROM app_users WHERE google_sub = $1`, id.Sub).Scan(&id2, &status)
		switch {
		case err == nil:
			if status != "ACTIVE" {
				return ErrLoginFailed
			}
			user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, id2))
			if err != nil {
				return err
			}
		case errors.Is(err, pgx.ErrNoRows):
			var existing bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM app_users WHERE email = $1)`, id.Email).Scan(&existing); err != nil {
				return err
			}
			if existing {
				return ErrLinkRequired
			}
			name := id.Name
			if validated, err := ValidateName(name); err == nil {
				name = validated
			} else {
				name, _, _ = strings.Cut(id.Email, "@")
			}
			username, err := suggestUsername(id.Email, func(u string) (bool, error) {
				var busy bool
				err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM app_users WHERE username = $1)`, u).Scan(&busy)
				return busy, err
			})
			if err != nil {
				return err
			}
			if err := tx.QueryRow(ctx, `INSERT INTO app_users (display_name, username, email, email_verified_at, google_sub)
				VALUES ($1, $2, $3, now(), $4) RETURNING id::text`, name, username, id.Email, id.Sub).Scan(&id2); err != nil {
				if isUniqueViolation(err) {
					return ErrLinkRequired
				}
				return err
			}
			created = true
			if user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, id2)); err != nil {
				return err
			}
		default:
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE app_users SET last_login_at = now(), failed_logins = 0, locked_until = NULL, updated_at = now() WHERE id = $1`, user.ID); err != nil {
			return err
		}
		session, err = createSession(ctx, tx, user.ID, device)
		return err
	})
	if err != nil {
		return Session{}, User{}, false, err
	}
	return session, user, created, nil
}

// LinkGoogle attaches a Google identity to the signed-in account.
func (s *PG) LinkGoogle(ctx context.Context, userID string, id GoogleIdentity) (User, error) {
	var user User
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var taken bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM app_users WHERE google_sub = $1 AND id <> $2)`, id.Sub, userID).Scan(&taken); err != nil {
			return err
		}
		if taken {
			return ErrAccountExists
		}
		if _, err := tx.Exec(ctx, `UPDATE app_users SET google_sub = $2, updated_at = now() WHERE id = $1`, userID, id.Sub); err != nil {
			if isUniqueViolation(err) {
				return ErrAccountExists
			}
			return err
		}
		var err error
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, userID))
		return err
	})
	return user, err
}

// UnlinkGoogle removes the link; refused when the account would be left without any way to sign in.
func (s *PG) UnlinkGoogle(ctx context.Context, userID string) (User, error) {
	var user User
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE app_users SET google_sub = NULL, updated_at = now()
			WHERE id = $1 AND google_sub IS NOT NULL AND password_hash IS NOT NULL`, userID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return bad("Bạn đặt mật khẩu trước khi huỷ liên kết Google nhé.")
		}
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, userID))
		return err
	})
	return user, err
}

// Logout revokes the current session.
func (s *PG) Logout(ctx context.Context, token string) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM app_sessions WHERE token_hash = $1`, tokenHash(token))
	return err
}

// LogoutAll revokes every session of the account, optionally keeping the current one.
func (s *PG) LogoutAll(ctx context.Context, userID, keepToken string) error {
	if keepToken == "" {
		_, err := s.Pool.Exec(ctx, `DELETE FROM app_sessions WHERE user_id = $1`, userID)
		return err
	}
	_, err := s.Pool.Exec(ctx, `DELETE FROM app_sessions WHERE user_id = $1 AND token_hash <> $2`, userID, tokenHash(keepToken))
	return err
}

// Forgot makes a reset code when the address has an active account. found=false covers an unknown address and a
// request that came too soon: the API answers the same either way.
func (s *PG) Forgot(ctx context.Context, rawEmail string) (User, string, bool, error) {
	email := NormalizeEmail(rawEmail)
	if email == "" {
		return User{}, "", false, nil
	}
	user, err := scanUser(s.Pool.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE email = $1 AND status = 'ACTIVE'`, email))
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, "", false, nil
	}
	if err != nil {
		return User{}, "", false, err
	}
	code, err := s.IssueCode(ctx, user.ID, ResetPassword)
	if errors.Is(err, ErrRateLimited) {
		return User{}, "", false, nil
	}
	if err != nil {
		return User{}, "", false, err
	}
	return user, code, true, nil
}

// ResetPassword sets a new password from a reset code and signs the account out everywhere.
func (s *PG) ResetPassword(ctx context.Context, rawEmail, code, password string) error {
	email := NormalizeEmail(rawEmail)
	if email == "" {
		return ErrCodeInvalid
	}
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	var userID, username string
	err = tx.QueryRow(ctx, `SELECT id::text, coalesce(username,'') FROM app_users WHERE email = $1 AND status = 'ACTIVE'`, email).Scan(&userID, &username)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrCodeInvalid
	}
	if err != nil {
		return err
	}
	ok, err := verifyCode(ctx, tx, userID, ResetPassword, code)
	if err != nil {
		return err
	}
	if !ok {
		if err := tx.Commit(ctx); err != nil { // keep the spent attempt
			return err
		}
		return ErrCodeInvalid
	}
	if err := ValidatePassword(password, username, email); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `UPDATE app_users SET password_hash = $2, failed_logins = 0, locked_until = NULL, updated_at = now() WHERE id = $1`,
		userID, passwords.Hash(password)); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `DELETE FROM app_sessions WHERE user_id = $1`, userID); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

// Me reads the account of a session and extends it, at most once an hour.
func (s *PG) Me(ctx context.Context, token string) (User, error) {
	var u User
	var lastSeen time.Time
	err := s.Pool.QueryRow(ctx, `SELECT `+userColumns+`, s.last_seen_at FROM app_sessions s JOIN app_users u ON u.id = s.user_id
		WHERE s.token_hash = $1 AND s.expires_at > now() AND u.status = 'ACTIVE'`, tokenHash(token)).Scan(
		&u.ID, &u.Name, &u.Username, &u.Email, &u.EmailVerified, &u.Phone, &u.PhoneVerified, &u.HasPassword, &u.Google, &lastSeen)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrAuthRequired
	}
	if err != nil {
		return User{}, err
	}
	if time.Since(lastSeen) > SessionTouch {
		_, _ = s.Pool.Exec(ctx, `UPDATE app_sessions SET last_seen_at = now(), expires_at = now() + $2::interval WHERE token_hash = $1`,
			tokenHash(token), SessionTTL.String())
	}
	return u, nil
}

// UpdateProfile changes the name, the sign-in name and/or the phone number (nil = leave as it is).
func (s *PG) UpdateProfile(ctx context.Context, userID string, name, username, phone *string) (User, error) {
	var user User
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var currentName, currentUsername, currentEmail, currentPhone string
		if err := tx.QueryRow(ctx, `SELECT display_name, coalesce(username,''), coalesce(email,''), coalesce(phone,'')
			FROM app_users WHERE id = $1 AND status = 'ACTIVE'`, userID).Scan(&currentName, &currentUsername, &currentEmail, &currentPhone); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrAuthRequired
			}
			return err
		}
		newName, newUsername, newPhone := currentName, currentUsername, currentPhone
		if name != nil {
			validated, err := ValidateName(*name)
			if err != nil {
				return err
			}
			newName = validated
		}
		if username != nil {
			newUsername = NormalizeUsername(*username)
			if newUsername == "" {
				return bad("Tên đăng nhập 3–30 ký tự, bắt đầu bằng chữ, chỉ gồm a-z 0-9 . _")
			}
		}
		if phone != nil {
			newPhone = ""
			if strings.TrimSpace(*phone) != "" {
				if newPhone = NormalizePhone(*phone); newPhone == "" {
					return bad("Số điện thoại chưa đúng, bạn kiểm tra lại nhé.")
				}
			}
		}
		if _, err := tx.Exec(ctx, `UPDATE app_users SET display_name = $2, username = $3, phone = NULLIF($4,''), updated_at = now()
			WHERE id = $1`, userID, newName, newUsername, newPhone); err != nil {
			if isUniqueViolation(err) {
				return ErrAccountExists
			}
			return err
		}
		var err error
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM app_users WHERE id = $1`, userID))
		return err
	})
	return user, err
}

// ChangePassword replaces the password, or sets the first one on a Google-only account, and signs out other devices.
func (s *PG) ChangePassword(ctx context.Context, userID, current, next, keepToken string) error {
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var hash, username, email string
		if err := tx.QueryRow(ctx, `SELECT coalesce(password_hash,''), coalesce(username,''), coalesce(email,'')
			FROM app_users WHERE id = $1 AND status = 'ACTIVE' FOR UPDATE`, userID).Scan(&hash, &username, &email); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrAuthRequired
			}
			return err
		}
		if hash != "" && !passwords.Check(hash, current) {
			return bad("Mật khẩu hiện tại không đúng.")
		}
		if err := ValidatePassword(next, username, email); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE app_users SET password_hash = $2, failed_logins = 0, locked_until = NULL, updated_at = now() WHERE id = $1`,
			userID, passwords.Hash(next)); err != nil {
			return err
		}
		if keepToken == "" {
			_, err := tx.Exec(ctx, `DELETE FROM app_sessions WHERE user_id = $1`, userID)
			return err
		}
		_, err := tx.Exec(ctx, `DELETE FROM app_sessions WHERE user_id = $1 AND token_hash <> $2`, userID, tokenHash(keepToken))
		return err
	})
}

// Delete removes every personal field at once (ADR-040 §3.8). Feedback keeps its words but loses the person.
func (s *PG) Delete(ctx context.Context, userID, password string, viaGoogle bool) error {
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var hash string
		var status string
		if err := tx.QueryRow(ctx, `SELECT coalesce(password_hash,''), status FROM app_users WHERE id = $1 FOR UPDATE`, userID).Scan(&hash, &status); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrAuthRequired
			}
			return err
		}
		if status == "DELETED" {
			return ErrAuthRequired
		}
		if !viaGoogle && hash != "" && !passwords.Check(hash, password) {
			return bad("Mật khẩu không đúng.")
		}
		if _, err := tx.Exec(ctx, `UPDATE feedback_reports SET app_user_id = NULL, sender_name = 'Tài khoản đã xoá' WHERE app_user_id = $1`, userID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `DELETE FROM app_codes WHERE user_id = $1`, userID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `DELETE FROM app_sessions WHERE user_id = $1`, userID); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `UPDATE app_users SET status = 'DELETED', display_name = 'Tài khoản đã xoá', username = NULL,
			email = NULL, phone = NULL, password_hash = NULL, google_sub = NULL, email_verified_at = NULL,
			phone_verified_at = NULL, failed_logins = 0, locked_until = NULL, updated_at = now() WHERE id = $1`, userID)
		return err
	})
}
