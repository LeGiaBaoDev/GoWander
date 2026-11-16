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
	"net/url"
	"slices"
	"strconv"
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
	Health         []Health
	DataStatus     string // ROUTING_DATA_STATUS (ADR-027)
	Log            *slog.Logger
}

type ctxKey struct{}

type bar struct {
	Label string
	Value int
}

var statusNames = map[string]string{"PUBLISHED": "Đang hiển thị", "HIDDEN": "Đang ẩn", "DRAFT": "Bản nháp"}

var missingNames = map[string]string{"photo": "Chưa có ảnh", "description": "Chưa có mô tả", "address": "Chưa có địa chỉ", "category": "Chưa có loại"}

// actionNames shows audit actions in Vietnamese; unknown codes are shown as they are.
var actionNames = map[string]string{
	"admin.bootstrap": "Tạo quản trị viên đầu tiên",
	"admin.login":     "Đăng nhập",
	"admin.logout":    "Đăng xuất",
	"role.grant":      "Cấp vai trò",
	"role.revoke":     "Thu hồi vai trò",
	"place.update":    "Sửa địa điểm",
	"category.update": "Sửa loại địa điểm",
}

// scaleBar is a bar's height: values 1-4 all get the height of 5 (capped at the top), so the bar does not show them.
func scaleBar(v, top, height int) int {
	if v <= 0 || top <= 0 {
		return 0
	}
	return min(max(v, MinShown), top) * height / top
}

// groupDigits: 1030 -> 1.030 (Vietnamese grouping).
func groupDigits(n int) string {
	s := strconv.Itoa(n)
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + "." + s[i:]
	}
	return s
}

func current(r *http.Request) Session { s, _ := r.Context().Value(ctxKey{}).(Session); return s }

var pages = func() map[string]*template.Template {
	funcs := template.FuncMap{
		"has": func(s Session, role string) bool { return s.Has(role) },
		"initial": func(s Session) string {
			for _, c := range strings.TrimSpace(s.Name + s.Email) {
				return strings.ToUpper(string(c))
			}
			return "?"
		},
		"list": func(kv ...any) []bar { // "Label", value, "Label", value ... -> rows of a bar list
			var out []bar
			for i := 0; i+1 < len(kv); i += 2 {
				out = append(out, bar{kv[i].(string), kv[i+1].(int)})
			}
			return out
		},
		"pct": func(part, whole int) int {
			if whole == 0 {
				return 0
			}
			return part * 100 / whole
		},
		"num":         groupDigits,
		"statusName":  func(s string) string { return statusNames[s] },
		"missingName": func(s string) string { return missingNames[s] },
		"action": func(a string) string {
			if v, ok := actionNames[a]; ok {
				return v
			}
			return a
		},
		"when": func(t *time.Time) string {
			if t == nil {
				return "chưa đăng nhập"
			}
			return t.Local().Format("02-01-2006 15:04")
		},
		"count": func(n int) string { // usage numbers: 1-4 show as "<5" (ADR-038 §5)
			if n > 0 && n < MinShown {
				return "<" + strconv.Itoa(MinShown)
			}
			return groupDigits(n)
		},
		"scale": scaleBar,
		"sub":   func(a, b int) int { return a - b },
		"mul":   func(a, b int) int { return a * b },
		"at":    func(t time.Time) string { return t.Local().Format("02-01-2006 15:04:05") },
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
	for _, name := range []string{"login", "home", "places", "place", "categories", "usage", "audit", "message"} {
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
				r.Use(requireRole(RoleSystemAdmin, RoleContentEditor))
				r.Get("/places", h.places)
				r.Get("/places/{id}", h.placeForm)
				r.Post("/places/{id}", h.savePlace)
				r.Get("/categories", h.categories)
				r.Post("/categories/{id}", h.saveCategory)
			})
			r.Group(func(r chi.Router) {
				r.Use(requireRole(RoleSystemAdmin))
				r.Get("/audit", h.audit)
				r.Get("/usage", h.usage)
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
	data["Page"] = page
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
	o, err := h.Store.Overview(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	recent, err := h.Store.AuditLog(r.Context(), 5)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	type state struct {
		Name string
		OK   bool
	}
	var health []state
	var down []string
	for _, c := range h.Health {
		ctx, cancel := context.WithTimeout(r.Context(), time.Second)
		err := c.Ready(ctx)
		cancel()
		if err != nil {
			h.Log.Warn("admin health check failed", "dependency", c.Name, "requestId", httpx.RequestID(r), "err", err)
			down = append(down, c.Name)
		}
		health = append(health, state{c.Name, err == nil})
	}
	h.render(w, r, http.StatusOK, "home", map[string]any{
		"O": o, "Recent": recent, "Health": health, "Official": h.DataStatus == "OFFICIAL", "Attention": attention(o, down, h.DataStatus),
	})
}

func (h Handler) places(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	q := PlaceQuery{Q: v.Get("q"), Status: v.Get("status"), Missing: v.Get("missing")}
	q.Category, _ = strconv.ParseInt(v.Get("category"), 10, 64)
	q.Page, _ = strconv.Atoi(v.Get("page"))
	q.Page = max(q.Page, 1)
	if !slices.Contains(PlaceStatuses, q.Status) {
		q.Status = ""
	}
	if _, ok := missingFilters[q.Missing]; !ok {
		q.Missing = ""
	}
	list, total, err := h.Store.Places(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	cats, err := h.Store.Categories(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	pageURL := func(page int) string {
		u := url.Values{}
		for k, val := range map[string]string{"q": q.Q, "status": q.Status, "missing": q.Missing} {
			if val != "" {
				u.Set(k, val)
			}
		}
		if q.Category != 0 {
			u.Set("category", strconv.FormatInt(q.Category, 10))
		}
		u.Set("page", strconv.Itoa(page))
		return "/admin/places?" + u.Encode()
	}
	data := map[string]any{"List": list, "Total": total, "Q": q, "Categories": cats, "Statuses": PlaceStatuses,
		"From": (q.Page-1)*PageSize + 1, "To": (q.Page-1)*PageSize + len(list)}
	if q.Page > 1 {
		data["Prev"] = pageURL(q.Page - 1)
	}
	if q.Page*PageSize < total {
		data["Next"] = pageURL(q.Page + 1)
	}
	h.render(w, r, http.StatusOK, "places", data)
}

func (h Handler) placeForm(w http.ResponseWriter, r *http.Request) {
	p, err := h.Store.Place(r.Context(), chi.URLParam(r, "id"))
	if errors.Is(err, ErrNotFound) {
		h.render(w, r, http.StatusNotFound, "message", map[string]any{"Title": "Không có địa điểm này", "Text": "Địa điểm không tồn tại hoặc đường dẫn sai."})
		return
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.renderPlace(w, r, http.StatusOK, p, "", r.URL.Query().Get("saved") == "1")
}

func (h Handler) renderPlace(w http.ResponseWriter, r *http.Request, status int, p Place, problem string, saved bool) {
	cats, err := h.Store.Categories(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.render(w, r, status, "place", map[string]any{"P": p, "Categories": cats, "Statuses": PlaceStatuses, "Problem": problem, "Saved": saved})
}

func (h Handler) savePlace(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	cat, _ := strconv.ParseInt(r.PostFormValue("category"), 10, 64)
	p := Place{ID: id, Name: r.PostFormValue("name"), NameEN: r.PostFormValue("name_en"), Description: r.PostFormValue("description"),
		Address: r.PostFormValue("address"), Status: r.PostFormValue("status"), CategoryID: cat}
	err := h.Store.UpdatePlace(r.Context(), current(r).UserID, p, httpx.RequestID(r))
	switch {
	case errors.Is(err, ErrBadInput):
		// Show the form again with what the editor typed, so nothing is lost.
		stored, lookupErr := h.Store.Place(r.Context(), id)
		if lookupErr != nil {
			h.fail(w, r, lookupErr)
			return
		}
		stored.Name, stored.NameEN, stored.Description, stored.Address, stored.Status, stored.CategoryID = p.Name, p.NameEN, p.Description, p.Address, p.Status, p.CategoryID
		h.renderPlace(w, r, http.StatusUnprocessableEntity, stored, strings.TrimPrefix(err.Error(), ErrBadInput.Error()+": "), false)
	case errors.Is(err, ErrNotFound):
		h.render(w, r, http.StatusNotFound, "message", map[string]any{"Title": "Không có địa điểm này", "Text": "Địa điểm không tồn tại hoặc đường dẫn sai."})
	case err != nil:
		h.fail(w, r, err)
	default:
		http.Redirect(w, r, "/admin/places/"+url.PathEscape(id)+"?saved=1", http.StatusSeeOther)
	}
}

func (h Handler) categories(w http.ResponseWriter, r *http.Request) {
	cats, err := h.Store.Categories(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.render(w, r, http.StatusOK, "categories", map[string]any{"Categories": cats, "Notice": r.URL.Query().Get("n")})
}

func (h Handler) saveCategory(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	order, orderErr := strconv.Atoi(strings.TrimSpace(r.PostFormValue("sort_order")))
	if err != nil || orderErr != nil {
		http.Redirect(w, r, "/admin/categories?n=bad", http.StatusSeeOther)
		return
	}
	c := Category{ID: id, Name: r.PostFormValue("name"), NameEN: r.PostFormValue("name_en"), SortOrder: order, Active: r.PostFormValue("active") == "on"}
	switch err := h.Store.UpdateCategory(r.Context(), current(r).UserID, c, httpx.RequestID(r)); {
	case errors.Is(err, ErrBadInput):
		http.Redirect(w, r, "/admin/categories?n=bad", http.StatusSeeOther)
	case err != nil:
		h.fail(w, r, err)
	default:
		http.Redirect(w, r, "/admin/categories?n=saved", http.StatusSeeOther)
	}
}

func (h Handler) usage(w http.ResponseWriter, r *http.Request) {
	q := UsageQuery{Area: r.URL.Query().Get("area")}
	q.Days, _ = strconv.Atoi(r.URL.Query().Get("days"))
	if !slices.Contains(UsageDays, q.Days) {
		q.Days = UsageDays[0]
	}
	u, err := h.Store.Usage(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.render(w, r, http.StatusOK, "usage", map[string]any{"U": u, "DaysOptions": UsageDays, "MinShown": MinShown})
}

func (h Handler) audit(w http.ResponseWriter, r *http.Request) {
	entries, err := h.Store.AuditLog(r.Context(), 200)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	h.render(w, r, http.StatusOK, "audit", map[string]any{"Entries": entries})
}
