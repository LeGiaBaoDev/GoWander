// Package httpx holds the HTTP plumbing shared by all modules: router, middleware and JSON responses.
package httpx

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"log/slog"
	"net/http"
	"runtime/debug"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

// NewRouter returns a chi router with request IDs, structured access logs and panic recovery.
func NewRouter(log *slog.Logger) *chi.Mux {
	r := chi.NewRouter()
	r.Use(requestID, accessLog(log), recoverer(log))
	r.NotFound(func(w http.ResponseWriter, r *http.Request) {
		WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Không có đường dẫn này.")
	})
	r.MethodNotAllowed(func(w http.ResponseWriter, r *http.Request) {
		WriteError(w, r, http.StatusMethodNotAllowed, "NOT_FOUND", "Đường dẫn này không hỗ trợ phương thức "+r.Method+".")
	})
	return r
}

// RequestID returns the request ID set by the router middleware.
func RequestID(r *http.Request) string { return middleware.GetReqID(r.Context()) }

// requestID gives every request a fresh random ID (never the client's header, never the host name) and echoes it in X-Request-Id.
func requestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := rand.Text()
		w.Header().Set(middleware.RequestIDHeader, id)
		w.Header().Set("X-Content-Type-Options", "nosniff")
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), middleware.RequestIDKey, id)))
	})
}

func accessLog(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
			start := time.Now()
			next.ServeHTTP(ww, r)
			log.Info("http request",
				"requestId", RequestID(r), "method", r.Method, "path", r.URL.Path,
				"status", ww.Status(), "bytes", ww.BytesWritten(), "durationMs", time.Since(start).Milliseconds())
		})
	}
}

// recoverer turns a panic into a logged stack trace and the JSON error envelope (500 INTERNAL_ERROR).
func recoverer(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				rec := recover()
				if rec == nil {
					return
				}
				if rec == http.ErrAbortHandler {
					panic(rec)
				}
				log.Error("panic", "requestId", RequestID(r), "panic", rec, "stack", string(debug.Stack()))
				WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại sau.")
			}()
			next.ServeHTTP(w, r)
		})
	}
}

// WriteJSON writes v as a JSON response with the given status. It encodes before sending the status,
// so an encoding failure becomes a 500 envelope instead of a truncated success.
func WriteJSON(w http.ResponseWriter, status int, v any) {
	b, err := json.Marshal(v)
	if err != nil {
		status = http.StatusInternalServerError
		b = []byte(`{"error":{"code":"INTERNAL_ERROR","message":"Lỗi hệ thống, vui lòng thử lại sau.","requestId":""}}`)
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_, _ = w.Write(append(b, '\n')) // the client may be gone; nothing useful to do with the error
}

// WriteError writes the shared error envelope {"error":{"code","message","requestId"}}.
func WriteError(w http.ResponseWriter, r *http.Request, status int, code, message string) {
	type body struct {
		Code      string `json:"code"`
		Message   string `json:"message"`
		RequestID string `json:"requestId"`
	}
	WriteJSON(w, status, map[string]body{"error": {code, message, RequestID(r)}})
}
