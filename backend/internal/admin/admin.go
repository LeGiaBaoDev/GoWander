// Package admin serves the operator pages under /admin (M07, ADR-037): Google sign-in, server-side sessions, role
// checks on every request and an append-only audit log. Pages are rendered on the server; htmx handles small updates.
package admin

import (
	"context"
	"errors"
	"slices"
	"time"
)

// Role codes (design 10.1). Permissions are mapped in code, not in the database, at this stage.
const (
	RoleContentEditor   = "CONTENT_EDITOR"
	RoleModerator       = "MODERATOR"
	RoleOperationsAdmin = "OPERATIONS_ADMIN"
	RoleBusinessAdmin   = "BUSINESS_ADMIN"
	RoleSystemAdmin     = "SYSTEM_ADMIN"
)

// AllRoles in display order.
var AllRoles = []string{RoleContentEditor, RoleModerator, RoleOperationsAdmin, RoleBusinessAdmin, RoleSystemAdmin}

// Identity is what a verified Google ID token says about the person signing in.
type Identity struct {
	Issuer, Subject, Email string
	EmailVerified          bool
	Name                   string
}

// Session is a signed-in operator. Email is shown only to the operator themself (top bar).
type Session struct {
	UserID, Email, Name, CSRF string
	Roles                     []string
	ExpiresAt                 time.Time
}

func (s Session) Has(role string) bool { return slices.Contains(s.Roles, role) }

// Operator is one row of the people-and-roles page (SYSTEM_ADMIN only).
type Operator struct {
	UserID, Email, Status string
	Roles                 []string
	LastLogin             *time.Time
}

// AuditEntry is one line of the audit log. Metadata never carries private contact data.
type AuditEntry struct {
	ActorID, Action, EntityType, EntityID, RequestID string
	Metadata                                         map[string]any
	ActorName                                        string    // filled when listing
	At                                               time.Time // filled when listing
}

var (
	// ErrNotAllowed: the person is not an operator (no role, suspended, or an unknown Google account for that email).
	ErrNotAllowed = errors.New("not an operator")
	// ErrLastSystemAdmin: removing this role would leave nobody able to grant roles.
	ErrLastSystemAdmin = errors.New("last system admin")
	// ErrBadInput: rejected input (unknown role, malformed email).
	ErrBadInput = errors.New("bad input")
)

// Store is the database side of the Admin pages (PG in store.go; a fake in the handler tests).
type Store interface {
	// Login maps a verified identity to an operator: an already linked Google account, an email a role was granted
	// to (first sign-in links it), or the bootstrap email while nobody holds a role. Anything else is ErrNotAllowed.
	Login(ctx context.Context, id Identity, bootstrapEmail, requestID string) (userID string, err error)
	CreateSession(ctx context.Context, userID string, ttl time.Duration) (token string, err error)
	// Session returns the live session for a cookie token, with the operator's current roles.
	Session(ctx context.Context, token string) (Session, error)
	DeleteSession(ctx context.Context, token string) error
	Operators(ctx context.Context) ([]Operator, error)
	Grant(ctx context.Context, actorID, email, role, requestID string) error
	Revoke(ctx context.Context, actorID, userID, role, requestID string) error
	Audit(ctx context.Context, e AuditEntry) error
	AuditLog(ctx context.Context, limit int) ([]AuditEntry, error)
	Ready(ctx context.Context) error
}

// Authenticator is the identity provider: the URL to send the browser to, and the code exchange on its return.
type Authenticator interface {
	AuthURL(ctx context.Context, state, nonce, verifier string) (string, error)
	Exchange(ctx context.Context, code, verifier, nonce string) (Identity, error)
}
