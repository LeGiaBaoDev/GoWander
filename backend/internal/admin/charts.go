package admin

import (
	"fmt"
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

// wardArea is one ward on the "Khách đi đâu" map. Level 0 = under MinShown searches (left white), 1..4 = share of
// the busiest ward. Few = busy (level 3+) with at most fewShops published places: where to look for affiliate shops.
type wardArea struct {
	WardShape
	Plans, Places, Level, Rank int
	Few                        bool
}

// wardMap is the old province map's look (owner, 16-11-2026: "giữ giống cái cũ") on Ho Chi Minh City's wards.
type wardMap struct {
	ViewBox string
	Areas   []wardArea // only the wards inside the frame
	Top     []wardArea // the five busiest, numbered
	Pin     float64    // badge radius in degrees, from the frame size
	PinDY   float64    // badge number baseline offset
	PinFont float64    // badge number size
}

const fewShops = 2

// newWardMap frames the wards with searches (at least central HCMC, ~20 km across) and keeps the wards in view.
func newWardMap(shapes []WardShape, counts []WardCount) wardMap {
	minX, minY, maxX, maxY := 106.60, -10.88, 106.80, -10.70 // central HCMC
	byCode := map[string]WardCount{}
	most := 0
	for _, c := range counts {
		byCode[c.Code] = c
		most = max(most, c.Plans)
	}
	for _, s := range shapes {
		if _, ok := byCode[s.Code]; ok {
			minX, minY, maxX, maxY = min(minX, s.MinX), min(minY, s.MinY), max(maxX, s.MaxX), max(maxY, s.MaxY)
		}
	}
	side := max(maxX-minX, maxY-minY) * 1.04
	cx, cy := (minX+maxX)/2, (minY+maxY)/2
	minX, minY, maxX, maxY = cx-side/2, cy-side/2, cx+side/2, cy+side/2
	m := wardMap{ViewBox: fmt.Sprintf("%.4f %.4f %.4f %.4f", minX, minY, side, side), Pin: side / 55, PinDY: side / 55 * .38, PinFont: side / 55 * 1.1}
	rank := map[string]int{}
	for i, c := range counts {
		if i < 5 {
			rank[c.Code] = i + 1
		}
	}
	for _, s := range shapes {
		if s.MaxX < minX || s.MinX > maxX || s.MaxY < minY || s.MinY > maxY {
			continue
		}
		c := byCode[s.Code]
		a := wardArea{WardShape: s, Plans: c.Plans, Places: c.Places, Rank: rank[s.Code]}
		if a.Plans > 0 && most > 0 {
			a.Level = min(4, 1+a.Plans*4/(most+1))
		}
		a.Few = a.Level >= 3 && a.Places <= fewShops
		m.Areas = append(m.Areas, a)
	}
	for r := 1; r <= 5; r++ {
		for _, a := range m.Areas {
			if a.Rank == r {
				m.Top = append(m.Top, a)
			}
		}
	}
	return m
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
