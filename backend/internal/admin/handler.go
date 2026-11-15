package admin

import (
	"context"
	"crypto/subtle"
	"embed"
	"errors"
	"html/template"
	"io/fs"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

//go:embed web/templates/*.html web/static/*
var webFS embed.FS

const (
	sessionCookie = "gw_admin"
	oauthCookie   = "gw_admin_oauth"
	sessionTTL    = 12 * time.Hour
)

// Handler serves /admin. Store is nil when ADMIN_DATABASE_URL is not set, Auth is nil without a Google client: the
// pages then say what is missing instead of failing.
type Handler struct {
	Store          Store
	Auth           Authenticator
	BootstrapEmail string
	SecureCookies  bool // https deployments; false only for http://localhost development
	Log            *slog.Logger
}

type ctxKey struct{}

func current(r *http.Request) Session { s, _ := r.Context().Value(ctxKey{}).(Session); return s }

var pages = func() map[string]*template.Template {
	funcs := template.FuncMap{
		"has": func(s Session, role string) bool { return s.Has(role) },
		"when": func(t *time.Time) string {
			if t == nil {
				return "chưa đăng nhập"
			}
			return t.Local().Format("02-01-2006 15:04")
		},
		"at": func(t time.Time) string { return t.Local().Format("02-01-2006 15:04:05") },
		"in": func(list []string, v string) bool {
			for _, x := range list {
				if x == v {
					return true
				}
			}
			return false
		},
	}
	out := map[string]*template.Template{}
	for _, name := range []string{"login", "home", "users", "audit", "message"} {
		out[name] = template.Must(template.New("layout.html").Funcs(funcs).ParseFS(webFS, "web/templates/layout.html", "web/templates/"+name+".html"))
	}
	return out
}()

func (h Handler) Mount(r chi.Router) {
	static, _ := fs.Sub(webFS, "web/static")
	r.Route("/admin", func(r chi.Router) {
		r.Use(securityHeaders)
		r.Handle("/static/*", http.StripPrefix("/admin/static/", http.FileServer(http.FS(static))))
		if h.Store == nil {
			r.HandleFunc("/*", func(w http.ResponseWriter, r *http.Request) {
				h.render(w, r, http.StatusServiceUnavailable, "message", map[string]any{"Title": "Admin chưa được cấu hình", "Text": "Đặt ADMIN_DATABASE_URL (role gowander_admin) để mở trang quản trị."})
			})
			return
		}
		r.Get("/login", h.loginPage)
		r.Get("/auth/start", h.authStart)
		r.Get("/auth/callback", h.authCallback)
		r.Group(func(r chi.Router) {
			r.Use(h.requireSession, h.requireCSRF)
			r.Get("/", h.home)
			r.Post("/logout", h.logout)
			r.Group(func(r chi.Router) {
				r.Use(requireRole(RoleSystemAdmin))
				r.Get("/users", h.users)
				r.Post("/users/grant", h.grant)
				r.Post("/users/revoke", h.revoke)
				r.Get("/audit", h.audit)
			})
		})
	})
}

// securityHeaders: no framing, no inline scripts, nothing cached (pages hold operator data).
func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hd := w.Header()
		hd.Set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
		hd.Set("X-Content-Type-Options", "nosniff")
		hd.Set("Referrer-Policy", "same-origin")
		hd.Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}

func (h Handler) render(w http.ResponseWriter, r *http.Request, status int, page string, data map[string]any) {
	if data == nil {
		data = map[string]any{}
	}
	data["Me"] = current(r)
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := pages[page].Execute(w, data); err != nil {
		h.Log.Warn("admin template failed", "page", page, "requestId", httpx.RequestID(r), "err", err)
	}
}

func (h Handler) fail(w http.ResponseWriter, r *http.Request, err error) {
	h.Log.Warn("admin request failed", "path", r.URL.Path, "requestId", httpx.RequestID(r), "err", err)
	h.render(w, r, http.StatusServiceUnavailable, "message", map[string]any{"Title": "Không làm được lúc này", "Text": "Cơ sở dữ liệu hoặc Google tạm thời không trả lời. Thử lại sau; mã yêu cầu: " + httpx.RequestID(r)})
}

func (h Handler) requireSession(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		c, err := r.Cookie(sessionCookie)
		if err != nil {
			http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			return
		}
		s, err := h.Store.Session(r.Context(), c.Value)
		if errors.Is(err, ErrNotAllowed) {
			h.clearCookie(w, sessionCookie, "/admin")
			http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			return
		}
		if err != nil {
			h.fail(w, r, err)
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), ctxKey{}, s)))
	})
}

// requireCSRF: every state-changing request carries the session's token (form field or htmx header).
func (h Handler) requireCSRF(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			got := r.Header.Get("X-CSRF-Token")
			if got == "" {
				got = r.PostFormValue("csrf")
			}
			if subtle.ConstantTimeCompare([]byte(got), []byte(current(r).CSRF)) != 1 {
				h.render(w, r, http.StatusForbidden, "message", map[string]any{"Title": "Yêu cầu không hợp lệ", "Text": "Trang đã cũ. Tải lại trang rồi thử lại."})
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

// requireRole is checked on the server for every request, whatever the page shows (ADR-037 §4).
func requireRole(roles ...string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			s := current(r)
			for _, role := range roles {
				if s.Has(role) {
					next.ServeHTTP(w, r)
					return
				}
			}
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = pages["message"].Execute(w, map[string]any{"Me": s, "Title": "Không có quyền", "Text": "Tài khoản của bạn không có vai trò cần cho trang này."})
		})
	}
}

func (h Handler) loginPage(w http.ResponseWriter, r *http.Request) {
	msg := map[string]string{
		"denied":  "Tài khoản Google này chưa được cấp quyền quản trị. Nhờ quản trị hệ thống cấp vai trò cho email của bạn.",
		"expired": "Phiên đăng nhập Google đã hết hạn. Bấm đăng nhập lại.",
	}[r.URL.Query().Get("e")]
	h.render(w, r, http.StatusOK, "login", map[string]any{"Error": msg, "GoogleReady": h.Auth != nil})
}

func (h Handler) authStart(w http.ResponseWriter, r *http.Request) {
	if h.Auth == nil {
		h.render(w, r, http.StatusServiceUnavailable, "message", map[string]any{"Title": "Chưa bật đăng nhập Google", "Text": "Đặt GOOGLE_CLIENT_ID và GOOGLE_CLIENT_SECRET (OAuth client loại Web) rồi khởi động lại API."})
		return
	}
	state, nonce, verifier := randomToken(), randomToken(), randomToken()
	url, err := h.Auth.AuthURL(r.Context(), state, nonce, verifier)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	// Short-lived, sent back only to the callback; holds no secret beyond this one sign-in.
	http.SetCookie(w, &http.Cookie{Name: oauthCookie, Value: state + "." + nonce + "." + verifier, Path: "/admin/auth",
		MaxAge: 600, HttpOnly: true, Secure: h.SecureCookies, SameSite: http.SameSiteLaxMode})
	http.Redirect(w, r, url, http.StatusSeeOther)
}

func (h Handler) authCallback(w http.ResponseWriter, r *http.Request) {
	c, err := r.Cookie(oauthCookie)
	h.clearCookie(w, oauthCookie, "/admin/auth")
	parts := []string{}
	if err == nil {
		parts = strings.Split(c.Value, ".")
	}
	if len(parts) != 3 || subtle.ConstantTimeCompare([]byte(parts[0]), []byte(r.URL.Query().Get("state"))) != 1 || r.URL.Query().Get("code") == "" {
		http.Redirect(w, r, "/admin/login?e=expired", http.StatusSeeOther)
		return
	}
	id, err := h.Auth.Exchange(r.Context(), r.URL.Query().Get("code"), parts[2], parts[1])
	if err != nil {
		h.Log.Info("admin sign-in rejected", "requestId", httpx.RequestID(r), "err", err) // no email logged
		http.Redirect(w, r, "/admin/login?e=expired", http.StatusSeeOther)
		return
	}
	userID, err := h.Store.Login(r.Context(), id, h.BootstrapEmail, httpx.RequestID(r))
	if errors.Is(err, ErrNotAllowed) {
		http.Redirect(w, r, "/admin/login?e=denied", http.StatusSeeOther)
		return
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	token, err := h.Store.CreateSession(r.Context(), userID, sessionTTL)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	http.SetCookie(w, &http.Cookie{Name: sessionCookie, Value: token, Path: "/admin", MaxAge: int(sessionTTL.Seconds()),
		HttpOnly: true, Secure: h.SecureCookies, SameSite: http.SameSiteLaxMode})
	http.Redirect(w, r, "/admin/", http.StatusSeeOther)
}

func (h Handler) clearCookie(w http.ResponseWriter, name, path string) {
	http.SetCookie(w, &http.Cookie{Name: name, Value: "", Path: path, MaxAge: -1, HttpOnly: true, Secure: h.SecureCookies, SameSite: http.SameSiteLaxMode})
}

func (h Handler) logout(w http.ResponseWriter, r *http.Request) {
	if c, err := r.Cookie(sessionCookie); err == nil {
		if err := h.Store.DeleteSession(r.Context(), c.Value); err != nil {
			h.fail(w, r, err)
			return
		}
	}
	_ = h.Store.Audit(r.Context(), AuditEntry{ActorID: current(r).UserID, Action: "admin.logout", EntityType: "user", EntityID: current(r).UserID, RequestID: httpx.RequestID(r)})
	h.clearCookie(w, sessionCookie, "/admin")
	http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
}

func (h Handler) home(w http.ResponseWriter, r *http.Request) {
	h.render(w, r, http.StatusOK, "home", nil)
}

func (h Handler) users(w http.ResponseWriter, r *http.Request) {
	ops, err := h.Store.Operators(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.render(w, r, http.StatusOK, "users", map[string]any{"Operators": ops, "Roles": AllRoles, "Notice": r.URL.Query().Get("n")})
}

func (h Handler) grant(w http.ResponseWriter, r *http.Request) {
	err := h.Store.Grant(r.Context(), current(r).UserID, r.PostFormValue("email"), r.PostFormValue("role"), httpx.RequestID(r))
	h.afterRoleChange(w, r, err, "granted")
}

func (h Handler) revoke(w http.ResponseWriter, r *http.Request) {
	err := h.Store.Revoke(r.Context(), current(r).UserID, r.PostFormValue("user"), r.PostFormValue("role"), httpx.RequestID(r))
	h.afterRoleChange(w, r, err, "revoked")
}

func (h Handler) afterRoleChange(w http.ResponseWriter, r *http.Request, err error, ok string) {
	switch {
	case errors.Is(err, ErrBadInput):
		http.Redirect(w, r, "/admin/users?n=bad", http.StatusSeeOther)
	case errors.Is(err, ErrLastSystemAdmin):
		http.Redirect(w, r, "/admin/users?n=last", http.StatusSeeOther)
	case err != nil:
		h.fail(w, r, err)
	default:
		http.Redirect(w, r, "/admin/users?n="+ok, http.StatusSeeOther)
	}
}

func (h Handler) audit(w http.ResponseWriter, r *http.Request) {
	entries, err := h.Store.AuditLog(r.Context(), 200)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.render(w, r, http.StatusOK, "audit", map[string]any{"Entries": entries})
}
