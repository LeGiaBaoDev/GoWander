package account

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/mail"
)

// Handler serves /api/v1/auth/* and /api/v1/me (ADR-040 §5).
type Handler struct {
	Store  Store
	Auth   *GoogleVerifier
	Mailer mail.Sender
	Log    *slog.Logger
	Limit  *httpx.Limiter
}

// Mount adds every account route. Routes marked (Bearer) need a session.
func (h Handler) Mount(r chi.Router) {
	if h.Limit == nil {
		h.Limit = httpx.NewLimiter(120, 15*time.Minute)
	}
	r.Post("/api/v1/auth/register", h.Register)
	r.Post("/api/v1/auth/verify-email", h.VerifyEmail) // Bearer
	r.Post("/api/v1/auth/resend-code", h.ResendCode)   // Bearer
	r.Post("/api/v1/auth/login", h.Login)
	r.Post("/api/v1/auth/google", h.Google)
	r.Post("/api/v1/auth/logout", h.Logout) // Bearer
	r.Post("/api/v1/auth/logout-all", h.LogoutAll)
	r.Post("/api/v1/auth/forgot", h.Forgot)
	r.Post("/api/v1/auth/reset", h.Reset)
	r.Get("/api/v1/auth/username-available", h.UsernameAvailable)
	r.Get("/api/v1/me", h.Me)
	r.Patch("/api/v1/me", h.UpdateMe)
	r.Post("/api/v1/me/password", h.ChangePassword)
	r.Post("/api/v1/me/google", h.LinkGoogle)
	r.Delete("/api/v1/me/google", h.UnlinkGoogle)
	r.Delete("/api/v1/me", h.DeleteMe)
}

type state struct {
	user  User
	token string
}

type ctxKey struct{}

// Optional reads a bearer token when there is one and attaches the account to the request. A missing, expired or
// unknown token simply means "guest": the map, Discover and route planning never need an account.
func Optional(store Store) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			token, ok := bearer(r)
			if !ok || store == nil {
				next.ServeHTTP(w, r)
				return
			}
			user, err := store.Me(r.Context(), token)
			if err != nil {
				next.ServeHTTP(w, r) // guest; /me itself answers 401
				return
			}
			next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), ctxKey{}, state{user: user, token: token})))
		})
	}
}

func bearer(r *http.Request) (string, bool) {
	h := r.Header.Get("Authorization")
	if len(h) < 8 || !strings.EqualFold(h[:7], "bearer ") {
		return "", false
	}
	token := strings.TrimSpace(h[7:])
	return token, token != ""
}

// FromContext returns the signed-in account of the request.
func FromContext(ctx context.Context) (User, bool) {
	s, ok := ctx.Value(ctxKey{}).(state)
	return s.user, ok
}

func tokenFrom(ctx context.Context) string {
	s, ok := ctx.Value(ctxKey{}).(state)
	if !ok {
		return ""
	}
	return s.token
}

// ---------- handlers ----------

func (h Handler) Register(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Name, Username, Email, Phone, Password, Device string
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	if h.Mailer == nil {
		noEmail(w, r)
		return
	}
	session, user, code, err := h.Store.Register(r.Context(), RegisterInput{
		Name: in.Name, Username: in.Username, Email: in.Email, Phone: in.Phone, Password: in.Password, Device: in.Device,
	})
	if err != nil {
		h.fail(w, r, err)
		return
	}
	sent := true
	if err := h.send(r.Context(), user, code, VerifyEmail); err != nil {
		sent = false
		h.logError("verification email failed", r, err)
	}
	httpx.WriteJSON(w, http.StatusCreated, map[string]any{"session": session, "user": user, "codeSent": sent})
}

func (h Handler) VerifyEmail(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	var in struct {
		Code string `json:"code"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	user, err := h.Store.VerifyEmail(r.Context(), me.ID, in.Code)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user": user})
}

func (h Handler) ResendCode(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	var in struct {
		Purpose string `json:"purpose"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	if h.Mailer == nil {
		noEmail(w, r)
		return
	}
	purpose := strings.ToUpper(strings.TrimSpace(in.Purpose))
	if purpose == "" {
		purpose = VerifyEmail
	}
	if purpose != VerifyEmail && purpose != ResetPassword {
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "Mục đích gửi mã không hợp lệ.")
		return
	}
	code, err := h.Store.IssueCode(r.Context(), me.ID, purpose)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	if err := h.send(r.Context(), me, code, purpose); err != nil {
		h.logError("code email failed", r, err)
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "EMAIL_UNAVAILABLE", "Chưa gửi được email. Vui lòng thử lại sau ít phút.")
		return
	}
	w.WriteHeader(http.StatusAccepted)
}

func (h Handler) Login(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Identifier, Password, Device string
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	session, user, err := h.Store.Login(r.Context(), in.Identifier, in.Password, in.Device)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"session": session, "user": user})
}

func (h Handler) Google(w http.ResponseWriter, r *http.Request) {
	var in struct {
		IDToken string `json:"idToken"`
		Device  string `json:"device"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	if !h.Auth.Enabled() {
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "GOOGLE_UNAVAILABLE", "Đăng nhập Google chưa được bật trên máy chủ này.")
		return
	}
	id, err := h.Auth.Verify(r.Context(), in.IDToken)
	if err != nil {
		h.logError("google token refused", r, err)
		httpx.WriteError(w, r, http.StatusUnauthorized, "GOOGLE_TOKEN_INVALID", "Không đăng nhập được bằng Google. Vui lòng thử lại.")
		return
	}
	session, user, created, err := h.Store.GoogleLogin(r.Context(), id, in.Device)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"session": session, "user": user, "created": created})
}

func (h Handler) Logout(w http.ResponseWriter, r *http.Request) {
	token := tokenFrom(r.Context())
	if token == "" {
		authRequired(w, r)
		return
	}
	if err := h.Store.Logout(r.Context(), token); err != nil {
		h.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h Handler) LogoutAll(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	if err := h.Store.LogoutAll(r.Context(), me.ID, tokenFrom(r.Context())); err != nil {
		h.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h Handler) Forgot(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Email string `json:"email"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	if h.Mailer == nil {
		noEmail(w, r)
		return
	}
	user, code, found, err := h.Store.Forgot(r.Context(), in.Email)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	if found {
		if err := h.send(r.Context(), user, code, ResetPassword); err != nil {
			h.logError("reset email failed", r, err)
		}
	}
	// The same answer whether the address has an account, came too soon, or does not exist.
	w.WriteHeader(http.StatusAccepted)
}

func (h Handler) Reset(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Email       string `json:"email"`
		Code        string `json:"code"`
		NewPassword string `json:"newPassword"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	if err := h.Store.ResetPassword(r.Context(), in.Email, in.Code, in.NewPassword); err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (h Handler) UsernameAvailable(w http.ResponseWriter, r *http.Request) {
	if !h.allow(w, r) {
		return
	}
	available, err := h.Store.UsernameAvailable(r.Context(), r.URL.Query().Get("u"))
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]bool{"available": available})
}

func (h Handler) Me(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user": me})
}

func (h Handler) UpdateMe(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	var in struct {
		Name     *string `json:"name"`
		Username *string `json:"username"`
		Phone    *string `json:"phone"`
	}
	if !decode(w, r, &in) {
		return
	}
	user, err := h.Store.UpdateProfile(r.Context(), me.ID, in.Name, in.Username, in.Phone)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user": user})
}

func (h Handler) ChangePassword(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	var in struct {
		Current string `json:"current"`
		New     string `json:"new"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	if err := h.Store.ChangePassword(r.Context(), me.ID, in.Current, in.New, tokenFrom(r.Context())); err != nil {
		h.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h Handler) LinkGoogle(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	var in struct {
		IDToken string `json:"idToken"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.Auth.Enabled() {
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "GOOGLE_UNAVAILABLE", "Đăng nhập Google chưa được bật trên máy chủ này.")
		return
	}
	id, err := h.Auth.Verify(r.Context(), in.IDToken)
	if err != nil {
		h.logError("google token refused", r, err)
		httpx.WriteError(w, r, http.StatusUnauthorized, "GOOGLE_TOKEN_INVALID", "Không xác minh được tài khoản Google. Vui lòng thử lại.")
		return
	}
	user, err := h.Store.LinkGoogle(r.Context(), me.ID, id)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user": user})
}

func (h Handler) UnlinkGoogle(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	user, err := h.Store.UnlinkGoogle(r.Context(), me.ID)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"user": user})
}

func (h Handler) DeleteMe(w http.ResponseWriter, r *http.Request) {
	me, ok := FromContext(r.Context())
	if !ok {
		authRequired(w, r)
		return
	}
	var in struct {
		Password string `json:"password"`
		IDToken  string `json:"idToken"`
	}
	if !decode(w, r, &in) {
		return
	}
	if !h.allow(w, r) {
		return
	}
	viaGoogle := strings.TrimSpace(in.IDToken) != ""
	if viaGoogle && me.HasPassword {
		// A password account must confirm with its password; Google is only the confirmation of a Google-only account.
		viaGoogle = false
	}
	if viaGoogle {
		if !h.Auth.Enabled() {
			httpx.WriteError(w, r, http.StatusServiceUnavailable, "GOOGLE_UNAVAILABLE", "Đăng nhập Google chưa được bật trên máy chủ này.")
			return
		}
		if _, err := h.Auth.Verify(r.Context(), in.IDToken); err != nil {
			h.logError("google token refused", r, err)
			httpx.WriteError(w, r, http.StatusUnauthorized, "GOOGLE_TOKEN_INVALID", "Không xác minh được tài khoản Google. Vui lòng thử lại.")
			return
		}
	}
	if err := h.Store.Delete(r.Context(), me.ID, in.Password, viaGoogle); err != nil {
		h.fail(w, r, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ---------- plumbing ----------

func decode(w http.ResponseWriter, r *http.Request, v any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 16<<10)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "Dữ liệu gửi lên không đọc được.")
		return false
	}
	return true
}

func (h Handler) allow(w http.ResponseWriter, r *http.Request) bool {
	if h.Limit == nil || h.Limit.Allow(r) {
		return true
	}
	w.Header().Set("Retry-After", "900")
	httpx.WriteError(w, r, http.StatusTooManyRequests, "RATE_LIMITED", "Bạn thao tác hơi nhanh. Vui lòng thử lại sau ít phút.")
	return false
}

func authRequired(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("WWW-Authenticate", `Bearer realm="GoWander"`)
	httpx.WriteError(w, r, http.StatusUnauthorized, "AUTH_REQUIRED", "Bạn cần đăng nhập để làm việc này.")
}

func noEmail(w http.ResponseWriter, r *http.Request) {
	httpx.WriteError(w, r, http.StatusServiceUnavailable, "EMAIL_UNAVAILABLE", "Máy chủ chưa gửi được email. Vui lòng thử lại sau.")
}

// fail turns a store error into the shared envelope. Internal errors are logged, never echoed.
func (h Handler) fail(w http.ResponseWriter, r *http.Request, err error) {
	var locked LockedError
	switch {
	case errors.As(err, &locked):
		retry := "Vui lòng thử lại sau ít phút."
		if until := locked.Until.Format("15:04"); until != "" {
			retry = "Tài khoản tạm khoá tới " + until + ". Thử lại sau nhé."
		}
		httpx.WriteError(w, r, http.StatusLocked, "ACCOUNT_LOCKED", retry)
	case errors.Is(err, ErrLoginFailed):
		httpx.WriteError(w, r, http.StatusUnauthorized, "LOGIN_FAILED", "Sai email, số điện thoại, tên đăng nhập hoặc mật khẩu.")
	case errors.Is(err, ErrAuthRequired):
		authRequired(w, r)
	case errors.Is(err, ErrCodeInvalid):
		httpx.WriteError(w, r, http.StatusBadRequest, "CODE_INVALID", "Mã không đúng hoặc đã hết hạn. Bạn yêu cầu mã mới nhé.")
	case errors.Is(err, ErrAccountExists):
		httpx.WriteError(w, r, http.StatusConflict, "ACCOUNT_EXISTS", "Email, số điện thoại hoặc tên đăng nhập này đã được dùng. Thử cách khác nhé.")
	case errors.Is(err, ErrLinkRequired):
		httpx.WriteError(w, r, http.StatusConflict, "LINK_REQUIRED", "Email này đã có tài khoản. Đăng nhập bằng mật khẩu rồi bật “Liên kết Google” trong Cài đặt.")
	case errors.Is(err, ErrRateLimited):
		w.Header().Set("Retry-After", "60")
		httpx.WriteError(w, r, http.StatusTooManyRequests, "RATE_LIMITED", "Bạn vừa yêu cầu mã. Chờ một chút rồi thử lại nhé.")
	case errors.Is(err, ErrGoogleToken):
		httpx.WriteError(w, r, http.StatusUnauthorized, "GOOGLE_TOKEN_INVALID", "Không xác minh được tài khoản Google. Vui lòng thử lại.")
	case errors.Is(err, ErrBadInput):
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", messageOf(err))
	default:
		h.logError("account request failed", r, err)
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "ACCOUNT_UNAVAILABLE", "Chưa thực hiện được. Vui lòng thử lại sau.")
	}
}

// messageOf is the sentence the app shows: the one the store wrote, or a plain fallback.
func messageOf(err error) string {
	var v ValidationError
	if errors.As(err, &v) && v.Message != "" {
		return v.Message
	}
	return "Thông tin chưa hợp lệ, bạn kiểm tra lại giúp nhé."
}

func (h Handler) logError(what string, r *http.Request, err error) {
	if h.Log == nil {
		return
	}
	h.Log.Error(what, "requestId", httpx.RequestID(r), "err", err) // never a code, a token or a password
}

func (h Handler) send(ctx context.Context, user User, code, purpose string) error {
	if h.Mailer == nil || user.Email == "" {
		return errors.New("email is not configured")
	}
	subject, body := codeEmail(user.Name, code, purpose)
	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	return h.Mailer.Send(ctx, user.Email, subject, body)
}

// codeEmail is the exact letter a person receives. It never says whether an address has an account; it carries no link.
func codeEmail(name, code, purpose string) (subject, body string) {
	if name == "" {
		name = "bạn"
	}
	if purpose == ResetPassword {
		return "GoWander: mã đặt lại mật khẩu",
			"Xin chào " + name + ",\n\n" +
				"Mã đặt lại mật khẩu GoWander của bạn là: " + code + "\n" +
				"Mã dùng được trong 10 phút và chỉ dùng một lần.\n\n" +
				"Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua thư này: mật khẩu cũ vẫn dùng được.\n\n" +
				"GoWander\n"
	}
	return "GoWander: mã xác minh email",
		"Xin chào " + name + ",\n\n" +
			"Mã xác minh GoWander của bạn là: " + code + "\n" +
			"Mã dùng được trong 10 phút và chỉ dùng một lần.\n\n" +
			"Nếu bạn không tạo tài khoản GoWander, hãy bỏ qua thư này.\n\n" +
			"GoWander\n"
}
