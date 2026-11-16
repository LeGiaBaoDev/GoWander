//go:build integration

package account

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/golang-migrate/migrate/v4"
	"github.com/jackc/pgx/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

// setup empties the app account tables of the TEST database and returns the store (public role) plus an owner
// connection for fixtures the API cannot make itself (for example a lock that has already run out).
func setup(t *testing.T) (*PG, *pgx.Conn) {
	t.Helper()
	ctx := context.Background()
	owner, app := os.Getenv("MIGRATE_DATABASE_URL"), os.Getenv("DATABASE_URL")
	if owner == "" || app == "" {
		t.Fatal("MIGRATE_DATABASE_URL and DATABASE_URL must be set (load ../.env first)")
	}
	owner, app = database.TestDatabaseURL(owner), database.TestDatabaseURL(app)
	m, err := database.NewMigrator(owner)
	if err != nil {
		t.Fatal(err)
	}
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatal(err)
	}
	m.Close()
	conn, err := pgx.Connect(ctx, owner)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close(ctx) })
	if _, err := conn.Exec(ctx, `TRUNCATE feedback_reports, app_codes, app_sessions, app_users CASCADE`); err != nil {
		t.Fatal(err)
	}
	pool, err := database.NewPool(ctx, app)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	return &PG{Pool: pool}, conn
}

func register(t *testing.T, s *PG, name, username, email, phone string) (Session, User, string) {
	t.Helper()
	session, user, code, err := s.Register(context.Background(), RegisterInput{
		Name: name, Username: username, Email: email, Phone: phone, Password: "cà phê sữa đá 47", Device: "iPhone",
	})
	if err != nil {
		t.Fatalf("register %s: %v", username, err)
	}
	return session, user, code
}

func TestRegisterVerifyAndSignIn(t *testing.T) {
	s, owner := setup(t)
	ctx := context.Background()

	session, user, code := register(t, s, "Lan Anh", "lananh", "LanAnh@Example.com", "0912 345 678")
	if user.Email != "lananh@example.com" || user.Phone != "+84912345678" || user.PhoneVerified || user.EmailVerified || !user.HasPassword || user.Google {
		t.Fatalf("user = %+v", user)
	}
	if code == "" || len(session.Token) < 40 || time.Until(session.ExpiresAt) < 59*24*time.Hour {
		t.Fatalf("session/code = %+v %q", session, code)
	}
	// Nothing personal is kept in the clear.
	var hash []byte
	if err := owner.QueryRow(ctx, `SELECT code_hash FROM app_codes WHERE user_id = $1`, user.ID).Scan(&hash); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(hash), code) {
		t.Fatal("the code is stored as it was sent")
	}

	if _, _, _, err := s.Register(ctx, RegisterInput{Name: "Khác", Username: "lananh2", Email: "lananh@example.com", Password: "cà phê sữa đá 47"}); !errors.Is(err, ErrAccountExists) {
		t.Fatalf("duplicate email: %v", err)
	}
	if _, _, _, err := s.Register(ctx, RegisterInput{Name: "Khác", Username: "lananh", Email: "khac@example.com", Password: "cà phê sữa đá 47"}); !errors.Is(err, ErrAccountExists) {
		t.Fatalf("duplicate username: %v", err)
	}

	// The session opens /me before the address is verified.
	if me, err := s.Me(ctx, session.Token); err != nil || me.ID != user.ID || me.EmailVerified {
		t.Fatalf("me before verifying: %+v %v", me, err)
	}
	// A phone may not sign in until the email is verified (§3.2).
	if _, _, err := s.Login(ctx, "0912 345 678", "cà phê sữa đá 47", ""); !errors.Is(err, ErrLoginFailed) {
		t.Fatalf("phone sign-in before verification: %v", err)
	}

	if _, err := s.VerifyEmail(ctx, user.ID, "000000"); !errors.Is(err, ErrCodeInvalid) {
		t.Fatalf("wrong code: %v", err)
	}
	verified, err := s.VerifyEmail(ctx, user.ID, code)
	if err != nil || !verified.EmailVerified {
		t.Fatalf("verify: %+v %v", verified, err)
	}
	if _, err := s.VerifyEmail(ctx, user.ID, code); !errors.Is(err, ErrCodeInvalid) {
		t.Fatalf("a used code must not work twice: %v", err)
	}

	// All three shapes of the sign-in field reach the same account.
	for _, identifier := range []string{"lananh@example.com", "0912 345 678", "LanAnh", "+84 912 345 678"} {
		token, _, err := s.Login(ctx, identifier, "cà phê sữa đá 47", "Android")
		if err != nil || token.Token == "" {
			t.Fatalf("login %q: %v", identifier, err)
		}
	}
	if _, _, err := s.Login(ctx, "lananh@example.com", "sai mật khẩu 47", ""); !errors.Is(err, ErrLoginFailed) {
		t.Fatalf("wrong password: %v", err)
	}
	if _, _, err := s.Login(ctx, "khongco@example.com", "cà phê sữa đá 47", ""); !errors.Is(err, ErrLoginFailed) {
		t.Fatalf("unknown email: %v", err)
	}
}

func TestCodeAttemptsAndResend(t *testing.T) {
	s, owner := setup(t)
	ctx := context.Background()
	_, user, code := register(t, s, "Lan Anh", "lananh", "lananh@example.com", "")

	// A second code cannot be asked for within a minute.
	if _, err := s.IssueCode(ctx, user.ID, VerifyEmail); !errors.Is(err, ErrRateLimited) {
		t.Fatalf("immediate resend: %v", err)
	}
	// Five wrong attempts kill the code, even if the sixth is right.
	for range CodeAttempts {
		if _, err := s.VerifyEmail(ctx, user.ID, "999999"); !errors.Is(err, ErrCodeInvalid) {
			t.Fatalf("wrong attempt: %v", err)
		}
	}
	if _, err := s.VerifyEmail(ctx, user.ID, code); !errors.Is(err, ErrCodeInvalid) {
		t.Fatalf("the right code must be dead after five wrong ones: %v", err)
	}
	// Once the minute has passed, a new code works (the owner moves the clock, the test does not wait).
	if _, err := owner.Exec(ctx, `UPDATE app_codes SET created_at = now() - interval '2 minutes'`); err != nil {
		t.Fatal(err)
	}
	fresh, err := s.IssueCode(ctx, user.ID, VerifyEmail)
	if err != nil || fresh == code {
		t.Fatalf("new code = %q %v", fresh, err)
	}
	if _, err := s.VerifyEmail(ctx, user.ID, fresh); err != nil {
		t.Fatalf("verify with the new code: %v", err)
	}
	// At most five codes an hour.
	if _, err := owner.Exec(ctx, `UPDATE app_codes SET created_at = now() - interval '2 minutes'`); err != nil {
		t.Fatal(err)
	}
	for i := range CodePerHour - 2 {
		if _, err := s.IssueCode(ctx, user.ID, VerifyEmail); err != nil {
			t.Fatalf("code %d: %v", i, err)
		}
		_, _ = owner.Exec(ctx, `UPDATE app_codes SET created_at = now() - interval '2 minutes'`)
	}
	if _, err := s.IssueCode(ctx, user.ID, VerifyEmail); !errors.Is(err, ErrRateLimited) {
		t.Fatalf("sixth code in the hour: %v", err)
	}
}

func TestLockAfterFiveWrongPasswords(t *testing.T) {
	s, owner := setup(t)
	ctx := context.Background()
	_, user, code := register(t, s, "Lan Anh", "lananh", "lananh@example.com", "")
	if _, err := s.VerifyEmail(ctx, user.ID, code); err != nil {
		t.Fatal(err)
	}
	for i := range MaxFailedLogin {
		if _, _, err := s.Login(ctx, "lananh", "sai mật khẩu 47", ""); !errors.Is(err, ErrLoginFailed) {
			t.Fatalf("attempt %d: %v", i, err)
		}
	}
	_, _, err := s.Login(ctx, "lananh", "cà phê sữa đá 47", "")
	var locked LockedError
	if !errors.As(err, &locked) || time.Until(locked.Until) < 14*time.Minute {
		t.Fatalf("after five wrong passwords: %v", err)
	}
	// The lock runs out and the counter starts from zero again.
	if _, err := owner.Exec(ctx, `UPDATE app_users SET locked_until = now() - interval '1 second' WHERE id = $1`, user.ID); err != nil {
		t.Fatal(err)
	}
	if _, _, err := s.Login(ctx, "lananh", "cà phê sữa đá 47", ""); err != nil {
		t.Fatalf("after the lock: %v", err)
	}
}

func TestForgotAndReset(t *testing.T) {
	s, owner := setup(t)
	ctx := context.Background()
	session, user, code := register(t, s, "Lan Anh", "lananh", "lananh@example.com", "")
	if _, err := s.VerifyEmail(ctx, user.ID, code); err != nil {
		t.Fatal(err)
	}
	// The verification code still counts for the one-a-minute rule: clear it as the owner would after a wait.
	if _, err := owner.Exec(ctx, `UPDATE app_codes SET created_at = now() - interval '2 minutes'`); err != nil {
		t.Fatal(err)
	}

	foundUser, resetCode, found, err := s.Forgot(ctx, "LanAnh@Example.com")
	if err != nil || !found || resetCode == "" || foundUser.ID != user.ID {
		t.Fatalf("forgot: %+v %q %v %v", foundUser, resetCode, found, err)
	}
	if _, _, found, _ := s.Forgot(ctx, "khongco@example.com"); found {
		t.Fatal("an unknown address must report nothing")
	}
	if _, _, found, _ := s.Forgot(ctx, "lananh@example.com"); found {
		t.Fatal("a second request within a minute must stay silent")
	}

	if err := s.ResetPassword(ctx, "lananh@example.com", "000000", "mật khẩu mới 2026"); !errors.Is(err, ErrCodeInvalid) {
		t.Fatalf("wrong reset code: %v", err)
	}
	if err := s.ResetPassword(ctx, "lananh@example.com", resetCode, "123456"); !errors.Is(err, ErrBadInput) {
		t.Fatalf("a common password must be refused: %v", err)
	}
	if err := s.ResetPassword(ctx, "lananh@example.com", resetCode, "mật khẩu mới 2026"); err != nil {
		t.Fatalf("reset: %v", err)
	}
	// Every session is gone, and the new password works.
	if _, err := s.Me(ctx, session.Token); !errors.Is(err, ErrAuthRequired) {
		t.Fatalf("old session after reset: %v", err)
	}
	if _, _, err := s.Login(ctx, "lananh", "mật khẩu mới 2026", ""); err != nil {
		t.Fatalf("login with the new password: %v", err)
	}
	if _, _, err := s.Login(ctx, "lananh.nguyen", "cà phê sữa đá 47", ""); !errors.Is(err, ErrLoginFailed) {
		t.Fatalf("the old password still works: %v", err)
	}
}

func TestGoogleSignIn(t *testing.T) {
	s, _ := setup(t)
	ctx := context.Background()
	id := GoogleIdentity{Sub: "10876543210", Email: "lananh@gmail.com", Name: "Lan Anh", EmailVerified: true}

	session, user, created, err := s.GoogleLogin(ctx, id, "iPhone")
	if err != nil || !created || user.Email != "lananh@gmail.com" || !user.EmailVerified || !user.Google || user.HasPassword {
		t.Fatalf("first google sign-in: %+v %v %v", user, created, err)
	}
	if user.Username == "" {
		t.Fatal("a google account needs a suggested sign-in name")
	}
	if session.Token == "" {
		t.Fatal("no session")
	}
	// The same Google account comes back to the same person.
	_, again, created, err := s.GoogleLogin(ctx, id, "Android")
	if err != nil || created || again.ID != user.ID {
		t.Fatalf("second google sign-in: %+v %v %v", again, created, err)
	}
	// A Google address that already has a password account is not merged silently.
	_, _, code := register(t, s, "Mai", "mai", "mai@example.com", "")
	_ = code
	if _, _, _, err := s.GoogleLogin(ctx, GoogleIdentity{Sub: "other", Email: "mai@example.com", EmailVerified: true}, ""); !errors.Is(err, ErrLinkRequired) {
		t.Fatalf("link required: %v", err)
	}
	// Linking a Google identity to a password account, then unlinking after setting a password.
	var mai User
	if err := s.Pool.QueryRow(ctx, `SELECT id::text FROM app_users WHERE email = 'mai@example.com'`).Scan(&mai.ID); err != nil {
		t.Fatal(err)
	}
	linked, err := s.LinkGoogle(ctx, mai.ID, GoogleIdentity{Sub: "sub-mai", Email: "mai@example.com", EmailVerified: true})
	if err != nil || !linked.Google {
		t.Fatalf("link: %+v %v", linked, err)
	}
	if _, err := s.UnlinkGoogle(ctx, mai.ID); err != nil {
		t.Fatalf("unlink: %v", err)
	}
}

func TestProfilePasswordAndDelete(t *testing.T) {
	s, owner := setup(t)
	ctx := context.Background()
	session, user, code := register(t, s, "Lan Anh", "lananh", "lananh@example.com", "")
	if _, err := s.VerifyEmail(ctx, user.ID, code); err != nil {
		t.Fatal(err)
	}
	other, _, _ := func() (Session, User, string) { return register(t, s, "Mai", "mai", "mai@example.com", "") }()
	_ = other

	name, username := "Lan Anh Nguyễn", "lananh.nguyen"
	updated, err := s.UpdateProfile(ctx, user.ID, &name, &username, nil)
	if err != nil || updated.Name != name || updated.Username != username || updated.Phone != "" {
		t.Fatalf("update profile: %+v %v", updated, err)
	}
	taken := "mai"
	if _, err := s.UpdateProfile(ctx, user.ID, nil, &taken, nil); !errors.Is(err, ErrAccountExists) {
		t.Fatalf("username taken: %v", err)
	}
	phone := "0912 345 678"
	if updated, err = s.UpdateProfile(ctx, user.ID, nil, nil, &phone); err != nil || updated.Phone != "+84912345678" || updated.PhoneVerified {
		t.Fatalf("phone: %+v %v", updated, err)
	}
	if _, err := s.UpdateProfile(ctx, user.ID, nil, nil, ptr("")); err != nil {
		t.Fatalf("clearing the phone: %v", err)
	}

	// Changing the password signs out the other device but keeps this one.
	second, _, err := s.Login(ctx, "lananh.nguyen", "cà phê sữa đá 47", "Android")
	if err != nil {
		t.Fatal(err)
	}
	if err := s.ChangePassword(ctx, user.ID, "sai", "mật khẩu mới 2026", session.Token); !errors.Is(err, ErrBadInput) {
		t.Fatalf("wrong current password: %v", err)
	}
	if err := s.ChangePassword(ctx, user.ID, "cà phê sữa đá 47", "mật khẩu mới 2026", session.Token); err != nil {
		t.Fatalf("change password: %v", err)
	}
	if _, err := s.Me(ctx, second.Token); !errors.Is(err, ErrAuthRequired) {
		t.Fatalf("the other device must be signed out: %v", err)
	}
	if _, err := s.Me(ctx, session.Token); err != nil {
		t.Fatalf("this device must stay: %v", err)
	}

	// Feedback written while signed in loses the person, not the words.
	var feedbackID string
	// Written as the owner: the public role may add feedbacks but never read them back, not even with RETURNING.
	if err := owner.QueryRow(ctx, `INSERT INTO feedback_reports (id, kind, message, sender_name, sender_email, app_user_id)
		VALUES (uuidv7(), 'OTHER', 'Thử xoá tài khoản', $1, $2, $3) RETURNING id::text`, user.Name, user.Email, user.ID).Scan(&feedbackID); err != nil {
		t.Fatal(err)
	}
	if err := s.Delete(ctx, user.ID, "sai mật khẩu", false); !errors.Is(err, ErrBadInput) {
		t.Fatalf("delete without the password: %v", err)
	}
	if err := s.Delete(ctx, user.ID, "mật khẩu mới 2026", false); err != nil {
		t.Fatalf("delete: %v", err)
	}
	if _, err := s.Me(ctx, session.Token); !errors.Is(err, ErrAuthRequired) {
		t.Fatalf("a deleted account keeps a session: %v", err)
	}
	var status, senderName, email, username2, phone2 string
	var appUserID *string
	if err := owner.QueryRow(ctx, `SELECT status, coalesce(email,''), coalesce(phone,''), coalesce(username,''), display_name FROM app_users WHERE id = $1`, user.ID).
		Scan(&status, &email, &phone2, &username2, &senderName); err != nil {
		t.Fatal(err)
	}
	if status != "DELETED" || email != "" || phone2 != "" || username2 != "" || senderName != "Tài khoản đã xoá" {
		t.Fatalf("deleted account still holds personal data: %q %q %q %q %q", status, email, phone2, username2, senderName)
	}
	if err := owner.QueryRow(ctx, `SELECT sender_name, app_user_id::text FROM feedback_reports WHERE id = $1`, feedbackID).Scan(&senderName, &appUserID); err != nil {
		t.Fatal(err)
	}
	if senderName != "Tài khoản đã xoá" || appUserID != nil {
		t.Fatalf("feedback after deleting the account: %q %v", senderName, appUserID)
	}
	// The email is free again.
	if _, _, err := s.Login(ctx, "lananh@example.com", "mật khẩu mới 2026", ""); !errors.Is(err, ErrLoginFailed) {
		t.Fatalf("a deleted account still signs in: %v", err)
	}
	if _, _, _, err := s.Register(ctx, RegisterInput{Name: "Mới", Username: "lananh", Email: "lananh@example.com", Password: "cà phê sữa đá 47"}); err != nil {
		t.Fatalf("registering the freed email and name: %v", err)
	}
}

func TestPublicRoleCannotReadAdminTables(t *testing.T) {
	s, _ := setup(t)
	ctx := context.Background()
	for _, query := range []string{`SELECT count(*) FROM admin_credentials`, `SELECT count(*) FROM user_contacts`, `SELECT count(*) FROM admin_sessions`} {
		var n int
		if err := s.Pool.QueryRow(ctx, query).Scan(&n); err == nil {
			t.Fatalf("the public role can run %q", query)
		}
	}
}

func ptr(s string) *string { return &s }

// AUTH-3: suspending an account in the Admin stops its sessions at the next call, without deleting any row.
func TestSuspendedAccountIsOut(t *testing.T) {
	s, owner := setup(t)
	ctx := context.Background()
	session, user, code := register(t, s, "Lan Anh", "lananh", "lananh@example.com", "")
	if _, err := s.VerifyEmail(ctx, user.ID, code); err != nil {
		t.Fatal(err)
	}
	if _, err := owner.Exec(ctx, `UPDATE app_users SET status = 'SUSPENDED' WHERE id = $1`, user.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Me(ctx, session.Token); !errors.Is(err, ErrAuthRequired) {
		t.Fatalf("a suspended session still works: %v", err)
	}
	if _, _, err := s.Login(ctx, "lananh", "cà phê sữa đá 47", ""); !errors.Is(err, ErrLoginFailed) {
		t.Fatalf("a suspended account signed in: %v", err)
	}
	if _, err := owner.Exec(ctx, `UPDATE app_users SET status = 'ACTIVE' WHERE id = $1`, user.ID); err != nil {
		t.Fatal(err)
	}
	if _, _, err := s.Login(ctx, "lananh", "cà phê sữa đá 47", ""); err != nil {
		t.Fatalf("restored account: %v", err)
	}
}
