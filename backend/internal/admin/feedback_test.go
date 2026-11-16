package admin

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/feedback"
	"github.com/go-chi/chi/v5"
)

type feedbackFake struct {
	feedback.Store
	report    feedback.Report
	query     feedback.Query
	updates   int
	updateErr error
}

func (f *feedbackFake) List(_ context.Context, q feedback.Query) ([]feedback.Report, int, error) {
	f.query = q
	return []feedback.Report{f.report}, 1, nil
}
func (f *feedbackFake) Get(context.Context, string) (feedback.Report, error) { return f.report, nil }
func (f *feedbackFake) Image(context.Context, string) ([]byte, error) {
	return []byte{0xff, 0xd8, 0xff}, nil
}
func (f *feedbackFake) Update(_ context.Context, id, status, note, expected, actor, request string) error {
	f.updates++
	return f.updateErr
}
func feedbackServer() (http.Handler, *fakeStore, *feedbackFake) {
	a := newFake()
	f := &feedbackFake{report: feedback.Report{ID: "01234567-1234-1234-1234-123456789abc", Kind: "BUG", Message: "Lỗi hiển thị <script>alert(1)</script>", Context: "Khám phá", SenderName: "Lan Anh", SenderEmail: "private@example.com", Status: "NEW", HasImage: true, CreatedAt: time.Now()}}
	r := chi.NewRouter()
	Handler{Store: a, Feedback: f, Log: slog.New(slog.NewTextHandler(io.Discard, nil))}.Mount(r)
	return r, a, f
}
func TestFeedbackAccessAndPrivacy(t *testing.T) {
	h, a, f := feedbackServer()
	for _, path := range []string{"/admin/feedbacks", "/admin/feedbacks/" + f.report.ID, "/admin/feedbacks/" + f.report.ID + "/image"} {
		if w := do(h, "GET", path, "", nil, nil); w.Code != 303 {
			t.Fatalf("anonymous %s: %d", path, w.Code)
		}
		if w := do(h, "GET", path, "editor", nil, nil); w.Code != 403 {
			t.Fatalf("editor %s: %d", path, w.Code)
		}
		if w := do(h, "GET", path, "mod", nil, nil); w.Code != 200 {
			t.Fatalf("moderator %s: %d %s", path, w.Code, w.Body.String())
		}
	}
	w := do(h, "GET", "/admin/feedbacks/"+f.report.ID, "sys", nil, nil)
	if strings.Contains(w.Body.String(), f.report.SenderEmail) {
		t.Fatal("contact revealed without explicit request")
	}
	w = do(h, "POST", "/admin/feedbacks/"+f.report.ID+"/contact", "sys", url.Values{"csrf": {"csrf-s"}}, nil)
	if w.Code != 200 || !strings.Contains(w.Body.String(), f.report.SenderEmail) || len(a.audits) != 1 {
		t.Fatalf("contact %d audits %d", w.Code, len(a.audits))
	}
	if w := do(h, "POST", "/admin/feedbacks/"+f.report.ID+"/contact", "mod", url.Values{"csrf": {"csrf-m"}}, nil); w.Code != 403 {
		t.Fatal("moderator sees private contact")
	}
	w = do(h, "GET", "/admin/feedbacks", "sys", nil, nil)
	if strings.Contains(w.Body.String(), f.report.SenderEmail) || strings.Contains(w.Body.String(), "<script>alert") {
		t.Fatal("PII or unescaped message in list")
	}
}
func TestFeedbackFiltersAndProgress(t *testing.T) {
	h, _, f := feedbackServer()
	w := do(h, "GET", "/admin/feedbacks?view=progress&status=NEW&kind=BUG&from=2026-11-10&to=2026-11-16&q=Lan", "mod", nil, nil)
	if w.Code != 200 || !strings.Contains(w.Body.String(), "Bảng kéo thả") || !strings.Contains(w.Body.String(), `data-feedback-board`) {
		t.Fatalf("%d %s", w.Code, w.Body.String())
	}
	if f.query.From != "2026-11-10" || f.query.To != "2026-11-16" || f.query.Search != "Lan" || f.query.Status != "NEW" {
		t.Fatalf("query %+v", f.query)
	}
	for _, query := range []string{"from=bad", "from=2026-11-16&to=2026-11-10", "status=INVALID", "kind=INVALID"} {
		if w := do(h, "GET", "/admin/feedbacks?"+query, "mod", nil, nil); w.Code != 400 {
			t.Fatalf("invalid %s: %d", query, w.Code)
		}
	}
}
func TestFeedbackUpdatesRequireCSRFAndSurfaceConflicts(t *testing.T) {
	h, _, f := feedbackServer()
	path := "/admin/feedbacks/" + f.report.ID
	form := url.Values{"status": {"IN_REVIEW"}, "expectedStatus": {"NEW"}, "note": {"Đang kiểm tra"}}
	if w := do(h, "POST", path, "mod", form, nil); w.Code != 403 || f.updates != 0 {
		t.Fatal("missing CSRF accepted")
	}
	form.Set("csrf", "csrf-m")
	if w := do(h, "POST", path, "mod", form, nil); w.Code != 303 || f.updates != 1 {
		t.Fatalf("update %d", w.Code)
	}
	f.updateErr = feedback.ErrConflict
	if w := do(h, "POST", path, "mod", form, nil); w.Code != 409 {
		t.Fatalf("conflict %d", w.Code)
	}
}
