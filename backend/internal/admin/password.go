package admin

import (
	"crypto/rand"
	"encoding/base64"
	"regexp"
	"strings"
	"unicode/utf8"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/passwords"
)

// HashPassword returns the PHC-style string stored in admin_credentials.password_hash (ADR-039).
// The hashing itself lives in platform/passwords so app accounts (ADR-040) share it.
func HashPassword(password string) string { return passwords.Hash(password) }

// CheckPassword compares in constant time, using the parameters stored with the hash.
func CheckPassword(encoded, password string) bool { return passwords.Check(encoded, password) }

// dummyHash is checked when a login does not exist, so a wrong login takes as long as a wrong password.
var dummyHash = passwords.Dummy

var loginPattern = regexp.MustCompile(`^[a-z0-9][a-z0-9._-]{2,39}$`)

// NormalizeLogin lower-cases and trims a login; "" when it is not 3–40 characters of a-z 0-9 . _ - (database CHECK).
func NormalizeLogin(raw string) string {
	l := strings.ToLower(strings.TrimSpace(raw))
	if !loginPattern.MatchString(l) {
		return ""
	}
	return l
}

// validatePassword: the message is shown to the operator.
func validatePassword(login, password string) error {
	n := utf8.RuneCountInString(password)
	switch {
	case n < 12 || n > 128:
		return bad("mật khẩu dài từ 12 đến 128 ký tự")
	case strings.EqualFold(strings.TrimSpace(password), login):
		return bad("mật khẩu không được giống mã tài khoản")
	}
	return nil
}

// TemporaryPassword is printed once by cmd/admin; the operator must change it at the first sign-in.
func TemporaryPassword() string {
	b := make([]byte, 12)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return base64.RawURLEncoding.EncodeToString(b) // 16 characters
}
