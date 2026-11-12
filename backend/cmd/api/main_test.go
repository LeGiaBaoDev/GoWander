package main

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

	"github.com/LeGiaBaoDev/GoWander/backend/internal/discover"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/config"
)

type stubProvider struct{ readyErr error }

// stubStore is an empty Discover store; route-case integration tests reuse it.
type stubStore struct{ readyErr error }

func (s stubStore) Categories(context.Context) ([]discover.Category, error) { return nil, nil }
func (s stubStore) ListPlaces(context.Context, discover.ListQuery) ([]discover.PlaceSummary, *discover.Cursor, error) {
	return nil, nil, nil
}
func (s stubStore) Nearby(context.Context, discover.NearbyQuery) ([]discover.PlaceSummary, error) {
	return nil, nil
}
func (s stubStore) Place(context.Context, string) (discover.PlaceDetail, error) {
	return discover.PlaceDetail{}, discover.ErrNotFound
}
func (s stubStore) Ready(context.Context) error { return s.readyErr }

func (s stubProvider) PlanRoute(context.Context, mobility.Query) ([]mobility.Route, error) {
	panic("boom") // exercises the recoverer
}
func (s stubProvider) Ready(context.Context) error { return s.readyErr }

func do(t *testing.T, p mobility.RoutingProvider, method, path string) (int, map[string]any, http.Header) {
	return doWith(t, p, stubStore{}, method, path)
}

func doWith(t *testing.T, p mobility.RoutingProvider, s discover.Store, method, path string) (int, map[string]any, http.Header) {
	t.Helper()
	h := newHandler(config.Config{DataStatus: "ASSUMED"}, slog.New(slog.NewTextHandler(io.Discard, nil)), p, s)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(method, path, nil))
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("%s %s: body is not JSON: %q", method, path, rec.Body)
	}
	return rec.Code, body, rec.Header()
}

func errCode(body map[string]any) any { e, _ := body["error"].(map[string]any); return e["code"] }

func TestRoutes(t *testing.T) {
	if code, body, h := do(t, stubProvider{}, "GET", "/health/live"); code != 200 || body["status"] != "ok" || h.Get("X-Content-Type-Options") != "nosniff" {
		t.Fatalf("live: %d %v %v", code, body, h)
	}
	if code, body, _ := do(t, stubProvider{}, "GET", "/health/ready"); code != 200 || body["status"] != "ok" {
		t.Fatalf("ready: %d %v", code, body)
	}
	if code, body, _ := do(t, stubProvider{readyErr: errors.New("down")}, "GET", "/health/ready"); code != 503 || body["status"] != "unavailable" {
		t.Fatalf("not ready: %d %v", code, body)
	}
	if code, body, _ := doWith(t, stubProvider{}, stubStore{readyErr: errors.New("db down")}, "GET", "/health/ready"); code != 503 || body["status"] != "unavailable" {
		t.Fatalf("database not ready: %d %v", code, body)
	}
	if code, body, _ := do(t, stubProvider{}, "GET", "/api/v1/place-categories"); code != 200 || body["categories"] == nil {
		t.Fatalf("discover not mounted: %d %v", code, body)
	}
	if code, body, _ := do(t, stubProvider{}, "GET", "/api/v1/places/0190f0c2-0000-7000-8000-000000000001"); code != 404 || errCode(body) != "PLACE_NOT_FOUND" {
		t.Fatalf("place 404: %d %v", code, body)
	}
	if code, body, _ := do(t, stubProvider{}, "GET", "/nope"); code != 404 || errCode(body) != "NOT_FOUND" {
		t.Fatalf("404: %d %v", code, body)
	}
	if code, body, _ := do(t, stubProvider{}, "GET", "/api/v1/routes/plan"); code != 405 || errCode(body) != "NOT_FOUND" {
		t.Fatalf("405: %d %v", code, body)
	}
}

func TestPanicBecomesEnvelope(t *testing.T) {
	h := newHandler(config.Config{DataStatus: "ASSUMED"}, slog.New(slog.NewTextHandler(io.Discard, nil)), stubProvider{}, stubStore{})
	rec := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/v1/routes/plan", http.NoBody)
	req.Body = io.NopCloser(strings.NewReader(`{"origin":{"lat":10.77,"lon":106.69},"destination":{"lat":10.87,"lon":106.81}}`))
	h.ServeHTTP(rec, req)
	var body map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &body)
	if rec.Code != 500 || errCode(body) != "INTERNAL_ERROR" {
		t.Fatalf("panic: %d %s", rec.Code, rec.Body)
	}
}
