package discover

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"
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
	r.Get("/api/v1/places/{id}", h.place)
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
