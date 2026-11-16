package passwords

import "testing"

func TestHashAndCheck(t *testing.T) {
	h := Hash("một mật khẩu đủ dài")
	if h == "một mật khẩu đủ dài" || len(h) < 60 || h[:10] != "$argon2id$" {
		t.Fatalf("unexpected hash %q", h)
	}
	if !Check(h, "một mật khẩu đủ dài") {
		t.Fatal("the right password was refused")
	}
	if Check(h, "một mật khẩu đủ dài ") || Check(h, "") {
		t.Fatal("a wrong password was accepted")
	}
	if Hash("x") == Hash("x") {
		t.Fatal("two hashes of the same password must differ (random salt)")
	}
	for _, broken := range []string{"", "$argon2id$", "$2a$10$abcdefghijklmnopqrstuv", "$argon2id$v=19$m=0,t=2,p=1$c2FsdA$a2V5"} {
		if Check(broken, "x") {
			t.Fatalf("broken hash %q accepted", broken)
		}
	}
}

func TestCommon(t *testing.T) {
	for _, weak := range []string{"123456", "password", "PASSWORD", " qwerty "} {
		if !Common(weak) {
			t.Errorf("%q should be on the common list", weak)
		}
	}
	for _, ok := range []string{"GoWander-2026-lan-anh", "cà phê sữa đá 47"} {
		if Common(ok) {
			t.Errorf("%q should not be on the common list", ok)
		}
	}
}
