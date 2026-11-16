package admin

import (
	"errors"
	"net/http"
	"net/url"
	"slices"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

// appUsers is the AUTH-3 page: the people who use the app (ADR-040 §8). Read-only for moderators; suspending an
// account is for system administrators and is written to the audit log.
func (h Handler) appUsers(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	q := AppUserQuery{Q: v.Get("q"), Status: v.Get("status")}
	q.Page, _ = strconv.Atoi(v.Get("page"))
	q.Page = max(q.Page, 1)
	if !slices.Contains(AppUserStatuses, q.Status) {
		q.Status = ""
	}
	list, total, err := h.Store.AppUsers(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	pageURL := func(page int) string {
		u := url.Values{}
		if q.Q != "" {
			u.Set("q", q.Q)
		}
		if q.Status != "" {
			u.Set("status", q.Status)
		}
		u.Set("page", strconv.Itoa(page))
		return "/admin/app-users?" + u.Encode()
	}
	data := map[string]any{
		"List": list, "Total": total, "Q": q, "Statuses": AppUserStatuses,
		"From": (q.Page-1)*PageSize + 1, "To": (q.Page-1)*PageSize + len(list),
		"Pager": newPager(q.Page, total, PageSize, pageURL), "Saved": r.URL.Query().Get("saved") == "1",
	}
	h.render(w, r, http.StatusOK, "app_users", data)
}

// appUserStatus suspends or restores one account. The person is signed out everywhere when suspended.
func (h Handler) appUserStatus(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	if !appUserIDValid(id) {
		h.appUserNotFound(w, r)
		return
	}
	status := r.PostFormValue("status")
	if err := h.Store.SetAppUserStatus(r.Context(), id, status, current(r).UserID, httpx.RequestID(r)); err != nil {
		if errors.Is(err, ErrNotFound) {
			h.appUserNotFound(w, r)
			return
		}
		h.fail(w, r, err)
		return
	}
	http.Redirect(w, r, "/admin/app-users?saved=1", http.StatusSeeOther)
}

func (h Handler) appUserNotFound(w http.ResponseWriter, r *http.Request) {
	h.render(w, r, http.StatusNotFound, "message", map[string]any{
		"Title": "Không tìm thấy tài khoản",
		"Text":  "Tài khoản này không còn trong danh sách. Quay lại trang Người dùng app.",
	})
}
