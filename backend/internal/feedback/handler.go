package feedback

import (
	"bytes"
	"context"
	"errors"
	"image"
	"io"
	"log/slog"
	"net"
	"net/http"
	"strings"
	"sync"
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

// Limiter uses only the TCP peer address (never untrusted forwarded headers), retains no IP in the database,
// bounds memory and fails closed when its capacity is reached. Per process; a gateway must limit clustered deployments.
type Limiter struct {
	mu     sync.Mutex
	counts map[string]int
	start  time.Time
	n      int
	window time.Duration
}

func NewLimiter(n int, window time.Duration) *Limiter {
	return &Limiter{counts: map[string]int{}, start: time.Now(), n: n, window: window}
}
func (l *Limiter) Allow(r *http.Request) bool {
	ip, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		ip = r.RemoteAddr
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if time.Since(l.start) >= l.window {
		l.start = time.Now()
		l.counts = map[string]int{}
	}
	if _, ok := l.counts[ip]; !ok && len(l.counts) >= 10000 {
		return false
	}
	if l.counts[ip] >= l.n {
		return false
	}
	l.counts[ip]++
	return true
}
