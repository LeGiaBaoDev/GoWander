package admin

import (
	"fmt"
	"time"
)

// Grain is how the dashboards group time (owner, 16-11-2026: "so sánh theo ngày/tuần/tháng/năm"): the last Count
// buckets of the unit, always shown against the Count buckets before them. The counters are daily (ADR-038), so every
// grain is built from days.
type Grain struct {
	Key, Name, Lower, Span string // "month", "Tháng", "tháng", "12 tháng"
	Count                  int
}

// Grains in the order of the selector; the first is the default.
var Grains = []Grain{{"day", "Ngày", "ngày", "30 ngày", 30}, {"week", "Tuần", "tuần", "12 tuần", 12},
	{"month", "Tháng", "tháng", "12 tháng", 12}, {"year", "Năm", "năm", "5 năm", 5}}

func grainOf(key string) Grain {
	for _, g := range Grains {
		if g.Key == key {
			return g
		}
	}
	return Grains[0]
}

// start of the bucket holding t (Ho Chi Minh City days; weeks start on Monday).
func (g Grain) start(t time.Time) time.Time {
	t = t.In(hcm)
	d := time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, hcm)
	switch g.Key {
	case "week":
		return d.AddDate(0, 0, -((int(d.Weekday()) + 6) % 7))
	case "month":
		return time.Date(d.Year(), d.Month(), 1, 0, 0, 0, 0, hcm)
	case "year":
		return time.Date(d.Year(), 1, 1, 0, 0, 0, 0, hcm)
	}
	return d
}

func (g Grain) add(t time.Time, n int) time.Time {
	switch g.Key {
	case "week":
		return t.AddDate(0, 0, 7*n)
	case "month":
		return t.AddDate(0, n, 0)
	case "year":
		return t.AddDate(n, 0, 0)
	}
	return t.AddDate(0, 0, n)
}

func (g Grain) label(t time.Time) string {
	switch g.Key {
	case "week":
		return "Tuần " + t.Format("02/01")
	case "month":
		return fmt.Sprintf("Th%d/%d", int(t.Month()), t.Year())
	case "year":
		return t.Format("2006")
	}
	return t.Format("02/01")
}

// window: the first day of the current period and of the previous one; the current period ends today.
func (g Grain) window(now time.Time) (from, prevFrom time.Time) {
	from = g.add(g.start(now), 1-g.Count)
	return from, g.add(from, -g.Count)
}

// buckets sums daily values into the current and previous series (same length, aligned bucket by bucket). The
// current period ends today, part way through its last bucket; the previous period stops at the same point (today
// minus Count units), so the two are compared like for like.
func (g Grain) buckets(daily map[time.Time]int, now time.Time) (cur, prev []bar) {
	from, prevFrom := g.window(now)
	today := g.start(now)
	if g.Key != "day" {
		t := now.In(hcm)
		today = time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, hcm)
	}
	prevEnd := g.add(today, -g.Count)
	for i := 0; i < g.Count; i++ {
		cur = append(cur, bar{Label: g.label(g.add(from, i))})
		prev = append(prev, bar{Label: g.label(g.add(prevFrom, i))})
	}
	for day, v := range daily {
		if day.After(today) || (day.Before(from) && day.After(prevEnd)) {
			continue
		}
		b := g.start(day)
		for i := 0; i < g.Count; i++ {
			if b.Equal(g.add(from, i)) {
				cur[i].Value += v
			} else if b.Equal(g.add(prevFrom, i)) {
				prev[i].Value += v
			}
		}
	}
	return cur, prev
}

// partial says whether the last bucket is still running (a week, month or year that has not ended).
func (g Grain) partial() bool { return g.Key != "day" }

func sum(bs []bar) (total, top int) {
	for _, b := range bs {
		total += b.Value
		top = max(top, b.Value)
	}
	return total, top
}
