package geocode

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

// Shape of a Photon 0.7 /api answer (GeoJSON FeatureCollection; coordinates are lon, lat).
const photonFixture = `{"type":"FeatureCollection","features":[
 {"type":"Feature","geometry":{"type":"Point","coordinates":[106.6981,10.7725]},
  "properties":{"osm_type":"N","osm_id":1,"name":"Chợ Bến Thành","street":"Lê Lợi","district":"Phường Bến Thành","city":"Thành phố Hồ Chí Minh","country":"Việt Nam","type":"house","osm_key":"amenity","osm_value":"marketplace"}},
 {"type":"Feature","geometry":{"type":"Point","coordinates":[106.5000,11.0000]},
  "properties":{"osm_type":"W","osm_id":2,"housenumber":"770","street":"Tỉnh lộ 15","district":"Xã Tân Thạnh Đông","city":"Huyện Củ Chi","type":"house"}},
 {"type":"Feature","geometry":{"type":"Point","coordinates":[105.85,21.03]},"properties":{"osm_type":"N","osm_id":3}}]}`

func photon(t *testing.T, h http.HandlerFunc) (*Client, *string) {
	t.Helper()
	var query string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		h(w, r)
	}))
	t.Cleanup(srv.Close)
	return New(srv.URL, time.Second), &query
}

func TestSearchMapsPhotonFeatures(t *testing.T) {
	c, q := photon(t, func(w http.ResponseWriter, _ *http.Request) { io.WriteString(w, photonFixture) })
	got, err := c.Search(context.Background(), Query{Text: "770 tinh lo 15", Lat: 10.77, Lon: 106.70, Limit: 5})
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"q=770+tinh+lo+15", "limit=5", "lat=10.77", "lon=106.7", "bbox=106.35%2C10.35%2C107.05%2C11.2"} {
		if !strings.Contains(*q, want) {
			t.Errorf("photon query %q lacks %q", *q, want)
		}
	}
	if len(got) != 2 { // the feature without name, street or house number is dropped
		t.Fatalf("got %+v", got)
	}
	if got[0].Name != "Chợ Bến Thành" || got[0].Lat != 10.7725 || got[0].Lon != 106.6981 || got[0].Detail != "Lê Lợi, Phường Bến Thành, Thành phố Hồ Chí Minh" {
		t.Errorf("place: %+v", got[0])
	}
	if got[1].Name != "770 Tỉnh lộ 15" || got[1].Detail != "Xã Tân Thạnh Đông, Huyện Củ Chi" || got[1].Ref != "way/2" {
		t.Errorf("address: %+v", got[1])
	}
}

func TestSearchFailures(t *testing.T) {
	for name, h := range map[string]http.HandlerFunc{
		"http 500":    func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(500) },
		"broken json": func(w http.ResponseWriter, _ *http.Request) { io.WriteString(w, `{"features":`) },
		"slow":        func(w http.ResponseWriter, r *http.Request) { <-r.Context().Done() },
	} {
		t.Run(name, func(t *testing.T) {
			c, _ := photon(t, h)
			c.timeout = 100 * time.Millisecond
			_, err := c.Search(context.Background(), Query{Text: "nha rieng 12", Limit: 5})
			if !errors.Is(err, ErrUnavailable) {
				t.Fatalf("want ErrUnavailable, got %v", err)
			}
			if strings.Contains(err.Error(), "nha") { // the query (possibly a home address) must not reach logs
				t.Fatalf("error leaks the query: %v", err)
			}
		})
	}
}

func serve(t *testing.T, c Searcher, target string) *httptest.ResponseRecorder {
	t.Helper()
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	mux := httpx.NewRouter(log)
	Handler{Geocoder: c, Log: log}.Mount(mux)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, target, nil))
	return rec
}

type fake struct {
	got Query
	err error
}

func (f *fake) Search(_ context.Context, q Query) ([]Result, error) {
	f.got = q
	return []Result{{Name: "770 Tỉnh lộ 15", Lat: 11, Lon: 106.5, Ref: "way/2"}}, f.err
}

func TestHandler(t *testing.T) {
	f := &fake{}
	rec := serve(t, f, "/api/v1/geocode?q=%20770%20tinh%20lo%2015%20&lat=10.77&lon=106.7")
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), `"attribution":"© OpenStreetMap contributors"`) || f.got.Text != "770 tinh lo 15" || f.got.Limit != 5 {
		t.Fatalf("%d %s %+v", rec.Code, rec.Body, f.got)
	}
	for _, bad := range []string{"/api/v1/geocode", "/api/v1/geocode?q=a", "/api/v1/geocode?q=%ff%fe", "/api/v1/geocode?q=abc&lat=95&lon=1", "/api/v1/geocode?q=abc&limit=11", "/api/v1/geocode?q=" + strings.Repeat("a", 201)} {
		if rec := serve(t, &fake{}, bad); rec.Code != 400 || !strings.Contains(rec.Body.String(), "INVALID_REQUEST") {
			t.Errorf("%s: %d %s", bad, rec.Code, rec.Body)
		}
	}
	rec = serve(t, &fake{err: ErrUnavailable}, "/api/v1/geocode?q=abc")
	if rec.Code != 503 || !strings.Contains(rec.Body.String(), "GEOCODER_UNAVAILABLE") || strings.Contains(rec.Body.String(), "2322") {
		t.Fatalf("%d %s", rec.Code, rec.Body)
	}
}

func TestApproximateWhenHouseNumberAskedButMissing(t *testing.T) {
	c, _ := photon(t, func(w http.ResponseWriter, _ *http.Request) { io.WriteString(w, photonFixture) })
	got, err := c.Search(context.Background(), Query{Text: "770 tinh lo 15", Limit: 5})
	if err != nil {
		t.Fatal(err)
	}
	// Query has a number; "Chợ Bến Thành" has no house number -> approximate; "770 Tỉnh lộ 15" has one -> exact.
	if !got[0].Approximate || got[1].Approximate {
		t.Fatalf("%+v", got)
	}
	got, _ = c.Search(context.Background(), Query{Text: "cho ben thanh", Limit: 5})
	if got[0].Approximate { // no number typed: nothing to be approximate about
		t.Fatalf("%+v", got[0])
	}
}

func TestReverse(t *testing.T) {
	c, q := photon(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/reverse" {
			t.Errorf("path %s", r.URL.Path)
		}
		io.WriteString(w, photonFixture)
	})
	got, err := c.Reverse(context.Background(), 10.7725, 106.6981, 3)
	if err != nil || len(got) != 2 || !strings.Contains(*q, "lat=10.7725") || !strings.Contains(*q, "limit=3") {
		t.Fatalf("%v %+v %s", err, got, *q)
	}
	rec := serve(t, &fake{}, "/api/v1/geocode/reverse?lat=10.7725&lon=106.6981")
	if rec.Code != 200 {
		t.Fatalf("%d %s", rec.Code, rec.Body)
	}
	for _, bad := range []string{"/api/v1/geocode/reverse", "/api/v1/geocode/reverse?lat=95&lon=1", "/api/v1/geocode/reverse?lat=10&lon=x"} {
		if rec := serve(t, &fake{}, bad); rec.Code != 400 {
			t.Errorf("%s: %d", bad, rec.Code)
		}
	}
}

func (f *fake) Reverse(_ context.Context, lat, lon float64, limit int) ([]Result, error) {
	return []Result{{Name: "Lê Lợi", Lat: lat, Lon: lon}}, f.err
}
