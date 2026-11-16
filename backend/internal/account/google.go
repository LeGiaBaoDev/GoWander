package account

import (
	"context"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"slices"
	"strings"
	"sync"
	"time"
)

// ErrGoogleToken: the ID token is not a valid Google token for this app. The API answers 401 GOOGLE_TOKEN_INVALID.
var ErrGoogleToken = errors.New("account: google token invalid")

// googleCertsURL is Google's public key set (ADR-040 §3.5).
const googleCertsURL = "https://www.googleapis.com/oauth2/v3/certs"

// GoogleVerifier checks the Google ID token the app gets on the device. The signature is checked against Google's
// published keys, so no client secret is needed or stored.
type GoogleVerifier struct {
	// Audiences are the app's own OAuth clients (Web, Android, iOS). Google mints the token for the client that asked,
	// so all of them are ours to accept; a token for a client of another project is refused.
	Audiences []string
	URL      string
	HTTP     *http.Client
	Now      func() time.Time

	mu      sync.Mutex
	keys    map[string]*rsa.PublicKey
	fetched time.Time
	retryAt time.Time
}

// NewGoogleVerifier returns a verifier for the app's client IDs; no IDs turns Google sign-in off.
func NewGoogleVerifier(clientID string, more ...string) *GoogleVerifier {
	audiences := make([]string, 0, 1+len(more))
	for _, id := range append([]string{clientID}, more...) {
		if id != "" && !slices.Contains(audiences, id) {
			audiences = append(audiences, id)
		}
	}
	return &GoogleVerifier{Audiences: audiences, URL: googleCertsURL, HTTP: &http.Client{Timeout: 5 * time.Second}, Now: time.Now}
}

func (g *GoogleVerifier) Enabled() bool { return g != nil && len(g.Audiences) > 0 }

// Verify checks the token and returns the identity the accounts need.
func (g *GoogleVerifier) Verify(ctx context.Context, idToken string) (GoogleIdentity, error) {
	if !g.Enabled() {
		return GoogleIdentity{}, ErrGoogleToken
	}
	parts := strings.Split(idToken, ".")
	if len(parts) != 3 {
		return GoogleIdentity{}, fmt.Errorf("%w: not a compact JWS", ErrGoogleToken)
	}
	headerBytes, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return GoogleIdentity{}, fmt.Errorf("%w: header", ErrGoogleToken)
	}
	var header struct {
		Alg string `json:"alg"`
		Kid string `json:"kid"`
	}
	if err := json.Unmarshal(headerBytes, &header); err != nil || header.Alg != "RS256" || header.Kid == "" {
		return GoogleIdentity{}, fmt.Errorf("%w: alg", ErrGoogleToken)
	}
	key, err := g.key(ctx, header.Kid)
	if err != nil {
		return GoogleIdentity{}, err
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return GoogleIdentity{}, fmt.Errorf("%w: signature", ErrGoogleToken)
	}
	sum := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if err := rsa.VerifyPKCS1v15(key, crypto.SHA256, sum[:], sig); err != nil {
		return GoogleIdentity{}, fmt.Errorf("%w: signature", ErrGoogleToken)
	}

	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return GoogleIdentity{}, fmt.Errorf("%w: payload", ErrGoogleToken)
	}
	var claims struct {
		Iss           string          `json:"iss"`
		Aud           json.RawMessage `json:"aud"`
		Sub           string          `json:"sub"`
		Email         string          `json:"email"`
		EmailVerified json.RawMessage `json:"email_verified"`
		Name          string          `json:"name"`
		Exp           *float64        `json:"exp"`
		Iat           *float64        `json:"iat"`
	}
	if err := json.Unmarshal(payloadBytes, &claims); err != nil {
		return GoogleIdentity{}, fmt.Errorf("%w: claims", ErrGoogleToken)
	}
	now := g.Now()
	if claims.Iss != "accounts.google.com" && claims.Iss != "https://accounts.google.com" {
		return GoogleIdentity{}, fmt.Errorf("%w: issuer", ErrGoogleToken)
	}
	if !audienceMatches(claims.Aud, g.Audiences) {
		// The received audience is a public client ID, and naming it is what makes this refusal fixable.
		return GoogleIdentity{}, fmt.Errorf("%w: audience %s, expected one of %v", ErrGoogleToken, string(claims.Aud), g.Audiences)
	}
	if claims.Exp == nil || now.After(time.Unix(int64(*claims.Exp), 0).Add(time.Minute)) {
		return GoogleIdentity{}, fmt.Errorf("%w: expired", ErrGoogleToken)
	}
	if claims.Iat != nil && time.Unix(int64(*claims.Iat), 0).After(now.Add(5*time.Minute)) {
		return GoogleIdentity{}, fmt.Errorf("%w: issued in the future", ErrGoogleToken)
	}
	email := NormalizeEmail(claims.Email)
	if claims.Sub == "" || email == "" {
		return GoogleIdentity{}, fmt.Errorf("%w: no subject or email", ErrGoogleToken)
	}
	if !verifiedFlag(claims.EmailVerified) {
		return GoogleIdentity{}, fmt.Errorf("%w: email not verified", ErrGoogleToken)
	}
	return GoogleIdentity{Sub: claims.Sub, Email: email, Name: strings.TrimSpace(claims.Name), EmailVerified: true}, nil
}

// key returns the signing key for kid, fetching Google's key set when it is unknown, at most once a minute.
func (g *GoogleVerifier) key(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if k, ok := g.keys[kid]; ok && g.Now().Sub(g.fetched) < time.Hour {
		return k, nil
	}
	if g.Now().Before(g.retryAt) && len(g.keys) > 0 {
		if k, ok := g.keys[kid]; ok {
			return k, nil
		}
	}
	keys, err := g.fetch(ctx)
	if err != nil {
		if k, ok := g.keys[kid]; ok { // an outage keeps the keys we already know
			return k, nil
		}
		return nil, err
	}
	g.keys, g.fetched, g.retryAt = keys, g.Now(), g.Now().Add(time.Minute)
	k, ok := keys[kid]
	if !ok {
		return nil, fmt.Errorf("%w: unknown key", ErrGoogleToken)
	}
	return k, nil
}

func (g *GoogleVerifier) fetch(ctx context.Context) (map[string]*rsa.PublicKey, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, g.URL, nil)
	if err != nil {
		return nil, err
	}
	resp, err := g.HTTP.Do(req)
	if err != nil {
		return nil, fmt.Errorf("google keys: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("google keys: status %d", resp.StatusCode)
	}
	var set struct {
		Keys []struct {
			Kid string `json:"kid"`
			N   string `json:"n"`
			E   string `json:"e"`
			Kty string `json:"kty"`
		} `json:"keys"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(&set); err != nil {
		return nil, fmt.Errorf("google keys: %w", err)
	}
	out := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, k := range set.Keys {
		if k.Kid == "" || k.Kty != "RSA" {
			continue
		}
		n, err1 := base64.RawURLEncoding.DecodeString(k.N)
		e, err2 := base64.RawURLEncoding.DecodeString(k.E)
		if err1 != nil || err2 != nil || len(e) == 0 || len(e) > 4 {
			continue
		}
		var exp int
		for _, b := range e {
			exp = exp<<8 | int(b)
		}
		out[k.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: exp}
	}
	if len(out) == 0 {
		return nil, errors.New("google keys: empty set")
	}
	return out, nil
}

// audienceMatches accepts both shapes Google uses for aud (a string or a list of strings) and any of our clients.
func audienceMatches(raw json.RawMessage, audiences []string) bool {
	if len(audiences) == 0 || len(raw) == 0 {
		return false
	}
	ok := func(v string) bool { return slices.Contains(audiences, v) }
	var one string
	if err := json.Unmarshal(raw, &one); err == nil {
		return ok(one)
	}
	var many []string
	if err := json.Unmarshal(raw, &many); err != nil {
		return false
	}
	for _, a := range many {
		if ok(a) {
			return true
		}
	}
	return false
}

// verifiedFlag accepts the boolean and the string form Google uses for email_verified.
func verifiedFlag(raw json.RawMessage) bool {
	var b bool
	if err := json.Unmarshal(raw, &b); err == nil {
		return b
	}
	var s string
	if err := json.Unmarshal(raw, &s); err == nil {
		return s == "true"
	}
	return false
}
