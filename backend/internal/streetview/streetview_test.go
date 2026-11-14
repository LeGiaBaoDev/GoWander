package streetview

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/go-chi/chi/v5"
)

const twoImages = `{"data":[
 {"id":"111","thumb_1024_url":"https://img.test/far.jpg","captured_at":1717200000000,"geometry":{"type":"Point","coordinates":[106.6990,10.7730]},"creator":{"username":"far_user"}},
 {"id":"222","thumb_1024_url":"https://img.test/near.jpg","captured_at":1717286400000,"geometry":{"type":"Point","coordinates":[106.69801,10.77201]},"creator":{"username":"be_rider"}}
]}`

func server(t *testing.T, status int, body string, seen *http.Request) *httptest.Server {
	t.Helper()
	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		*seen = *r.Clone(context.Background())
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	}))
	t.Cleanup(s.Close)
	return s
}

func TestNearestPicksClosestImageAndSendsTokenInHeader(t *testing.T) {
	var seen http.Request
	s := server(t, 200, twoImages, &seen)
	img, err := New(s.URL, "MLY|secret", time.Second).Nearest(context.Background(), 10.7720, 106.6980)
	if err != nil {
		t.Fatal(err)
	}
	if img.ID != "222" || img.URL != "https://img.test/near.jpg" || img.Creator != "be_rider" || img.Lat != 10.77201 ||
		img.PageURL != "https://www.mapillary.com/app/?pKey=222" || !img.CapturedAt.Equal(time.UnixMilli(1717286400000).UTC()) {
		t.Fatalf("image %+v", img)
	}
	if seen.Header.Get("Authorization") != "OAuth MLY|secret" || strings.Contains(seen.URL.RawQuery, "secret") {
		t.Fatalf("token must go in the header only: %v %q", seen.Header, seen.URL.RawQuery)
	}
	q := seen.URL.Query()
	if seen.URL.Path != "/images" || q.Get("limit") == "" || !strings.Contains(q.Get("fields"), "thumb_1024_url") {
		t.Fatalf("request %s?%s", seen.URL.Path, seen.URL.RawQuery)
	}
	parts := strings.Split(q.Get("bbox"), ",")
	if len(parts) != 4 {
		t.Fatalf("bbox %q", q.Get("bbox"))
	}
	b := make([]float64, 4)
	for i, p := range parts {
		b[i], _ = strconv.ParseFloat(p, 64)
	}
	if !(b[0] < 106.6980 && b[2] > 106.6980 && b[1] < 10.7720 && b[3] > 10.7720) || b[2]-b[0] > 0.002 {
		t.Fatalf("bbox (minLon,minLat,maxLon,maxLat) %v", b)
	}
}

func TestNearestErrors(t *testing.T) {
	var seen http.Request
	for name, c := range map[string]struct {
		status int
		body   string
		want   error
	}{
		"no images":    {200, `{"data":[]}`, ErrNotFound},
		"no url":       {200, `{"data":[{"id":"1","geometry":{"type":"Point","coordinates":[106.698,10.772]}}]}`, ErrNotFound},
		"upstream 500": {500, `oops`, ErrUnavailable},
		"bad json":     {200, `{`, ErrUnavailable},
	} {
		t.Run(name, func(t *testing.T) {
			s := server(t, c.status, c.body, &seen)
			if _, err := New(s.URL, "MLY|x", time.Second).Nearest(context.Background(), 10.772, 106.698); !errors.Is(err, c.want) {
				t.Fatalf("got %v, want %v", err, c.want)
			}
		})
	}
	if _, err := New("http://127.0.0.1:1", "", time.Second).Nearest(context.Background(), 10.772, 106.698); !errors.Is(err, ErrUnavailable) {
		t.Fatalf("no token: %v", err)
	}
}

type fake struct {
	img *Image
	err error
}

func (f fake) Nearest(context.Context, float64, float64) (*Image, error) { return f.img, f.err }

func TestHandler(t *testing.T) {
	img := &Image{ID: "222", URL: "https://img.test/near.jpg", Creator: "be_rider", Lat: 10.772, Lon: 106.698, PageURL: "https://www.mapillary.com/app/?pKey=222"}
	for name, c := range map[string]struct {
		finder Finder
		query  string
		status int
		code   string
	}{
		"ok":          {fake{img: img}, "lat=10.772&lon=106.698", 200, ""},
		"bad lat":     {fake{img: img}, "lat=95&lon=106.698", 400, "INVALID_REQUEST"},
		"missing lon": {fake{img: img}, "lat=10.772", 400, "INVALID_REQUEST"},
		"not found":   {fake{err: ErrNotFound}, "lat=10.772&lon=106.698", 404, "STREET_IMAGE_NOT_FOUND"},
		"unavailable": {fake{err: ErrUnavailable}, "lat=10.772&lon=106.698", 503, "STREET_IMAGERY_UNAVAILABLE"},
	} {
		t.Run(name, func(t *testing.T) {
			mux := chi.NewRouter()
			Handler{Finder: c.finder, Log: slog.New(slog.DiscardHandler)}.Mount(mux)
			rec := httptest.NewRecorder()
			mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/v1/street-images/nearest?"+c.query, nil))
			if rec.Code != c.status {
				t.Fatalf("status %d: %s", rec.Code, rec.Body)
			}
			var body map[string]any
			_ = json.Unmarshal(rec.Body.Bytes(), &body)
			if c.code != "" {
				if body["error"].(map[string]any)["code"] != c.code {
					t.Fatalf("body %s", rec.Body)
				}
				return
			}
			src := body["source"].(map[string]any)
			if body["image"].(map[string]any)["id"] != "222" || src["license"] != "CC-BY-SA-4.0" || !strings.Contains(src["attribution"].(string), "be_rider") {
				t.Fatalf("body %s", rec.Body)
			}
		})
	}
}
