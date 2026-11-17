package geocode

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

const vmSearchFixture = `[
 {"ref_id":"geocode:1","name":"128/4 Trương Đăng Quế","display":"128/4 Trương Đăng Quế, Phường Hạnh Thông, TP.HCM","address":"Phường Hạnh Thông, TP.HCM"},
 {"ref_id":"geocode:2","name":"128/2a Trương Đăng Quế","display":"128/2a Trương Đăng Quế, Phường Hạnh Thông, TP.HCM","address":"Phường Hạnh Thông, TP.HCM"}
]`

func TestVietmapSearchMapsPlaceAndMarksApproximate(t *testing.T) {
	var placeCalls atomic.Int32
	var searchQuery string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/search/v4":
			searchQuery = r.URL.RawQuery
			io.WriteString(w, vmSearchFixture)
		case "/place/v4":
			placeCalls.Add(1)
			switch r.URL.Query().Get("refid") {
			case "geocode:1":
				io.WriteString(w, `{"name":"128/4 Trương Đăng Quế","address":"128/4 Trương Đăng Quế, Phường Hạnh Thông, TP.HCM","hs_num":"128/4","lat":10.81,"lng":106.68}`)
			case "geocode:2":
				io.WriteString(w, `{"name":"128/2a Trương Đăng Quế","address":"128/2a Trương Đăng Quế, Phường Hạnh Thông, TP.HCM","hs_num":"128/2a","lat":10.82,"lng":106.69}`)
			default:
				w.WriteHeader(http.StatusNotFound)
			}
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	t.Cleanup(srv.Close)

	c := NewVietmap(srv.URL, "test-key", time.Second)
	got, err := c.Search(context.Background(), Query{Text: "128/4 Trương Đăng Quế", Lat: 10.82, Lon: 106.68, Limit: 2})
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"apikey=test-key", "text=128%2F4+Tr%C6%B0%C6%A1ng+%C4%90%C4%83ng+Qu%E1%BA%BF", "focus=10.82%2C106.68", "display_type=6", "admin_new=true"} {
		if !strings.Contains(searchQuery, want) {
			t.Errorf("search query %q lacks %q", searchQuery, want)
		}
	}
	if len(got) != 2 || placeCalls.Load() != 2 {
		t.Fatalf("got %+v, place calls %d", got, placeCalls.Load())
	}
	if got[0].Name != "128/4 Trương Đăng Quế" || got[0].Lat != 10.81 || got[0].Lon != 106.68 || got[0].Source != SourceVietmap || !strings.HasPrefix(got[0].Ref, "vietmap:") {
		t.Errorf("exact result: %+v", got[0])
	}
	if got[0].Approximate {
		t.Errorf("128/4 should not be approximate: %+v", got[0])
	}
	if !got[1].Approximate {
		t.Errorf("128/2a for query 128/4 should be approximate: %+v", got[1])
	}
}

func TestVietmapSearchTruncatesToLimit(t *testing.T) {
	var placeCalls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/search/v4":
			io.WriteString(w, `[
			 {"ref_id":"1","name":"A","display":"A"},
			 {"ref_id":"2","name":"B","display":"B"},
			 {"ref_id":"3","name":"C","display":"C"}]`)
		case "/place/v4":
			placeCalls.Add(1)
			io.WriteString(w, `{"name":"A","address":"A","lat":10.8,"lng":106.7}`)
		default:
			w.WriteHeader(404)
		}
	}))
	t.Cleanup(srv.Close)
	got, err := NewVietmap(srv.URL, "k", time.Second).Search(context.Background(), Query{Text: "abc", Limit: 1})
	if err != nil || len(got) != 1 || placeCalls.Load() != 1 {
		t.Fatalf("%v %+v calls=%d", err, got, placeCalls.Load())
	}
}

func TestVietmapSearchPartialAndTotalFailure(t *testing.T) {
	t.Run("partial", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.URL.Path {
			case "/search/v4":
				io.WriteString(w, vmSearchFixture)
			case "/place/v4":
				if r.URL.Query().Get("refid") == "geocode:1" {
					w.WriteHeader(http.StatusInternalServerError)
					return
				}
				io.WriteString(w, `{"name":"128/2a","address":"128/2a","hs_num":"128/2a","lat":10.82,"lng":106.69}`)
			default:
				w.WriteHeader(404)
			}
		}))
		t.Cleanup(srv.Close)
		got, err := NewVietmap(srv.URL, "k", time.Second).Search(context.Background(), Query{Text: "128/4", Limit: 2})
		if err != nil || len(got) != 1 || got[0].Name != "128/2a" {
			t.Fatalf("%v %+v", err, got)
		}
	})
	t.Run("all fail", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/search/v4" {
				io.WriteString(w, vmSearchFixture)
				return
			}
			w.WriteHeader(http.StatusUnauthorized)
		}))
		t.Cleanup(srv.Close)
		_, err := NewVietmap(srv.URL, "k", time.Second).Search(context.Background(), Query{Text: "128/4", Limit: 2})
		if !errors.Is(err, ErrUnavailable) {
			t.Fatalf("want ErrUnavailable, got %v", err)
		}
	})
}

func TestVietmapReverse(t *testing.T) {
	var query string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/reverse/v4" {
			t.Errorf("path %s", r.URL.Path)
		}
		query = r.URL.RawQuery
		io.WriteString(w, `[{"ref_id":"r1","name":"122 Trương Đăng Quế","address":"Phường 3, Gò Vấp","lat":10.81,"lng":106.68}]`)
	}))
	t.Cleanup(srv.Close)
	got, err := NewVietmap(srv.URL, "k", time.Second).Reverse(context.Background(), 10.81, 106.68, 3)
	if err != nil || len(got) != 1 || got[0].Source != SourceVietmap || got[0].Lat != 10.81 {
		t.Fatalf("%v %+v", err, got)
	}
	for _, want := range []string{"lat=10.81", "lng=106.68", "display_type=6"} {
		if !strings.Contains(query, want) {
			t.Errorf("reverse query %q lacks %q", query, want)
		}
	}
}

func TestVietmapErrorDoesNotLeakKeyOrQuery(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	t.Cleanup(srv.Close)
	_, err := NewVietmap(srv.URL, "super-secret-key", time.Second).Search(context.Background(), Query{Text: "128/4 nhà riêng", Limit: 1})
	if !errors.Is(err, ErrUnavailable) {
		t.Fatalf("want ErrUnavailable, got %v", err)
	}
	if strings.Contains(err.Error(), "super-secret-key") || strings.Contains(err.Error(), "128/4") {
		t.Fatalf("error leaks key or query: %v", err)
	}
}

type countingSearcher struct {
	calls int
	res   []Result
	err   error
}

func (c *countingSearcher) Search(context.Context, Query) ([]Result, error) {
	c.calls++
	return c.res, c.err
}

func (c *countingSearcher) Reverse(context.Context, float64, float64, int) ([]Result, error) {
	c.calls++
	return c.res, c.err
}

func TestFallbackUsesSecondaryOnlyOnError(t *testing.T) {
	primary := &countingSearcher{err: ErrUnavailable}
	secondary := &countingSearcher{res: []Result{{Name: "photon"}}}
	got, err := (Fallback{Primary: primary, Secondary: secondary}).Search(context.Background(), Query{Text: "abc"})
	if err != nil || len(got) != 1 || got[0].Name != "photon" || primary.calls != 1 || secondary.calls != 1 {
		t.Fatalf("%v %+v primary=%d secondary=%d", err, got, primary.calls, secondary.calls)
	}

	primary = &countingSearcher{res: []Result{{Name: "vietmap"}}}
	secondary = &countingSearcher{res: []Result{{Name: "photon"}}}
	got, err = (Fallback{Primary: primary, Secondary: secondary}).Search(context.Background(), Query{Text: "abc"})
	if err != nil || got[0].Name != "vietmap" || secondary.calls != 0 {
		t.Fatalf("%v %+v secondary=%d", err, got, secondary.calls)
	}
}

type staticSearcher struct{ res []Result }

func (s staticSearcher) Search(context.Context, Query) ([]Result, error) { return s.res, nil }
func (s staticSearcher) Reverse(context.Context, float64, float64, int) ([]Result, error) {
	return s.res, nil
}

func TestHandlerVietmapAttribution(t *testing.T) {
	rec := serve(t, staticSearcher{res: []Result{{Name: "128/4", Source: SourceVietmap}}}, "/api/v1/geocode?q=abc")
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), `"provider":"Vietmap"`) || !strings.Contains(rec.Body.String(), "Vietmap") {
		t.Fatalf("%d %s", rec.Code, rec.Body)
	}
}
