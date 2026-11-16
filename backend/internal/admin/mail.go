package admin

import (
	"bytes"
	"context"
	"fmt"
	"mime"
	"mime/quotedprintable"
	"net"
	"net/smtp"
	"strconv"
	"strings"
	"time"
)

// Mailer sends the "quên mật khẩu" email. nil in Handler means email is not configured.
type Mailer interface {
	Send(ctx context.Context, to, subject, body string) error
}

// SMTP sends through an SMTP server with STARTTLS (ADR-039: Gmail, smtp.gmail.com:587, an app password).
// net/smtp refuses to send the password over a connection that is not TLS, except to localhost (Mailpit in tests).
type SMTP struct {
	Host, Username, Password, From string
	Port                           int
}

func (s SMTP) Send(ctx context.Context, to, subject, body string) error {
	if strings.ContainsAny(to+subject, "\r\n") {
		return fmt.Errorf("header injection refused")
	}
	var msg bytes.Buffer
	fmt.Fprintf(&msg, "From: GoWander Admin <%s>\r\nTo: %s\r\nSubject: %s\r\nDate: %s\r\n", s.From, to,
		mime.QEncoding.Encode("utf-8", subject), time.Now().Format(time.RFC1123Z))
	msg.WriteString("MIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n")
	qp := quotedprintable.NewWriter(&msg)
	if _, err := qp.Write([]byte(body)); err != nil {
		return err
	}
	if err := qp.Close(); err != nil {
		return err
	}
	var auth smtp.Auth
	if s.Username != "" {
		auth = smtp.PlainAuth("", s.Username, s.Password, s.Host)
	}
	done := make(chan error, 1)
	go func() {
		done <- smtp.SendMail(net.JoinHostPort(s.Host, strconv.Itoa(s.Port)), auth, s.From, []string{to}, msg.Bytes())
	}()
	select {
	case err := <-done:
		return err
	case <-ctx.Done():
		return ctx.Err()
	}
}
