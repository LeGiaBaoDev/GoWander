//go:build integration

package admin

import (
	"context"
	"encoding/json"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"
)

// Sends through a local Mailpit (docker run -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit) and reads
// the message back from its API. Skipped when MAILPIT_URL is not set: never sends real email.
func TestSMTPSendsUTF8Mail(t *testing.T) {
	api := os.Getenv("MAILPIT_URL") // e.g. http://127.0.0.1:8025
	if api == "" {
		t.Skip("MAILPIT_URL not set")
	}
	m := SMTP{Host: "127.0.0.1", Port: 1025, From: "admin@gowander.test"}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := m.Send(ctx, "op@gowander.test", "Đặt lại mật khẩu GoWander Admin", "Mở link sau trong 30 phút:\nhttps://admin.example.vn/admin/reset?t=abc\n"); err != nil {
		t.Fatal(err)
	}
	if err := m.Send(ctx, "op@gowander.test\r\nBcc: x@evil.test", "s", "b"); err == nil {
		t.Fatal("header injection accepted")
	}
	res, err := http.Get(api + "/api/v1/message/latest")
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	var msg struct {
		Subject string
		Text    string
		To      []struct{ Address string }
	}
	if err := json.NewDecoder(res.Body).Decode(&msg); err != nil {
		t.Fatal(err)
	}
	if msg.Subject != "Đặt lại mật khẩu GoWander Admin" || len(msg.To) != 1 || msg.To[0].Address != "op@gowander.test" ||
		!strings.Contains(msg.Text, "https://admin.example.vn/admin/reset?t=abc") {
		t.Fatalf("message %+v", msg)
	}
}
