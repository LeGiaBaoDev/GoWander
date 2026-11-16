package account

import (
	"errors"
	"strings"
	"testing"
)

func TestNormalizeEmail(t *testing.T) {
	cases := map[string]string{
		"  LanAnh@Example.COM ":  "lananh@example.com",
		"lananh@example.com":     "lananh@example.com",
		"lananh@example":         "",
		"lan anh@example.com":    "",
		"lananh@":                "",
		"":                       "",
		"lananh@example.com\r\n": "lananh@example.com", // surrounding whitespace is trimmed, not carried on
		"lan\r\nanh@example.com": "",                   // an address never contains a line break
	}
	for in, want := range cases {
		if got := NormalizeEmail(in); got != want {
			t.Errorf("NormalizeEmail(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestNormalizePhone(t *testing.T) {
	cases := map[string]string{
		"0912 345 678":      "+84912345678",
		"0912345678":        "+84912345678",
		"+84 912 345 678":   "+84912345678",
		"84-912-345-678":    "+84912345678",
		"(028) 3822 1234":   "+842838221234",
		"+1 415 555 0100":   "+14155550100",
		"0123":              "",
		"abc":               "",
		"":                  "",
		"+9991234567890123": "", // longer than E.164 allows
	}
	for in, want := range cases {
		if got := NormalizePhone(in); got != want {
			t.Errorf("NormalizePhone(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestNormalizeUsername(t *testing.T) {
	cases := map[string]string{
		"LanAnh":                "lananh",
		" lan.anh ":             "lan.anh",
		"lan_anh":               "lan_anh",
		"9lananh":               "",
		"la":                    "",
		"lan anh":               "",
		"lananh@x":              "",
		strings.Repeat("a", 31): "",
	}
	for in, want := range cases {
		if got := NormalizeUsername(in); got != want {
			t.Errorf("NormalizeUsername(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestIdentify(t *testing.T) {
	cases := []struct {
		in   string
		kind Kind
		val  string
	}{
		{"lananh@example.com", KindEmail, "lananh@example.com"},
		{"LanAnh@Example.com", KindEmail, "lananh@example.com"},
		{"0912 345 678", KindPhone, "+84912345678"},
		{"+84912345678", KindPhone, "+84912345678"},
		{"lananh", KindUsername, "lananh"},
		{"lan_anh", KindUsername, "lan_anh"},
		{"", KindUnknown, ""},
		{"@@", KindUnknown, ""},
	}
	for _, c := range cases {
		kind, val := Identify(c.in)
		if kind != c.kind || val != c.val {
			t.Errorf("Identify(%q) = %v %q, want %v %q", c.in, kind, val, c.kind, c.val)
		}
	}
}

func TestValidatePassword(t *testing.T) {
	ok := []string{"cà phê sữa đá 47", "GoWander-2026"}
	for _, p := range ok {
		if err := ValidatePassword(p, "lananh", "lananh@example.com"); err != nil {
			t.Errorf("%q refused: %v", p, err)
		}
	}
	bad := []string{"short7!", "123456", "password", "lananh", "lananh@example.com", strings.Repeat("a", 129)}
	for _, p := range bad {
		if err := ValidatePassword(p, "lananh", "lananh@example.com"); !errors.Is(err, ErrBadInput) {
			t.Errorf("%q accepted, want ErrBadInput (got %v)", p, err)
		}
	}
	if got := ValidatePassword("mật khẩu tốt 2026", "", ""); got != nil {
		t.Fatalf("a good password without a username was refused: %v", got)
	}
}

func TestValidateName(t *testing.T) {
	if got, err := ValidateName("  Lan   Anh "); err != nil || got != "Lan Anh" {
		t.Fatalf("ValidateName = %q %v", got, err)
	}
	for _, bad := range []string{"", "   ", strings.Repeat("a", 101), "Lan\x00Anh"} {
		if _, err := ValidateName(bad); !errors.Is(err, ErrBadInput) {
			t.Errorf("ValidateName(%q) accepted", bad)
		}
	}
}

func TestNewCode(t *testing.T) {
	seen := map[string]bool{}
	for range 50 {
		code := newCode()
		if len(code) != 6 || strings.Trim(code, "0123456789") != "" {
			t.Fatalf("code %q is not six digits", code)
		}
		seen[code] = true
	}
	if len(seen) < 40 {
		t.Fatalf("codes repeat too often: %d distinct in 50", len(seen))
	}
}

func TestSuggestUsername(t *testing.T) {
	taken := func(u string) (bool, error) { return u == "lananh" || u == "lananh2", nil }
	got, err := suggestUsername("lananh@example.com", taken)
	if err != nil || got != "lananh3" {
		t.Fatalf("suggestUsername = %q %v, want lananh3", got, err)
	}
	got, err = suggestUsername("0123@example.com", func(string) (bool, error) { return false, nil })
	if err != nil || !strings.HasPrefix(got, "gowander") {
		t.Fatalf("a local part that does not start with a letter: %q %v", got, err)
	}
	got, err = suggestUsername("a@example.com", func(string) (bool, error) { return false, nil })
	if err != nil || NormalizeUsername(got) != got {
		t.Fatalf("suggestion %q is not a valid username (%v)", got, err)
	}
}

func TestDeviceLabel(t *testing.T) {
	if got := deviceLabel("  "); got == "" {
		t.Fatal("empty label should fall back to a default")
	}
	long := deviceLabel(strings.Repeat("a", 200))
	if len([]rune(long)) != 100 {
		t.Fatalf("long label = %d runes, want 100", len([]rune(long)))
	}
}
