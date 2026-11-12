package mobility

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

const validBody = `{"origin":{"lat":10.77,"lon":106.69},"destination":{"lat":10.87,"lon":106.81}}`

type fakeProvider struct {
	routes []Route
	err    error
	got    Query
}

func (f *fakeProvider) PlanRoute(_ context.Context, q Query) ([]Route, error) {
	f.got = q
	return f.routes, f.err
}
func (f *fakeProvider) Ready(context.Context) error { return f.err }

func serve(t *testing.T, p RoutingProvider, req *http.Request) *httptest.ResponseRecorder {
	t.Helper()
	r := httpx.NewRouter(slog.New(slog.NewTextHandler(io.Discard, nil)))
	r.Post("/api/v1/routes/plan", Handler{Provider: p, DataStatus: "ASSUMED", Log: slog.New(slog.NewTextHandler(io.Discard, nil))}.Plan)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)
	return rec
}

func post(body string) *http.Request {
	return httptest.NewRequest(http.MethodPost, "/api/v1/routes/plan", strings.NewReader(body))
}

func TestPlanOK(t *testing.T) {
	p := &fakeProvider{routes: []Route{{DurationSec: 1800, Legs: []Leg{{Mode: "SUBWAY", Transit: true}}}}}
	req := post(validBody)
	req.Header.Set("X-Request-Id", "client-chosen")
	rec := serve(t, p, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status %d, body %s", rec.Code, rec.Body)
	}
	if id := rec.Header().Get("X-Request-Id"); len(id) != 26 || strings.Contains(id, "/") {
		t.Fatalf("X-Request-Id %q: want a 26-char random ID without host name", id)
	}
	var got struct {
		DataStatus string  `json:"dataStatus"`
		Routes     []Route `json:"routes"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	if got.DataStatus != "ASSUMED" || len(got.Routes) != 1 || got.Routes[0].DurationSec != 1800 {
		t.Fatalf("unexpected body %s", rec.Body)
	}
	if p.got.MaxResults != 3 {
		t.Fatalf("provider got %+v", p.got)
	}
}

func TestPlanErrors(t *testing.T) {
	cases := []struct {
		name     string
		body     string
		provider *fakeProvider
		status   int
		code     Code
	}{
		{"invalid input", `{}`, &fakeProvider{}, 400, InvalidRequest},
		{"body too large", `{"origin":{"lat":1,"lon":1},"pad":"` + strings.Repeat("x", 70<<10) + `"}`, &fakeProvider{}, 400, InvalidRequest},
		{"no route", validBody, &fakeProvider{err: Fail(NoRouteFound, nil)}, 422, NoRouteFound},
		{"empty routes without error", validBody, &fakeProvider{}, 422, NoRouteFound},
		{"out of coverage", validBody, &fakeProvider{err: Fail(OutOfCoverage, nil)}, 422, OutOfCoverage},
		{"no schedule", validBody, &fakeProvider{err: Fail(DataUnavailable, nil)}, 422, DataUnavailable},
		{"timeout", validBody, &fakeProvider{err: Fail(RoutingTimeout, context.DeadlineExceeded)}, 504, RoutingTimeout},
		{"unavailable", validBody, &fakeProvider{err: Fail(RoutingUnavailable, errors.New("dial tcp: refused"))}, 503, RoutingUnavailable},
		{"unknown error code", validBody, &fakeProvider{err: &Error{Code: "WEIRD", Message: "x"}}, 503, RoutingUnavailable},
		{"unexpected provider error", validBody, &fakeProvider{err: errors.New("boom")}, 503, RoutingUnavailable},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			rec := serve(t, c.provider, post(c.body))
			if rec.Code != c.status {
				t.Fatalf("status %d, want %d, body %s", rec.Code, c.status, rec.Body)
			}
			var env struct {
				Error struct{ Code, Message, RequestID string } `json:"error"`
			}
			if err := json.Unmarshal(rec.Body.Bytes(), &env); err != nil {
				t.Fatalf("body not JSON: %s", rec.Body)
			}
			if env.Error.Code != string(c.code) || env.Error.Message == "" || env.Error.RequestID == "" {
				t.Fatalf("bad envelope %s", rec.Body)
			}
			if strings.Contains(rec.Body.String(), "refused") || strings.Contains(rec.Body.String(), "boom") {
				t.Fatalf("internal error leaked to client: %s", rec.Body)
			}
		})
	}
}

func TestPlanClientCancelledWritesNothing(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	rec := serve(t, &fakeProvider{err: context.Canceled}, post(validBody).WithContext(ctx))
	if rec.Body.Len() != 0 {
		t.Fatalf("expected no body for cancelled client, got %s", rec.Body)
	}
}
