// Package passwords hashes and checks passwords for both account types (Admin ADR-039, app ADR-040).
package passwords

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"strings"
	"sync"

	"golang.org/x/crypto/argon2"
)

// argon2id parameters (OWASP minimum): 19 MiB, 2 passes, 1 lane, 16-byte salt, 32-byte key.
const (
	argonMemory  = 19 * 1024
	argonTime    = 2
	argonThreads = 1
	argonKeyLen  = 32
)

// Hash returns the PHC-style string stored in password_hash.
func Hash(password string) string {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		panic(err) // crypto/rand does not fail on supported platforms
	}
	key := argon2.IDKey([]byte(password), salt, argonTime, argonMemory, argonThreads, argonKeyLen)
	b64 := base64.RawStdEncoding.EncodeToString
	return fmt.Sprintf("$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s", argon2.Version, argonMemory, argonTime, argonThreads, b64(salt), b64(key))
}

// Check compares in constant time, using the parameters stored with the hash (so they can be raised later).
func Check(encoded, password string) bool {
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

// Dummy is checked when a sign-in names no account, so a wrong login takes as long as a wrong password.
var Dummy = Hash("no such account")

var (
	commonOnce sync.Once
	common     map[string]struct{}
)

// Common reports whether the password is one of the thousand most used (case-insensitive).
func Common(password string) bool {
	commonOnce.Do(func() {
		common = make(map[string]struct{}, 1024)
		for line := range strings.SplitSeq(top1000, "\n") {
			if line != "" {
				common[strings.ToLower(line)] = struct{}{}
			}
		}
	})
	_, found := common[strings.ToLower(strings.TrimSpace(password))]
	return found
}
