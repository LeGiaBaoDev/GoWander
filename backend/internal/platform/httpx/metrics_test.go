package httpx

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestMetricsExposition(t *testing.T) {
	m := NewMetrics()
	m.Observe("/api/v1/places/{id}", "GET", 200, 40*time.Millisecond)
	m.Observe("/api/v1/places/{id}", "GET", 404, 2*time.Second)
	m.Observe("/api/v1/feedbacks", "POST", 500, 6*time.Second)
	m.Observe("", "GET", 999, time.Millisecond) // no route (404) and an impossible status must not panic
	c := m.RegisterCounter("gowander_feedback_created_total", "Feedbacks stored from the app")
	c.Inc()
	c.Inc()
	m.RegisterGauge("gowander_ready", "1 when everything answers", func() float64 { return 1 })

	out := m.Render()
	for _, want := range []string{
		`gowander_http_requests_total{route="/api/v1/places/{id}",method="GET",status="2xx"} 1`,
		`gowander_http_requests_total{route="/api/v1/places/{id}",method="GET",status="4xx"} 1`,
		`gowander_http_requests_total{route="/api/v1/feedbacks",method="POST",status="5xx"} 1`,
		`gowander_http_requests_total{route="unmatched",method="GET",status="other"} 1`,
		`gowander_http_request_duration_seconds_count{route="/api/v1/places/{id}",method="GET"} 2`,
		`gowander_http_request_duration_seconds_bucket{route="/api/v1/feedbacks",method="POST",le="5"} 0`,
		`gowander_http_request_duration_seconds_bucket{route="/api/v1/feedbacks",method="POST",le="+Inf"} 1`,
		"gowander_feedback_created_total 2",
		"gowander_ready 1",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("exposition is missing %q\n%s", want, out)
		}
	}
	if !strings.HasPrefix(out, "# HELP gowander_http_requests_total") {
		t.Fatal("exposition must start with the HTTP counter help")
	}
}

func TestMetricsHTTP(t *testing.T) {
	m := NewMetrics()
	m.Observe("/health/live", "GET", 200, time.Millisecond)
	rec := httptest.NewRecorder()
	m.ServeHTTP(rec, httptest.NewRequest("GET", "/metrics", nil))
	if rec.Code != 200 || !strings.HasPrefix(rec.Header().Get("Content-Type"), "text/plain") {
		t.Fatalf("%d %q", rec.Code, rec.Header().Get("Content-Type"))
	}
	if rec.Header().Get("Cache-Control") != "no-store" {
		t.Fatal("metrics must not be cached")
	}
	if !strings.Contains(rec.Body.String(), `gowander_http_requests_total{route="/health/live"`) {
		t.Fatalf("body = %q", rec.Body.String())
	}
}

// The router records every request it serves, with chi's pattern rather than the raw URL.
func TestRouterFeedsMetrics(t *testing.T) {
	before := Default.Render()
	router := NewRouter(slog.New(slog.NewTextHandler(io.Discard, nil)))
	router.Get("/probe/{id}", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
	router.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest("GET", "/probe/42", nil))
	router.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest("GET", "/probe/43", nil))
	after := Default.Render()
	if before == after {
		t.Fatal("the router did not record the requests")
	}
	if !strings.Contains(after, `route="/probe/{id}"`) || strings.Contains(after, `route="/probe/42"`) {
		t.Fatal("the metric uses the raw URL instead of the route pattern")
	}
}
