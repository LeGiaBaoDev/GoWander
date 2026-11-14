// Package geocode turns typed addresses into coordinates through a self-hosted Photon (OSM data, ODbL).
// Free option chosen by the owner on 14-11-2026; Vietmap is the paid upgrade path.
package geocode

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
	"unicode/utf8"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

var ErrUnavailable = errors.New("geocoder unavailable")

// Query: Lat/Lon (both non-zero) bias results towards the user; Limit 1..10.
type Query struct {
	Text     string
	Lat, Lon float64
	Limit    int
}

// Result is one address or named place. Ref is the OSM object ("node/1", "way/2").
type Result struct {
	Name   string  `json:"name"`
	Detail string  `json:"detail,omitempty"`
	Lat    float64 `json:"lat"`
	Lon    float64 `json:"lon"`
	Ref    string  `json:"ref,omitempty"`
	// Approximate: the user typed a house number but this result has none (OSM often lacks Vietnamese house numbers).
	Approximate bool `json:"approximate,omitempty"`
}

type Searcher interface {
	Search(ctx context.Context, q Query) ([]Result, error)
	Reverse(ctx context.Context, lat, lon float64, limit int) ([]Result, error)
}

// shortcut: results are limited to Ho Chi Minh City (the pilot city), so a Vietnamese street name never jumps to Hanoi;
// make this a per-city setting when GoWander covers more than one city.
const pilotBBox = "106.35,10.35,107.05,11.2" // minLon,minLat,maxLon,maxLat (Photon bbox)

type Client struct {
	baseURL string
	timeout time.Duration
	http    *http.Client
}

func New(baseURL string, timeout time.Duration) *Client {
	return &Client{baseURL: strings.TrimRight(baseURL, "/"), timeout: timeout, http: &http.Client{
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}}
}

type photonAnswer struct {
	Features []struct {
		Geometry struct {
			Coordinates []float64 `json:"coordinates"`
		} `json:"geometry"`
		Properties struct {
			OSMType     string `json:"osm_type"`
			OSMID       int64  `json:"osm_id"`
			Name        string `json:"name"`
			HouseNumber string `json:"housenumber"`
			Street      string `json:"street"`
			District    string `json:"district"`
			City        string `json:"city"`
		} `json:"properties"`
	} `json:"features"`
}

func (c *Client) Search(ctx context.Context, q Query) ([]Result, error) {
	v := url.Values{"q": {q.Text}, "limit": {strconv.Itoa(q.Limit)}, "bbox": {pilotBBox}}
	if q.Lat != 0 || q.Lon != 0 {
		v.Set("lat", strconv.FormatFloat(q.Lat, 'f', -1, 64))
		v.Set("lon", strconv.FormatFloat(q.Lon, 'f', -1, 64))
	}
	return c.get(ctx, "/api", v, strings.ContainsAny(q.Text, "0123456789"))
}

// Reverse returns the address/places at a point (M4 "chọn trên bản đồ" suggestions).
func (c *Client) Reverse(ctx context.Context, lat, lon float64, limit int) ([]Result, error) {
	v := url.Values{"lat": {strconv.FormatFloat(lat, 'f', -1, 64)}, "lon": {strconv.FormatFloat(lon, 'f', -1, 64)}, "limit": {strconv.Itoa(limit)}}
	return c.get(ctx, "/reverse", v, false)
}

func (c *Client) get(ctx context.Context, path string, v url.Values, wantNumber bool) ([]Result, error) {
	ctx, cancel := context.WithTimeout(ctx, c.timeout)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path+"?"+v.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrUnavailable, err)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		// *url.Error repeats the URL, which holds the user's query (maybe a home address): keep it out of logs.
		var ue *url.Error
		if errors.As(err, &ue) {
			err = ue.Err
		}
		return nil, fmt.Errorf("%w: %v", ErrUnavailable, err)
	}
	defer func() { _, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10)); _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%w: photon returned HTTP %d", ErrUnavailable, resp.StatusCode)
	}
	var a photonAnswer
	if err := json.NewDecoder(io.LimitReader(resp.Body, 2<<20)).Decode(&a); err != nil {
		return nil, fmt.Errorf("%w: decode: %v", ErrUnavailable, err)
	}
	out := make([]Result, 0, len(a.Features))
	for _, f := range a.Features {
		p := f.Properties
		street := strings.TrimSpace(strings.Join(nonEmpty(p.HouseNumber, p.Street), " "))
		name := p.Name
		if name == "" {
			name = street // a bare address: "770 Tỉnh lộ 15"
		}
		if name == "" || len(f.Geometry.Coordinates) != 2 {
			continue // nothing a user can recognise
		}
		detailParts := nonEmpty(p.District, p.City)
		if p.Name != "" && street != "" {
			detailParts = append([]string{street}, detailParts...)
		}
		ref := ""
		if kind := map[string]string{"N": "node", "W": "way", "R": "relation"}[p.OSMType]; kind != "" {
			ref = fmt.Sprintf("%s/%d", kind, p.OSMID)
		}
		out = append(out, Result{
			Name: name, Detail: strings.Join(detailParts, ", "), Lat: f.Geometry.Coordinates[1], Lon: f.Geometry.Coordinates[0],
			Ref: ref, Approximate: wantNumber && p.HouseNumber == "",
		})
	}
	return out, nil
}

func nonEmpty(s ...string) []string {
	out := s[:0:0]
	for _, x := range s {
		if strings.TrimSpace(x) != "" {
			out = append(out, x)
		}
	}
	return out
}

// Handler serves GET /api/v1/geocode?q=&lat=&lon=&limit=.
type Handler struct {
	Geocoder Searcher
	Log      *slog.Logger
}

func (h Handler) Mount(r chi.Router) {
	r.Get("/api/v1/geocode", h.search)
	r.Get("/api/v1/geocode/reverse", h.reverse)
}

func (h Handler) reverse(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	lat, err1 := strconv.ParseFloat(v.Get("lat"), 64)
	lon, err2 := strconv.ParseFloat(v.Get("lon"), 64)
	if err1 != nil || err2 != nil || math.IsNaN(lat) || math.IsNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180 {
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "lat và lon là bắt buộc và phải hợp lệ.")
		return
	}
	results, err := h.Geocoder.Reverse(r.Context(), lat, lon, 3)
	h.respond(w, r, results, err)
}

func (h Handler) search(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	q := Query{Text: strings.TrimSpace(v.Get("q")), Limit: 5}
	bad := func(msg string) { httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", msg) }
	if n := utf8.RuneCountInString(q.Text); !utf8.ValidString(q.Text) || strings.ContainsRune(q.Text, 0) || n < 2 || n > 200 {
		bad("q phải là văn bản UTF-8 từ 2 đến 200 ký tự.")
		return
	}
	if s := v.Get("limit"); s != "" {
		n, err := strconv.Atoi(s)
		if err != nil || n < 1 || n > 10 {
			bad("limit phải là số nguyên từ 1 đến 10.")
			return
		}
		q.Limit = n
	}
	if v.Has("lat") || v.Has("lon") {
		lat, err1 := strconv.ParseFloat(v.Get("lat"), 64)
		lon, err2 := strconv.ParseFloat(v.Get("lon"), 64)
		if err1 != nil || err2 != nil || math.IsNaN(lat) || math.IsNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180 {
			bad("lat/lon không hợp lệ.")
			return
		}
		q.Lat, q.Lon = lat, lon
	}
	results, err := h.Geocoder.Search(r.Context(), q)
	h.respond(w, r, results, err)
}

func (h Handler) respond(w http.ResponseWriter, r *http.Request, results []Result, err error) {
	if err != nil {
		if r.Context().Err() != nil {
			w.WriteHeader(499)
			return
		}
		h.Log.Warn("geocoder failed", "requestId", httpx.RequestID(r), "err", err)
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "GEOCODER_UNAVAILABLE", "Tìm địa chỉ tạm thời không hoạt động, vui lòng thử lại sau.")
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{
		"results": results,
		// OSM data shown to users carries its attribution (ADR-028 rule).
		"source": map[string]string{"provider": "Photon", "attribution": "© OpenStreetMap contributors", "license": "ODbL-1.0"},
	})
}
