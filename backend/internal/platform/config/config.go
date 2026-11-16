// Package config reads and validates the API configuration from environment variables at startup.
package config

import (
	"errors"
	"fmt"
	"net/mail"
	"net/url"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Addr       string        // HTTP_ADDR, default "127.0.0.1:8090" (localhost only; containers set their own)
	OTPURL     string        // OTP_URL, default "http://127.0.0.1:8088" (infrastructure/otp/scripts/serve.sh)
	OTPTimeout time.Duration // OTP_TIMEOUT, default 8s, (0, 30s]
	DataStatus string        // ROUTING_DATA_STATUS: ASSUMED (default) or OFFICIAL, see ADR-027
	// DATABASE_URL, required: postgres:// URL of the runtime role gowander_app (ADR-028). Holds a password: never log it.
	// Query time is bounded by the role's statement_timeout (5 s, infrastructure/postgres/init), not by the app.
	DatabaseURL string
	// PHOTON_URL: self-hosted Photon geocoder (infrastructure/photon), default http://127.0.0.1:2322. Optional at runtime:
	// if it is down only address search fails (GEOCODER_UNAVAILABLE); readiness does not depend on it.
	PhotonURL string
	// MAPILLARY_TOKEN: client token for street photos in the route preview (NAV-1, ADR-033). Optional: empty turns the
	// feature off (STREET_IMAGERY_UNAVAILABLE) and the app shows a 3D map instead. A secret: never log it.
	MapillaryToken string
	MapillaryURL   string // MAPILLARY_URL, default https://graph.mapillary.com

	// Admin pages (M07, ADR-037, ADR-039). Optional: without ADMIN_DATABASE_URL the /admin pages answer 503 and the
	// public API works as before. ADMIN_DATABASE_URL uses the role gowander_admin and holds a password: never log it.
	AdminDatabaseURL string
	AdminBaseURL     string // ADMIN_BASE_URL, default http://localhost:8090: the link in "quên mật khẩu" emails

	// Photos (ADR-035). MEDIA_DIR: development storage on this machine, default var/media; refused when the Admin runs
	// on https (production keeps photos in R2, not on the API server). MEDIA_BASE_URL: where photos are loaded from;
	// default /media, a path on the API itself, so every client resolves it against the address it already uses
	// (emulator 10.0.2.2, simulator localhost, Tailscale). With R2: the public https address of the bucket.
	MediaDir, MediaBaseURL string

	// R2 (ADR-035, docs/runbooks/cloudflare-r2.md): with R2_BUCKET set, photos go to that bucket instead of MEDIA_DIR,
	// and MEDIA_BASE_URL must be the bucket's public https address. R2_SECRET_ACCESS_KEY is a secret: never log it.
	R2AccountID, R2Bucket, R2AccessKeyID, R2SecretAccessKey string

	// Google sign-in of the app (ADR-040 §3.5). Google mints the ID token for the client that asked: the Web client
	// from Android's Credential Manager and from the iOS SDK when a server client is configured, the iOS client from
	// the iOS SDK otherwise. All of them belong to this project, so all are accepted (never a secret).
	GoogleClientID  string
	GoogleAudiences []string // the Web, Android and iOS client IDs that exist in .env, deduplicated

	// SMTP for "quên mật khẩu" (ADR-039: Gmail, smtp.gmail.com:587 with an app password). Optional: without SMTP_HOST
	// the page says email is not set up. SMTP_PASSWORD is a secret, SMTP_USERNAME/SMTP_FROM are private: never log them.
	SMTPHost, SMTPUsername, SMTPPassword, SMTPFrom string
	SMTPPort                                       int // SMTP_PORT, default 587
}

var (
	hexID          = regexp.MustCompile(`^[0-9a-f]{32}$`)
	bucketName     = regexp.MustCompile(`^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$`)
	googleClientID = regexp.MustCompile(`^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$`)
)

// R2Enabled: photos are stored in the R2 bucket.
func (c Config) R2Enabled() bool { return c.R2Bucket != "" }

// AdminEnabled: the Admin pages can open the database. MailEnabled: they can send "quên mật khẩu" emails.
func (c Config) AdminEnabled() bool { return c.AdminDatabaseURL != "" }
func (c Config) MailEnabled() bool  { return c.SMTPHost != "" }

// Load reads the configuration through getenv (os.Getenv in production) and returns every problem found.
func Load(getenv func(string) string) (Config, error) {
	or := func(key, def string) string {
		if v := getenv(key); v != "" {
			return v
		}
		return def
	}
	c := Config{
		Addr:           or("HTTP_ADDR", "127.0.0.1:8090"),
		OTPURL:         or("OTP_URL", "http://127.0.0.1:8088"),
		DataStatus:     or("ROUTING_DATA_STATUS", "ASSUMED"),
		DatabaseURL:    getenv("DATABASE_URL"),
		PhotonURL:      or("PHOTON_URL", "http://127.0.0.1:2322"),
		MapillaryToken: getenv("MAPILLARY_TOKEN"),
		MapillaryURL:   or("MAPILLARY_URL", "https://graph.mapillary.com"),

		AdminDatabaseURL:  getenv("ADMIN_DATABASE_URL"),
		AdminBaseURL:      strings.TrimRight(or("ADMIN_BASE_URL", "http://localhost:8090"), "/"),
		MediaDir:          or("MEDIA_DIR", "var/media"),
		MediaBaseURL:      strings.TrimRight(or("MEDIA_BASE_URL", "/media"), "/"),
		R2AccountID:       strings.TrimSpace(getenv("R2_ACCOUNT_ID")),
		R2Bucket:          strings.TrimSpace(getenv("R2_BUCKET")),
		R2AccessKeyID:     strings.TrimSpace(getenv("R2_ACCESS_KEY_ID")),
		R2SecretAccessKey: strings.TrimSpace(getenv("R2_SECRET_ACCESS_KEY")),
		GoogleClientID:    strings.TrimSpace(getenv("GOOGLE_CLIENT_ID")),
		SMTPHost:          getenv("SMTP_HOST"),
		SMTPUsername:      getenv("SMTP_USERNAME"),
		SMTPPassword:      getenv("SMTP_PASSWORD"),
		SMTPFrom:          strings.TrimSpace(getenv("SMTP_FROM")),
	}
	var errs []error
	if u, err := url.Parse(c.OTPURL); err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" || u.User != nil {
		errs = append(errs, errors.New("OTP_URL must be an absolute http(s) URL without credentials"))
	}
	t, err := time.ParseDuration(or("OTP_TIMEOUT", "8s"))
	if err != nil || t <= 0 || t > 30*time.Second {
		errs = append(errs, fmt.Errorf("OTP_TIMEOUT must be a duration in (0, 30s], got %q", getenv("OTP_TIMEOUT")))
	}
	c.OTPTimeout = t
	if c.DataStatus != "ASSUMED" && c.DataStatus != "OFFICIAL" {
		errs = append(errs, fmt.Errorf("ROUTING_DATA_STATUS must be ASSUMED or OFFICIAL, got %q", c.DataStatus))
	}
	if u, err := url.Parse(c.PhotonURL); err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" || u.User != nil {
		errs = append(errs, errors.New("PHOTON_URL must be an absolute http(s) URL without credentials"))
	}
	// The token is sent to this URL: plain http only for a local stub (security review 14-11-2026).
	if u, err := url.Parse(c.MapillaryURL); err != nil || u.Host == "" || u.User != nil ||
		!(u.Scheme == "https" || (u.Scheme == "http" && (u.Hostname() == "127.0.0.1" || u.Hostname() == "localhost"))) {
		errs = append(errs, errors.New("MAPILLARY_URL must be https (http only for 127.0.0.1/localhost) without credentials"))
	}
	if !strings.HasPrefix(c.DatabaseURL, "postgres://") && !strings.HasPrefix(c.DatabaseURL, "postgresql://") {
		errs = append(errs, errors.New("DATABASE_URL is required and must be a postgres:// URL"))
	}
	if c.AdminDatabaseURL != "" && !strings.HasPrefix(c.AdminDatabaseURL, "postgres://") && !strings.HasPrefix(c.AdminDatabaseURL, "postgresql://") {
		errs = append(errs, errors.New("ADMIN_DATABASE_URL must be a postgres:// URL"))
	}
	// Session cookies are Secure: the browser only sends them over https, or to localhost during development.
	if u, err := url.Parse(c.AdminBaseURL); err != nil || u.Host == "" || u.User != nil || u.Path != "" ||
		!(u.Scheme == "https" || (u.Scheme == "http" && (u.Hostname() == "localhost" || u.Hostname() == "127.0.0.1"))) {
		errs = append(errs, errors.New("ADMIN_BASE_URL must be https://host (http only for localhost), without a path"))
	}
	if c.R2Bucket == "" && strings.HasPrefix(c.AdminBaseURL, "https://") {
		errs = append(errs, errors.New("photos are stored on this machine (MEDIA_DIR), which ADR-035 allows only in development: set R2_BUCKET and the other R2_* settings"))
	}
	if c.R2Bucket != "" {
		if !hexID.MatchString(c.R2AccountID) || c.R2AccessKeyID == "" || c.R2SecretAccessKey == "" || !bucketName.MatchString(c.R2Bucket) {
			errs = append(errs, errors.New("R2_BUCKET needs R2_ACCOUNT_ID (32 hex characters), R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY, and a valid bucket name"))
		}
		if !strings.HasPrefix(c.MediaBaseURL, "https://") {
			errs = append(errs, errors.New("with R2, MEDIA_BASE_URL must be the bucket's public https address"))
		}
	}
	if u, err := url.Parse(c.MediaBaseURL); err != nil || u.User != nil || u.RawQuery != "" || u.Fragment != "" ||
		!((u.Scheme == "https" || u.Scheme == "http") && u.Host != "" || u.Scheme == "" && u.Host == "" && strings.HasPrefix(u.Path, "/") && !strings.HasPrefix(c.MediaBaseURL, "//")) {
		errs = append(errs, errors.New("MEDIA_BASE_URL must be a path on this API (/media) or an http(s) URL, without credentials"))
	}
	for _, v := range []struct{ name, value string }{
		{"GOOGLE_CLIENT_ID", c.GoogleClientID},
		{"GOOGLE_ANDROID_CLIENT_ID", strings.TrimSpace(getenv("GOOGLE_ANDROID_CLIENT_ID"))},
		{"GOOGLE_IOS_CLIENT_ID", strings.TrimSpace(getenv("GOOGLE_IOS_CLIENT_ID"))},
	} {
		if v.value == "" {
			continue
		}
		if !googleClientID.MatchString(v.value) {
			errs = append(errs, fmt.Errorf("%s must look like 1234-abc.apps.googleusercontent.com", v.name))
			continue
		}
		if !slices.Contains(c.GoogleAudiences, v.value) {
			c.GoogleAudiences = append(c.GoogleAudiences, v.value)
		}
	}
	port, err := strconv.Atoi(or("SMTP_PORT", "587"))
	if err != nil || port < 1 || port > 65535 {
		errs = append(errs, errors.New("SMTP_PORT must be a port number"))
	}
	c.SMTPPort = port
	if c.SMTPHost != "" {
		if a, err := mail.ParseAddress(c.SMTPFrom); err != nil || a.Address != c.SMTPFrom {
			errs = append(errs, errors.New("SMTP_FROM must be a plain email address when SMTP_HOST is set"))
		}
		// net/smtp only sends a password over TLS (STARTTLS), except to localhost (Mailpit).
		if (c.SMTPUsername == "") != (c.SMTPPassword == "") {
			errs = append(errs, errors.New("SMTP_USERNAME and SMTP_PASSWORD must be set together"))
		}
	}
	return c, errors.Join(errs...)
}
