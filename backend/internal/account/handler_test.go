package account

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

// fakeStore answers what each test needs and records how it was called.
type fakeStore struct {
	me     User
	meErr  error
	calls  []string
	code   string
	err    error
	sess   Session
	user   User
	exists bool
}

func (f *fakeStore) note(name string) { f.calls = append(f.calls, name) }

func (f *fakeStore) Register(_ context.Context, in RegisterInput) (Session, User, string, error) {
	f.note("register:" + in.Email)
	return f.sess, f.user, f.code, f.err
}
func (f *fakeStore) UsernameAvailable(context.Context, string) (bool, error) {
	f.note("available")
	return f.exists, f.err
}
func (f *fakeStore) VerifyEmail(context.Context, string, string) (User, error) {
	f.note("verify")
	return f.user, f.err
}
func (f *fakeStore) IssueCode(context.Context, string, string) (string, error) {
	f.note("issue")
	return f.code, f.err
}
func (f *fakeStore) Login(context.Context, string, string, string) (Session, User, error) {
	f.note("login")
	return f.sess, f.user, f.err
}
func (f *fakeStore) GoogleLogin(context.Context, GoogleIdentity, string) (Session, User, bool, error) {
	f.note("google")
	return f.sess, f.user, true, f.err
}
func (f *fakeStore) LinkGoogle(context.Context, string, GoogleIdentity) (User, error) {
	f.note("link")
	return f.user, f.err
}
func (f *fakeStore) UnlinkGoogle(context.Context, string) (User, error) {
	f.note("unlink")
	return f.user, f.err
}
func (f *fakeStore) Logout(context.Context, string) error { f.note("logout"); return f.err }
func (f *fakeStore) LogoutAll(context.Context, string, string) error {
	f.note("logout-all")
	return f.err
}
func (f *fakeStore) Forgot(context.Context, string) (User, string, bool, error) {
	f.note("forgot")
	return f.user, f.code, f.exists, f.err
}
func (f *fakeStore) ResetPassword(context.Context, string, string, string) error {
	f.note("reset")
	return f.err
}
func (f *fakeStore) Me(context.Context, string) (User, error) { return f.me, f.meErr }
func (f *fakeStore) UpdateProfile(context.Context, string, *string, *string, *string) (User, error) {
	f.note("update")
	return f.user, f.err
}
func (f *fakeStore) ChangePassword(context.Context, string, string, string, string) error {
	f.note("password")
	return f.err
}
func (f *fakeStore) Delete(context.Context, string, string, bool) error {
	f.note("delete")
	return f.err
}

type fakeMailer struct {
	to, subject, body string
	sent              int
	err               error
}

func (m *fakeMailer) Send(_ context.Context, to, subject, body string) error {
	m.sent++
	m.to, m.subject, m.body = to, subject, body
	return m.err
}

func discard() *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }

func routerFor(store Store, h Handler) http.Handler {
	mux := httpx.NewRouter(discard())
	mux.Use(Optional(store))
	h.Store = store
	h.Log = discard()
	h.Mount(mux)
	return mux
}

func call(t *testing.T, h http.Handler, method, path, body, token string) (*httptest.ResponseRecorder, map[string]any) {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	if body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	var out map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &out)
	return rec, out
}

func code(out map[string]any) string {
	if e, ok := out["error"].(map[string]any); ok {
		s, _ := e["code"].(string)
		return s
	}
	return ""
}

func TestMeNeedsASession(t *testing.T) {
	h := routerFor(&fakeStore{meErr: ErrAuthRequired}, Handler{})
	rec, out := call(t, h, "GET", "/api/v1/me", "", "")
	if rec.Code != 401 || code(out) != "AUTH_REQUIRED" {
		t.Fatalf("no token: %d %v", rec.Code, out)
	}
	if rec.Header().Get("WWW-Authenticate") == "" {
		t.Fatal("a 401 should tell the client it may use a bearer token")
	}
}

func TestMeWithASession(t *testing.T) {
	store := &fakeStore{me: User{ID: "u1", Name: "Lan Anh", Email: "lananh@example.com", EmailVerified: true}}
	h := routerFor(store, Handler{})
	rec, out := call(t, h, "GET", "/api/v1/me", "", "good-token")
	if rec.Code != 200 {
		t.Fatalf("me: %d %v", rec.Code, out)
	}
	user, _ := out["user"].(map[string]any)
	if user["name"] != "Lan Anh" || user["emailVerified"] != true {
		t.Fatalf("user = %v", user)
	}
	if strings.Contains(rec.Body.String(), "good-token") {
		t.Fatal("the response echoes the token")
	}
}

func TestLoginErrors(t *testing.T) {
	h := routerFor(&fakeStore{err: ErrLoginFailed}, Handler{})
	rec, out := call(t, h, "POST", "/api/v1/auth/login", `{"identifier":"lananh","password":"x"}`, "")
	if rec.Code != 401 || code(out) != "LOGIN_FAILED" {
		t.Fatalf("wrong password: %d %v", rec.Code, out)
	}

	h = routerFor(&fakeStore{err: LockedError{Until: time.Date(2026, 11, 16, 14, 32, 0, 0, time.Local)}}, Handler{})
	rec, out = call(t, h, "POST", "/api/v1/auth/login", `{"identifier":"lananh","password":"x"}`, "")
	if rec.Code != 423 || code(out) != "ACCOUNT_LOCKED" {
		t.Fatalf("locked: %d %v", rec.Code, out)
	}
	msg, _ := out["error"].(map[string]any)["message"].(string)
	if !strings.Contains(msg, "14:32") {
		t.Fatalf("the lock message should tell when it opens again: %q", msg)
	}
}

func TestRegisterWithoutMailSaysSo(t *testing.T) {
	store := &fakeStore{}
	h := routerFor(store, Handler{})
	rec, out := call(t, h, "POST", "/api/v1/auth/register",
		`{"name":"Lan Anh","username":"lananh","email":"lananh@example.com","password":"cà phê sữa đá 47"}`, "")
	if rec.Code != 503 || code(out) != "EMAIL_UNAVAILABLE" {
		t.Fatalf("no mailer: %d %v", rec.Code, out)
	}
	if len(store.calls) != 0 {
		t.Fatalf("nothing should be written without a way to send the code: %v", store.calls)
	}
}

func TestRegisterSendsTheCode(t *testing.T) {
	store := &fakeStore{code: "123456", sess: Session{Token: "tok", ExpiresAt: time.Now().Add(time.Hour)},
		user: User{ID: "u1", Name: "Lan Anh", Email: "lananh@example.com", Username: "lananh"}}
	mailer := &fakeMailer{}
	h := routerFor(store, Handler{Mailer: mailer})
	rec, out := call(t, h, "POST", "/api/v1/auth/register",
		`{"name":"Lan Anh","username":"lananh","email":"lananh@example.com","phone":"0912 345 678","password":"cà phê sữa đá 47","device":"iPhone"}`, "")
	if rec.Code != 201 || out["codeSent"] != true {
		t.Fatalf("register: %d %v", rec.Code, out)
	}
	if mailer.sent != 1 || mailer.to != "lananh@example.com" || !strings.Contains(mailer.body, "123456") {
		t.Fatalf("mail = %+v", mailer)
	}
	if !strings.Contains(mailer.subject, "xác minh") {
		t.Fatalf("subject = %q", mailer.subject)
	}
	if strings.Contains(rec.Body.String(), "123456") {
		t.Fatal("the code must not come back in the response")
	}
}

func TestRegisterWhenTheMailFails(t *testing.T) {
	store := &fakeStore{code: "123456", user: User{ID: "u1", Email: "lananh@example.com"}}
	mailer := &fakeMailer{err: errors.New("smtp down")}
	h := routerFor(store, Handler{Mailer: mailer})
	rec, out := call(t, h, "POST", "/api/v1/auth/register",
		`{"name":"Lan Anh","username":"lananh","email":"lananh@example.com","password":"cà phê sữa đá 47"}`, "")
	if rec.Code != 201 || out["codeSent"] != false {
		t.Fatalf("the account exists, so the app must be told to resend: %d %v", rec.Code, out)
	}
}

func TestGoogleRoutes(t *testing.T) {
	h := routerFor(&fakeStore{}, Handler{Auth: NewGoogleVerifier("")})
	rec, out := call(t, h, "POST", "/api/v1/auth/google", `{"idToken":"a.b.c"}`, "")
	if rec.Code != 503 || code(out) != "GOOGLE_UNAVAILABLE" {
		t.Fatalf("google off: %d %v", rec.Code, out)
	}
}

func TestUsernameAvailable(t *testing.T) {
	h := routerFor(&fakeStore{exists: true}, Handler{})
	rec, out := call(t, h, "GET", "/api/v1/auth/username-available?u=lananh", "", "")
	if rec.Code != 200 || out["available"] != true {
		t.Fatalf("available: %d %v", rec.Code, out)
	}
}

func TestForgotIsAlwaysTheSame(t *testing.T) {
	mailer := &fakeMailer{}
	h := routerFor(&fakeStore{exists: false}, Handler{Mailer: mailer})
	rec, _ := call(t, h, "POST", "/api/v1/auth/forgot", `{"email":"khongco@example.com"}`, "")
	if rec.Code != 202 {
		t.Fatalf("unknown address: %d", rec.Code)
	}
	if mailer.sent != 0 {
		t.Fatal("no mail for an unknown address")
	}

	store := &fakeStore{exists: true, code: "654321", user: User{Name: "Lan Anh", Email: "lananh@example.com"}}
	h = routerFor(store, Handler{Mailer: mailer})
	rec, _ = call(t, h, "POST", "/api/v1/auth/forgot", `{"email":"lananh@example.com"}`, "")
	if rec.Code != 202 || mailer.sent != 1 || !strings.Contains(mailer.body, "654321") {
		t.Fatalf("known address: %d mail=%+v", rec.Code, mailer)
	}
	if !strings.Contains(mailer.subject, "đặt lại mật khẩu") {
		t.Fatalf("subject = %q", mailer.subject)
	}
}

func TestResetWithABadCode(t *testing.T) {
	h := routerFor(&fakeStore{err: ErrCodeInvalid}, Handler{})
	rec, out := call(t, h, "POST", "/api/v1/auth/reset",
		`{"email":"lananh@example.com","code":"000000","newPassword":"cà phê sữa đá 47"}`, "")
	if rec.Code != 400 || code(out) != "CODE_INVALID" {
		t.Fatalf("bad code: %d %v", rec.Code, out)
	}
}

func TestDeleteWithoutThePassword(t *testing.T) {
	h := routerFor(&fakeStore{me: User{ID: "u1"}, err: ErrBadInput}, Handler{})
	rec, out := call(t, h, "DELETE", "/api/v1/me", `{"password":"sai"}`, "tok")
	if rec.Code != 400 {
		t.Fatalf("wrong password: %d %v", rec.Code, out)
	}
}

func TestValidationMessageReachesTheApp(t *testing.T) {
	store := &fakeStore{err: bad("Tên đăng nhập 3–30 ký tự, bắt đầu bằng chữ, chỉ gồm a-z 0-9 . _")}
	h := routerFor(store, Handler{Mailer: &fakeMailer{}})
	rec, out := call(t, h, "POST", "/api/v1/auth/register", `{"name":"Lan","username":"9x","email":"a@b.co","password":"cà phê sữa đá 47"}`, "")
	if rec.Code != 400 || code(out) != "INVALID_REQUEST" {
		t.Fatalf("bad username: %d %v", rec.Code, out)
	}
	msg, _ := out["error"].(map[string]any)["message"].(string)
	if !strings.Contains(strings.ToLower(msg), "tên đăng nhập") {
		t.Fatalf("message = %q", msg)
	}
}
