package config

import (
	"strings"
	"testing"
	"time"
)

const dbURL = "postgres://gowander_app:secret@127.0.0.1:5432/gowander?sslmode=disable"

// only returns an env with DATABASE_URL (the one required variable) plus the given overrides.
func only(kv ...string) func(string) string {
	env := map[string]string{"DATABASE_URL": dbURL}
	for i := 0; i < len(kv); i += 2 {
		env[kv[i]] = kv[i+1]
	}
	return func(k string) string { return env[k] }
}

func TestLoadDefaults(t *testing.T) {
	c, err := Load(only())
	if err != nil {
		t.Fatal(err)
	}
	want := Config{Addr: "127.0.0.1:8090", OTPURL: "http://127.0.0.1:8088", OTPTimeout: 8 * time.Second, DataStatus: "ASSUMED", DatabaseURL: dbURL, PhotonURL: "http://127.0.0.1:2322"}
	if c != want {
		t.Fatalf("got %+v, want %+v", c, want)
	}
}

func TestLoadOverrides(t *testing.T) {
	c, err := Load(only("HTTP_ADDR", "127.0.0.1:9000", "OTP_URL", "https://otp.internal/", "OTP_TIMEOUT", "3s", "ROUTING_DATA_STATUS", "OFFICIAL"))
	if err != nil {
		t.Fatal(err)
	}
	if c.Addr != "127.0.0.1:9000" || c.OTPURL != "https://otp.internal/" || c.OTPTimeout != 3*time.Second || c.DataStatus != "OFFICIAL" {
		t.Fatalf("got %+v", c)
	}
}

func TestLoadInvalid(t *testing.T) {
	for name, getenv := range map[string]func(string) string{
		"OTP_URL without scheme":    only("OTP_URL", "localhost:8088"),
		"OTP_URL with credentials":  only("OTP_URL", "http://user:secret@otp:8088"),
		"OTP_TIMEOUT not duration":  only("OTP_TIMEOUT", "soon"),
		"OTP_TIMEOUT zero":          only("OTP_TIMEOUT", "0s"),
		"OTP_TIMEOUT negative":      only("OTP_TIMEOUT", "-1s"),
		"OTP_TIMEOUT too long":      only("OTP_TIMEOUT", "31s"),
		"ROUTING_DATA_STATUS":       only("ROUTING_DATA_STATUS", "MAYBE"),
		"DATABASE_URL missing":      func(string) string { return "" },
		"PHOTON_URL without scheme": only("PHOTON_URL", "127.0.0.1:2322"),
		"DATABASE_URL not postgres": only("DATABASE_URL", "mysql://root:secret@db/gowander"),
	} {
		t.Run(name, func(t *testing.T) {
			_, err := Load(getenv)
			if err == nil {
				t.Fatal("want error")
			}
			if strings.Contains(err.Error(), "secret") {
				t.Fatalf("error leaks a password: %v", err)
			}
		})
	}
}
