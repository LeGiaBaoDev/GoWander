// Package account holds the app-side accounts of GoWander (ADR-040): registration, signing in by email, phone,
// username or Google, six-digit codes sent by email, sessions, and the signed-in person's own settings.
//
// It never touches the Admin tables: the public API role may write app_users, app_sessions and app_codes and nothing
// on the Admin side (migration 0014 grants exactly that).
package account

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"net/mail"
	"regexp"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/passwords"
)

// Limits (ADR-040 §3.3–3.4).
const (
	SessionTTL     = 60 * 24 * time.Hour // a session lasts 60 days, sliding
	SessionTouch   = time.Hour           // extending it at most once an hour spares writes
	CodeTTL        = 10 * time.Minute
	CodeEvery      = 60 * time.Second // one new code per minute
	CodePerHour    = 5                // and five an hour per account
	CodeAttempts   = 5                // five wrong codes kill it
	MaxFailedLogin = 5                // five wrong passwords
	LockFor        = 15 * time.Minute
	MinPassword    = 8
	MaxPassword    = 128
)

// Purposes of a six-digit code.
const (
	VerifyEmail   = "VERIFY_EMAIL"
	ResetPassword = "RESET_PASSWORD"
)

var (
	// ErrBadInput covers every validation failure the caller can fix; the API answers 400.
	ErrBadInput = errors.New("account: invalid input")
	// ErrLoginFailed is one answer for an unknown identifier, a wrong password, a password-less (Google-only)
	// account and a suspended account, so no one can tell which it was.
	ErrLoginFailed = errors.New("account: sign-in failed")
	// ErrCodeInvalid: wrong or expired code, or one that ran out of attempts.
	ErrCodeInvalid = errors.New("account: code invalid")
	// ErrAccountExists: this email, phone or username is already used (the message stays vague).
	ErrAccountExists = errors.New("account: already used")
	// ErrLinkRequired: a Google address that already has a password account; it is not merged silently.
	ErrLinkRequired = errors.New("account: link required")
	// ErrAuthRequired: no usable bearer session.
	ErrAuthRequired = errors.New("account: sign-in required")
	// ErrRateLimited: too many codes requested for this account.
	ErrRateLimited = errors.New("account: too many requests")
	// ErrNotFound: no such account (used internally; callers answer vaguely).
	ErrNotFound = errors.New("account: not found")
)

// ValidationError is a refusal whose message is meant for the person, not for the log.
type ValidationError struct{ Message string }

func (e ValidationError) Error() string { return "account: " + e.Message }
func (e ValidationError) Unwrap() error { return ErrBadInput }

// bad returns a validation refusal with a sentence the app can show as it is.
func bad(message string) error { return ValidationError{Message: message} }

// LockedError carries the moment the account opens again (API 423).
type LockedError struct{ Until time.Time }

func (e LockedError) Error() string {
	return "account: locked until " + e.Until.Format(time.RFC3339)
}

// User is an app account as the app sees it; it never carries a password hash, a code or a session.
type User struct {
	ID            string `json:"id"`
	Name          string `json:"name"`
	Username      string `json:"username,omitempty"`
	Email         string `json:"email,omitempty"`
	EmailVerified bool   `json:"emailVerified"`
	Phone         string `json:"phone,omitempty"`
	PhoneVerified bool   `json:"phoneVerified"`
	HasPassword   bool   `json:"hasPassword"`
	Google        bool   `json:"google"`
}

// Session is what the app stores (Keychain / Keystore, ADR-040 §3.4).
type Session struct {
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expiresAt"`
}

// RegisterInput is one sign-up by password.
type RegisterInput struct {
	Name, Username, Email, Phone, Password, Device string
}

// GoogleIdentity is the part of a verified Google ID token the accounts need.
type GoogleIdentity struct {
	Sub, Email, Name string
	EmailVerified    bool
}

// Store is the persistence the HTTP handlers need; *PG implements it.
type Store interface {
	Register(ctx context.Context, in RegisterInput) (Session, User, string, error)
	UsernameAvailable(ctx context.Context, username string) (bool, error)
	VerifyEmail(ctx context.Context, userID, code string) (User, error)
	IssueCode(ctx context.Context, userID, purpose string) (string, error)
	Login(ctx context.Context, identifier, password, device string) (Session, User, error)
	GoogleLogin(ctx context.Context, id GoogleIdentity, device string) (Session, User, bool, error)
	LinkGoogle(ctx context.Context, userID string, id GoogleIdentity) (User, error)
	UnlinkGoogle(ctx context.Context, userID string) (User, error)
	Logout(ctx context.Context, token string) error
	LogoutAll(ctx context.Context, userID, keepToken string) error
	Forgot(ctx context.Context, email string) (User, string, bool, error)
	ResetPassword(ctx context.Context, email, code, password string) error
	Me(ctx context.Context, token string) (User, error)
	UpdateProfile(ctx context.Context, userID string, name, username, phone *string) (User, error)
	ChangePassword(ctx context.Context, userID, current, next, keepToken string) error
	Delete(ctx context.Context, userID, password string, viaGoogle bool) error
}

var (
	emailPattern    = regexp.MustCompile(`^[^\s@]+@[^\s@]+\.[^\s@]+$`)
	usernamePattern = regexp.MustCompile(`^[a-z][a-z0-9._]{2,29}$`)
	phonePattern    = regexp.MustCompile(`^\+[1-9][0-9]{7,14}$`)
	digitsOnly      = regexp.MustCompile(`^[0-9]+$`)
)

// NormalizeEmail lower-cases and trims an address and checks it is a plain one; "" when it is not.
func NormalizeEmail(raw string) string {
	e := strings.ToLower(strings.TrimSpace(raw))
	if len(e) > 254 || !emailPattern.MatchString(e) {
		return ""
	}
	if a, err := mail.ParseAddress(e); err != nil || a.Address != e || a.Name != "" {
		return ""
	}
	return e
}

// NormalizePhone returns an E.164 number; a leading 0 is read as a Vietnamese number (+84). "" when it is not one.
func NormalizePhone(raw string) string {
	s := strings.Map(func(r rune) rune {
		if unicode.IsSpace(r) || r == '-' || r == '.' || r == '(' || r == ')' {
			return -1
		}
		return r
	}, strings.TrimSpace(raw))
	switch {
	case s == "":
		return ""
	case strings.HasPrefix(s, "+84"):
		s = "+84" + strings.TrimPrefix(s, "+84")
		s = "+84" + strings.TrimLeft(s[3:], "0")
	case strings.HasPrefix(s, "84") && !strings.HasPrefix(s, "+"):
		s = "+84" + strings.TrimLeft(strings.TrimPrefix(s, "84"), "0")
	case strings.HasPrefix(s, "0"):
		s = "+84" + strings.TrimLeft(s, "0")
	}
	if !phonePattern.MatchString(s) || len(s) > 16 {
		return ""
	}
	return s
}

// NormalizeUsername lower-cases and trims a sign-in name; "" when it does not match the database rule
// (3–30 characters, starts with a letter, a-z 0-9 . _).
func NormalizeUsername(raw string) string {
	u := strings.ToLower(strings.TrimSpace(raw))
	if !usernamePattern.MatchString(u) {
		return ""
	}
	return u
}

// Kind of identifier the person typed into the single sign-in field.
type Kind int

const (
	KindUnknown Kind = iota
	KindEmail
	KindPhone
	KindUsername
)

// Identify reads the single sign-in field: an @ means email, digits and + mean phone, otherwise a username.
func Identify(raw string) (Kind, string) {
	s := strings.TrimSpace(raw)
	switch {
	case strings.Contains(s, "@"):
		if e := NormalizeEmail(s); e != "" {
			return KindEmail, e
		}
	case strings.HasPrefix(s, "+"), digitsOnly.MatchString(strings.NewReplacer(" ", "", "-", "", ".", "").Replace(s)):
		if p := NormalizePhone(s); p != "" {
			return KindPhone, p
		}
	}
	if u := NormalizeUsername(s); u != "" {
		return KindUsername, u
	}
	return KindUnknown, ""
}

// ValidateName: a display name of 1–100 characters, no control characters.
func ValidateName(raw string) (string, error) {
	name := strings.TrimSpace(strings.Join(strings.Fields(raw), " "))
	if name == "" || utf8.RuneCountInString(name) > 100 || strings.ContainsFunc(name, unicode.IsControl) {
		return "", bad("Tên hiển thị dài 1–100 ký tự.")
	}
	return name, nil
}

// ValidatePassword: 8–128 characters, not one of the thousand most used, not the sign-in name or the email local part.
func ValidatePassword(password, username, email string) error {
	n := utf8.RuneCountInString(password)
	if n < MinPassword || n > MaxPassword {
		return bad(fmt.Sprintf("Mật khẩu dài %d–%d ký tự.", MinPassword, MaxPassword))
	}
	if strings.TrimSpace(password) == "" || strings.ContainsFunc(password, unicode.IsControl) {
		return bad("Mật khẩu không hợp lệ.")
	}
	if passwords.Common(password) {
		return bad("Mật khẩu này quá phổ biến, bạn chọn mật khẩu khác nhé.")
	}
	if username != "" && strings.EqualFold(password, username) {
		return bad("Mật khẩu không được giống tên đăng nhập.")
	}
	if email != "" {
		local, _, _ := strings.Cut(email, "@")
		if strings.EqualFold(password, email) || strings.EqualFold(password, local) {
			return bad("Mật khẩu không được giống email của bạn.")
		}
	}
	return nil
}

// newCode returns a uniformly random six-digit code.
func newCode() string {
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(err) // crypto/rand does not fail on supported platforms
	}
	return fmt.Sprintf("%06d", binary.BigEndian.Uint64(b[:])%1_000_000)
}

// codeHash is what the database keeps: the code itself is never stored or logged.
func codeHash(code string) []byte {
	h := sha256.Sum256([]byte(code))
	return h[:]
}

// deviceLabel trims a client-sent device name to what app_sessions.device_label allows.
func deviceLabel(device string) string {
	d := strings.TrimSpace(device)
	if d == "" {
		return "Ứng dụng GoWander"
	}
	if r := []rune(d); len(r) > 100 {
		return string(r[:100])
	}
	return d
}

// suggestUsername turns an email local part into a free sign-in name.
func suggestUsername(email string, taken func(string) (bool, error)) (string, error) {
	local, _, _ := strings.Cut(email, "@")
	base := strings.Map(func(r rune) rune {
		switch {
		case r >= 'a' && r <= 'z', r >= '0' && r <= '9', r == '.':
			return r
		case r >= 'A' && r <= 'Z':
			return r + ('a' - 'A')
		default:
			return '.'
		}
	}, local)
	for strings.HasPrefix(base, ".") {
		base = base[1:]
	}
	if base == "" || base[0] < 'a' || base[0] > 'z' {
		base = "gowander" + base
	}
	if len(base) > 26 {
		base = base[:26]
	}
	if len(base) < 3 {
		base += "user"
	}
	candidate := base
	for i := 2; i <= 60; i++ {
		busy, err := taken(candidate)
		if err != nil {
			return "", err
		}
		if !busy {
			return candidate, nil
		}
		candidate = fmt.Sprintf("%s%d", base, i)
		if len(candidate) > 30 {
			candidate = candidate[:len(base)-len(fmt.Sprint(i))] + fmt.Sprint(i)
		}
	}
	return "", ErrAccountExists
}
