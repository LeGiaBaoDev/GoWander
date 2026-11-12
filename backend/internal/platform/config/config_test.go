package config

import (
	"strings"
	"testing"
	"time"
)

func TestLoadDefaults(t *testing.T) {
	c, err := Load(func(string) string { return "" })
	if err != nil {
		t.Fatal(err)
	}
	want := Config{Addr: ":8090", OTPURL: "http://127.0.0.1:8088", OTPTimeout: 8 * time.Second, DataStatus: "ASSUMED"}
	if c != want {
		t.Fatalf("got %+v, want %+v", c, want)
	}
}

func TestLoadOverrides(t *testing.T) {
	env := map[string]string{"HTTP_ADDR": "127.0.0.1:9000", "OTP_URL": "https://otp.internal/", "OTP_TIMEOUT": "3s", "ROUTING_DATA_STATUS": "OFFICIAL"}
	c, err := Load(func(k string) string { return env[k] })
	if err != nil {
		t.Fatal(err)
	}
	if c.Addr != "127.0.0.1:9000" || c.OTPURL != "https://otp.internal/" || c.OTPTimeout != 3*time.Second || c.DataStatus != "OFFICIAL" {
		t.Fatalf("got %+v", c)
	}
}

func TestLoadInvalid(t *testing.T) {
	for k, v := range map[string]string{
		"OTP_URL":             "localhost:8088",
		"OTP_TIMEOUT":         "soon",
		"ROUTING_DATA_STATUS": "MAYBE",
	} {
		t.Run(k, func(t *testing.T) {
			if _, err := Load(func(key string) string {
				if key == k {
					return v
				}
				return ""
			}); err == nil {
				t.Fatalf("want error for %s=%q", k, v)
			}
		})
	}
	if _, err := Load(func(k string) string {
		if k == "OTP_URL" {
			return "http://user:secret@otp:8088"
		}
		return ""
	}); err == nil || strings.Contains(err.Error(), "secret") {
		t.Fatalf("want credential-free error for OTP_URL with userinfo, got %v", err)
	}
	for _, v := range []string{"0s", "-1s", "31s"} {
		if _, err := Load(func(key string) string {
			if key == "OTP_TIMEOUT" {
				return v
			}
			return ""
		}); err == nil {
			t.Fatalf("want error for OTP_TIMEOUT=%s", v)
		}
	}
}
