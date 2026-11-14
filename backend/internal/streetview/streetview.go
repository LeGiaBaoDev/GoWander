// Package streetview finds a street-level photo near a point for the route preview (NAV-1, ADR-033).
// Source: Mapillary (images CC BY-SA 4.0, faces and plates blurred by Mapillary). The token stays on the server.
package streetview

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

var (
	ErrNotFound    = errors.New("no street image near this point")
	ErrUnavailable = errors.New("street imagery unavailable")
)

// searchHalfSide is half the side of the search box in degrees (~60 m at HCMC's latitude).
const searchHalfSide = 0.00055

type Image struct {
	ID         string    `json:"id"`
	URL        string    `json:"url"`
	CapturedAt time.Time `json:"capturedAt"`
	Lat        float64   `json:"lat"`
	Lon        float64   `json:"lon"`
	Creator    string    `json:"creator,omitempty"`
	PageURL    string    `json:"pageUrl"`
}

type Finder interface {
	Nearest(ctx context.Context, lat, lon float64) (*Image, error)
}

type Client struct {
	baseURL, token string
	timeout        time.Duration
	http           *http.Client
}

// New returns a Mapillary client. An empty token makes every call return ErrUnavailable (feature off).
func New(baseURL, token string, timeout time.Duration) *Client {
	return &Client{baseURL: strings.TrimRight(baseURL, "/"), token: token, timeout: timeout, http: &http.Client{
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}}
}

type mapillaryAnswer struct {
	Data []struct {
		ID         string `json:"id"`
		Thumb      string `json:"thumb_1024_url"`
		CapturedAt int64  `json:"captured_at"` // epoch millis
		Geometry   struct {
			Coordinates []float64 `json:"coordinates"`
		} `json:"geometry"`
		Creator struct {
			Username string `json:"username"`
		} `json:"creator"`
	} `json:"data"`
}

func (c *Client) Nearest(ctx context.Context, lat, lon float64) (*Image, error) {
	if c.token == "" {
		return nil, fmt.Errorf("%w: MAPILLARY_TOKEN not set", ErrUnavailable)
	}
	ctx, cancel := context.WithTimeout(ctx, c.timeout)
	defer cancel()
	f := func(x float64) string { return strconv.FormatFloat(x, 'f', 6, 64) }
	v := url.Values{
		"bbox":   {strings.Join([]string{f(lon - searchHalfSide), f(lat - searchHalfSide), f(lon + searchHalfSide), f(lat + searchHalfSide)}, ",")},
		"fields": {"id,thumb_1024_url,captured_at,geometry,creator"},
		"limit":  {"20"},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/images?"+v.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", ErrUnavailable, err)
	}
	req.Header.Set("Authorization", "OAuth "+c.token) // header, not URL: keeps the token out of logs
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", ErrUnavailable, err)
	}
	defer func() { _, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10)); _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%w: mapillary returned HTTP %d", ErrUnavailable, resp.StatusCode)
	}
	var a mapillaryAnswer
	if err := json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(&a); err != nil {
		return nil, fmt.Errorf("%w: decode: %v", ErrUnavailable, err)
	}
	var best *Image
	bestD := math.Inf(1)
	for _, d := range a.Data {
		if !allowedImageURL(d.Thumb) || d.CapturedAt <= 0 || len(d.Geometry.Coordinates) != 2 {
			continue // only HTTPS photos on Mapillary's CDN with a capture date are passed on to the app
		}
		ilon, ilat := d.Geometry.Coordinates[0], d.Geometry.Coordinates[1]
		// Equirectangular distance is enough to rank points ~60 m apart.
		dist := math.Hypot((ilon-lon)*math.Cos(lat*math.Pi/180), ilat-lat)
		if dist < bestD {
			bestD = dist
			best = &Image{
				ID: d.ID, URL: d.Thumb, CapturedAt: time.UnixMilli(d.CapturedAt).UTC(), Lat: ilat, Lon: ilon,
				Creator: d.Creator.Username, PageURL: "https://www.mapillary.com/app/?pKey=" + url.QueryEscape(d.ID),
			}
		}
	}
	if best == nil {
		return nil, ErrNotFound
	}
	return best, nil
}

// allowedImageURL: the app downloads this URL, so only HTTPS on Mapillary's CDN hosts is accepted (security review).
func allowedImageURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "https" || u.User != nil {
		return false
	}
	h := u.Hostname()
	for _, d := range []string{"fbcdn.net", "mapillary.com"} {
		if h == d || strings.HasSuffix(h, "."+d) {
			return true
		}
	}
	return false
}

// Handler serves GET /api/v1/street-images/nearest?lat=&lon=. Limit (optional) caps requests per client IP.
type Handler struct {
	Finder Finder
	Log    *slog.Logger
	Limit  *Limiter
}

func (h Handler) Mount(r chi.Router) { r.Get("/api/v1/street-images/nearest", h.nearest) }

func (h Handler) nearest(w http.ResponseWriter, r *http.Request) {
	if h.Limit != nil && !h.Limit.Allow(r) {
		httpx.WriteError(w, r, http.StatusTooManyRequests, "RATE_LIMITED", "Bạn xem ảnh quá nhanh, vui lòng thử lại sau ít phút.")
		return
	}
	v := r.URL.Query()
	lat, err1 := strconv.ParseFloat(v.Get("lat"), 64)
	lon, err2 := strconv.ParseFloat(v.Get("lon"), 64)
	if err1 != nil || err2 != nil || math.IsNaN(lat) || math.IsNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180 {
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "lat và lon là bắt buộc và phải hợp lệ.")
		return
	}
	img, err := h.Finder.Nearest(r.Context(), lat, lon)
	switch {
	case errors.Is(err, ErrNotFound):
		httpx.WriteError(w, r, http.StatusNotFound, "STREET_IMAGE_NOT_FOUND", "Chưa có ảnh đường phố gần điểm này.")
		return
	case err != nil:
		if r.Context().Err() != nil {
			w.WriteHeader(499) // client went away: nobody reads this, the status is only for the access log
			return
		}
		h.Log.Warn("street imagery failed", "requestId", httpx.RequestID(r), "err", err)
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "STREET_IMAGERY_UNAVAILABLE", "Ảnh đường phố tạm thời không có.")
		return
	}
	attribution := "Mapillary, CC BY-SA 4.0"
	if img.Creator != "" {
		attribution = "© " + img.Creator + ", " + attribution
	}
	w.Header().Set("Cache-Control", "public, max-age=3600") // photos at a point rarely change
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"image":  img,
		"source": map[string]string{"provider": "Mapillary", "license": "CC-BY-SA-4.0", "attribution": attribution},
	})
}
