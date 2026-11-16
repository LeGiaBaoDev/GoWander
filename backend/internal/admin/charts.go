package admin

import (
	"fmt"
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

// areaPaths draws a series in a w×h box: the line and the closed area under it (for the gradient fill).
func areaPaths(series []bar, top, w, h int) map[string]string {
	if len(series) == 0 {
		return map[string]string{"Line": "", "Area": ""}
	}
	top = max(top, 1)
	step := 0.0
	if len(series) > 1 {
		step = float64(w) / float64(len(series)-1)
	}
	var line strings.Builder
	for i, b := range series {
		x, y := float64(i)*step, float64(h)-float64(scaleBar(b.Value, top, h-8))
		if i == 0 {
			fmt.Fprintf(&line, "M%.1f %.1f", x, y)
		} else {
			fmt.Fprintf(&line, " L%.1f %.1f", x, y)
		}
	}
	area := line.String() + fmt.Sprintf(" L%.1f %d L0 %d Z", float64(len(series)-1)*step, h, h)
	return map[string]string{"Line": line.String(), "Area": area}
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
