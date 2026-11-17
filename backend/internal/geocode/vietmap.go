package geocode

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Vietmap is the paid geocoder (Maps API v4). Search v4 returns ref_ids without coordinates, so each
// result needs one Place v4 call; Reverse v4 returns coordinates directly. Cost: 1 + N transactions
// per Search (N = results requested), 1 per Reverse.
type Vietmap struct {
	baseURL string
	apiKey  string
	timeout time.Duration
	http    *http.Client
}

const vietmapMaxResults = 10

func NewVietmap(baseURL, apiKey string, timeout time.Duration) *Vietmap {
	return &Vietmap{
		baseURL: strings.TrimRight(baseURL, "/"),
		apiKey:  apiKey,
		timeout: timeout,
		http: &http.Client{
			// The key is in the query string: never follow a redirect to another host.
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		},
	}
}

type vmSearchHit struct {
	RefID   string `json:"ref_id"`
	Name    string `json:"name"`
	Display string `json:"display"`
	Address string `json:"address"`
}

type vmPlace struct {
	Name     string  `json:"name"`
	Display  string  `json:"display"`
	Address  string  `json:"address"`
	HsNum    string  `json:"hs_num"`
	Street   string  `json:"street"`
	Ward     string  `json:"ward"`
	District string  `json:"district"`
	City     string  `json:"city"`
	Lat      float64 `json:"lat"`
	Lng      float64 `json:"lng"`
}

type vmReverse struct {
	RefID   string  `json:"ref_id"`
	Name    string  `json:"name"`
	Display string  `json:"display"`
	Address string  `json:"address"`
	Lat     float64 `json:"lat"`
	Lng     float64 `json:"lng"`
}

// Search: Search v4 for candidates, then Place v4 (parallel, at most 4 at a time) for coordinates.
func (v *Vietmap) Search(ctx context.Context, q Query) ([]Result, error) {
	limit := q.Limit
	if limit < 1 {
		limit = 5
	}
	if limit > vietmapMaxResults {
		limit = vietmapMaxResults
	}
	params := url.Values{
		"text":         {q.Text},
		"display_type": {"6"}, // new administrative format in data_new; old format still in the main fields
		"admin_new":    {"true"},
	}
	if q.Lat != 0 || q.Lon != 0 {
		params.Set("focus", coord(q.Lat)+","+coord(q.Lon))
	}
	var hits []vmSearchHit
	if err := v.get(ctx, "/search/v4", params, &hits); err != nil {
		return nil, err
	}
	if len(hits) == 0 {
		return nil, nil
	}
	if len(hits) > limit {
		hits = hits[:limit]
	}

	results := make([]Result, len(hits))
	ok := make([]bool, len(hits))
	var firstErr error
	var mu sync.Mutex
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)
	for i, h := range hits {
		wg.Add(1)
		go func(i int, h vmSearchHit) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			var p vmPlace
			if err := v.get(ctx, "/place/v4", url.Values{"refid": {h.RefID}}, &p); err != nil {
				mu.Lock()
				if firstErr == nil {
					firstErr = err
				}
				mu.Unlock()
				return
			}
			if p.Lat == 0 && p.Lng == 0 {
				return // a Place record without coordinates is useless to the map
			}
			results[i] = Result{
				Name:        firstNonEmpty(p.Name, h.Name, p.Display, h.Display),
				Detail:      firstNonEmpty(p.Address, h.Address, p.Display, h.Display),
				Lat:         p.Lat,
				Lon:         p.Lng,
				Ref:         "vietmap:" + h.RefID,
				Source:      SourceVietmap,
				Approximate: approximateHouseNumber(q.Text, p.HsNum),
			}
			ok[i] = true
		}(i, h)
	}
	wg.Wait()

	out := make([]Result, 0, len(results))
	for i := range results {
		if ok[i] {
			out = append(out, results[i])
		}
	}
	if len(out) == 0 && firstErr != nil {
		return nil, firstErr
	}
	return out, nil
}

func (v *Vietmap) Reverse(ctx context.Context, lat, lon float64, limit int) ([]Result, error) {
	if limit < 1 {
		limit = 3
	}
	params := url.Values{
		"lat":          {coord(lat)},
		"lng":          {coord(lon)},
		"display_type": {"6"},
	}
	var hits []vmReverse
	if err := v.get(ctx, "/reverse/v4", params, &hits); err != nil {
		return nil, err
	}
	if len(hits) > limit {
		hits = hits[:limit]
	}
	out := make([]Result, 0, len(hits))
	for _, h := range hits {
		name := firstNonEmpty(h.Name, h.Display)
		detail := firstNonEmpty(h.Address, h.Display)
		if name == "" && detail == "" {
			continue
		}
		out = append(out, Result{
			Name: name, Detail: detail, Lat: h.Lat, Lon: h.Lng,
			Ref: "vietmap:" + h.RefID, Source: SourceVietmap,
		})
	}
	return out, nil
}

func (v *Vietmap) get(ctx context.Context, path string, params url.Values, out any) error {
	ctx, cancel := context.WithTimeout(ctx, v.timeout)
	defer cancel()
	params.Set("apikey", v.apiKey)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, v.baseURL+path+"?"+params.Encode(), nil)
	if err != nil {
		// The URL holds the key and the user's query: never put either in the error.
		return fmt.Errorf("%w: invalid request", ErrUnavailable)
	}
	resp, err := v.http.Do(req)
	if err != nil {
		var ue *url.Error
		if errors.As(err, &ue) {
			err = ue.Err // *url.Error repeats the URL (key + query)
		}
		return fmt.Errorf("%w: %v", ErrUnavailable, err)
	}
	defer func() { _, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10)); _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		// 401 wrong key, 403 restricted, 423 API disabled/out of credit, 429 rate limit.
		return fmt.Errorf("%w: vietmap returned HTTP %d", ErrUnavailable, resp.StatusCode)
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 4<<20)).Decode(out); err != nil {
		return fmt.Errorf("%w: decode: %v", ErrUnavailable, err)
	}
	return nil
}

func coord(f float64) string { return strconv.FormatFloat(f, 'f', -1, 64) }

func firstNonEmpty(vals ...string) string {
	for _, s := range vals {
		if strings.TrimSpace(s) != "" {
			return strings.TrimSpace(s)
		}
	}
	return ""
}

var leadingHouseNumber = regexp.MustCompile(`^\s*(\d+[a-zA-Z]?(?:/\d+[a-zA-Z]?)*)`)

// approximateHouseNumber: the user typed a house number (128/4) but the provider returned another one
// (128/2a) or none. The caller should show "gần đúng" and let the user move the pin.
func approximateHouseNumber(query, got string) bool {
	m := leadingHouseNumber.FindStringSubmatch(query)
	if len(m) < 2 {
		return false
	}
	want := strings.ToLower(strings.ReplaceAll(m[1], " ", ""))
	have := strings.ToLower(strings.ReplaceAll(strings.TrimSpace(got), " ", ""))
	return have != want
}
