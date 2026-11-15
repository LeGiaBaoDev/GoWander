// Package config reads and validates the API configuration from environment variables at startup.
package config

import (
	"errors"
	"fmt"
	"net/url"
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

	// Admin pages (M07, ADR-037). Optional: without ADMIN_DATABASE_URL the /admin pages answer 503 and the public API
	// works as before. ADMIN_DATABASE_URL uses the role gowander_admin and holds a password: never log it.
	AdminDatabaseURL    string
	AdminBaseURL        string // ADMIN_BASE_URL, default http://localhost:8090: builds the OAuth redirect URI
	GoogleClientID      string // GOOGLE_CLIENT_ID; with GOOGLE_CLIENT_SECRET turns Google sign-in on
	GoogleClientSecret  string // a secret: never log it
	AdminBootstrapEmail string // ADMIN_BOOTSTRAP_EMAIL: first SYSTEM_ADMIN while nobody has a role. Private data.
}

// AdminEnabled: the Admin pages can open the database. GoogleLogin: they can also sign people in.
func (c Config) AdminEnabled() bool { return c.AdminDatabaseURL != "" }
func (c Config) GoogleLogin() bool  { return c.GoogleClientID != "" && c.GoogleClientSecret != "" }

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

		AdminDatabaseURL:    getenv("ADMIN_DATABASE_URL"),
		AdminBaseURL:        strings.TrimRight(or("ADMIN_BASE_URL", "http://localhost:8090"), "/"),
		GoogleClientID:      getenv("GOOGLE_CLIENT_ID"),
		GoogleClientSecret:  getenv("GOOGLE_CLIENT_SECRET"),
		AdminBootstrapEmail: strings.ToLower(strings.TrimSpace(getenv("ADMIN_BOOTSTRAP_EMAIL"))),
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
	if (c.GoogleClientID == "") != (c.GoogleClientSecret == "") {
		errs = append(errs, errors.New("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must be set together"))
	}
	return c, errors.Join(errs...)
}
