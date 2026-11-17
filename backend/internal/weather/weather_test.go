package weather

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

const fixture = `{"hourly":{
 "time":["2026-11-17T14:00","2026-11-17T15:00","2026-11-17T16:00"],
 "temperature_2m":[31.0,32.0,30.5],
 "apparent_temperature":[35.0,37.0,34.0],
 "precipitation":[0.0,1.2,0.0],
 "precipitation_probability":[10,80,20],
 "weathercode":[1,61,2],
 "windspeed_10m":[8.0,12.0,10.0]}}`

func TestAtPicksNearestHourAndCaches(t *testing.T) {
	var calls atomic.Int32
	var query string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		query = r.URL.RawQuery
		io.WriteString(w, fixture)
	}))
	t.Cleanup(srv.Close)

	c := New(srv.URL, time.Second)
	at := time.Date(2026, 11, 17, 15, 10, 0, 0, pilotZone())
	got, err := c.At(context.Background(), 10.7769, 106.7009, at)
	if err != nil {
		t.Fatal(err)
	}
	if got.TemperatureC != 32 || got.PrecipitationMm != 1.2 || got.PrecipitationProbability != 80 || !got.IsRain || got.Intensity != "moderate" || got.At != "2026-11-17T15:00" {
		t.Fatalf("got %+v", got)
	}
	for _, want := range []string{"latitude=10.7769", "longitude=106.7009", "timezone=Asia%2FHo_Chi_Minh", "forecast_days=16"} {
		if !strings.Contains(query, want) {
			t.Errorf("query %q lacks %q", query, want)
		}
	}
	if _, err := c.At(context.Background(), 10.7769, 106.7009, at); err != nil {
		t.Fatal(err)
	}
	if calls.Load() != 1 {
		t.Fatalf("cache miss: %d calls", calls.Load())
	}
}

func TestAtRejectsUpstreamFailure(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(500) }))
	t.Cleanup(srv.Close)
	_, err := New(srv.URL, time.Second).At(context.Background(), 10.7, 106.7, time.Now())
	if !errors.Is(err, ErrUnavailable) {
		t.Fatalf("want ErrUnavailable, got %v", err)
	}
}

func TestHandler(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { io.WriteString(w, fixture) }))
	t.Cleanup(upstream.Close)
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	mux := httpx.NewRouter(log)
	Handler{Client: New(upstream.URL, time.Second), Log: log}.Mount(mux)

	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/v1/weather?lat=10.77&lon=106.70&at=2026-11-17T15:10:00%2B07:00", nil))
	if rec.Code != 200 || !strings.Contains(rec.Body.String(), `"provider":"Open-Meteo"`) || !strings.Contains(rec.Body.String(), `"isRain":true`) {
		t.Fatalf("%d %s", rec.Code, rec.Body)
	}
	for _, bad := range []string{"/api/v1/weather", "/api/v1/weather?lat=95&lon=1", "/api/v1/weather?lat=10&lon=106&at=soon"} {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, bad, nil))
		if rec.Code != 400 || !strings.Contains(rec.Body.String(), "INVALID_REQUEST") {
			t.Errorf("%s: %d %s", bad, rec.Code, rec.Body)
		}
	}
}
