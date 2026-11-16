package discover

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
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

// Handler serves the read-only Discover endpoints (openapi.yaml, ADR-028 §5).
type Handler struct {
	Store Store
	Log   *slog.Logger
}

func (h Handler) Mount(r chi.Router) {
	r.Get("/api/v1/place-categories", h.categories)
	r.Get("/api/v1/places", h.list)
	r.Get("/api/v1/places/nearby", h.nearby)
	r.Get("/api/v1/places/map", h.inBounds)
	r.Get("/api/v1/places/map/clusters", h.clusters)
	r.Get("/api/v1/places/{id}", h.place)
	r.Get("/api/v1/guide/hcm", h.guide)
}

type badRequest string

func (h Handler) categories(w http.ResponseWriter, r *http.Request) {
	cats, err := h.Store.Categories(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"categories": nonNil(cats)})
}

func (h Handler) list(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	q := ListQuery{Category: v.Get("category"), Q: strings.TrimSpace(v.Get("q"))}
	err := textParam(q.Category, "category", 0, 100)
	if err == nil && v.Has("q") {
		err = textParam(q.Q, "q", 2, 100) // 1 character cannot use the trigram index
	}
	if err == nil {
		q.Limit, err = intParam(v.Get("limit"), "limit", 20, 1, 50)
	}
	if err == nil {
		q.After, err = decodeCursor(v.Get("cursor"))
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	places, next, err := h.Store.ListPlaces(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	body := map[string]any{"places": nonNil(places)}
	if next != nil {
		body["nextCursor"] = encodeCursor(*next)
	}
	httpx.WriteJSON(w, http.StatusOK, body)
}

func (h Handler) nearby(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	q := NearbyQuery{Category: v.Get("category")}
	err := textParam(q.Category, "category", 0, 100)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	if q.Lat, err = floatParam(v.Get("lat"), "lat", -90, 90); err == nil {
		if q.Lon, err = floatParam(v.Get("lon"), "lon", -180, 180); err == nil {
			if q.RadiusM, err = intParam(v.Get("radiusM"), "radiusM", 1000, 1, 5000); err == nil {
				q.Limit, err = intParam(v.Get("limit"), "limit", 20, 1, 50)
			}
		}
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	places, err := h.Store.Nearby(r.Context(), q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"places": nonNil(places)})
}

// mapBox reads the viewport and category filter shared by the map endpoints.
const maxBoxDeg = 20 // Vietnam is about 15 degrees tall

// mapQueryTimeout bounds one map query so a burst of requests cannot hold every pool connection.
const mapQueryTimeout = 3 * time.Second

func mapBox(v url.Values) (MapQuery, error) {
	var q MapQuery
	var err error
	for _, p := range []struct {
		dst  *float64
		name string
		max  float64
	}{{&q.South, "south", 90}, {&q.North, "north", 90}, {&q.West, "west", 180}, {&q.East, "east", 180}} {
		if err == nil {
			*p.dst, err = floatParam(v.Get(p.name), p.name, -p.max, p.max)
		}
	}
	if err == nil && (q.South >= q.North || q.West >= q.East) {
		err = badRequest("south phải nhỏ hơn north và west phải nhỏ hơn east.")
	}
	if s := v.Get("categories"); err == nil && s != "" {
		if q.Categories = strings.Split(s, ","); len(q.Categories) > 10 {
			err = badRequest("categories có tối đa 10 mục, cách nhau bằng dấu phẩy.")
		}
		for i, c := range q.Categories {
			q.Categories[i] = strings.TrimSpace(c) // "food, cafe" means food and cafe
			if err == nil {
				err = textParam(q.Categories[i], "categories", 1, 100)
			}
		}
	}
	// A box larger than this is a whole region, not a map screen: refuse it rather than scan everything (review 15-11-2026).
	if err == nil && (q.North-q.South > maxBoxDeg || q.East-q.West > maxBoxDeg) {
		err = badRequest("Khung bản đồ quá lớn: mỗi chiều tối đa " + strconv.Itoa(maxBoxDeg) + " độ.")
	}
	return q, err
}

func (h Handler) inBounds(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	q, err := mapBox(v)
	if err == nil {
		q.Limit, err = intParam(v.Get("limit"), "limit", 50, 1, 100)
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), mapQueryTimeout)
	defer cancel()
	places, err := h.Store.InBounds(ctx, q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"places": nonNil(places)})
}

func (h Handler) clusters(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query()
	box, err := mapBox(v)
	q := ClusterQuery{MapQuery: box}
	if err == nil {
		q.Cols, err = intParam(v.Get("cols"), "cols", 4, 1, 12)
	}
	if err == nil {
		q.Rows, err = intParam(v.Get("rows"), "rows", 6, 1, 12)
	}
	if err != nil {
		h.fail(w, r, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), mapQueryTimeout)
	defer cancel()
	cs, err := h.Store.Clusters(ctx, q)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]any{"clusters": nonNil(cs)})
}

func (h Handler) place(w http.ResponseWriter, r *http.Request) {
	id := strings.ToLower(chi.URLParam(r, "id"))
	if !isUUID(id) {
		h.fail(w, r, badRequest("id phải là UUID."))
		return
	}
	d, err := h.Store.Place(r.Context(), id)
	if err != nil {
		h.fail(w, r, err)
		return
	}
	d.Categories, d.Media, d.Foods = nonNil(d.Categories), nonNil(d.Media), nonNil(d.Foods)
	httpx.WriteJSON(w, http.StatusOK, d)
}

func (b badRequest) Error() string { return string(b) }

func (h Handler) fail(w http.ResponseWriter, r *http.Request, err error) {
	var bad badRequest
	switch {
	case errors.As(err, &bad):
		httpx.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", string(bad))
	case r.Context().Err() != nil:
		h.Log.Info("discover request cancelled by client", "requestId", httpx.RequestID(r), "err", err)
		w.WriteHeader(499) // client closed request (nginx convention): keeps cancelled requests out of success metrics
	case errors.Is(err, ErrNotFound):
		httpx.WriteError(w, r, http.StatusNotFound, "PLACE_NOT_FOUND", "Không tìm thấy địa điểm.")
	default:
		h.Log.Warn("discover store failed", "requestId", httpx.RequestID(r), "path", r.URL.Path, "err", err)
		httpx.WriteError(w, r, http.StatusServiceUnavailable, "DATA_STORE_UNAVAILABLE", "Dữ liệu địa điểm tạm thời không truy cập được, vui lòng thử lại sau.")
	}
}

// textParam rejects text PostgreSQL would refuse (invalid UTF-8, NUL) so it is a 400, not a 503.
func textParam(s, name string, minRunes, maxRunes int) error {
	n := utf8.RuneCountInString(s)
	if !utf8.ValidString(s) || strings.ContainsRune(s, 0) || n < minRunes || n > maxRunes {
		return badRequest(name + " phải là văn bản UTF-8 từ " + strconv.Itoa(minRunes) + " đến " + strconv.Itoa(maxRunes) + " ký tự.")
	}
	return nil
}

func intParam(s, name string, def, min, max int) (int, error) {
	if s == "" {
		return def, nil
	}
	n, err := strconv.Atoi(s)
	if err != nil || n < min || n > max {
		return 0, badRequest(name + " phải là số nguyên từ " + strconv.Itoa(min) + " đến " + strconv.Itoa(max) + ".")
	}
	return n, nil
}

func floatParam(s, name string, min, max float64) (float64, error) {
	f, err := strconv.ParseFloat(s, 64)
	if err != nil || math.IsNaN(f) || f < min || f > max {
		return 0, badRequest(name + " là bắt buộc và phải trong khoảng " + strconv.FormatFloat(min, 'f', -1, 64) + " đến " + strconv.FormatFloat(max, 'f', -1, 64) + ".")
	}
	return f, nil
}

func encodeCursor(c Cursor) string {
	b, _ := json.Marshal(c) // two strings: cannot fail
	return base64.RawURLEncoding.EncodeToString(b)
}

func decodeCursor(s string) (*Cursor, error) {
	if s == "" {
		return nil, nil
	}
	var c Cursor
	b, err := base64.RawURLEncoding.DecodeString(s)
	if err != nil || json.Unmarshal(b, &c) != nil || !isUUID(c.ID) || textParam(c.Name, "cursor", 1, 200) != nil {
		return nil, badRequest("cursor không hợp lệ.")
	}
	return &c, nil
}

func isUUID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i, r := range s {
		switch i {
		case 8, 13, 18, 23:
			if r != '-' {
				return false
			}
		default:
			if !strings.ContainsRune("0123456789abcdef", r) {
				return false
			}
		}
	}
	return true
}

// nonNil keeps empty lists as [] in JSON instead of null.
func nonNil[T any](s []T) []T {
	if s == nil {
		return []T{}
	}
	return s
}
