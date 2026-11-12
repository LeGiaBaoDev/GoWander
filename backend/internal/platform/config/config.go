// Package config reads and validates the API configuration from environment variables at startup.
package config

import (
	"errors"
	"fmt"
	"net/url"
	"time"
)

type Config struct {
	Addr       string        // HTTP_ADDR, default ":8090"
	OTPURL     string        // OTP_URL, default "http://127.0.0.1:8088" (infrastructure/otp/scripts/serve.sh)
	OTPTimeout time.Duration // OTP_TIMEOUT, default 8s, (0, 30s]
	DataStatus string        // ROUTING_DATA_STATUS: ASSUMED (default) or OFFICIAL, see ADR-027
}

// Load reads the configuration through getenv (os.Getenv in production) and returns every problem found.
func Load(getenv func(string) string) (Config, error) {
	or := func(key, def string) string {
		if v := getenv(key); v != "" {
			return v
		}
		return def
	}
	c := Config{
		Addr:       or("HTTP_ADDR", ":8090"),
		OTPURL:     or("OTP_URL", "http://127.0.0.1:8088"),
		DataStatus: or("ROUTING_DATA_STATUS", "ASSUMED"),
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
	return c, errors.Join(errs...)
}
