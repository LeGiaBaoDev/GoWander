package admin

import (
	"context"
	"errors"
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
	places    map[string]Place
	saved     []Place
	catSaved  []Category
	sessions  map[string]Session
	loginErr  error
	granted   []string
	revoked   []string
	revokeErr error
	audits    []AuditEntry
	usage     Usage
	usageQ    []UsageQuery
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
func (f *fakeStore) Places(_ context.Context, q PlaceQuery) ([]PlaceRow, int, error) {
	var out []PlaceRow
	for _, p := range f.places {
		if q.Status == "" || p.Status == q.Status {
			out = append(out, PlaceRow{ID: p.ID, Name: p.Name, Status: p.Status, Source: p.Source})
		}
	}
	return out, 120, nil // pretend there are more pages
}
func (f *fakeStore) Place(_ context.Context, id string) (Place, error) {
	if p, ok := f.places[id]; ok {
		return p, nil
	}
	return Place{}, ErrNotFound
}
func (f *fakeStore) UpdatePlace(_ context.Context, _ string, p Place, _ string) error {
	if _, ok := f.places[p.ID]; !ok {
		return ErrNotFound
	}
	if err := validatePlace(&p); err != nil {
		return err
	}
	f.saved = append(f.saved, p)
	return nil
}
func (f *fakeStore) Categories(context.Context) ([]Category, error) {
	return []Category{{ID: 7, Name: "Ăn uống", Slug: "food", Active: true, Places: 539}, {ID: 8, Name: "Cũ", Slug: "old"}}, nil
}
func (f *fakeStore) UpdateCategory(_ context.Context, _ string, c Category, _ string) error {
	if strings.TrimSpace(c.Name) == "" {
		return ErrBadInput
	}
	f.catSaved = append(f.catSaved, c)
	return nil
}
func (f *fakeStore) Usage(_ context.Context, q UsageQuery) (Usage, error) {
	f.usageQ = append(f.usageQ, q)
	u := f.usage
	u.UsageQuery = q
	return u, nil
}

func (f *fakeStore) Overview(context.Context) (Overview, error) {
	return Overview{Places: 1030, Published: 1030, WithAddress: 410, WithCategory: 1030, Categories: 10,
		ByCategory: []CategoryCount{{"Ăn uống", 600}, {"Bảo tàng", 30}}}, nil
}

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
	return &fakeStore{places: map[string]Place{
		"p1": {ID: "p1", Name: "Chợ Bến Thành", Status: "PUBLISHED", Source: "OSM", CategoryID: 7},
	}, sessions: map[string]Session{
		"editor": {UserID: "e", CSRF: "csrf-e", Roles: []string{RoleContentEditor}},
		"mod":    {UserID: "m", CSRF: "csrf-m", Roles: []string{RoleModerator}},
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

func TestHomeShowsRealCountsAndWhatNeedsAttention(t *testing.T) {
	r := chi.NewRouter()
	Handler{Store: newFake(), Log: slog.New(slog.NewTextHandler(io.Discard, nil)), DataStatus: "ASSUMED",
		Health: []Health{{"Tìm đường", func(context.Context) error { return errors.New("down") }}, {"Cơ sở dữ liệu", func(context.Context) error { return nil }}}}.Mount(r)
	w := do(r, "GET", "/admin/", "sys", nil, nil)
	body := w.Body.String()
	for _, want := range []string{"1.030", "Tìm đường không phản hồi", "620", "chưa có địa chỉ", "giả định", "Ăn uống", "39%"} {
		if !strings.Contains(body, want) {
			t.Errorf("home page lacks %q", want)
		}
	}
	if w.Code != http.StatusOK {
		t.Fatalf("%d", w.Code)
	}
}

func TestAttentionListsOnlyProblems(t *testing.T) {
	done := Overview{Places: 5, Published: 5, WithPhoto: 5, WithDescription: 5, WithAddress: 5, WithCategory: 5, Foods: 1}
	if got := attention(done, nil, "OFFICIAL"); len(got) != 0 {
		t.Fatalf("nothing to do, got %+v", got)
	}
	got := attention(Overview{Places: 3, WithPhoto: 1, WithDescription: 3, WithAddress: 3, WithCategory: 3, Draft: 2}, []string{"Tìm đường"}, "ASSUMED")
	if len(got) < 4 || !got[0].Bad || got[2].Count != 2 || got[3].Count != 2 {
		t.Fatalf("%+v", got)
	}
}

func TestPlacesAreForContentRoles(t *testing.T) {
	h := server(newFake(), nil)
	for token, want := range map[string]int{"editor": http.StatusOK, "sys": http.StatusOK, "mod": http.StatusForbidden} {
		if w := do(h, "GET", "/admin/places", token, nil, nil); w.Code != want {
			t.Errorf("%s: %d, want %d", token, w.Code, want)
		}
	}
}

func TestPlacesListFiltersAndPages(t *testing.T) {
	w := do(server(newFake(), nil), "GET", "/admin/places?status=PUBLISHED&missing=nonsense&page=2", "editor", nil, nil)
	body := w.Body.String()
	for _, want := range []string{"Chợ Bến Thành", "/admin/places/p1", "Đang hiển thị", "51–51 trên 120", "page=1", "page=3", "status=PUBLISHED"} {
		if !strings.Contains(body, want) {
			t.Errorf("list lacks %q", want)
		}
	}
	if strings.Contains(body, "missing=nonsense") {
		t.Error("an unknown filter was kept")
	}
}

func TestSavePlace(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	form := url.Values{"csrf": {"csrf-e"}, "name": {"  Chợ Bến Thành mới "}, "status": {"HIDDEN"}, "category": {"7"}, "description": {"Chợ lâu đời"}}
	w := do(h, "POST", "/admin/places/p1", "editor", form, nil)
	if w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/places/p1?saved=1" || len(store.saved) != 1 || store.saved[0].Name != "Chợ Bến Thành mới" {
		t.Fatalf("save: %d %s %+v", w.Code, w.Header().Get("Location"), store.saved)
	}
	// Rejected input: the form comes back with the message and what the editor typed.
	form.Set("status", "DELETED")
	form.Set("description", "giữ lại chữ này")
	w = do(h, "POST", "/admin/places/p1", "editor", form, nil)
	if w.Code != http.StatusUnprocessableEntity || !strings.Contains(w.Body.String(), "trạng thái không hợp lệ") || !strings.Contains(w.Body.String(), "giữ lại chữ này") || len(store.saved) != 1 {
		t.Fatalf("bad input: %d %v", w.Code, store.saved)
	}
	form.Del("csrf")
	if w := do(h, "POST", "/admin/places/p1", "editor", form, nil); w.Code != http.StatusForbidden {
		t.Fatalf("no csrf: %d", w.Code)
	}
	if w := do(h, "GET", "/admin/places/nope", "editor", nil, nil); w.Code != http.StatusNotFound {
		t.Fatalf("unknown place: %d", w.Code)
	}
}

func TestSaveCategory(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	w := do(h, "POST", "/admin/categories/7", "sys", url.Values{"csrf": {"csrf-s"}, "name": {"Ăn uống"}, "sort_order": {"5"}, "active": {"on"}}, nil)
	if w.Header().Get("Location") != "/admin/categories?n=saved" || len(store.catSaved) != 1 || !store.catSaved[0].Active || store.catSaved[0].SortOrder != 5 {
		t.Fatalf("%s %+v", w.Header().Get("Location"), store.catSaved)
	}
	// Unticked box = off; a non-number order is refused before the store.
	do(h, "POST", "/admin/categories/7", "sys", url.Values{"csrf": {"csrf-s"}, "name": {"Ăn uống"}, "sort_order": {"1"}}, nil)
	if store.catSaved[1].Active {
		t.Fatal("unticked box saved as on")
	}
	if w := do(h, "POST", "/admin/categories/7", "sys", url.Values{"csrf": {"csrf-s"}, "name": {"x"}, "sort_order": {"abc"}}, nil); w.Header().Get("Location") != "/admin/categories?n=bad" || len(store.catSaved) != 2 {
		t.Fatalf("bad order: %s", w.Header().Get("Location"))
	}
}

func TestValidatePlace(t *testing.T) {
	for name, p := range map[string]Place{
		"empty name":   {Name: "  ", Status: "PUBLISHED"},
		"long name":    {Name: strings.Repeat("ạ", 201), Status: "PUBLISHED"},
		"long summary": {Name: "a", Description: strings.Repeat("x", 501), Status: "PUBLISHED"},
		"bad status":   {Name: "a", Status: "GONE"},
	} {
		if err := validatePlace(&p); !errors.Is(err, ErrBadInput) {
			t.Errorf("%s: %v", name, err)
		}
	}
	ok := Place{Name: " a ", Description: strings.Repeat("ạ", 500), Status: "DRAFT"}
	if err := validatePlace(&ok); err != nil || ok.Name != "a" {
		t.Fatalf("%v %q", err, ok.Name)
	}
}

func TestUsageIsForTheSystemAdmin(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	for token, want := range map[string]int{"sys": http.StatusOK, "editor": http.StatusForbidden, "mod": http.StatusForbidden} {
		if w := do(h, "GET", "/admin/usage", token, nil, nil); w.Code != want {
			t.Errorf("%s: %d, want %d", token, w.Code, want)
		}
	}
	// Unknown periods fall back to 30 days.
	do(h, "GET", "/admin/usage?days=365&area=r1", "sys", nil, nil)
	if q := store.usageQ[len(store.usageQ)-1]; q.Days != 30 || q.Area != "r1" {
		t.Fatalf("query %+v", q)
	}
}

func TestUsageHidesSmallNumbers(t *testing.T) {
	store := newFake()
	store.usage = Usage{Total: 3, Daily: []bar{{"15-11", 3}}}
	if body := do(server(store, nil), "GET", "/admin/usage", "sys", nil, nil).Body.String(); !strings.Contains(body, "Chưa đủ dữ liệu") || !strings.Contains(body, "Có dưới 5") {
		t.Fatalf("too little data must say so:\n%s", body)
	}
	store.usage = Usage{Total: 1240, DailyMax: 900, Daily: []bar{{"15-11", 2}, {"16-11", 900}},
		Modes: []bar{{"Xe buýt", 1238}, {"Xe đạp", 2}}, Lines: []bar{{"19", 600}}, Places: []bar{{"Chợ Bến Thành", 80}}}
	body := do(server(store, nil), "GET", "/admin/usage", "sys", nil, nil).Body.String()
	for _, want := range []string{"1.240", "Tuyến 19", "Chợ Bến Thành", "Xe đạp</span><span class=\"muted\">&lt;5", "15-11: &lt;5 lượt"} {
		if !strings.Contains(body, want) {
			t.Errorf("page lacks %q", want)
		}
	}
	if strings.Contains(body, ">2 lượt") || strings.Contains(body, "Xe đạp</span><span class=\"muted\">2") {
		t.Error("a number under 5 is shown as is")
	}
}

func TestScaleNeverRevealsSmallValues(t *testing.T) {
	for _, c := range []struct{ v, top, want int }{{0, 100, 0}, {1, 100, 5}, {4, 100, 5}, {50, 100, 50}, {3, 3, 100}} {
		if got := scaleBar(c.v, c.top, 100); got != c.want {
			t.Errorf("scale(%d,%d) = %d, want %d", c.v, c.top, got, c.want)
		}
	}
}
