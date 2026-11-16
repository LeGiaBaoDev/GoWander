package feedback

import (
	"bytes"
	"context"
	"errors"
	"image"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/media"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
	"github.com/go-chi/chi/v5"
)

type Creator interface {
	Create(context.Context, Input) (string, error)
}
type Handler struct {
	Store Creator
	Limit *Limiter
	Log   *slog.Logger
	// Viewer returns the signed-in app account of the request, when there is one. The server then takes the sender
	// name and email from the account (ADR-040) and never from the form.
	Viewer func(*http.Request) (Identity, bool)
}

// Identity is who is signed in: enough to fill the sender fields, no password or token.
type Identity struct {
	ID, Name, Email string
	EmailVerified   bool
}

func (h Handler) Mount(r chi.Router) {
	if h.Limit == nil {
		h.Limit = NewLimiter(5, time.Hour)
	}
	r.Post("/api/v1/feedbacks", h.Create)
}
func (h Handler) Create(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if h.Limit != nil && !h.Limit.Allow(r) {
		w.Header().Set("Retry-After", "3600")
		httpx.WriteError(w, r, 429, "RATE_LIMITED", "Bạn đã gửi nhiều feedback. Vui lòng thử lại sau.")
		return
	}
	if h.Store == nil {
		httpx.WriteError(w, r, 503, "FEEDBACK_UNAVAILABLE", "Chưa thể gửi feedback. Vui lòng thử lại sau.")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, MaxImageBytes+(32<<10))
	mr, err := r.MultipartReader()
	if err != nil {
		httpx.WriteError(w, r, 400, "INVALID_REQUEST", "Feedback phải là biểu mẫu multipart.")
		return
	}
	fields := map[string]string{}
	var raw []byte
	seen := map[string]bool{}
	bad := func() {
		httpx.WriteError(w, r, 400, "INVALID_REQUEST", "Kiểm tra loại, nội dung, tên/email và ảnh JPEG hoặc PNG (tối đa 5 MB).")
	}
	for {
		part, e := mr.NextPart()
		if errors.Is(e, io.EOF) {
			break
		}
		if e != nil {
			bad()
			return
		}
		key := part.FormName()
		if seen[key] {
			_ = part.Close()
			bad()
			return
		}
		seen[key] = true
		limit := int64(4096)
		switch key {
		case "kind", "message", "context", "senderName", "senderEmail":
		case "image":
			limit = MaxImageBytes
		default:
			_ = part.Close()
			bad()
			return
		}
		b, e := io.ReadAll(io.LimitReader(part, limit+1))
		_ = part.Close()
		if e != nil || int64(len(b)) > limit {
			bad()
			return
		}
		if key == "image" {
			raw = b
		} else {
			fields[key] = strings.TrimSpace(string(b))
		}
	}
	in := Input{Kind: fields["kind"], Message: fields["message"], Context: fields["context"], SenderName: fields["senderName"], SenderEmail: fields["senderEmail"]}
	if h.Viewer != nil {
		// A verified account wins over the form: the app cannot set someone else's name or email (ADR-040).
		if who, ok := h.Viewer(r); ok && who.EmailVerified && who.Email != "" {
			in.SenderName, in.SenderEmail, in.AppUserID = who.Name, who.Email, who.ID
		}
	}
	if err := Validate(in); err != nil {
		bad()
		return
	}
	if len(raw) > 0 {
		cfg, format, e := image.DecodeConfig(bytes.NewReader(raw))
		if e != nil || (format != "jpeg" && format != "png") || cfg.Width*cfg.Height > 12_000_000 {
			bad()
			return
		}
		p, e := media.Process(bytes.NewReader(raw))
		if e != nil {
			bad()
			return
		}
		in.Image = p.Files[media.FileName(p.Key, 1600)]
		if len(in.Image) > MaxStoredImageBytes {
			bad()
			return
		}
	} else if seen["image"] {
		bad()
		return
	}
	id, err := h.Store.Create(r.Context(), in)
	if err != nil {
		if h.Log != nil {
			h.Log.Error("feedback save failed", "requestId", httpx.RequestID(r))
		}
		httpx.WriteError(w, r, 503, "FEEDBACK_UNAVAILABLE", "Chưa thể gửi feedback. Nội dung chưa được lưu; vui lòng thử lại.")
		return
	}
	httpx.WriteJSON(w, 201, map[string]string{"id": id})
}

// Limiter is the shared per-address limiter (platform/httpx), kept under this name for the feedback callers and tests.
type Limiter = httpx.Limiter

// NewLimiter allows n feedbacks per window and peer address.
func NewLimiter(n int, window time.Duration) *Limiter { return httpx.NewLimiter(n, window) }
