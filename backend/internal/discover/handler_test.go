package discover

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

type fakeStore struct {
	err     error
	list    ListQuery
	nearby  NearbyQuery
	placeID string
	next    *Cursor
	places  []PlaceSummary
	detail  PlaceDetail
	cats    []Category
}

func (f *fakeStore) Categories(context.Context) ([]Category, error) { return f.cats, f.err }
func (f *fakeStore) ListPlaces(_ context.Context, q ListQuery) ([]PlaceSummary, *Cursor, error) {
	f.list = q
	return f.places, f.next, f.err
}
func (f *fakeStore) Nearby(_ context.Context, q NearbyQuery) ([]PlaceSummary, error) {
	f.nearby = q
	return f.places, f.err
}
func (f *fakeStore) Place(_ context.Context, id string) (PlaceDetail, error) {
	f.placeID = id
	return f.detail, f.err
}
func (f *fakeStore) Ready(context.Context) error { return f.err }

func get(t *testing.T, s Store, target string) (int, map[string]any) {
	t.Helper()
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	mux := httpx.NewRouter(log)
	Handler{Store: s, Log: log}.Mount(mux)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, target, nil))
	var body map[string]any
	if rec.Body.Len() > 0 {
		if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
			t.Fatalf("%s: body not JSON: %s", target, rec.Body)
		}
	}
	return rec.Code, body
}

func code(body map[string]any) string {
	e, _ := body["error"].(map[string]any)
	c, _ := e["code"].(string)
	return c
}

const placeID = "0190f0c2-0000-7000-8000-000000000001"

func TestCategories(t *testing.T) {
	s := &fakeStore{cats: []Category{{CategoryRef: CategoryRef{Slug: "museum", Name: "Bảo tàng"}, PlaceCount: 3}}}
	status, body := get(t, s, "/api/v1/place-categories")
	cats, _ := body["categories"].([]any)
	if status != 200 || len(cats) != 1 || cats[0].(map[string]any)["placeCount"] != 3.0 {
		t.Fatalf("%d %v", status, body)
	}
}

func TestListDefaultsAndFilters(t *testing.T) {
	s := &fakeStore{places: []PlaceSummary{{ID: placeID, Name: "Chợ Bến Thành"}}}
	status, body := get(t, s, "/api/v1/places")
	if status != 200 || s.list.Limit != 20 || s.list.Category != "" || s.list.Q != "" || s.list.After != nil {
		t.Fatalf("defaults: %d %+v %v", status, s.list, body)
	}
	if _, has := body["nextCursor"]; has {
		t.Fatalf("last page must not have nextCursor: %v", body)
	}
	get(t, s, "/api/v1/places?category=museum&q=%20ben%20thanh%20&limit=50")
	if s.list.Category != "museum" || s.list.Q != "ben thanh" || s.list.Limit != 50 {
		t.Fatalf("filters: %+v", s.list)
	}
}

func TestListCursorRoundTrip(t *testing.T) {
	s := &fakeStore{places: []PlaceSummary{{ID: placeID}}, next: &Cursor{Name: "Chợ Bến Thành", ID: placeID}}
	_, body := get(t, s, "/api/v1/places?limit=1")
	next, _ := body["nextCursor"].(string)
	if next == "" {
		t.Fatalf("missing nextCursor: %v", body)
	}
	s.next = nil
	if status, _ := get(t, s, "/api/v1/places?limit=1&cursor="+url.QueryEscape(next)); status != 200 || s.list.After == nil || *s.list.After != (Cursor{Name: "Chợ Bến Thành", ID: placeID}) {
		t.Fatalf("cursor not decoded: %d %+v", status, s.list.After)
	}
}

func TestNearby(t *testing.T) {
	d := 120.5
	s := &fakeStore{places: []PlaceSummary{{ID: placeID, DistanceM: &d}}}
	status, body := get(t, s, "/api/v1/places/nearby?lat=10.7725&lon=106.698")
	if status != 200 || s.nearby != (NearbyQuery{Lat: 10.7725, Lon: 106.698, RadiusM: 1000, Limit: 20}) {
		t.Fatalf("%d %+v", status, s.nearby)
	}
	if p := body["places"].([]any)[0].(map[string]any); p["distanceM"] != 120.5 {
		t.Fatalf("distance missing: %v", p)
	}
	get(t, s, "/api/v1/places/nearby?lat=10&lon=106&radiusM=5000&category=food&limit=5")
	if s.nearby.RadiusM != 5000 || s.nearby.Category != "food" || s.nearby.Limit != 5 {
		t.Fatalf("%+v", s.nearby)
	}
}

func TestInvalidRequests(t *testing.T) {
	for _, target := range []string{
		"/api/v1/places?limit=0", "/api/v1/places?limit=51", "/api/v1/places?limit=abc",
		"/api/v1/places?cursor=not-a-cursor", "/api/v1/places?q=" + strings.Repeat("a", 101), "/api/v1/places?q=%20%20",
		"/api/v1/places/nearby?lon=106", "/api/v1/places/nearby?lat=95&lon=106", "/api/v1/places/nearby?lat=10&lon=181",
		"/api/v1/places/nearby?lat=NaN&lon=106", "/api/v1/places/nearby?lat=10&lon=106&radiusM=0",
		"/api/v1/places/nearby?lat=10&lon=106&radiusM=5001", "/api/v1/places/not-a-uuid",
		"/api/v1/places?q=a", "/api/v1/places?q=%ff%fe", "/api/v1/places?q=ab%00", "/api/v1/places?category=%00",
		"/api/v1/places?category=" + strings.Repeat("x", 101), "/api/v1/places/nearby?lat=10&lon=106&category=%ff",
		"/api/v1/places?cursor=eyJuIjogImFcdTAwMDBiIiwgImkiOiAiMDE5MGYwYzItMDAwMC03MDAwLTgwMDAtMDAwMDAwMDAwMDAxIn0", "/api/v1/places?cursor=eyJpIjogIjAxOTBmMGMyLTAwMDAtNzAwMC04MDAwLTAwMDAwMDAwMDAwMSJ9", "/api/v1/places?cursor=eyJuIjogImFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYSIsICJpIjogIjAxOTBmMGMyLTAwMDAtNzAwMC04MDAwLTAwMDAwMDAwMDAwMSJ9",
	} {
		s := &fakeStore{}
		if status, body := get(t, s, target); status != 400 || code(body) != "INVALID_REQUEST" {
			t.Errorf("%s: %d %v", target, status, body)
		}
	}
}

func TestGetPlace(t *testing.T) {
	s := &fakeStore{detail: PlaceDetail{PlaceSummary: PlaceSummary{ID: placeID, Name: "Chợ Bến Thành"}}}
	if status, body := get(t, s, "/api/v1/places/"+strings.ToUpper(placeID)); status != 200 || body["name"] != "Chợ Bến Thành" || s.placeID != placeID {
		t.Fatalf("%d %v id=%s", status, body, s.placeID)
	}
	s.err = ErrNotFound
	if status, body := get(t, s, "/api/v1/places/"+placeID); status != 404 || code(body) != "PLACE_NOT_FOUND" {
		t.Fatalf("%d %v", status, body)
	}
}

func TestStoreFailure(t *testing.T) {
	for _, target := range []string{"/api/v1/place-categories", "/api/v1/places", "/api/v1/places/nearby?lat=10&lon=106", "/api/v1/places/" + placeID} {
		s := &fakeStore{err: errors.New("dial tcp 127.0.0.1:5432: connection refused")}
		status, body := get(t, s, target)
		raw, _ := json.Marshal(body)
		if status != 503 || code(body) != "DATA_STORE_UNAVAILABLE" || strings.Contains(string(raw), "5432") {
			t.Errorf("%s: %d %s", target, status, raw)
		}
	}
}

func TestCancelledRequestWritesNothing(t *testing.T) {
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	mux := httpx.NewRouter(log)
	Handler{Store: &fakeStore{err: context.Canceled}, Log: log}.Mount(mux)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/v1/places", nil).WithContext(ctx))
	if rec.Body.Len() != 0 || rec.Code != 499 { // 499 = client closed request, so logs do not count it as a success
		t.Fatalf("status %d, wrote %s", rec.Code, rec.Body)
	}
}
