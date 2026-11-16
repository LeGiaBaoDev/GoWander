package account

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// signingKey is a throwaway RSA key that plays the part of Google's.
type signingKey struct {
	key *rsa.PrivateKey
	kid string
}

func newSigningKey(t *testing.T) signingKey {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	return signingKey{key: key, kid: "test-key-1"}
}

func (s signingKey) token(t *testing.T, claims map[string]any) string {
	t.Helper()
	header, _ := json.Marshal(map[string]any{"alg": "RS256", "typ": "JWT", "kid": s.kid})
	payload, _ := json.Marshal(claims)
	unsigned := base64.RawURLEncoding.EncodeToString(header) + "." + base64.RawURLEncoding.EncodeToString(payload)
	sum := sha256.Sum256([]byte(unsigned))
	sig, err := rsa.SignPKCS1v15(rand.Reader, s.key, crypto.SHA256, sum[:])
	if err != nil {
		t.Fatal(err)
	}
	return unsigned + "." + base64.RawURLEncoding.EncodeToString(sig)
}

// verifierFor returns a verifier pointed at a local key set, as if it were Google's.
func verifierFor(t *testing.T, clientID string, s signingKey) *GoogleVerifier {
	t.Helper()
	n := base64.RawURLEncoding.EncodeToString(s.key.N.Bytes())
	e := base64.RawURLEncoding.EncodeToString([]byte{1, 0, 1}) // 65537
	set, _ := json.Marshal(map[string]any{"keys": []map[string]any{{"kid": s.kid, "kty": "RSA", "alg": "RS256", "use": "sig", "n": n, "e": e}}})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/certs" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(set)
	}))
	t.Cleanup(srv.Close)
	g := NewGoogleVerifier(clientID)
	g.URL = srv.URL + "/certs"
	return g
}

func claims(now time.Time) map[string]any {
	return map[string]any{
		"iss":            "https://accounts.google.com",
		"aud":            "1234-abc.apps.googleusercontent.com",
		"sub":            "10876543210",
		"email":          "LanAnh@Example.com",
		"email_verified": true,
		"name":           "Lan Anh",
		"iat":            now.Unix(),
		"exp":            now.Add(time.Hour).Unix(),
	}
}

func TestGoogleVerifyAcceptsARealToken(t *testing.T) {
	key := newSigningKey(t)
	g := verifierFor(t, "1234-abc.apps.googleusercontent.com", key)
	now := time.Now()
	got, err := g.Verify(context.Background(), key.token(t, claims(now)))
	if err != nil {
		t.Fatalf("verify: %v", err)
	}
	if got.Sub != "10876543210" || got.Email != "lananh@example.com" || got.Name != "Lan Anh" || !got.EmailVerified {
		t.Fatalf("identity = %+v", got)
	}
}

func TestGoogleVerifyRefuses(t *testing.T) {
	key := newSigningKey(t)
	g := verifierFor(t, "1234-abc.apps.googleusercontent.com", key)
	now := time.Now()
	other := newSigningKey(t)
	other.kid = key.kid // same kid, different key: the signature must fail

	cases := map[string]string{
		"wrong audience":   key.token(t, with(claims(now), "aud", "999-zzz.apps.googleusercontent.com")),
		"wrong issuer":     key.token(t, with(claims(now), "iss", "https://evil.example")),
		"expired":          key.token(t, with(claims(now), "exp", now.Add(-2*time.Minute).Unix())),
		"email unverified": key.token(t, with(claims(now), "email_verified", false)),
		"no email":         key.token(t, with(claims(now), "email", "")),
		"bad signature":    other.token(t, claims(now)),
		"not a token":      "not.a.jwt",
		"two parts":        "abc.def",
	}
	for name, token := range cases {
		if _, err := g.Verify(context.Background(), token); !errors.Is(err, ErrGoogleToken) {
			t.Errorf("%s: err = %v, want ErrGoogleToken", name, err)
		}
	}
}

func TestGoogleVerifyAcceptsAnAudienceList(t *testing.T) {
	key := newSigningKey(t)
	g := verifierFor(t, "1234-abc.apps.googleusercontent.com", key)
	token := key.token(t, with(claims(time.Now()), "aud", []string{"other", "1234-abc.apps.googleusercontent.com"}))
	if _, err := g.Verify(context.Background(), token); err != nil {
		t.Fatalf("audience list refused: %v", err)
	}
}

func TestGoogleDisabled(t *testing.T) {
	g := NewGoogleVerifier("")
	if g.Enabled() {
		t.Fatal("an empty client ID must turn sign-in off")
	}
	if _, err := g.Verify(context.Background(), "a.b.c"); !errors.Is(err, ErrGoogleToken) {
		t.Fatalf("err = %v", err)
	}
}

func with(m map[string]any, key string, value any) map[string]any {
	m[key] = value
	return m
}
