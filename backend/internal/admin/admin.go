// Package admin serves the operator pages under /admin (M07, ADR-037, ADR-039): sign-in with a login and a password,
// server-side sessions, role checks on every request and an append-only audit log. Pages are rendered on the server; htmx handles small updates.
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

// Session is a signed-in operator. Email is shown only to the operator themself (top bar). MustChange: signed in
// with a temporary password, only the change-password page opens.
type Session struct {
	UserID, Email, Name, Login, CSRF string
	MustChange                       bool
	Roles                            []string
	ExpiresAt                        time.Time
}

func (s Session) Has(role string) bool { return slices.Contains(s.Roles, role) }

// AuditEntry is one line of the audit log. Metadata never carries private contact data.
type AuditEntry struct {
	ActorID, Action, EntityType, EntityID, RequestID string
	Metadata                                         map[string]any
	ActorName                                        string    // filled when listing: display name or login, never email
	EntityName                                       string    // filled when listing: the place, dish, story, account …
	At                                               time.Time // filled when listing
}

// Overview is what the home page shows: real counts from the content tables, never estimates.
type Overview struct {
	Places, Published, Hidden, Draft                      int
	WithPhoto, WithDescription, WithAddress, WithCategory int
	Categories, Foods, Stories                            int
	ByCategory                                            []CategoryCount
	LastPlaceUpdate                                       *time.Time
}

// CategoryCount is the number of published places whose main category is Name.
type CategoryCount struct {
	Name  string
	Count int
}

var (
	// ErrNotAllowed: no such login, wrong password, locked, suspended or without a role: the page says the same.
	ErrNotAllowed = errors.New("not an operator")
	// ErrBadInput: rejected input; the text after "bad input: " is shown to the operator.
	ErrBadInput = errors.New("bad input")
)

// Store is the database side of the Admin pages (PG in store.go; a fake in the handler tests).
type Store interface {
	PasswordLogin(ctx context.Context, login, password, requestID string) (userID string, err error)
	ChangePassword(ctx context.Context, userID, current, next, keepToken, requestID string) error
	SetDisplayName(ctx context.Context, userID, name, requestID string) error
	SetEmail(ctx context.Context, userID, email, password, requestID string) error
	StartPasswordReset(ctx context.Context, login, requestID string) (email, token string, err error)
	ResetPassword(ctx context.Context, token, next, requestID string) error
	CreateSession(ctx context.Context, userID string, ttl time.Duration) (token string, err error)
	// Session returns the live session for a cookie token, with the operator's current roles.
	Session(ctx context.Context, token string) (Session, error)
	DeleteSession(ctx context.Context, token string) error
	Audit(ctx context.Context, e AuditEntry) error
	AuditLog(ctx context.Context, limit int) ([]AuditEntry, error)
	Notifications(ctx context.Context, userID string, limit int) (Bell, error)
	WardShapes(ctx context.Context) ([]WardShape, error)
	MarkNotificationsSeen(ctx context.Context, userID string) error
	AuditPage(ctx context.Context, q AuditQuery) ([]AuditEntry, int, error)
	AuditActivity(ctx context.Context, q AuditQuery) ([]bar, error)
	Overview(ctx context.Context) (Overview, error)
	Usage(ctx context.Context, q UsageQuery) (Usage, error)
	GuideItems(ctx context.Context, kind string) ([]GuideRow, error)
	GuideItem(ctx context.Context, kind, id string) (GuideItem, error)
	SaveGuideItem(ctx context.Context, actorID, kind string, g GuideItem, requestID string) (id string, err error)
	Places(ctx context.Context, q PlaceQuery) (list []PlaceRow, total int, err error)
	Place(ctx context.Context, id string) (Place, error)
	PlacePhotos(ctx context.Context, placeID string) ([]Photo, error)
	AddPlacePhoto(ctx context.Context, actorID, placeID, key string, in PhotoInput, requestID string) error
	SetPlaceCover(ctx context.Context, actorID, placeID, photoID, requestID string) error
	DeletePlacePhoto(ctx context.Context, actorID, placeID, photoID, requestID string) (orphan string, err error)
	SetGuideImage(ctx context.Context, actorID, kind, id, key string, in PhotoInput, requestID string) (orphan string, err error)
	UpdatePlace(ctx context.Context, actorID string, p Place, requestID string) error
	Categories(ctx context.Context) ([]Category, error)
	AppUsers(ctx context.Context, q AppUserQuery) (list []AppUserRow, total int, err error)
	SetAppUserStatus(ctx context.Context, id, status, actorID, requestID string) error
	UpdateCategory(ctx context.Context, actorID string, c Category, requestID string) error
	Ready(ctx context.Context) error
}
