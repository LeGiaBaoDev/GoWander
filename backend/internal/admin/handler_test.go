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
	changed   []string          // new passwords
	resets    map[string]string // login -> email, for StartPasswordReset
	reset     []string          // tokens used
	audits    []AuditEntry
	usage     Usage
	usageQ    []UsageQuery
	auditQ    []AuditQuery
	guide     map[string]GuideItem
	guideSave []GuideItem
}

func (f *fakeStore) PasswordLogin(_ context.Context, login, password, _ string) (string, error) {
	if f.loginErr != nil || password != "đúng mật khẩu rồi" {
		return "", ErrNotAllowed
	}
	return "u-" + login, nil
}
func (f *fakeStore) ChangePassword(_ context.Context, _, current, next, _, _ string) error {
	if current != "đúng mật khẩu rồi" {
		return bad("mật khẩu hiện tại không đúng")
	}
	f.changed = append(f.changed, next)
	return nil
}
func (f *fakeStore) SetEmail(_ context.Context, _, email, password, _ string) error {
	if password != "đúng mật khẩu rồi" {
		return bad("mật khẩu hiện tại không đúng")
	}
	f.changed = append(f.changed, "email:"+email)
	return nil
}
func (f *fakeStore) SetDisplayName(_ context.Context, _, name, _ string) error {
	if strings.TrimSpace(name) == "" {
		return bad("tên hiển thị dài 1–80 ký tự")
	}
	f.changed = append(f.changed, "name:"+name)
	return nil
}
func (f *fakeStore) StartPasswordReset(_ context.Context, login, _ string) (string, string, error) {
	if email, ok := f.resets[login]; ok {
		return email, "tok-" + login, nil
	}
	return "", "", ErrNotFound
}
func (f *fakeStore) ResetPassword(_ context.Context, token, next, _ string) error {
	if token != "tok-baole" {
		return bad("link đặt lại mật khẩu đã dùng hoặc đã hết hạn")
	}
	if err := validatePassword("baole", next); err != nil {
		return err
	}
	f.reset = append(f.reset, token)
	return nil
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
func (f *fakeStore) Audit(_ context.Context, e AuditEntry) error {
	f.audits = append(f.audits, e)
	return nil
}
func (f *fakeStore) AuditLog(context.Context, int) ([]AuditEntry, error) { return nil, nil }
func (f *fakeStore) AuditPage(_ context.Context, q AuditQuery) ([]AuditEntry, int, error) {
	f.auditQ = append(f.auditQ, q)
	return []AuditEntry{{ActorName: "Gia Bảo", Action: "place.update", EntityType: "place", EntityName: "Chợ Bến Thành", RequestID: "R65GEKS5KD7TCGUV",
		Metadata: map[string]any{"fields": []any{"name", "address"}, "status": "PUBLISHED→HIDDEN"}, At: time.Now()},
		{Action: "account.create", EntityType: "user", EntityID: "01a1e1e8-b74c", Metadata: map[string]any{"login": "baole", "role": "SYSTEM_ADMIN"}, At: time.Now()}}, 120, nil
}
func (f *fakeStore) AuditActivity(context.Context, AuditQuery) ([]bar, error) {
	return []bar{{"07:00 16/11", 0}, {"08:00 16/11", 3}}, nil
}
func (f *fakeStore) Ready(context.Context) error { return nil }
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
func (f *fakeStore) GuideItems(_ context.Context, kind string) ([]GuideRow, error) {
	var out []GuideRow
	for _, g := range f.guide {
		out = append(out, GuideRow{ID: g.ID, Title: g.Title, Status: g.Status})
	}
	return out, nil
}

func (f *fakeStore) GuideItem(_ context.Context, kind, id string) (GuideItem, error) {
	if g, ok := f.guide[id]; ok {
		return g, nil
	}
	return GuideItem{}, ErrNotFound
}

func (f *fakeStore) SaveGuideItem(_ context.Context, _, kind string, g GuideItem, _ string) (string, error) {
	if err := validateGuide(GuideKinds[kind], &g); err != nil {
		return "", err
	}
	if g.ID == "" {
		g.ID = "new-1"
	} else if _, ok := f.guide[g.ID]; !ok {
		return "", ErrNotFound
	}
	f.guideSave = append(f.guideSave, g)
	return g.ID, nil
}

func (f *fakeStore) AreaShapes(context.Context) ([]AreaShape, error) {
	return []AreaShape{{Code: "r1", Name: "Thành phố Hồ Chí Minh", Path: "M 106 -10 L 107 -10 L 107 -11 Z", X: 106.5, Y: -10.5},
		{Code: "r2", Name: "Tỉnh Khánh Hòa", Path: "M 109 -12 L 110 -12 L 110 -13 Z", X: 109.5, Y: -12.5}}, nil
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

// fakeMailer records what would be emailed; sent is buffered so the background send never blocks a test.
type fakeMailer struct{ sent chan string }

func (m fakeMailer) Send(_ context.Context, to, _, body string) error {
	m.sent <- to + "\n" + body
	return nil
}

func server(store Store, mailer Mailer) http.Handler {
	r := chi.NewRouter()
	h := Handler{Mailer: mailer, BaseURL: "https://admin.example.vn", Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
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

func TestPasswordSignIn(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	w := do(h, "POST", "/admin/login", "", url.Values{"login": {"BaoLe"}, "password": {"sai"}}, nil)
	if w.Code != http.StatusUnauthorized || !strings.Contains(w.Body.String(), "Sai mã tài khoản hoặc mật khẩu") || !strings.Contains(w.Body.String(), `value="BaoLe"`) {
		t.Fatalf("wrong password: %d", w.Code)
	}
	if strings.Contains(w.Body.String(), "sai") && strings.Contains(w.Body.String(), `value="sai"`) {
		t.Fatal("the password is echoed back")
	}
	w = do(h, "POST", "/admin/login", "", url.Values{"login": {"baole"}, "password": {"đúng mật khẩu rồi"}}, nil)
	if w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/" {
		t.Fatalf("sign-in: %d %s", w.Code, w.Header().Get("Location"))
	}
	var session *http.Cookie
	for _, c := range w.Result().Cookies() {
		if c.Name == sessionCookie {
			session = c
		}
	}
	if session == nil || session.Value != "new-token" || !session.HttpOnly || session.Path != "/admin" || session.SameSite != http.SameSiteLaxMode {
		t.Fatalf("session cookie: %+v", session)
	}
	// A login form posted from another site is refused before any password check.
	if w := do(h, "POST", "/admin/login", "", url.Values{"login": {"baole"}, "password": {"đúng mật khẩu rồi"}}, map[string]string{"Origin": "https://evil.example"}); w.Code != http.StatusForbidden {
		t.Fatalf("cross-site login: %d", w.Code)
	}
}

func TestTemporaryPasswordOpensOnlyThePasswordPage(t *testing.T) {
	store := newFake()
	store.sessions["temp"] = Session{UserID: "t", Login: "baole", CSRF: "csrf-t", Roles: []string{RoleSystemAdmin}, MustChange: true}
	h := server(store, nil)
	for _, target := range []string{"/admin/", "/admin/places", "/admin/audit"} {
		if w := do(h, "GET", target, "temp", nil, nil); w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/password" {
			t.Errorf("%s: %d %s", target, w.Code, w.Header().Get("Location"))
		}
	}
	if w := do(h, "GET", "/admin/password", "temp", nil, nil); w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "mật khẩu tạm") {
		t.Fatalf("password page: %d", w.Code)
	}
	form := url.Values{"csrf": {"csrf-t"}, "current": {"đúng mật khẩu rồi"}, "new": {"mật khẩu mới dài"}, "confirm": {"khác hẳn luôn nè"}}
	if w := do(h, "POST", "/admin/password", "temp", form, nil); w.Code != http.StatusUnprocessableEntity || len(store.changed) != 0 {
		t.Fatalf("confirmation differs: %d", w.Code)
	}
	form.Set("confirm", "mật khẩu mới dài")
	if w := do(h, "POST", "/admin/password", "temp", form, nil); w.Code != http.StatusSeeOther || len(store.changed) != 1 || w.Header().Get("Location") != "/admin/?welcome=1" {
		t.Fatalf("forced change goes on to the overview: %d %s %v", w.Code, w.Header().Get("Location"), store.changed)
	}
	if w := do(h, "POST", "/admin/password", "sys", url.Values{"csrf": {"csrf-s"}, "current": {"đúng mật khẩu rồi"}, "new": {"mật khẩu mới dài"}, "confirm": {"mật khẩu mới dài"}}, nil); w.Header().Get("Location") != "/admin/account?saved=1" {
		t.Fatalf("a chosen change stays on the page with a notice: %s", w.Header().Get("Location"))
	}
	form.Set("current", "sai")
	if w := do(h, "POST", "/admin/password", "temp", form, nil); w.Code != http.StatusUnprocessableEntity || !strings.Contains(w.Body.String(), "không đúng") {
		t.Fatalf("wrong current password: %d", w.Code)
	}
}

func TestForgotPasswordAnswersTheSameAndEmailsALink(t *testing.T) {
	store := newFake()
	store.resets = map[string]string{"baole": "owner@example.vn"}
	mail := fakeMailer{sent: make(chan string, 4)}
	h := server(store, mail)
	known := do(h, "POST", "/admin/forgot", "", url.Values{"login": {"baole"}}, nil)
	unknown := do(h, "POST", "/admin/forgot", "", url.Values{"login": {"ai-do"}}, nil)
	if known.Code != http.StatusOK || known.Body.String() != unknown.Body.String() {
		t.Fatalf("answers differ: %d / %d", known.Code, unknown.Code)
	}
	if got := <-mail.sent; !strings.HasPrefix(got, "owner@example.vn\n") || !strings.Contains(got, "https://admin.example.vn/admin/reset?t=tok-baole") {
		t.Fatalf("email: %q", got)
	}
	select {
	case extra := <-mail.sent:
		t.Fatalf("an unknown login got an email: %q", extra)
	default:
	}
	// Without SMTP the page says so instead of pretending.
	if w := do(server(store, nil), "GET", "/admin/forgot", "", nil, nil); !strings.Contains(w.Body.String(), "Chưa bật gửi email") {
		t.Fatal("no SMTP: page does not say so")
	}
}

func TestResetPassword(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	if w := do(h, "GET", "/admin/reset?t=tok-baole", "", nil, nil); !strings.Contains(w.Body.String(), `value="tok-baole"`) {
		t.Fatal("reset form lacks the token")
	}
	form := url.Values{"t": {"tok-other"}, "new": {"mật khẩu mới dài"}, "confirm": {"mật khẩu mới dài"}}
	if w := do(h, "POST", "/admin/reset", "", form, nil); w.Code != http.StatusUnprocessableEntity || !strings.Contains(w.Body.String(), "hết hạn") {
		t.Fatalf("used link: %d", w.Code)
	}
	form.Set("t", "tok-baole")
	if w := do(h, "POST", "/admin/reset", "", form, nil); w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/login?n=reset" || len(store.reset) != 1 {
		t.Fatalf("reset: %d %s", w.Code, w.Header().Get("Location"))
	}
}

func TestSecurityHeaders(t *testing.T) {
	w := do(server(newFake(), nil), "GET", "/admin/login", "", nil, nil)
	if !strings.Contains(w.Header().Get("Content-Security-Policy"), "frame-ancestors 'none'") || w.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("%v", w.Header())
	}
	if body := w.Body.String(); !strings.Contains(body, "Mã tài khoản") || !strings.Contains(body, `autocomplete="current-password"`) || strings.Contains(body, "Google") {
		t.Fatalf("login page: %s", body)
	}
}

func TestNormalizeEmail(t *testing.T) {
	for in, want := range map[string]string{" Owner@Example.VN ": "owner@example.vn", "not an email": "", "a@b.c": "a@b.c", "Name <a@b.c>": ""} {
		if got := NormalizeEmail(in); got != want {
			t.Errorf("%q: got %q want %q", in, got, want)
		}
	}
}

func TestReportsContentTabShowsRealCountsAndWhatNeedsAttention(t *testing.T) {
	r := chi.NewRouter()
	Handler{Store: newFake(), Log: slog.New(slog.NewTextHandler(io.Discard, nil)), DataStatus: "ASSUMED",
		Health: []Health{{"Tìm đường", func(context.Context) error { return errors.New("down") }}, {"Cơ sở dữ liệu", func(context.Context) error { return nil }}}}.Mount(r)
	w := do(r, "GET", "/admin/reports?tab=noi-dung", "editor", nil, nil)
	body := w.Body.String()
	for _, want := range []string{"1.030", "Tìm đường không phản hồi", "620", "chưa có địa chỉ", "giả định", "Ăn uống", "39%"} {
		if !strings.Contains(body, want) {
			t.Errorf("content report lacks %q", want)
		}
	}
	if w.Code != http.StatusOK {
		t.Fatalf("%d", w.Code)
	}
	// The overview only raises the alarm for a service that is down, with a link to the details.
	if body := do(r, "GET", "/admin/", "editor", nil, nil).Body.String(); !strings.Contains(body, "không phản hồi: Tìm đường") || strings.Contains(body, "chưa có địa chỉ") {
		t.Error("overview: alarm missing or content details shown")
	}
}

func TestOverviewIsCustomersAndRevenueAtAGlance(t *testing.T) {
	store := newFake()
	store.usage = Usage{Opens: 120, PrevOpens: 100, Total: 80, PrevTotal: 100, OpensSeries: []bar{{"Th10/2026", 4}, {"Th11/2026", 9}},
		PrevOpensSeries: []bar{{"Th10/2025", 2}, {"Th11/2025", 3}},
		Modes:           []bar{{"Xe buýt", 60}, {"Đi bộ", 20}}, Places: []bar{{"Chợ Bến Thành · Chợ", 30}, {"Dinh Độc Lập · Di tích", 12}}}
	body := do(server(store, nil), "GET", "/admin/?grain=month", "editor", nil, nil).Body.String()
	for _, want := range []string{"Lượt mở app theo tháng", "so với 12 tháng trước", `aria-current="true">Tháng`, "Kỳ trước, cùng số ngày: 3", "Th11/2026 (đến hôm nay)", "Kỳ trước (Th10/2025): 2",
		`href="/admin/?grain=year"`, "Lượt mở app", `change up">&#43;20%`, `change down">-20%`, "Doanh thu", "Chưa có", "Lợi nhuận", "Chợ Bến Thành · Chợ", "Xe buýt", "75%", "Hành trình của khách", "Dữ liệu mẫu", `href="/admin/reports"`} {
		if !strings.Contains(body, want) {
			t.Errorf("overview lacks %q", want)
		}
	}
	if q := store.usageQ[len(store.usageQ)-1]; q.Grain.Key != "month" || q.Area != "" {
		t.Fatalf("overview query %+v", q)
	}
	// The province map: every province, both archipelagos named, a province links to the reports filtered by it.
	store.usage.AreaCounts = map[string]int{"r1": 40}
	body = do(server(store, nil), "GET", "/admin/?grain=week", "editor", nil, nil).Body.String()
	for _, want := range []string{"Khách tìm đường từ đâu", "QĐ. Hoàng Sa", "QĐ. Trường Sa", "Tỉnh Khánh Hòa", `href="/admin/reports?grain=week&amp;area=r1"`, `class="lv4"`, `class="lv0"`} {
		if !strings.Contains(body, want) {
			t.Errorf("map lacks %q", want)
		}
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

func TestOverviewShowsUsageWithItsFilters(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	// An unknown grain falls back to days; the province code is passed on (reports page; the overview has no area),
	// and the grain links keep it.
	w := do(h, "GET", "/admin/reports?grain=century&area=r1", "editor", nil, nil)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), `href="/admin/reports?area=r1&amp;grain=year"`) {
		t.Fatalf("reports: %d", w.Code)
	}
	if q := store.usageQ[len(store.usageQ)-1]; q.Grain.Key != "day" || q.Area != "r1" {
		t.Fatalf("query %+v", q)
	}
	if w := do(h, "GET", "/admin/usage", "sys", nil, nil); w.Code != http.StatusNotFound {
		t.Fatalf("the separate usage page is gone: %d", w.Code)
	}
}

func TestOverviewUsageHidesSmallNumbers(t *testing.T) {
	store := newFake()
	store.usage = Usage{Opens: 3, Total: 3, Series: []bar{{"15/11", 3}}, Areas: []Area{{"r1", "Thành phố Hồ Chí Minh"}}}
	body := do(server(store, nil), "GET", "/admin/reports", "sys", nil, nil).Body.String()
	// Too little real use: the page shows sample numbers, and says so.
	for _, want := range []string{"Khách dùng app", "Lượt mở app", "Dữ liệu mẫu", "Chưa đủ 5 lượt tìm đường thật", "Thành phố Hồ Chí Minh", "Cả nước"} {
		if !strings.Contains(body, want) {
			t.Errorf("too little data: page lacks %q", want)
		}
	}
	store.usage = Usage{Opens: 5000, Total: 1240, Series: []bar{{"15/11", 2}, {"16/11", 900}},
		Modes: []bar{{"Xe buýt", 1238}, {"Xe đạp", 2}}, Lines: []bar{{"19", 600}}, Places: []bar{{"Chợ Bến Thành · Chợ và mua sắm", 80}}}
	body = do(server(store, nil), "GET", "/admin/reports", "sys", nil, nil).Body.String()
	for _, want := range []string{"5.000", "1.240", "Tuyến 19", "Chợ Bến Thành · Chợ và mua sắm", "Xe buýt", "Xe đạp</span><span class=\"muted\">&lt;5"} {
		if !strings.Contains(body, want) {
			t.Errorf("page lacks %q", want)
		}
	}
	if strings.Contains(body, "Xe đạp</span><span class=\"muted\">2") {
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

func TestGuidePagesForContentRoles(t *testing.T) {
	store := newFake()
	store.guide = map[string]GuideItem{"f1": {ID: "f1", Slug: "hcm-food-pho", Title: "Phở bò", Status: "PUBLISHED", Sources: "Nguồn"}}
	h := server(store, nil)
	for token, want := range map[string]int{"editor": http.StatusOK, "sys": http.StatusOK, "mod": http.StatusForbidden} {
		for _, path := range []string{"/admin/foods", "/admin/stories", "/admin/foods/new", "/admin/foods/f1"} {
			if w := do(h, "GET", path, token, nil, nil); w.Code != want {
				t.Errorf("%s %s: %d, want %d", token, path, w.Code, want)
			}
		}
	}
	if body := do(h, "GET", "/admin/foods", "editor", nil, nil).Body.String(); !strings.Contains(body, "Phở bò") || !strings.Contains(body, `href="/admin/foods/new"`) {
		t.Error("list lacks the dish or the add button")
	}
	if w := do(h, "GET", "/admin/stories/nope", "editor", nil, nil); w.Code != http.StatusNotFound {
		t.Errorf("unknown story: %d", w.Code)
	}
}

func TestSaveGuideItem(t *testing.T) {
	store := newFake()
	store.guide = map[string]GuideItem{}
	h := server(store, nil)
	form := url.Values{"csrf": {"csrf-e"}, "title": {" Bánh xèo "}, "summary": {"Giòn"}, "extra": {"Bánh xèo 46A | 46A Đinh Công Tráng"},
		"sources": {"Cục Du lịch"}, "status": {"PUBLISHED"}, "sort_order": {"50"}}
	w := do(h, "POST", "/admin/foods/new", "editor", form, nil)
	if w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/admin/foods/new-1?saved=1" || store.guideSave[0].Title != "Bánh xèo" {
		t.Fatalf("create: %d %s %+v", w.Code, w.Header().Get("Location"), store.guideSave)
	}
	// Published without a source: refused, the form keeps what was typed.
	form.Set("sources", "")
	w = do(h, "POST", "/admin/foods/new", "editor", form, nil)
	if body := w.Body.String(); w.Code != http.StatusUnprocessableEntity || !strings.Contains(body, "cần ghi nguồn") || !strings.Contains(body, "Bánh xèo 46A") {
		t.Fatalf("no source: %d", w.Code)
	}
	form.Del("csrf")
	if w := do(h, "POST", "/admin/stories/new", "editor", form, nil); w.Code != http.StatusForbidden {
		t.Fatalf("without CSRF: %d", w.Code)
	}
}

func TestValidateGuide(t *testing.T) {
	story := GuideKinds["stories"]
	for name, c := range map[string]struct {
		k    guideKind
		g    GuideItem
		want string
	}{
		"ok draft dish without source":         {GuideKinds["foods"], GuideItem{Title: "Phở", Status: "DRAFT"}, ""},
		"story needs a body":                   {story, GuideItem{Title: "T", Sources: "S", Status: "DRAFT"}, "nội dung"},
		"story needs a source even as a draft": {story, GuideItem{Title: "T", Body: "B", Status: "DRAFT"}, "nguồn"},
		"link must be http":                    {story, GuideItem{Title: "T", Body: "B", SourceURL: "javascript:alert(1)", Status: "DRAFT"}, "https://"},
		"bad status":                           {story, GuideItem{Title: "T", Body: "B", Sources: "S", Status: "LIVE"}, "trạng thái"},
		"title too long":                       {GuideKinds["foods"], GuideItem{Title: strings.Repeat("a", 151), Status: "DRAFT"}, "tên"},
	} {
		err := validateGuide(c.k, &c.g)
		if (c.want == "") != (err == nil) || (err != nil && !strings.Contains(err.Error(), c.want)) {
			t.Errorf("%s: %v", name, err)
		}
	}
}

func TestAuditRangeOnlyThePast(t *testing.T) {
	now := time.Date(2026, 11, 16, 1, 0, 0, 0, time.UTC) // 16/11 08:00 in HCMC
	day := func(s string) time.Time { d, _ := time.ParseInLocation(time.DateOnly, s, hcm); return d }
	for name, c := range map[string]struct {
		from, to, wantFrom, wantTo string
		notice                     bool
	}{
		"default: last 7 days":     {"", "", "2026-11-10", "2026-11-16", false},
		"future end pulled back":   {"2026-11-14", "2026-11-20", "2026-11-14", "2026-11-16", true},
		"future start, future end": {"2026-12-01", "2026-12-05", "2026-11-16", "2026-11-16", true},
		"swapped":                  {"2026-11-15", "2026-11-01", "2026-11-01", "2026-11-15", true},
		"over 90 days":             {"2026-01-01", "2026-11-16", "2026-08-19", "2026-11-16", true},
		"rubbish ignored":          {"hôm qua", "x", "2026-11-10", "2026-11-16", false},
	} {
		from, to, notice := auditRange(url.Values{"from": {c.from}, "to": {c.to}}, now)
		if !from.Equal(day(c.wantFrom)) || !to.Equal(day(c.wantTo)) || (notice != "") != c.notice {
			t.Errorf("%s: %s..%s %q", name, from.Format(time.DateOnly), to.Format(time.DateOnly), notice)
		}
	}
}

func TestAuditPage(t *testing.T) {
	store := newFake()
	body := do(server(store, nil), "GET", "/admin/audit?from=2026-11-01&to=2099-01-01&action=place.update&page=2", "sys", nil, nil).Body.String()
	for _, want := range []string{"Chợ Bến Thành", "Đã sửa: tên, địa chỉ", "Trạng thái: Đang hiển thị→Đang ẩn", "Mã tài khoản: baole",
		"Vai trò: Quản trị hệ thống", "Hệ thống (lệnh trên server)", `title="R65GEKS5KD7TCGUV">R65GEKS5<`, "nhật ký chỉ có quá khứ",
		`max="`, "Trang 2 / 3", "08:00 16/11 · 3 thao tác"} {
		if !strings.Contains(body, want) {
			t.Errorf("audit page lacks %q", want)
		}
	}
	if strings.Contains(body, "@") {
		t.Error("an email address is shown in the audit log")
	}
	q := store.auditQ[0]
	if q.Action != "place.update" || q.Offset != 50 || q.To.Sub(q.From) > 91*24*time.Hour || q.To.After(time.Now().Add(48*time.Hour)) {
		t.Fatalf("query %+v", q)
	}
}

func TestDisplayName(t *testing.T) {
	store := newFake()
	h := server(store, nil)
	if w := do(h, "POST", "/admin/account/name", "sys", url.Values{"csrf": {"csrf-s"}, "name": {"Gia Bảo Lê"}}, nil); w.Header().Get("Location") != "/admin/account?named=1" || store.changed[0] != "name:Gia Bảo Lê" {
		t.Fatalf("%d %s %v", w.Code, w.Header().Get("Location"), store.changed)
	}
	if w := do(h, "POST", "/admin/account/name", "sys", url.Values{"csrf": {"csrf-s"}, "name": {"  "}}, nil); w.Code != http.StatusUnprocessableEntity {
		t.Fatalf("empty name: %d", w.Code)
	}
	if w := do(h, "POST", "/admin/account/name", "sys", url.Values{"name": {"x"}}, nil); w.Code != http.StatusForbidden {
		t.Fatalf("without CSRF: %d", w.Code)
	}
}

func TestAccountPage(t *testing.T) {
	store := newFake()
	store.sessions["me"] = Session{UserID: "u", Login: "baole", Name: "Gia Bảo", Email: "me@example.vn", CSRF: "csrf-me", Roles: []string{RoleSystemAdmin}}
	h := server(store, nil)
	body := do(h, "GET", "/admin/account", "me", nil, nil).Body.String()
	for _, want := range []string{"Tài khoản của bạn", "baole", "me@example.vn", "Quản trị hệ thống", "5 phút", `action="/admin/account/email"`, `href="/admin/account" aria-current="page"`} {
		if !strings.Contains(body, want) {
			t.Errorf("account page lacks %q", want)
		}
	}
	// The email shows on the account page only.
	if strings.Contains(do(h, "GET", "/admin/", "me", nil, nil).Body.String(), "me@example.vn") {
		t.Error("the email shows outside the account page")
	}
	form := url.Values{"csrf": {"csrf-me"}, "email": {"new@example.vn"}, "password": {"sai"}}
	if w := do(h, "POST", "/admin/account/email", "me", form, nil); w.Code != http.StatusUnprocessableEntity || !strings.Contains(w.Body.String(), "new@example.vn") {
		t.Fatalf("wrong password keeps the typed email: %d", w.Code)
	}
	form.Set("password", "đúng mật khẩu rồi")
	if w := do(h, "POST", "/admin/account/email", "me", form, nil); w.Header().Get("Location") != "/admin/account?email=1" || store.changed[len(store.changed)-1] != "email:new@example.vn" {
		t.Fatalf("email change: %d %s", w.Code, w.Header().Get("Location"))
	}
}
