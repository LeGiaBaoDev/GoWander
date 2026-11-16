package admin

import (
	"testing"
	"time"
)

func TestGrains(t *testing.T) {
	now := time.Date(2026, 11, 16, 1, 0, 0, 0, time.UTC) // Monday 16/11/2026 08:00 in HCMC
	d := func(y int, m time.Month, day int) time.Time { return time.Date(y, m, day, 0, 0, 0, 0, hcm) }
	for _, c := range []struct {
		key            string
		from, prevFrom time.Time
		first, last    string
	}{
		{"day", d(2026, 10, 18), d(2026, 9, 18), "18/10", "16/11"},
		{"week", d(2026, 8, 31), d(2026, 6, 8), "Tuần 31/08", "Tuần 16/11"},
		{"month", d(2025, 12, 1), d(2024, 12, 1), "Th12/2025", "Th11/2026"},
		{"year", d(2022, 1, 1), d(2017, 1, 1), "2022", "2026"},
	} {
		g := grainOf(c.key)
		from, prevFrom := g.window(now)
		cur, prev := g.buckets(nil, now)
		if !from.Equal(c.from) || !prevFrom.Equal(c.prevFrom) || len(cur) != g.Count || len(prev) != g.Count || cur[0].Label != c.first || cur[g.Count-1].Label != c.last {
			t.Errorf("%s: %v %v %s..%s", c.key, from, prevFrom, cur[0].Label, cur[len(cur)-1].Label)
		}
	}
	if grainOf("nonsense").Key != "day" {
		t.Error("unknown grain falls back to day")
	}
	g := grainOf("month")
	cur, prev := g.buckets(map[time.Time]int{d(2026, 11, 3): 5, d(2026, 11, 16): 2, d(2025, 11, 30): 7, d(2024, 12, 2): 1, d(2020, 1, 1): 99}, now)
	// Like for like: the previous November only counts up to the 16th (the 30th is left out).
	if cur[11].Value != 7 || cur[0].Value != 0 || prev[11].Value != 0 || prev[0].Value != 1 {
		t.Errorf("monthly sums: cur %v prev %v", cur, prev)
	}
	if total, top := sum(cur); total != 7 || top != 7 {
		t.Error("sum")
	}
}

func TestPreviousPeriodStopsAtTheSamePoint(t *testing.T) {
	now := time.Date(2026, 11, 16, 1, 0, 0, 0, time.UTC)
	d := func(y int, m time.Month, day int) time.Time { return time.Date(y, m, day, 0, 0, 0, 0, hcm) }
	_, prev := grainOf("month").buckets(map[time.Time]int{d(2025, 11, 10): 3, d(2025, 11, 16): 4, d(2025, 11, 17): 100}, now)
	if prev[11].Value != 7 {
		t.Fatalf("previous November up to the 16th only: %v", prev[11])
	}
	_, prev = grainOf("day").buckets(map[time.Time]int{d(2026, 10, 17): 5}, now)
	if prev[29].Value != 5 {
		t.Fatalf("days: the whole previous 30 days: %v", prev[29])
	}
}
