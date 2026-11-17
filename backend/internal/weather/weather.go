// Package weather turns coordinates into a small hourly forecast for the journey planner.
// It uses Open-Meteo (CC BY 4.0, free for non-commercial use): buy or self-host before a commercial launch.
package weather

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

var ErrUnavailable = errors.New("weather unavailable")

// Reading is one hour of weather. At is local time in Asia/Ho_Chi_Minh.
type Reading struct {
	At                       string  `json:"at"`
	TemperatureC             float64 `json:"temperatureC"`
	ApparentC                float64 `json:"apparentC"`
	PrecipitationMm          float64 `json:"precipitationMm"`
	PrecipitationProbability int     `json:"precipitationProbability"`
	WeatherCode              int     `json:"weatherCode"`
	WindKph                  float64 `json:"windKph"`
	IsRain                   bool    `json:"isRain"`
	Intensity                string  `json:"intensity"` // none, light, moderate, heavy
}

type Client struct {
	baseURL string
	timeout time.Duration
	http    *http.Client

	mu    sync.Mutex
	cache map[string]entry
}

type entry struct {
	reading Reading
	expires time.Time
}

const (
	cacheTTL   = 15 * time.Minute
	cacheLimit = 500
)

func New(baseURL string, timeout time.Duration) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		timeout: timeout,
		http: &http.Client{
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		},
		cache: map[string]entry{},
	}
}

// At returns the forecast hour closest to at (local time). Open-Meteo returns hourly values in the requested timezone.
func (c *Client) At(ctx context.Context, lat, lon float64, at time.Time) (Reading, error) {
	key := fmt.Sprintf("%.2f,%.2f,%s", lat, lon, at.In(pilotZone()).Format("2006-01-02T15"))
	if r, ok := c.fromCache(key); ok {
		return r, nil
	}
	ctx, cancel := context.WithTimeout(ctx, c.timeout)
	defer cancel()
	q := url.Values{
		"latitude":      {strconv.FormatFloat(lat, 'f', 4, 64)},
		"longitude":     {strconv.FormatFloat(lon, 'f', 4, 64)},
		"hourly":        {"temperature_2m,apparent_temperature,precipitation,precipitation_probability,weathercode,windspeed_10m"},
		"timezone":      {"Asia/Ho_Chi_Minh"},
		"forecast_days": {"16"},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"?"+q.Encode(), nil)
	if err != nil {
		return Reading{}, fmt.Errorf("%w: invalid request", ErrUnavailable)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		var ue *url.Error
		if errors.As(err, &ue) {
			err = ue.Err
		}
		return Reading{}, fmt.Errorf("%w: %v", ErrUnavailable, err)
	}
	defer func() { _, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10)); _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return Reading{}, fmt.Errorf("%w: open-meteo returned HTTP %d", ErrUnavailable, resp.StatusCode)
	}
	var body struct {
		Hourly struct {
			Time          []string  `json:"time"`
			Temperature   []float64 `json:"temperature_2m"`
			Apparent      []float64 `json:"apparent_temperature"`
			Precipitation []float64 `json:"precipitation"`
			Probability   []int     `json:"precipitation_probability"`
			Code          []int     `json:"weathercode"`
			Wind          []float64 `json:"windspeed_10m"`
		} `json:"hourly"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 8<<20)).Decode(&body); err != nil {
		return Reading{}, fmt.Errorf("%w: decode: %v", ErrUnavailable, err)
	}
	r, err := pick(body.Hourly.Time, body.Hourly.Temperature, body.Hourly.Apparent, body.Hourly.Precipitation, body.Hourly.Probability, body.Hourly.Code, body.Hourly.Wind, at)
	if err != nil {
		return Reading{}, err
	}
	c.putCache(key, r)
	return r, nil
}

func pick(times []string, temp, apparent, precip []float64, prob, code []int, wind []float64, at time.Time) (Reading, error) {
	if len(times) == 0 {
		return Reading{}, fmt.Errorf("%w: empty hourly forecast", ErrUnavailable)
	}
	want := at.In(pilotZone())
	best, bestDiff := 0, time.Duration(1<<62)
	for i, s := range times {
		t, err := time.ParseInLocation("2006-01-02T15:04", s, pilotZone())
		if err != nil {
			continue
		}
		d := t.Sub(want)
		if d < 0 {
			d = -d
		}
		if d < bestDiff {
			best, bestDiff = i, d
		}
	}
	if bestDiff > 90*time.Minute {
		return Reading{}, fmt.Errorf("%w: no forecast near %s", ErrUnavailable, want.Format(time.RFC3339))
	}
	atValue := func(i int) float64 {
		if i < len(temp) {
			return temp[i]
		}
		return 0
	}
	apparentValue := func(i int) float64 {
		if i < len(apparent) {
			return apparent[i]
		}
		return atValue(i)
	}
	precipValue := func(i int) float64 {
		if i < len(precip) {
			return precip[i]
		}
		return 0
	}
	intValue := func(v []int, i int) int {
		if i < len(v) {
			return v[i]
		}
		return 0
	}
	floatValue := func(v []float64, i int) float64 {
		if i < len(v) {
			return v[i]
		}
		return 0
	}
	p := precipValue(best)
	probability := intValue(prob, best)
	r := Reading{
		At:                       times[best],
		TemperatureC:             atValue(best),
		ApparentC:                apparentValue(best),
		PrecipitationMm:          p,
		PrecipitationProbability: probability,
		WeatherCode:              intValue(code, best),
		WindKph:                  floatValue(wind, best),
		IsRain:                   p > 0.05 || probability >= 50,
		Intensity:                intensity(p),
	}
	return r, nil
}

func intensity(mm float64) string {
	switch {
	case mm >= 4:
		return "heavy"
	case mm >= 0.5:
		return "moderate"
	case mm > 0.05:
		return "light"
	default:
		return "none"
	}
}

func (c *Client) fromCache(key string) (Reading, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	e, ok := c.cache[key]
	if !ok || time.Now().After(e.expires) {
		if ok {
			delete(c.cache, key)
		}
		return Reading{}, false
	}
	return e.reading, true
}

func (c *Client) putCache(key string, r Reading) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.cache) >= cacheLimit {
		for k := range c.cache {
			delete(c.cache, k)
			break
		}
	}
	c.cache[key] = entry{reading: r, expires: time.Now().Add(cacheTTL)}
}

func pilotZone() *time.Location {
	if loc, err := time.LoadLocation("Asia/Ho_Chi_Minh"); err == nil {
		return loc
	}
	return time.FixedZone("ICT", 7*3600)
}

// Handler serves GET /api/v1/weather?lat=&lon=&at=RFC3339 (at optional, default now).
type Handler struct {
	Client *Client
	Log    *slog.Logger
}

func (h Handler) Mount(r chi.Router) {
	r.Get("/api/v1/weather", h.get)
}

func (h Handler) get(w http.ResponseWriter, r *http.Request) {
	lat, err1 := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, err2 := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if err1 != nil || err2 != nil || lat < -90 || lat > 90 || lon < -180 || lon > 180 {
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "lat và lon là bắt buộc và phải hợp lệ.")
		return
	}
	at := time.Now()
	if s := r.URL.Query().Get("at"); s != "" {
		t, err := time.Parse(time.RFC3339, s)
		if err != nil {
			httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "at phải theo RFC 3339, ví dụ 2026-11-17T15:00:00+07:00.")
			return
		}
		at = t
	}
	reading, err := h.Client.At(r.Context(), lat, lon, at)
	if err != nil {
		if h.Log != nil {
			h.Log.Warn("weather failed", "requestId", httpx.RequestID(r), "err", err)
		}
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "WEATHER_UNAVAILABLE", "Thời tiết tạm thời không hoạt động, vui lòng thử lại sau.")
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"weather": reading,
		"source": map[string]string{
			"provider":    "Open-Meteo",
			"attribution": "Dữ liệu thời tiết: Open-Meteo.com",
			"license":     "CC BY 4.0",
		},
	})
}
