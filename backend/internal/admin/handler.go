package admin

import (
	"context"
	"crypto/subtle"
	"embed"
	"errors"
	"fmt"
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
	sessionTTL    = 12 * time.Hour
)

// Handler serves /admin. Store is nil when ADMIN_DATABASE_URL is not set, Mailer is nil without SMTP: the pages then
// say what is missing instead of failing.
type Handler struct {
	Store         Store
	Mailer        Mailer // "quên mật khẩu" emails (ADR-039)
	BaseURL       string // ADMIN_BASE_URL, for the link in that email
	SecureCookies bool   // https deployments; false only for http://localhost development
	Health        []Health
	DataStatus    string // ROUTING_DATA_STATUS (ADR-027)
	Log           *slog.Logger
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
	"admin.bootstrap":                "Tạo quản trị viên đầu tiên",
	"admin.login":                    "Đăng nhập",
	"admin.login_failed":             "Đăng nhập sai",
	"admin.logout":                   "Đăng xuất",
	"admin.password_change":          "Đổi mật khẩu",
	"admin.password_reset_requested": "Yêu cầu đặt lại mật khẩu",
	"admin.password_reset":           "Đặt lại mật khẩu",
	"account.create":                 "Tạo tài khoản",
	"account.temporary_password":     "Cấp mật khẩu tạm",
	"account.rename":                 "Đổi tên hiển thị",
	"account.email_change":           "Đổi email nhận link",
	"role.grant":                     "Cấp vai trò",
	"role.revoke":                    "Thu hồi vai trò",
	"place.update":                   "Sửa địa điểm",
	"food.create":                    "Thêm món ăn",
	"food.update":                    "Sửa món ăn",
	"story.create":                   "Thêm câu chuyện",
	"story.update":                   "Sửa câu chuyện",
	"category.update":                "Sửa loại địa điểm",
}

// scaleBar is a bar's height: values 1-4 all get the height of 5 (capped at the top), so the bar does not show them.
func scaleBar(v, top, height int) int {
	if v <= 0 || top <= 0 {
		return 0
	}
	return min(max(v, MinShown), top) * height / top
}

func cmpOr(a, b string) string {
	if a != "" {
		return a
	}
	return b
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
			for _, c := range strings.TrimSpace(s.Name + s.Login) {
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
		"detail":      auditDetail,
		"roleName":    func(code string) string { return cmpOr(roleNames[code], code) },
		"entityName":  func(t string) string { return cmpOr(entityNames[t], t) },
		"short": func(s string) string {
			if len(s) > 8 {
				return s[:8]
			}
			return s
		},
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
			return friendlyTime(*t, time.Now())
		},
		"count":  countText, // usage numbers: 1-4 show as "<5" (ADR-038 §5)
		"scale":  scaleBar,
		"sub":    func(a, b int) int { return a - b },
		"add":    func(a, b int) int { return a + b },
		"vnd":    vnd,
		"mul":    func(a, b int) int { return a * b },
		"at":     func(t time.Time) string { return friendlyTime(t, time.Now()) },
		"atFull": func(t time.Time) string { return t.In(hcm).Format("15:04:05 · 02/01/2006") },
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
	for _, name := range []string{"login", "home", "places", "place", "categories", "reports", "guide_list", "guide_item", "audit", "message", "password", "forgot", "reset"} {
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
		r.Group(func(r chi.Router) { // no session yet, so no CSRF token: refuse cross-site posts instead
			r.Use(sameOrigin)
			r.Get("/login", h.loginPage)
			r.Post("/login", h.login)
			r.Get("/forgot", h.forgotPage)
			r.Post("/forgot", h.forgot)
			r.Get("/reset", h.resetPage)
			r.Post("/reset", h.reset)
		})
		r.Group(func(r chi.Router) {
			r.Use(h.requireSession, h.requireCSRF)
			r.Get("/", h.home)
			r.Get("/reports", h.reports)
			r.Post("/logout", h.logout)
			r.Get("/password", h.passwordPage)
			r.Post("/password", h.changePassword)
			r.Get("/account", h.passwordPage)
			r.Post("/account/name", h.setName)
			r.Post("/account/email", h.setEmail)
			r.Group(func(r chi.Router) {
				r.Use(requireRole(RoleSystemAdmin, RoleContentEditor))
				r.Get("/places", h.places)
				r.Get("/places/{id}", h.placeForm)
				r.Post("/places/{id}", h.savePlace)
				r.Get("/categories", h.categories)
				r.Post("/categories/{id}", h.saveCategory)
				for kind := range GuideKinds {
					r.Get("/"+kind, h.guideList(kind))
					r.Get("/"+kind+"/new", h.guideForm(kind))
					r.Post("/"+kind+"/new", h.saveGuide(kind))
					r.Get("/"+kind+"/{id}", h.guideForm(kind))
					r.Post("/"+kind+"/{id}", h.saveGuide(kind))
				}
			})
			r.Group(func(r chi.Router) {
				r.Use(requireRole(RoleSystemAdmin))
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
		// A temporary password opens only the change-password page (and sign-out).
		if s.MustChange && r.URL.Path != "/admin/password" && r.URL.Path != "/admin/logout" {
			http.Redirect(w, r, "/admin/password", http.StatusSeeOther)
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

// sameOrigin refuses a POST whose Origin header names another site (login CSRF); browsers send Origin on POST.
func sameOrigin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if o := r.Header.Get("Origin"); r.Method == http.MethodPost && o != "" {
			if u, err := url.Parse(o); err != nil || u.Host != r.Host {
				http.Error(w, "cross-site request refused", http.StatusForbidden)
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func (h Handler) loginPage(w http.ResponseWriter, r *http.Request) {
	notice := map[string]string{"reset": "Đã đặt mật khẩu mới. Đăng nhập bằng mật khẩu mới."}[r.URL.Query().Get("n")]
	h.render(w, r, http.StatusOK, "login", map[string]any{"Notice": notice, "MailReady": h.Mailer != nil})
}

func (h Handler) login(w http.ResponseWriter, r *http.Request) {
	login := r.PostFormValue("login")
	userID, err := h.Store.PasswordLogin(r.Context(), login, r.PostFormValue("password"), httpx.RequestID(r))
	if errors.Is(err, ErrNotAllowed) {
		h.Log.Info("admin sign-in refused", "requestId", httpx.RequestID(r)) // no login, no password in logs
		h.render(w, r, http.StatusUnauthorized, "login", map[string]any{"Login": login, "MailReady": h.Mailer != nil,
			"Error": "Sai mã tài khoản hoặc mật khẩu. Sai 5 lần liên tiếp thì tài khoản bị khoá 15 phút."})
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
	http.Redirect(w, r, "/admin/", http.StatusSeeOther) // requireSession sends a temporary password on to /admin/password
}

func (h Handler) passwordPage(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	h.render(w, r, http.StatusOK, "password", map[string]any{"Saved": q.Get("saved") == "1", "Named": q.Get("named") == "1", "EmailSaved": q.Get("email") == "1"})
}

func (h Handler) changePassword(w http.ResponseWriter, r *http.Request) {
	if r.PostFormValue("new") != r.PostFormValue("confirm") {
		h.render(w, r, http.StatusUnprocessableEntity, "password", map[string]any{"Problem": "hai lần nhập mật khẩu mới không giống nhau"})
		return
	}
	c, _ := r.Cookie(sessionCookie) // present: requireSession passed
	err := h.Store.ChangePassword(r.Context(), current(r).UserID, r.PostFormValue("current"), r.PostFormValue("new"), c.Value, httpx.RequestID(r))
	if errors.Is(err, ErrBadInput) {
		h.render(w, r, http.StatusUnprocessableEntity, "password", map[string]any{"Problem": strings.TrimPrefix(err.Error(), ErrBadInput.Error()+": ")})
		return
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	if current(r).MustChange { // the forced first change is done: straight into the Admin
		http.Redirect(w, r, "/admin/?welcome=1", http.StatusSeeOther)
		return
	}
	http.Redirect(w, r, "/admin/account?saved=1", http.StatusSeeOther)
}

func (h Handler) setName(w http.ResponseWriter, r *http.Request) {
	err := h.Store.SetDisplayName(r.Context(), current(r).UserID, r.PostFormValue("name"), httpx.RequestID(r))
	if errors.Is(err, ErrBadInput) {
		h.render(w, r, http.StatusUnprocessableEntity, "password", map[string]any{"Problem": strings.TrimPrefix(err.Error(), ErrBadInput.Error()+": ")})
		return
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	http.Redirect(w, r, "/admin/account?named=1", http.StatusSeeOther)
}

func (h Handler) setEmail(w http.ResponseWriter, r *http.Request) {
	err := h.Store.SetEmail(r.Context(), current(r).UserID, r.PostFormValue("email"), r.PostFormValue("password"), httpx.RequestID(r))
	if errors.Is(err, ErrBadInput) {
		h.render(w, r, http.StatusUnprocessableEntity, "password", map[string]any{"EmailProblem": strings.TrimPrefix(err.Error(), ErrBadInput.Error()+": "), "TypedEmail": r.PostFormValue("email")})
		return
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	http.Redirect(w, r, "/admin/account?email=1", http.StatusSeeOther)
}

func (h Handler) forgotPage(w http.ResponseWriter, r *http.Request) {
	h.render(w, r, http.StatusOK, "forgot", map[string]any{"MailReady": h.Mailer != nil})
}

// forgot answers the same whether the login exists or not; the email goes out in the background so the response time
// does not tell either.
func (h Handler) forgot(w http.ResponseWriter, r *http.Request) {
	if h.Mailer == nil {
		h.render(w, r, http.StatusServiceUnavailable, "forgot", map[string]any{"MailReady": false})
		return
	}
	email, token, err := h.Store.StartPasswordReset(r.Context(), r.PostFormValue("login"), httpx.RequestID(r))
	switch {
	case err == nil:
		link := h.BaseURL + "/admin/reset?t=" + url.QueryEscape(token)
		body := "Có người (có thể là bạn) yêu cầu đặt lại mật khẩu trang quản trị GoWander.\n\n" +
			"Mở link sau trong 5 phút để đặt mật khẩu mới (chỉ dùng được một lần):\n" + link + "\n\n" +
			"Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu hiện tại vẫn giữ nguyên.\n"
		requestID := httpx.RequestID(r)
		go func() {
			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			defer cancel()
			if err := h.Mailer.Send(ctx, email, "Đặt lại mật khẩu GoWander Admin", body); err != nil {
				h.Log.Warn("password reset email failed", "requestId", requestID, "err", err) // no address, no link
			}
		}()
	case !errors.Is(err, ErrNotFound):
		h.fail(w, r, err)
		return
	}
	h.render(w, r, http.StatusOK, "forgot", map[string]any{"MailReady": true, "Sent": true})
}

func (h Handler) resetPage(w http.ResponseWriter, r *http.Request) {
	h.render(w, r, http.StatusOK, "reset", map[string]any{"Token": r.URL.Query().Get("t")})
}

func (h Handler) reset(w http.ResponseWriter, r *http.Request) {
	token := r.PostFormValue("t")
	if r.PostFormValue("new") != r.PostFormValue("confirm") {
		h.render(w, r, http.StatusUnprocessableEntity, "reset", map[string]any{"Token": token, "Problem": "hai lần nhập mật khẩu mới không giống nhau"})
		return
	}
	err := h.Store.ResetPassword(r.Context(), token, r.PostFormValue("new"), httpx.RequestID(r))
	if errors.Is(err, ErrBadInput) {
		h.render(w, r, http.StatusUnprocessableEntity, "reset", map[string]any{"Token": token, "Problem": strings.TrimPrefix(err.Error(), ErrBadInput.Error()+": ")})
		return
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	http.Redirect(w, r, "/admin/login?n=reset", http.StatusSeeOther)
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

// usageQuery reads the period (and, on the reports page, the departure province) from the query string.
func usageQuery(r *http.Request, withArea bool) UsageQuery {
	q := UsageQuery{}
	q.Grain = grainOf(r.URL.Query().Get("grain"))
	if withArea {
		q.Area = r.URL.Query().Get("area")
	}
	return q
}

// health runs each dependency check with a 1 s budget.
func (h Handler) health(r *http.Request) (states []map[string]any, down []string) {
	for _, c := range h.Health {
		ctx, cancel := context.WithTimeout(r.Context(), time.Second)
		err := c.Ready(ctx)
		cancel()
		if err != nil {
			h.Log.Warn("admin health check failed", "dependency", c.Name, "requestId", httpx.RequestID(r), "err", err)
			down = append(down, c.Name)
		}
		states = append(states, map[string]any{"Name": c.Name, "OK": err == nil})
	}
	return states, down
}

// home is the overview: customers and revenue at a glance (owner, 16-11-2026); details live on /admin/reports.
func (h Handler) home(w http.ResponseWriter, r *http.Request) {
	q := usageQuery(r, false)
	u, err := h.Store.Usage(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	_, down := h.health(r)
	shapes, err := h.Store.AreaShapes(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	if u.Total < MinShown { // not enough real use yet: sample numbers, badged (mock.go)
		u = sampleUsage(q, u.Areas, shapes)
	}
	rev := sampleRevenue(q.Grain) // no revenue source yet (mock.go)
	var top []bar
	if len(u.Places) > 5 {
		top = u.Places[:5]
	} else {
		top = u.Places
	}
	h.render(w, r, http.StatusOK, "home", map[string]any{
		"U": u, "Grains": Grains, "GrainBase": "/admin/?", "MinShown": MinShown, "Down": down, "Greeting": greeting(time.Now()), "Welcome": r.URL.Query().Get("welcome") == "1",
		"OpensChange": delta(u.Opens, u.PrevOpens), "SearchChange": delta(u.Total, u.PrevTotal),
		"Opens": newLineChart("opens", u.OpensSeries, u.PrevOpensSeries, "lượt mở", groupDigits, u.Grain.partial()), "Modes": donut(u.Modes, u.Total), "TopPlaces": top,
		"Map": newVNMap(shapes, u.AreaCounts, "", "/admin/reports?grain="+u.Grain.Key+"&area="),
		"Rev": rev, "RevChange": delta(int(rev.Total/1000), int(rev.Prev/1000)), "ProfitChange": delta(int(rev.Profit/1000), int(rev.PrevProfit/1000)),
		"RevChart": revenueChart(rev), "Starts": SampleStarts(u.Total),
	})
}

// reports is "Báo cáo thống kê": how the app is used (filters, rankings) and the state of the content.
func (h Handler) reports(w http.ResponseWriter, r *http.Request) {
	tab := r.URL.Query().Get("tab")
	data := map[string]any{"Tab": tab, "Grains": Grains, "MinShown": MinShown}
	if tab == "noi-dung" {
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
		states, down := h.health(r)
		data["O"], data["Recent"], data["Health"], data["Official"] = o, recent, states, h.DataStatus == "OFFICIAL"
		data["Attention"] = attention(o, down, h.DataStatus)
	} else {
		data["Tab"] = "khach"
		u, err := h.Store.Usage(r.Context(), usageQuery(r, true))
		if err != nil {
			h.fail(w, r, err)
			return
		}
		shapes, err := h.Store.AreaShapes(r.Context())
		if err != nil {
			h.fail(w, r, err)
			return
		}
		if u.Total < MinShown { // not enough real use yet: sample numbers, badged (mock.go)
			u = sampleUsage(u.UsageQuery, u.Areas, shapes)
		}
		data["U"], data["Searches"] = u, newLineChart("searches", u.Series, u.PrevSeries, "lượt tìm", countText, u.Grain.partial())
		data["GrainBase"] = "/admin/reports?" + map[bool]string{true: "area=" + url.QueryEscape(u.Area) + "&", false: ""}[u.Area != ""]
		data["Map"] = newVNMap(shapes, u.AreaCounts, u.Area, "/admin/reports?grain="+u.Grain.Key+"&area=")
		data["SearchChange"] = delta(u.Total, u.PrevTotal)
	}
	h.render(w, r, http.StatusOK, "reports", data)
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
		"From": (q.Page-1)*PageSize + 1, "To": (q.Page-1)*PageSize + len(list), "Pager": newPager(q.Page, total, PageSize, pageURL)}
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

func (h Handler) guideList(kind string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		list, err := h.Store.GuideItems(r.Context(), kind)
		if err != nil {
			h.fail(w, r, err)
			return
		}
		h.render(w, r, http.StatusOK, "guide_list", map[string]any{"K": GuideKinds[kind], "List": list})
	}
}

func (h Handler) notFound(w http.ResponseWriter, r *http.Request, k guideKind) {
	h.render(w, r, http.StatusNotFound, "message", map[string]any{"Title": "Không có " + k.Noun + " này", "Text": "Mục không tồn tại hoặc đường dẫn sai."})
}

func (h Handler) guideForm(kind string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		k := GuideKinds[kind]
		g := GuideItem{Status: "DRAFT"}
		if id := chi.URLParam(r, "id"); id != "" {
			var err error
			if g, err = h.Store.GuideItem(r.Context(), kind, id); errors.Is(err, ErrNotFound) {
				h.notFound(w, r, k)
				return
			} else if err != nil {
				h.fail(w, r, err)
				return
			}
		}
		h.renderGuide(w, r, http.StatusOK, k, g, "", r.URL.Query().Get("saved") == "1")
	}
}

func (h Handler) renderGuide(w http.ResponseWriter, r *http.Request, status int, k guideKind, g GuideItem, problem string, saved bool) {
	h.render(w, r, status, "guide_item", map[string]any{"K": k, "G": g, "Statuses": PlaceStatuses, "Problem": problem, "Saved": saved})
}

func (h Handler) saveGuide(kind string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		k := GuideKinds[kind]
		order, _ := strconv.Atoi(strings.TrimSpace(r.PostFormValue("sort_order")))
		g := GuideItem{ID: chi.URLParam(r, "id"), Title: r.PostFormValue("title"), NameEN: r.PostFormValue("name_en"), Summary: r.PostFormValue("summary"),
			Body: r.PostFormValue("body"), Extra: r.PostFormValue("extra"), Sources: r.PostFormValue("sources"), SourceURL: r.PostFormValue("source_url"),
			Status: r.PostFormValue("status"), SortOrder: order}
		id, err := h.Store.SaveGuideItem(r.Context(), current(r).UserID, kind, g, httpx.RequestID(r))
		switch {
		case errors.Is(err, ErrBadInput):
			if g.ID != "" { // keep what the editor typed, with the stored slug and dates
				if stored, lookupErr := h.Store.GuideItem(r.Context(), kind, g.ID); lookupErr == nil {
					g.Slug, g.UpdatedAt, g.EditedAt = stored.Slug, stored.UpdatedAt, stored.EditedAt
				}
			}
			h.renderGuide(w, r, http.StatusUnprocessableEntity, k, g, strings.TrimPrefix(err.Error(), ErrBadInput.Error()+": "), false)
		case errors.Is(err, ErrNotFound):
			h.notFound(w, r, k)
		case err != nil:
			h.fail(w, r, err)
		default:
			http.Redirect(w, r, "/admin/"+kind+"/"+url.PathEscape(id)+"?saved=1", http.StatusSeeOther)
		}
	}
}

const auditPageSize = 50

// auditRange reads ?from=&to= (YYYY-MM-DD, Ho Chi Minh City days). The log only holds the past: a day after today is
// pulled back to today, from after to is swapped, and a window is at most 90 days. The notice says what was changed.
func auditRange(v url.Values, now time.Time) (from, to time.Time, notice string) {
	today := time.Date(now.In(hcm).Year(), now.In(hcm).Month(), now.In(hcm).Day(), 0, 0, 0, 0, hcm)
	from, to = today.AddDate(0, 0, -6), today
	parse := func(key string, def time.Time) time.Time {
		if d, err := time.ParseInLocation(time.DateOnly, v.Get(key), hcm); err == nil {
			return d
		}
		return def
	}
	from, to = parse("from", from), parse("to", to)
	var notes []string
	if to.After(today) {
		to, notes = today, append(notes, "nhật ký chỉ có quá khứ: ngày kết thúc lùi về hôm nay")
	}
	if from.After(today) {
		from = today
	}
	if from.After(to) {
		from, to, notes = to, from, append(notes, "ngày bắt đầu sau ngày kết thúc nên đã đổi chỗ")
	}
	if to.Sub(from) > 89*24*time.Hour {
		from, notes = to.AddDate(0, 0, -89), append(notes, "xem tối đa 90 ngày một lần")
	}
	return from, to, strings.Join(notes, "; ")
}

var entityNames = map[string]string{"user": "Tài khoản", "place": "Địa điểm", "food": "Món ăn", "story": "Câu chuyện", "place_category": "Loại địa điểm"}

var fieldNames = map[string]string{"name": "tên", "name_en": "tên tiếng Anh", "short_description": "mô tả", "address": "địa chỉ",
	"status": "trạng thái", "primary_category_id": "loại"}

// auditDetail turns an audit line's metadata into short Vietnamese phrases.
func auditDetail(meta map[string]any) []string {
	var out []string
	keys := make([]string, 0, len(meta))
	for k := range meta {
		keys = append(keys, k)
	}
	slices.Sort(keys)
	for _, k := range keys {
		v := meta[k]
		switch k {
		case "fields":
			var names []string
			if list, ok := v.([]any); ok {
				for _, f := range list {
					name := fmt.Sprint(f)
					if n, ok := fieldNames[name]; ok {
						name = n
					}
					names = append(names, name)
				}
			}
			out = append(out, "Đã sửa: "+strings.Join(names, ", "))
		case "status":
			text := fmt.Sprint(v)
			for code, name := range statusNames {
				text = strings.ReplaceAll(text, code, name)
			}
			out = append(out, "Trạng thái: "+text)
		case "role":
			out = append(out, "Vai trò: "+roleNames[fmt.Sprint(v)])
		case "login":
			out = append(out, "Mã tài khoản: "+fmt.Sprint(v))
		case "title", "name":
			out = append(out, "Tên: "+fmt.Sprint(v))
		case "active":
			out = append(out, map[bool]string{true: "Đang bật", false: "Đã tắt"}[v == true])
		default:
			out = append(out, k+": "+fmt.Sprint(v))
		}
	}
	return out
}

var roleNames = map[string]string{RoleContentEditor: "Biên tập nội dung", RoleModerator: "Kiểm duyệt", RoleOperationsAdmin: "Vận hành",
	RoleBusinessAdmin: "Kinh doanh", RoleSystemAdmin: "Quản trị hệ thống"}

func (h Handler) audit(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	from, to, notice := auditRange(v, time.Now())
	q := AuditQuery{From: from, To: to.AddDate(0, 0, 1), Limit: auditPageSize}
	if _, ok := actionNames[v.Get("action")]; ok {
		q.Action = v.Get("action")
	}
	page, _ := strconv.Atoi(v.Get("page"))
	page = max(page, 1)
	q.Offset = (page - 1) * auditPageSize
	entries, total, err := h.Store.AuditPage(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	activity, err := h.Store.AuditActivity(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	link := func(p int) string {
		u := url.Values{"from": {from.Format(time.DateOnly)}, "to": {to.Format(time.DateOnly)}, "page": {strconv.Itoa(p)}}
		if q.Action != "" {
			u.Set("action", q.Action)
		}
		return "/admin/audit?" + u.Encode()
	}
	actions := make([]string, 0, len(actionNames))
	for a := range actionNames {
		actions = append(actions, a)
	}
	slices.SortFunc(actions, func(a, b string) int { return strings.Compare(actionNames[a], actionNames[b]) })
	today := time.Now().In(hcm).Format(time.DateOnly)
	h.render(w, r, http.StatusOK, "audit", map[string]any{
		"Entries": entries, "Total": total, "From": from.Format(time.DateOnly), "To": to.Format(time.DateOnly), "Today": today,
		"Action": q.Action, "Actions": actions, "Notice": notice, "Pager": newPager(page, total, auditPageSize, link),
		"Chart": newLineChart("audit", activity, nil, "thao tác", groupDigits),
		"Quick": []map[string]string{
			{"Label": "Hôm nay", "URL": "/admin/audit?from=" + today + "&to=" + today},
			{"Label": "7 ngày", "URL": "/admin/audit?from=" + time.Now().In(hcm).AddDate(0, 0, -6).Format(time.DateOnly) + "&to=" + today},
			{"Label": "30 ngày", "URL": "/admin/audit?from=" + time.Now().In(hcm).AddDate(0, 0, -29).Format(time.DateOnly) + "&to=" + today},
		},
	})
}
