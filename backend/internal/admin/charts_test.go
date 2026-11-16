package admin

import (
	"strings"
	"testing"
	"time"
)

func TestChartHelpers(t *testing.T) {
	if d := delta(120, 100); !d.Show || !d.Up || d.Text != "+20%" {
		t.Errorf("up %+v", d)
	}
	if d := delta(80, 100); !d.Show || d.Up || d.Text != "-20%" {
		t.Errorf("down %+v", d)
	}
	if d := delta(5, 0); d.Show {
		t.Error("no badge without a previous period")
	}
	p := areaPaths([]bar{{"a", 0}, {"b", 10}, {"c", 5}}, 10, 200, 100)
	if !strings.HasPrefix(p["Line"], "M0.0 100.0 L100.0 8.0") || !strings.HasSuffix(p["Area"], "L200.0 100 L0 100 Z") {
		t.Errorf("paths %v", p)
	}
	s := donut([]bar{{"Xe buýt", 75}, {"Đi bộ", 25}}, 100)
	if len(s) != 2 || s[0].Dash != "75.00 25.00" || s[0].Offset != "25.00" || s[1].Offset != "-50.00" || s[1].Pct != 25 {
		t.Errorf("donut %+v", s)
	}
	if donut(nil, 0) != nil {
		t.Error("empty donut")
	}
	if g := greeting(time.Date(2026, 11, 16, 1, 0, 0, 0, time.UTC)); g != "Chào buổi sáng" { // 08:00 in HCMC
		t.Error(g)
	}
}
