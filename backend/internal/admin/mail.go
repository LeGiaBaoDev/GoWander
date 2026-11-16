package admin

import "github.com/LeGiaBaoDev/GoWander/backend/internal/platform/mail"

// Mailer sends the "quên mật khẩu" email. nil in Handler means email is not configured.
type Mailer = mail.Sender

// SMTP sends through an SMTP server with STARTTLS (ADR-039: Gmail, smtp.gmail.com:587, an app password).
type SMTP = mail.SMTP
