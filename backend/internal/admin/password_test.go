package admin

import (
	"strings"
	"testing"
)

func TestPasswordHashing(t *testing.T) {
	h := HashPassword("một mật khẩu dài")
	if !strings.HasPrefix(h, "$argon2id$v=19$m=19456,t=2,p=1$") || !CheckPassword(h, "một mật khẩu dài") {
		t.Fatalf("hash %q", h)
	}
	if CheckPassword(h, "một mật khẩu dài ") || CheckPassword(h, "") || HashPassword("x") == HashPassword("x") {
		t.Fatal("wrong password accepted, or salt not random")
	}
	for _, broken := range []string{"", "$argon2id$", "$bcrypt$v=19$m=1,t=1,p=1$AA$AA", "$argon2id$v=18$m=19456,t=2,p=1$AA$AA", h[:len(h)-3] + "!!!"} {
		if CheckPassword(broken, "x") {
			t.Errorf("accepted %q", broken)
		}
	}
}

func TestNormalizeLoginAndPasswordRules(t *testing.T) {
	for raw, want := range map[string]string{" BaoLe ": "baole", "bao.le_1-x": "bao.le_1-x", "ab": "", "-bao": "", "bảo": "", "bao le": "", strings.Repeat("a", 41): ""} {
		if got := NormalizeLogin(raw); got != want {
			t.Errorf("%q -> %q, want %q", raw, got, want)
		}
	}
	if validatePassword("baole", "ngắn") == nil || validatePassword("baole", "BAOLE") == nil || validatePassword("baole", strings.Repeat("a", 129)) == nil {
		t.Error("weak password accepted")
	}
	if err := validatePassword("baolebaolebaole", "baolebaolebaole"); err == nil {
		t.Error("password equal to the login accepted")
	}
	if err := validatePassword("baole", "mật khẩu đủ dài"); err != nil {
		t.Error(err)
	}
	if p := TemporaryPassword(); len(p) != 16 || validatePassword("baole", p) != nil {
		t.Errorf("temporary password %q", p)
	}
}
