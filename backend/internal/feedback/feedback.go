// Package feedback accepts guest contributions and keeps attachments private to authorised operators.
package feedback

import (
	"context"
	"errors"
	"fmt"
	"net/mail"
	"strings"
	"time"
	"unicode/utf8"
)

const PageSize = 50
const MaxImageBytes = 5 << 20
const MaxStoredImageBytes = 2 << 20

// PreserveNote requests a status-only move without overwriting a concurrent note edit.
const PreserveNote = "\x00"

var ErrBadInput = errors.New("invalid feedback")
var ErrNotFound = errors.New("feedback not found")
var ErrConflict = errors.New("feedback changed by another operator")

type Input struct {
	Kind, Message, Context, SenderName, SenderEmail string
	// AppUserID is the signed-in account (ADR-040), empty for a guest. The server, not the app, decides it.
	AppUserID string
	Image     []byte
}
type Report struct {
	ID, Kind, Message, Context, SenderName, SenderEmail, Status, Note string
	CreatedAt, UpdatedAt                                              time.Time
	HasImage                                                          bool
	History                                                           []Event
}
type Event struct {
	ActorName, FromStatus, ToStatus, Note string
	At                                    time.Time
}
type Query struct {
	Status, Kind, Search, From, To string
	Page                           int
}
type Store interface {
	Create(context.Context, Input) (string, error)
	List(context.Context, Query) ([]Report, int, error)
	Get(context.Context, string) (Report, error)
	Image(context.Context, string) ([]byte, error)
	Update(ctx context.Context, id, status, note, expectedStatus, actorID, requestID string) error
}

func ValidKind(s string) bool {
	return s == "INFORMATION" || s == "BUG" || s == "SUGGESTION" || s == "OTHER"
}
func ValidStatus(s string) bool {
	return s == "NEW" || s == "IN_REVIEW" || s == "RESOLVED" || s == "REJECTED"
}
func textOK(s string, max int) bool {
	return utf8.ValidString(s) && !strings.ContainsRune(s, 0) && utf8.RuneCountInString(s) <= max
}
func Validate(in Input) error {
	if !ValidKind(in.Kind) || strings.TrimSpace(in.Message) == "" || !textOK(in.Message, 1000) || !textOK(in.Context, 200) || !textOK(in.SenderName, 100) || !textOK(in.SenderEmail, 254) || len(in.Image) > MaxStoredImageBytes {
		return ErrBadInput
	}
	if in.SenderEmail != "" {
		a, err := mail.ParseAddress(in.SenderEmail)
		if err != nil || a.Address != in.SenderEmail || !strings.Contains(a.Address, "@") {
			return ErrBadInput
		}
	}
	return nil
}
func ValidateQuery(q Query) error {
	if (q.Status != "" && !ValidStatus(q.Status)) || (q.Kind != "" && !ValidKind(q.Kind)) || !textOK(q.Search, 200) || q.Page < 0 || q.Page > 10000 {
		return ErrBadInput
	}
	for _, s := range []string{q.From, q.To} {
		if s != "" {
			if _, err := time.Parse("2006-01-02", s); err != nil {
				return fmt.Errorf("%w: date", ErrBadInput)
			}
		}
	}
	if q.From != "" && q.To != "" && q.From > q.To {
		return ErrBadInput
	}
	return nil
}
