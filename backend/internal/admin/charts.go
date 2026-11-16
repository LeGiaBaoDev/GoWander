package admin

import (
	"fmt"
	"slices"
	"strconv"
	"strings"
	"time"
)

// Chart helpers for the overview and reports (dashboard references from the owner, 16-11-2026). Charts are SVG built
// on the server: the CSP forbids inline styles and scripts, and SVG attributes are neither.

// change compares a number with the period before: "+12%", "-8%", or no badge when there is nothing to compare with.
type change struct {
	Text string
	Up   bool
	Show bool
}

func delta(now, before int) change {
	if before <= 0 {
		return change{}
	}
	pct := (now - before) * 100 / before
	if pct >= 0 {
		return change{Text: fmt.Sprintf("+%d%%", pct), Up: true, Show: true}
	}
	return change{Text: fmt.Sprintf("%d%%", pct), Show: true}
}

// slice is one ring segment of a donut drawn with stroke-dasharray on a circle of circumference 100.
type slice struct {
	Label        string
	Value, Pct   int
	Dash, Offset string
	Tone         int // 1..5, colour class
}

func donut(parts []bar, total int) []slice {
	if total <= 0 {
		return nil
	}
	var out []slice
	start := 0.0
	for i, p := range parts {
		share := float64(p.Value) * 100 / float64(total)
		out = append(out, slice{Label: p.Label, Value: p.Value, Pct: int(share + 0.5),
			Dash: fmt.Sprintf("%.2f %.2f", share, 100-share), Offset: fmt.Sprintf("%.2f", 25-start), Tone: i%5 + 1})
		start += share
	}
	return out
}

// greeting by the hour in Ho Chi Minh City.
func greeting(now time.Time) string {
	switch h := now.In(hcm).Hour(); {
	case h < 11:
		return "Chào buổi sáng"
	case h < 18:
		return "Chào buổi chiều"
	default:
		return "Chào buổi tối"
	}
}

// mapArea is one province on the dashboard map. Level 0 = fewer than MinShown searches (or none), 1..4 = share of the
// busiest province. Rank 1..5 gets a numbered badge, like the owner's reference map.
type mapArea struct {
	AreaShape
	Count, Level, Rank int
	Selected           bool
}

type mapLabel struct {
	Text1, Text2 string
	X, Y         float64
}

// vnMap is the province map: fixed frame over Vietnam with Hoàng Sa and Trường Sa (OSM has no polygon for either
// archipelago; they are drawn as island dots and named as in the app's region map). Labels sit just above the dots.
type vnMap struct {
	Areas   []mapArea
	Islands []mapLabel
	Sea     mapLabel
	Top     []mapArea // the ranked provinces, for the list beside the map
	Link    string    // a province's link is Link + its code (the reports page filtered by it)
}

func newVNMap(shapes []AreaShape, counts map[string]int, selected, link string) vnMap {
	most := 0
	for _, n := range counts {
		most = max(most, n)
	}
	m := vnMap{
		Islands: []mapLabel{{"QĐ. Hoàng Sa", "(Việt Nam)", 112.0, -17.3}, {"QĐ. Trường Sa", "(Việt Nam)", 113.7, -12.3}},
		Sea:     mapLabel{"Biển", "Đông", 115.6, -15.0},
		Link:    link,
	}
	for _, s := range shapes {
		a := mapArea{AreaShape: s, Count: counts[s.Code], Selected: s.Code == selected}
		if a.Count > 0 && most > 0 {
			a.Level = min(4, 1+a.Count*4/(most+1))
		}
		m.Areas = append(m.Areas, a)
	}
	ranked := slices.Clone(m.Areas)
	slices.SortStableFunc(ranked, func(a, b mapArea) int { return b.Count - a.Count })
	for i := range ranked {
		if i == 5 || ranked[i].Count == 0 {
			break
		}
		ranked[i].Rank = i + 1
		m.Top = append(m.Top, ranked[i])
		for j := range m.Areas {
			if m.Areas[j].Code == ranked[i].Code {
				m.Areas[j].Rank = i + 1
			}
		}
	}
	return m
}

// lineChart is the current period as a line + area and the previous period as a dashed line, with a hover/focus
// tooltip per point showing both (no script: CSS shows the tooltip of the point under the pointer). It scales
// uniformly (viewBox kept, no stretching) so the tooltip text stays sharp.
type lineChart struct {
	ID          string // unique per page: gradient ids
	Class       string // "money" for the revenue colours
	Line, Area  string
	PrevLine    string // empty when there is no previous period
	Points      []chartPoint
	First, Last string
	Peak        string
}

type chartPoint struct {
	X, Y, HitX, HitW, TipX, TipY float64
	Text, Sub                    string // "Th11/2026 · 5.874 lượt mở", "Kỳ trước (Th11/2025): 4.810"
}

// The chart is drawn in a 1000×300 box shown at about that width, so the tooltip text is close to its real size.
const chartW, chartH, tipW, tipH = 1000.0, 300.0, 330.0, 58.0

// newLineChart: label is what each value is ("lượt mở", "nghìn ₫", "thao tác"); value formats a number for people;
// prev (same length as cur, or nil) is the period before. partial marks the last bucket as running ("đến hôm nay").
func newLineChart(id string, cur, prev []bar, label string, value func(int) string, partial ...bool) lineChart {
	c := lineChart{ID: id}
	if len(cur) == 0 {
		return c
	}
	_, top := sum(cur)
	if len(prev) == len(cur) {
		_, pt := sum(prev)
		top = max(top, pt)
	} else {
		prev = nil
	}
	top = max(top, 1)
	plotTop, plotH := 70.0, chartH-80 // room above the line for the tooltip
	step := 0.0
	if len(cur) > 1 {
		step = chartW / float64(len(cur)-1)
	}
	y := func(v int) float64 { return plotTop + plotH - float64(scaleBar(v, top, int(plotH))) }
	path := func(series []bar) string {
		var b strings.Builder
		for i, p := range series {
			cmd := " L"
			if i == 0 {
				cmd = "M"
			}
			fmt.Fprintf(&b, "%s%.1f %.1f", cmd, float64(i)*step, y(p.Value))
		}
		return b.String()
	}
	c.Line = path(cur)
	if prev != nil {
		c.PrevLine = path(prev)
	}
	for i, b := range cur {
		x, py := float64(i)*step, y(b.Value)
		hitW := max(step, 12)
		p := chartPoint{X: x, Y: py, HitX: x - hitW/2, HitW: hitW, TipX: min(max(x-tipW/2, 0), chartW-tipW), TipY: max(py-tipH-14, 2),
			Text: b.Label + " · " + value(b.Value) + " " + label}
		if prev != nil {
			p.Sub = "Kỳ trước (" + prev[i].Label + "): " + value(prev[i].Value)
		}
		if i == len(cur)-1 && len(partial) > 0 && partial[0] {
			p.Text = b.Label + " (đến hôm nay) · " + value(b.Value)
			if prev != nil {
				p.Sub = "Kỳ trước, cùng số ngày: " + value(prev[i].Value)
			}
		}
		c.Points = append(c.Points, p)
	}
	c.Area = c.Line + fmt.Sprintf(" L%.1f %.1f L0 %.1f Z", float64(len(cur)-1)*step, chartH-10, chartH-10)
	c.First, c.Last, c.Peak = cur[0].Label, cur[len(cur)-1].Label, value(top)+" "+label
	return c
}

func revenueChart(r Revenue) lineChart {
	c := newLineChart("revenue", r.Series, r.PrevSeries, "nghìn ₫", groupDigits, r.Partial)
	c.Class = "money"
	return c
}

// countText: usage numbers 1-4 show as "<5" (ADR-038 §5), the same rule as the template's count.
func countText(n int) string {
	if n > 0 && n < MinShown {
		return "<" + strconv.Itoa(MinShown)
	}
	return groupDigits(n)
}

// pager is the page navigation: arrows plus a dropdown of every page (the owner: choose, don't type).
type pager struct {
	Prev, Next string
	Pages      []pageLink
	Count      int
}

type pageLink struct {
	N       int
	URL     string
	Current bool
}

// newPager: page is 1-based; url builds the link of a page with the current filters.
func newPager(page, total, size int, url func(int) string) pager {
	p := pager{Count: max((total+size-1)/size, 1)}
	page = min(max(page, 1), p.Count)
	for n := 1; n <= p.Count; n++ {
		p.Pages = append(p.Pages, pageLink{N: n, URL: url(n), Current: n == page})
	}
	if page > 1 {
		p.Prev = url(page - 1)
	}
	if page < p.Count {
		p.Next = url(page + 1)
	}
	return p
}

// friendlyTime shows a moment in Ho Chi Minh City time the way people say it: "Hôm nay, 07:37", "Hôm qua, 21:05",
// "16/11/2026, 07:37".
func friendlyTime(t, now time.Time) string {
	t, now = t.In(hcm), now.In(hcm)
	day := func(x time.Time) time.Time { y, m, d := x.Date(); return time.Date(y, m, d, 0, 0, 0, 0, hcm) }
	switch day(now).Sub(day(t)) / (24 * time.Hour) {
	case 0:
		return "Hôm nay, " + t.Format("15:04")
	case 1:
		return "Hôm qua, " + t.Format("15:04")
	}
	return t.Format("02/01/2006, 15:04")
}
