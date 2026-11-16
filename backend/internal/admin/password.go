package admin

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"regexp"
	"strings"
	"unicode/utf8"

	"golang.org/x/crypto/argon2"
)

// argon2id parameters (ADR-039, OWASP minimum): 19 MiB, 2 passes, 1 lane, 16-byte salt, 32-byte key.
const (
	argonMemory  = 19 * 1024
	argonTime    = 2
	argonThreads = 1
	argonKeyLen  = 32
)

// HashPassword returns the PHC-style string stored in admin_credentials.password_hash.
func HashPassword(password string) string {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		panic(err) // crypto/rand does not fail on supported platforms
	}
	key := argon2.IDKey([]byte(password), salt, argonTime, argonMemory, argonThreads, argonKeyLen)
	b64 := base64.RawStdEncoding.EncodeToString
	return fmt.Sprintf("$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s", argon2.Version, argonMemory, argonTime, argonThreads, b64(salt), b64(key))
}

// CheckPassword compares in constant time, using the parameters stored with the hash (so they can be raised later).
func CheckPassword(encoded, password string) bool {
	parts := strings.Split(encoded, "$") // "", argon2id, v=19, m=..,t=..,p=.., salt, key
	if len(parts) != 6 || parts[1] != "argon2id" {
		return false
	}
	var version int
	var memory, passes uint32
	var lanes uint8
	if _, err := fmt.Sscanf(parts[2], "v=%d", &version); err != nil || version != argon2.Version {
		return false
	}
	if _, err := fmt.Sscanf(parts[3], "m=%d,t=%d,p=%d", &memory, &passes, &lanes); err != nil || memory == 0 || passes == 0 || lanes == 0 {
		return false
	}
	salt, err1 := base64.RawStdEncoding.DecodeString(parts[4])
	key, err2 := base64.RawStdEncoding.DecodeString(parts[5])
	if err1 != nil || err2 != nil || len(key) == 0 {
		return false
	}
	got := argon2.IDKey([]byte(password), salt, passes, memory, lanes, uint32(len(key)))
	return subtle.ConstantTimeCompare(got, key) == 1
}

// dummyHash is checked when a login does not exist, so a wrong login takes as long as a wrong password.
var dummyHash = HashPassword("no such account")

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
