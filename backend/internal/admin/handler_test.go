package admin

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/go-chi/chi/v5"
)

type fakeStore struct {
	sessions  map[string]Session
	loginErr  error
	granted   []string
	revoked   []string
	revokeErr error
	audits    []AuditEntry
}

func (f *fakeStore) Login(_ context.Context, id Identity, _, _ string) (string, error) {
	if f.loginErr != nil {
		return "", f.loginErr
	}
	return "u-" + id.Subject, nil
}
func (f *fakeStore) CreateSession(_ context.Context, userID string, _ time.Duration) (string, error) {
	f.sessions["new-token"] = Session{UserID: userID, CSRF: "c", Roles: []string{RoleContentEditor}}
	return "new-token", nil
}
func (f *fakeStore) Session(_ context.Context, token string) (Session, error) {
	if s, ok := f.sessions[token]; ok {
		return s, nil
	}
	return Session{}, ErrNotAllowed
}
func (f *fakeStore) DeleteSession(_ context.Context, token string) error {
	delete(f.sessions, token)
	return nil
}
func (f *fakeStore) Operators(context.Context) ([]Operator, error) { return nil, nil }
func (f *fakeStore) Grant(_ context.Context, _, email, role, _ string) error {
	f.granted = append(f.granted, email+":"+role)
	return nil
}
func (f *fakeStore) Revoke(_ context.Context, _, user, role, _ string) error {
	if f.revokeErr != nil {
		return f.revokeErr
	}
	f.revoked = append(f.revoked, user+":"+role)
	return nil
}
func (f *fakeStore) Audit(_ context.Context, e AuditEntry) error {
	f.audits = append(f.audits, e)
	return nil
}
func (f *fakeStore) AuditLog(context.Context, int) ([]AuditEntry, error) { return nil, nil }
func (f *fakeStore) Ready(context.Context) error                         { return nil }

type fakeAuth struct{ id Identity }

func (a fakeAuth) AuthURL(_ context.Context, state, _, _ string) (string, error) {
	return "https://accounts.example/auth?state=" + state, nil
}
func (a fakeAuth) Exchange(_ context.Context, _, _, _ string) (Identity, error) { return a.id, nil }

func server(store Store, auth Authenticator) http.Handler {
	r := chi.NewRouter()
	h := Handler{Auth: auth, Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
	if store != nil {
		h.Store = store
	}
	h.Mount(r)
	return r
}

func newFake() *fakeStore {
	return &fakeStore{sessions: map[string]Session{
		"editor": {UserID: "e", CSRF: "csrf-e", Roles: []string{RoleContentEditor}},
		"sys":    {UserID: "s", CSRF: "csrf-s", Roles: []string{RoleSystemAdmin}},
	}}
}

func do(h http.Handler, method, target, token string, form url.Values, header map[string]string) *httptest.ResponseRecorder {
	var body io.Reader
	if form != nil {
		body = strings.NewReader(form.Encode())
	}
	req := httptest.NewRequest(method, target, body)
	if form != nil {
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	}
	for k, v := range header {
		req.Header.Set(k, v)
	}
	if token != "" {
		req.AddCookie(&http.Cookie{Name: sessionCookie, Value: token})
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)
	return w
}

func TestNotConfigured(t *testing.T) {
	w := do(server(nil, nil), "GET", "/admin/", "", nil, nil)
	if w.Code != http.StatusServiceUnavailable || !strings.Contains(w.Body.String(), "ADMIN_DATABASE_URL") {
		t.Fatalf("%d %s", w.Code, w.Body.String())
	}
}

func TestPagesNeedASession(t *testing.T) {
	h := server(newFake(), nil)
	for _, target := range []string{"/admin/", "/admin/audit"} {
		w := do(h, "GET", target, "", nil, nil)
		if w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/login" {
			t.Errorf("%s: %d %s", target, w.Code, w.Header().Get("Location"))
		}
	}
	if w := do(h, "GET", "/admin/", "forged", nil, nil); w.Code != http.StatusSeeOther {
		t.Errorf("unknown token: %d", w.Code)
	}
}

func TestRolesAreCheckedOnTheServer(t *testing.T) {
	h := server(newFake(), nil)
	// The audit log needs SYSTEM_ADMIN: a content editor is refused by the server.
	if w := do(h, "GET", "/admin/audit", "editor", nil, nil); w.Code != http.StatusForbidden {
		t.Fatalf("editor GET audit: %d", w.Code)
	}
	if w := do(h, "GET", "/admin/audit", "sys", nil, nil); w.Code != http.StatusOK {
		t.Fatalf("sys GET audit: %d", w.Code)
	}
}

func TestWritesNeedTheCSRFToken(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	for name, form := range map[string]url.Values{"missing": {}, "other session": {"csrf": {"csrf-e"}}} {
		if w := do(h, "POST", "/admin/logout", "sys", form, nil); w.Code != http.StatusForbidden {
			t.Errorf("%s: %d", name, w.Code)
		}
	}
	if _, ok := store.sessions["sys"]; !ok {
		t.Fatal("signed out without a valid token")
	}
	// htmx sends the token in a header.
	if w := do(h, "POST", "/admin/logout", "sys", url.Values{}, map[string]string{"X-CSRF-Token": "csrf-s"}); w.Code != http.StatusSeeOther {
		t.Fatalf("header token: %d", w.Code)
	}
	if _, ok := store.sessions["sys"]; ok {
		t.Fatal("session kept after sign-out")
	}
}

func TestGoogleSignIn(t *testing.T) {
	store := newFake()
	h := server(store, fakeAuth{id: Identity{Issuer: googleIssuer, Subject: "123", Email: "op@example.vn", EmailVerified: true}})

	start := do(h, "GET", "/admin/auth/start", "", nil, nil)
	if start.Code != http.StatusSeeOther || !strings.HasPrefix(start.Header().Get("Location"), "https://accounts.example/auth?state=") {
		t.Fatalf("start: %d %s", start.Code, start.Header().Get("Location"))
	}
	var oauth *http.Cookie
	for _, c := range start.Result().Cookies() {
		if c.Name == oauthCookie {
			oauth = c
		}
	}
	if oauth == nil || !oauth.HttpOnly || oauth.SameSite != http.SameSiteLaxMode {
		t.Fatalf("oauth cookie: %+v", oauth)
	}
	state := strings.SplitN(oauth.Value, ".", 2)[0]

	callback := func(state string) *httptest.ResponseRecorder {
		req := httptest.NewRequest("GET", "/admin/auth/callback?code=c&state="+url.QueryEscape(state), nil)
		req.AddCookie(oauth)
		w := httptest.NewRecorder()
		h.ServeHTTP(w, req)
		return w
	}
	if w := callback("forged"); w.Header().Get("Location") != "/admin/login?e=expired" {
		t.Fatalf("forged state: %s", w.Header().Get("Location"))
	}
	w := callback(state)
	if w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/" {
		t.Fatalf("callback: %d %s", w.Code, w.Header().Get("Location"))
	}
	var session *http.Cookie
	for _, c := range w.Result().Cookies() {
		if c.Name == sessionCookie {
			session = c
		}
	}
	if session == nil || session.Value != "new-token" || !session.HttpOnly || session.Path != "/admin" {
		t.Fatalf("session cookie: %+v", session)
	}

	store.loginErr = ErrNotAllowed
	if w := callback(state); w.Header().Get("Location") != "/admin/login?e=denied" {
		t.Fatalf("not an operator: %s", w.Header().Get("Location"))
	}
}

func TestSecurityHeaders(t *testing.T) {
	w := do(server(newFake(), nil), "GET", "/admin/login", "", nil, nil)
	if !strings.Contains(w.Header().Get("Content-Security-Policy"), "frame-ancestors 'none'") || w.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("%v", w.Header())
	}
	if !strings.Contains(w.Body.String(), "chưa được bật") { // no Google client configured: the page says so
		t.Fatalf("login page: %s", w.Body.String())
	}
}

func TestNormalizeEmail(t *testing.T) {
	for in, want := range map[string]string{" Owner@Example.VN ": "owner@example.vn", "not an email": "", "a@b.c": "a@b.c", "Name <a@b.c>": ""} {
		if got := NormalizeEmail(in); got != want {
			t.Errorf("%q: got %q want %q", in, got, want)
		}
	}
}
