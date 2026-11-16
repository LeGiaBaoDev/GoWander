package admin

import (
	"context"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

// MinShown: a ranked item needs at least this many searches in the period, and smaller numbers show as "<5", so one
// person's searches cannot be picked out (ADR-038 §5).
const MinShown = 5

var modeNames = map[string]string{"WALK": "Đi bộ", "TRANSIT": "Phương tiện công cộng", "BUS": "Xe buýt", "CAR": "Ô tô", "BICYCLE": "Xe đạp"}

var hcm = time.FixedZone("ICT", 7*3600) // the counters' days are Ho Chi Minh City days

// UsageQuery: the current period of Grain (against the one before), optionally one bus line (line_id).
// Province statistics were dropped (owner, 16-11-2026: "bỏ đi mà thống kê theo tuyến xe buýt").
type UsageQuery struct {
	Grain Grain
	Line  string
	Kind  string // "", BUS, METRO or BOAT: which vehicles the line ranking shows (reports page)
}

// Vehicles are the kinds of the line ranking's tabs.
var Vehicles = []struct{ Key, Name string }{{"", "Tất cả"}, {"BUS", "Xe buýt"}, {"METRO", "Metro"}, {"BOAT", "Buýt sông"}}

// BusLine is one bus or metro line: the filter list and the ranking (Value = times it was in a suggested route).
type BusLine struct {
	ID, Name, Vehicle string
	Value             int
}

// Title is how the Admin names a line: "Tuyến 19", "Metro số 1", "Buýt sông 1".
func (l BusLine) Title() string {
	low := strings.ToLower(l.Name)
	switch {
	case l.Vehicle == "METRO" && !strings.Contains(low, "metro"):
		return "Metro " + l.Name
	case l.Vehicle == "BOAT" && !strings.Contains(low, "sông"):
		return "Buýt sông " + l.Name
	case l.Vehicle == "BUS" && !strings.Contains(low, "tuyến"):
		return "Tuyến " + l.Name
	}
	return l.Name
}

// LineTitle names the filtered line ("" without a filter).
func (u Usage) LineTitle() string {
	for _, l := range u.LineOptions {
		if l.ID == u.Line {
			return l.Title()
		}
	}
	return ""
}

// CellDeg is the destination grid of the counters (analytics.CellDeg): ~500 m squares.
const CellDeg = 0.0045

// WardCount: searches ending in a ward of Ho Chi Minh City (destination cells grouped by the ward holding their
// centre, so a ward's edge is right to ~250 m) and the published places inside it.
type WardCount struct {
	Code, Name    string
	Plans, Places int
}

// WardShape is one ward drawn on the map: an SVG path in degrees (y = -latitude), its box and a point inside it.
type WardShape struct {
	Code, Name, Path       string
	X, Y                   float64
	MinX, MinY, MaxX, MaxY float64
}

// Usage is the "Khách dùng app" part of the overview. Ranked lists already leave out items under MinShown.
type Usage struct {
	UsageQuery
	Sample                       bool // generated sample numbers (mock.go), shown with a "Dữ liệu mẫu" badge
	LineName                     string
	LineOptions                  []BusLine // every line seen in the counters, for the filter
	Opens, PrevOpens             int       // app starts in the period and the one before
	OpensSeries, PrevOpensSeries []bar
	Total, PrevTotal             int // route searches; with a line: times that line was suggested
	Series, PrevSeries           []bar
	Modes, Places                []bar       // only without a line filter
	Lines                        []BusLine   // top lines (of Kind), only without a line filter
	Wards                        []WardCount // HCMC wards with at least MinShown searches ending there, busiest first
}

func (s *PG) Usage(ctx context.Context, q UsageQuery) (Usage, error) {
	u := Usage{UsageQuery: q}
	now := time.Now()
	fromDay, prevFromDay := q.Grain.window(now)
	from, to, prevFrom := fromDay.Format(time.DateOnly), now.In(hcm).Format(time.DateOnly), prevFromDay.Format(time.DateOnly)
	bars := func(sql string, args ...any) ([]bar, error) {
		rows, err := s.Pool.Query(ctx, sql, args...)
		if err != nil {
			return nil, err
		}
		return pgx.CollectRows(rows, pgx.RowToStructByPos[bar])
	}
	// daily reads one value per day over both periods; the grain groups them in Go.
	daily := func(sql string, args ...any) (map[time.Time]int, error) {
		rows, err := s.Pool.Query(ctx, sql, args...)
		if err != nil {
			return nil, err
		}
		out := map[time.Time]int{}
		for rows.Next() {
			var d time.Time
			var v int
			if err := rows.Scan(&d, &v); err != nil {
				rows.Close()
				return nil, err
			}
			out[time.Date(d.Year(), d.Month(), d.Day(), 0, 0, 0, 0, hcm)] = v
		}
		return out, rows.Err()
	}
	// Line names sort naturally: "8" before "19" before "150", letters (metro L1) after.
	rows, err := s.Pool.Query(ctx, `SELECT line_id, max(line_name), max(vehicle), 0 FROM usage_bus_lines GROUP BY line_id
		ORDER BY max(vehicle), max(line_name) !~ '^[0-9]+$', length(max(line_name)), max(line_name)`)
	if err == nil {
		u.LineOptions, err = pgx.CollectRows(rows, pgx.RowToStructByPos[BusLine])
	}
	if err != nil {
		return u, fmt.Errorf("line options: %w", err)
	}
	if i := slices.IndexFunc(u.LineOptions, func(l BusLine) bool { return l.ID == q.Line }); i >= 0 {
		u.LineName = u.LineOptions[i].Name
	} else {
		u.Line = ""
	}
	opens, err := daily(`SELECT day, sum(opens)::int FROM usage_app_opens WHERE day BETWEEN $1::date AND $2::date GROUP BY day`, prevFrom, to)
	if err != nil {
		return u, fmt.Errorf("app opens: %w", err)
	}
	u.OpensSeries, u.PrevOpensSeries = q.Grain.buckets(opens, now)
	u.Opens, _ = sum(u.OpensSeries)
	u.PrevOpens, _ = sum(u.PrevOpensSeries)
	if u.Line != "" {
		suggested, err := daily(`SELECT day, sum(suggested)::int FROM usage_bus_lines WHERE day BETWEEN $1::date AND $2::date AND line_id = $3 GROUP BY day`, prevFrom, to, u.Line)
		if err != nil {
			return u, fmt.Errorf("line: %w", err)
		}
		u.Series, u.PrevSeries = q.Grain.buckets(suggested, now)
		u.Total, _ = sum(u.Series)
		u.PrevTotal, _ = sum(u.PrevSeries)
		return u, nil
	}
	// Origin side only: each search counts once.
	searches, err := daily(`SELECT day, sum(plans)::int FROM usage_route_plans WHERE day BETWEEN $1::date AND $2::date AND side = 'FROM' GROUP BY day`, prevFrom, to)
	if err != nil {
		return u, fmt.Errorf("searches: %w", err)
	}
	u.Series, u.PrevSeries = q.Grain.buckets(searches, now)
	u.Total, _ = sum(u.Series)
	u.PrevTotal, _ = sum(u.PrevSeries)
	if u.Modes, err = bars(`SELECT mode, sum(plans)::int FROM usage_route_plans
		WHERE day BETWEEN $1::date AND $2::date AND side = 'FROM' GROUP BY mode ORDER BY 2 DESC, 1`, from, to); err != nil {
		return u, fmt.Errorf("modes: %w", err)
	}
	for i := range u.Modes {
		u.Modes[i].Label = modeNames[u.Modes[i].Label]
	}
	rows, err = s.Pool.Query(ctx, `SELECT line_id, max(line_name), max(vehicle), sum(suggested)::int FROM usage_bus_lines
		WHERE day BETWEEN $1::date AND $2::date AND ($4 = '' OR vehicle = $4)
		GROUP BY line_id HAVING sum(suggested) >= $3 ORDER BY 4 DESC, 2 LIMIT 10`, from, to, MinShown, q.Kind)
	if err == nil {
		u.Lines, err = pgx.CollectRows(rows, pgx.RowToStructByPos[BusLine])
	}
	if err != nil {
		return u, fmt.Errorf("lines: %w", err)
	}
	// "Điểm đến được tìm nhiều nhất": the place name with its category (a sight, a restaurant ...).
	if u.Places, err = bars(`SELECT p.name || coalesce(' · ' || c.name, ''), sum(u.plans)::int
		FROM usage_place_routes u JOIN places p ON p.id = u.place_id LEFT JOIN place_categories c ON c.id = p.primary_category_id
		WHERE u.day BETWEEN $1::date AND $2::date GROUP BY p.id, p.name, c.name HAVING sum(u.plans) >= $3 ORDER BY 2 DESC, 1 LIMIT 10`, from, to, MinShown); err != nil {
		return u, fmt.Errorf("places: %w", err)
	}
	// Wards: destination cells grouped by the ward holding their centre; places counted by the ward they stand in.
	rows, err = s.Pool.Query(ctx, `
		WITH c AS (SELECT lat_i, lon_i, sum(plans)::int AS n FROM usage_dest_cells WHERE day BETWEEN $1::date AND $2::date GROUP BY 1, 2),
		     w AS (SELECT w.code, w.name, sum(c.n)::int AS n FROM c JOIN city_wards w
		             ON ST_Contains(w.geom, ST_SetSRID(ST_MakePoint((c.lon_i + .5) * $4, (c.lat_i + .5) * $4), 4326))
		            GROUP BY 1, 2 HAVING sum(c.n) >= $3)
		SELECT w.code, w.name, w.n, (SELECT count(*)::int FROM places p JOIN city_wards x ON x.code = w.code
		         WHERE p.status = 'PUBLISHED' AND ST_Contains(x.geom, p.location::geometry))
		  FROM w ORDER BY w.n DESC, w.name`, from, to, MinShown, CellDeg)
	if err == nil {
		u.Wards, err = pgx.CollectRows(rows, pgx.RowToStructByPos[WardCount])
	}
	if err != nil {
		return u, fmt.Errorf("wards: %w", err)
	}
	return u, nil
}

// WardShapes are read once and kept (they change only when hcm-wards.sql is reloaded: restart the API). Simplified
// to ~50 m, invisible at dashboard size. An empty table (seed not loaded) is read again next time.
func (s *PG) WardShapes(ctx context.Context) ([]WardShape, error) {
	s.wardsMu.Lock()
	defer s.wardsMu.Unlock()
	if s.wards != nil {
		return s.wards, nil
	}
	rows, err := s.Pool.Query(ctx, `SELECT code, name, ST_AsSVG(g, 0, 4), ST_X(p), -ST_Y(p), ST_XMin(g), -ST_YMax(g), ST_XMax(g), -ST_YMin(g)
		FROM (SELECT code, name, ST_SimplifyPreserveTopology(geom, 0.0005) AS g, ST_PointOnSurface(geom) AS p FROM city_wards) w ORDER BY name`)
	if err != nil {
		return nil, fmt.Errorf("ward shapes: %w", err)
	}
	shapes, err := pgx.CollectRows(rows, pgx.RowToStructByPos[WardShape])
	if err != nil {
		return nil, fmt.Errorf("ward shapes: %w", err)
	}
	if len(shapes) > 0 {
		s.wards = shapes
	}
	return shapes, nil
}
