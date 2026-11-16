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

// UsageDays are the periods the usage page offers; the first is the default.
var UsageDays = []int{30, 7, 90}

var modeNames = map[string]string{"WALK": "Đi bộ", "TRANSIT": "Phương tiện công cộng", "BUS": "Xe buýt", "CAR": "Ô tô", "BICYCLE": "Xe đạp"}

var hcm = time.FixedZone("ICT", 7*3600) // the counters' days are Ho Chi Minh City days

// UsageQuery: the last Days days, optionally only searches starting in ward Area.
type UsageQuery struct {
	Days int
	Area string
}

type Area struct{ Code, Name string }

// Usage is the usage page. Ranked lists already leave out items under MinShown.
type Usage struct {
	UsageQuery
	AreaName         string
	Areas            []Area
	Total, DailyMax  int
	Daily, Modes     []bar
	Lines            []bar
	From, To, Places []bar // only without an area filter: FROM and TO are counted separately (ADR-038 §3)
}

func (s *PG) Usage(ctx context.Context, q UsageQuery) (Usage, error) {
	u := Usage{UsageQuery: q}
	today := time.Now().In(hcm)
	from, to := today.AddDate(0, 0, 1-q.Days).Format(time.DateOnly), today.Format(time.DateOnly)
	bars := func(sql string, args ...any) ([]bar, error) {
		rows, err := s.Pool.Query(ctx, sql, args...)
		if err != nil {
			return nil, err
		}
		return pgx.CollectRows(rows, pgx.RowToStructByPos[bar])
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
	// Origin side only: each search counts once.
	if u.Daily, err = bars(`SELECT to_char(d, 'DD-MM'), coalesce(sum(u.plans), 0)::int
		FROM generate_series($1::date, $2::date, interval '1 day') d
		LEFT JOIN usage_route_plans u ON u.day = d::date AND u.side = 'FROM' AND ($3 = '' OR u.area_code = $3)
		GROUP BY d ORDER BY d`, from, to, u.Area); err != nil {
		return u, fmt.Errorf("daily usage: %w", err)
	}
	for _, d := range u.Daily {
		u.Total += d.Value
		u.DailyMax = max(u.DailyMax, d.Value)
	}
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
	if u.Area != "" {
		return u, nil
	}
	for side, dst := range map[string]*[]bar{"FROM": &u.From, "TO": &u.To} {
		if *dst, err = bars(`SELECT coalesce(a.name, 'Ngoài TP.HCM hoặc chưa rõ phường'), sum(u.plans)::int
			FROM usage_route_plans u LEFT JOIN admin_areas a ON a.code = u.area_code
			WHERE u.day BETWEEN $1::date AND $2::date AND u.side = $3
			GROUP BY 1 HAVING sum(u.plans) >= $4 ORDER BY 2 DESC, 1 LIMIT 10`, from, to, side, MinShown); err != nil {
			return u, fmt.Errorf("areas %s: %w", side, err)
		}
	}
	if u.Places, err = bars(`SELECT p.name, sum(u.plans)::int FROM usage_place_routes u JOIN places p ON p.id = u.place_id
		WHERE u.day BETWEEN $1::date AND $2::date GROUP BY p.id, p.name HAVING sum(u.plans) >= $3 ORDER BY 2 DESC, 1 LIMIT 10`, from, to, MinShown); err != nil {
		return u, fmt.Errorf("places: %w", err)
	}
	return u, nil
}
