package admin

import (
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/feedback"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
	"github.com/go-chi/chi/v5"
)

var feedbackStatuses = []string{"NEW", "IN_REVIEW", "RESOLVED", "REJECTED"}
var feedbackKinds = []string{"INFORMATION", "BUG", "SUGGESTION", "OTHER"}
var feedbackStatusNames = map[string]string{"NEW": "Mới", "IN_REVIEW": "Đang xử lý", "RESOLVED": "Đã xử lý", "REJECTED": "Không tiếp nhận"}
var feedbackKindNames = map[string]string{"INFORMATION": "Thông tin chưa đúng", "BUG": "Lỗi ứng dụng", "SUGGESTION": "Góp ý tính năng", "OTHER": "Khác"}

type feedbackColumn struct {
	Status  string
	Reports []feedback.Report
}

func (h Handler) feedbackReady(w http.ResponseWriter, r *http.Request) bool {
	if h.Feedback != nil {
		return true
	}
	h.render(w, r, http.StatusServiceUnavailable, "message", map[string]any{"Title": "Feedbacks chưa sẵn sàng", "Text": "Chưa cấu hình kho feedback. Vui lòng thử lại sau."})
	return false
}

func feedbackURL(q feedback.Query, view string, page int) string {
	v := url.Values{"view": {view}, "page": {strconv.Itoa(page)}}
	for k, s := range map[string]string{"status": q.Status, "kind": q.Kind, "q": q.Search, "from": q.From, "to": q.To} {
		if s != "" {
			v.Set(k, s)
		}
	}
	return "/admin/feedbacks?" + v.Encode()
}

func (h Handler) feedbacks(w http.ResponseWriter, r *http.Request) {
	if !h.feedbackReady(w, r) {
		return
	}
	v := r.URL.Query()
	page := 1
	if raw := v.Get("page"); raw != "" {
		var err error
		page, err = strconv.Atoi(raw)
		if err != nil || page < 1 || page > 100000 {
			h.feedbackError(w, r, feedback.ErrBadInput)
			return
		}
	}
	q := feedback.Query{Status: v.Get("status"), Kind: v.Get("kind"), Search: strings.TrimSpace(v.Get("q")), From: v.Get("from"), To: v.Get("to"), Page: page}
	if err := feedback.ValidateQuery(q); err != nil {
		h.feedbackError(w, r, err)
		return
	}
	view := v.Get("view")
	if view != "progress" {
		view = "list"
	}
	rows, total, err := h.Feedback.List(r.Context(), q)
	if err != nil {
		h.feedbackError(w, r, err)
		return
	}
	// Defense in depth: list templates never receive private contact details.
	for i := range rows {
		rows[i].SenderEmail = ""
	}
	columns := make([]feedbackColumn, 0, 4)
	for _, s := range feedbackStatuses {
		col := feedbackColumn{Status: s}
		for _, row := range rows {
			if row.Status == s {
				col.Reports = append(col.Reports, row)
			}
		}
		columns = append(columns, col)
	}
	h.render(w, r, http.StatusOK, "feedbacks", map[string]any{"Q": q, "View": view, "List": rows, "Total": total, "Columns": columns, "Statuses": feedbackStatuses, "Kinds": feedbackKinds,
		"ListURL": feedbackURL(q, "list", 1), "ProgressURL": feedbackURL(q, "progress", 1), "Pager": newPager(page, total, feedback.PageSize, func(p int) string { return feedbackURL(q, view, p) }), "PageNumber": page})
}

func (h Handler) feedbackDetail(w http.ResponseWriter, r *http.Request) {
	if !h.feedbackReady(w, r) {
		return
	}
	report, err := h.Feedback.Get(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		h.feedbackError(w, r, err)
		return
	}
	report.SenderEmail = ""
	h.renderFeedback(w, r, http.StatusOK, report, false, "")
}

func (h Handler) renderFeedback(w http.ResponseWriter, r *http.Request, status int, report feedback.Report, contact bool, notice string) {
	h.render(w, r, status, "feedback", map[string]any{"Report": report, "Statuses": feedbackStatuses, "ContactShown": contact, "Notice": notice, "Saved": r.URL.Query().Get("saved") == "1"})
}

func (h Handler) feedbackContact(w http.ResponseWriter, r *http.Request) {
	if !h.feedbackReady(w, r) {
		return
	}
	report, err := h.Feedback.Get(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		h.feedbackError(w, r, err)
		return
	}
	if err = h.Store.Audit(r.Context(), AuditEntry{ActorID: current(r).UserID, Action: "feedback.contact_view", EntityType: "feedback", EntityID: report.ID, RequestID: httpx.RequestID(r)}); err != nil {
		h.feedbackError(w, r, err)
		return
	}
	h.renderFeedback(w, r, http.StatusOK, report, true, "")
}

func (h Handler) feedbackImage(w http.ResponseWriter, r *http.Request) {
	if !h.feedbackReady(w, r) {
		return
	}
	b, err := h.Feedback.Image(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		h.feedbackError(w, r, err)
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Content-Disposition", `inline; filename="feedback.jpg"`)
	w.Header().Set("Cache-Control", "private, no-store")
	_, _ = w.Write(b)
}

func (h Handler) feedbackUpdate(w http.ResponseWriter, r *http.Request) {
	if !h.feedbackReady(w, r) {
		return
	}
	if err := r.ParseForm(); err != nil {
		h.feedbackError(w, r, feedback.ErrBadInput)
		return
	}
	status, expected, note := r.PostForm.Get("status"), r.PostForm.Get("expectedStatus"), r.PostForm.Get("note")
	if !feedback.ValidStatus(status) || !feedback.ValidStatus(expected) || utf8.RuneCountInString(note) > 2000 || strings.ContainsRune(note, 0) {
		h.feedbackError(w, r, feedback.ErrBadInput)
		return
	}
	if r.PostForm.Get("move") == "1" {
		note = feedback.PreserveNote
	}
	id := chi.URLParam(r, "id")
	if err := h.Feedback.Update(r.Context(), id, status, note, expected, current(r).UserID, httpx.RequestID(r)); err != nil {
		h.feedbackError(w, r, err)
		return
	}
	if r.Header.Get("X-Feedback-Request") == "move" {
		httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": status})
		return
	}
	http.Redirect(w, r, "/admin/feedbacks/"+url.PathEscape(id)+"?saved=1", http.StatusSeeOther)
}

func (h Handler) feedbackError(w http.ResponseWriter, r *http.Request, err error) {
	status, message := http.StatusServiceUnavailable, "Chưa thể đọc hoặc lưu feedback. Vui lòng thử lại."
	switch {
	case errors.Is(err, feedback.ErrBadInput):
		status, message = http.StatusBadRequest, "Kiểm tra bộ lọc, khoảng ngày, trạng thái và độ dài nội dung."
	case errors.Is(err, feedback.ErrNotFound):
		status, message = http.StatusNotFound, "Không tìm thấy feedback hoặc ảnh đính kèm."
	case errors.Is(err, feedback.ErrConflict):
		status, message = http.StatusConflict, "Feedback đã được thay đổi ở nơi khác. Tải lại trang để xem trạng thái mới rồi thử lại."
	default:
		h.Log.Warn("feedback admin failed", "requestId", httpx.RequestID(r), "err", err)
	}
	if r.Header.Get("X-Feedback-Request") == "move" {
		httpx.WriteJSON(w, status, map[string]string{"error": message})
		return
	}
	h.render(w, r, status, "message", map[string]any{"Title": "Chưa hoàn tất thao tác", "Text": message})
}
