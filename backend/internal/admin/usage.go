package admin

import (
	"context"
	"fmt"
	"slices"
	"time"

	"github.com/jackc/pgx/v5"
)

// MinShown: a ranked item needs at least this many searches in the period, and smaller numbers show as "<5", so one
// person's searches cannot be picked out (ADR-038 §5).
const MinShown = 5

var modeNames = map[string]string{"WALK": "Đi bộ", "TRANSIT": "Phương tiện công cộng", "BUS": "Xe buýt", "CAR": "Ô tô", "BICYCLE": "Xe đạp"}

var hcm = time.FixedZone("ICT", 7*3600) // the counters' days are Ho Chi Minh City days

// UsageQuery: the current period of Grain (against the one before), optionally only searches starting in Area.
type UsageQuery struct {
	Grain Grain
	Area  string
}

type Area struct{ Code, Name string }

// Usage is the "Khách dùng app" part of the overview. Ranked lists already leave out items under MinShown.
type Usage struct {
	UsageQuery
	Sample                       bool // generated sample numbers (mock.go), shown with a "Dữ liệu mẫu" badge
	AreaName                     string
	Areas                        []Area // provinces, for the filter
	Opens, PrevOpens             int    // app starts in the period and in the period before it, whole country (no location)
	OpensSeries, PrevOpensSeries []bar
	Total, PrevTotal             int // route searches, this period and the one before (same origin filter)
	Series, PrevSeries           []bar
	Modes, Lines                 []bar
	From, To, Places             []bar          // only without an area filter: FROM and TO are counted separately (ADR-038 §3)
	AreaCounts                   map[string]int // searches by departure province for the map; provinces under MinShown left out
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
	rows, err := s.Pool.Query(ctx, `SELECT code, name FROM admin_areas ORDER BY name`)
	if err == nil {
		u.Areas, err = pgx.CollectRows(rows, pgx.RowToStructByPos[Area])
	}
	if err != nil {
		return u, fmt.Errorf("areas: %w", err)
	}
	if i := slices.IndexFunc(u.Areas, func(a Area) bool { return a.Code == q.Area }); i >= 0 {
		u.AreaName = u.Areas[i].Name
	} else {
		u.Area = ""
	}
	opens, err := daily(`SELECT day, sum(opens)::int FROM usage_app_opens WHERE day BETWEEN $1::date AND $2::date GROUP BY day`, prevFrom, to)
	if err != nil {
		return u, fmt.Errorf("app opens: %w", err)
	}
	// Origin side only: each search counts once.
	searches, err := daily(`SELECT day, sum(plans)::int FROM usage_route_plans WHERE day BETWEEN $1::date AND $2::date AND side = 'FROM'
		AND ($3 = '' OR area_code = $3) GROUP BY day`, prevFrom, to, u.Area)
	if err != nil {
		return u, fmt.Errorf("searches: %w", err)
	}
	u.OpensSeries, u.PrevOpensSeries = q.Grain.buckets(opens, now)
	u.Series, u.PrevSeries = q.Grain.buckets(searches, now)
	u.Opens, _ = sum(u.OpensSeries)
	u.PrevOpens, _ = sum(u.PrevOpensSeries)
	u.Total, _ = sum(u.Series)
	u.PrevTotal, _ = sum(u.PrevSeries)
	if u.Modes, err = bars(`SELECT mode, sum(plans)::int FROM usage_route_plans
		WHERE day BETWEEN $1::date AND $2::date AND side = 'FROM' AND ($3 = '' OR area_code = $3) GROUP BY mode ORDER BY 2 DESC, 1`, from, to, u.Area); err != nil {
		return u, fmt.Errorf("modes: %w", err)
	}
	for i := range u.Modes {
		u.Modes[i].Label = modeNames[u.Modes[i].Label]
	}
	if u.Lines, err = bars(`SELECT max(line_name), sum(suggested)::int FROM usage_bus_lines
		WHERE day BETWEEN $1::date AND $2::date AND ($3 = '' OR area_code = $3)
		GROUP BY line_id HAVING sum(suggested) >= $4 ORDER BY 2 DESC, 1 LIMIT 10`, from, to, u.Area, MinShown); err != nil {
		return u, fmt.Errorf("lines: %w", err)
	}
	u.AreaCounts = map[string]int{}
	rows, err = s.Pool.Query(ctx, `SELECT area_code, sum(plans)::int FROM usage_route_plans
		WHERE day BETWEEN $1::date AND $2::date AND side = 'FROM' AND area_code <> '' GROUP BY 1 HAVING sum(plans) >= $3`, from, to, MinShown)
	if err != nil {
		return u, fmt.Errorf("area counts: %w", err)
	}
	for rows.Next() {
		var code string
		var n int
		if err := rows.Scan(&code, &n); err != nil {
			rows.Close()
			return u, fmt.Errorf("area counts: %w", err)
		}
		u.AreaCounts[code] = n
	}
	if err := rows.Err(); err != nil {
		return u, fmt.Errorf("area counts: %w", err)
	}
	if u.Area != "" {
		return u, nil
	}
	for side, dst := range map[string]*[]bar{"FROM": &u.From, "TO": &u.To} {
		if *dst, err = bars(`SELECT coalesce(a.name, 'Chưa rõ tỉnh, thành'), sum(u.plans)::int
			FROM usage_route_plans u LEFT JOIN admin_areas a ON a.code = u.area_code
			WHERE u.day BETWEEN $1::date AND $2::date AND u.side = $3
			GROUP BY 1 HAVING sum(u.plans) >= $4 ORDER BY 2 DESC, 1 LIMIT 10`, from, to, side, MinShown); err != nil {
			return u, fmt.Errorf("areas %s: %w", side, err)
		}
	}
	// "Điểm đến được tìm nhiều nhất": the place name with its category (a sight, a restaurant ...).
	if u.Places, err = bars(`SELECT p.name || coalesce(' · ' || c.name, ''), sum(u.plans)::int
		FROM usage_place_routes u JOIN places p ON p.id = u.place_id LEFT JOIN place_categories c ON c.id = p.primary_category_id
		WHERE u.day BETWEEN $1::date AND $2::date GROUP BY p.id, p.name, c.name HAVING sum(u.plans) >= $3 ORDER BY 2 DESC, 1 LIMIT 10`, from, to, MinShown); err != nil {
		return u, fmt.Errorf("places: %w", err)
	}
	return u, nil
}

// AreaShape is one province drawn on the map: an SVG path in degrees (y = -latitude) and a point inside it for labels.
type AreaShape struct {
	Code, Name, Path string
	X, Y             float64
}

// AreaShapes are read once and kept: the provinces only change when vn-provinces.sql is reloaded (restart the API).
// Simplified to ~2 km, which is invisible at dashboard size and keeps the page small.
func (s *PG) AreaShapes(ctx context.Context) ([]AreaShape, error) {
	s.shapesMu.Lock()
	defer s.shapesMu.Unlock()
	if s.shapes != nil {
		return s.shapes, nil
	}
	rows, err := s.Pool.Query(ctx, `SELECT code, name, ST_AsSVG(ST_SimplifyPreserveTopology(geom, 0.02), 0, 3),
		ST_X(ST_PointOnSurface(geom)), -ST_Y(ST_PointOnSurface(geom)) FROM admin_areas ORDER BY name`)
	if err != nil {
		return nil, fmt.Errorf("area shapes: %w", err)
	}
	shapes, err := pgx.CollectRows(rows, pgx.RowToStructByPos[AreaShape])
	if err != nil {
		return nil, fmt.Errorf("area shapes: %w", err)
	}
	if len(shapes) > 0 { // an empty table (seed not loaded yet) is read again next time
		s.shapes = shapes
	}
	return shapes, nil
}
