package admin

import (
	"strconv"
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

func TestVNMapLevelsAndRanks(t *testing.T) {
	shapes := []AreaShape{{Code: "hcm", Name: "TP.HCM"}, {Code: "hn", Name: "Hà Nội"}, {Code: "kh", Name: "Khánh Hòa"}}
	m := newVNMap(shapes, map[string]int{"hcm": 100, "hn": 20}, "hn", "/admin/reports?area=")
	byCode := map[string]mapArea{}
	for _, a := range m.Areas {
		byCode[a.Code] = a
	}
	if byCode["hcm"].Level != 4 || byCode["hcm"].Rank != 1 || byCode["hn"].Level != 1 || byCode["hn"].Rank != 2 || !byCode["hn"].Selected {
		t.Fatalf("%+v", byCode)
	}
	if byCode["kh"].Level != 0 || byCode["kh"].Rank != 0 || len(m.Top) != 2 {
		t.Fatalf("a province without counts stays blank and unranked: %+v %d", byCode["kh"], len(m.Top))
	}
	if len(m.Islands) != 2 || m.Islands[0].Text1 != "QĐ. Hoàng Sa" || m.Islands[1].Text1 != "QĐ. Trường Sa" {
		t.Fatal("both archipelagos must be on the map")
	}
	if empty := newVNMap(shapes, nil, "", ""); len(empty.Top) != 0 || empty.Areas[0].Level != 0 {
		t.Fatal("no data: no ranks, no colour")
	}
}

func TestSampleDataIsMarkedAndConsistent(t *testing.T) {
	shapes := []AreaShape{{Code: "hcm", Name: "Thành phố Hồ Chí Minh"}, {Code: "hn", Name: "Thành phố Hà Nội"}}
	for _, g := range Grains {
		u := sampleUsage(UsageQuery{Grain: g}, []Area{{"hcm", "Thành phố Hồ Chí Minh"}}, shapes)
		if !u.Sample || len(u.Series) != g.Count || len(u.PrevSeries) != g.Count || len(u.OpensSeries) != g.Count || u.Total <= 0 ||
			u.Opens <= u.Total || u.Total <= u.PrevTotal || u.AreaCounts["hcm"] <= u.AreaCounts["hn"] {
			t.Fatalf("%s: sample usage %+v", g.Key, u)
		}
		r := sampleRevenue(g)
		if !r.Sample || r.Total <= r.Prev || r.Profit >= r.Total || len(r.Series) != g.Count || len(r.PrevSeries) != g.Count {
			t.Fatalf("%s: sample revenue %+v", g.Key, r)
		}
	}
	day := grainOf("day")
	if a, b := sampleUsage(UsageQuery{Grain: day}, nil, shapes), sampleUsage(UsageQuery{Grain: day}, nil, shapes); a.Total != b.Total {
		t.Error("sample numbers must be the same on every load")
	}
	f := sampleUsage(UsageQuery{Grain: day, Area: "hcm"}, []Area{{"hcm", "Thành phố Hồ Chí Minh"}}, shapes)
	if f.AreaName != "Thành phố Hồ Chí Minh" || f.From != nil || f.Total >= sampleUsage(UsageQuery{Grain: day}, nil, shapes).Total {
		t.Fatalf("a province filter scales the sample down and hides destinations: %+v", f)
	}
	for v, want := range map[int64]string{48_230_000: "48,2 triệu ₫", 1_500_000_000: "1,5 tỷ ₫", 950_000: "950.000 ₫"} {
		if got := vnd(v); got != want {
			t.Errorf("vnd(%d) = %q, want %q", v, got, want)
		}
	}
}

func TestLineChartHasATooltipPerPoint(t *testing.T) {
	c := newLineChart("x", []bar{{"01-11", 0}, {"02-11", 10}, {"03-11", 5}}, []bar{{"01-10", 2}, {"02-10", 12}, {"03-10", 1}}, "lượt", groupDigits)
	if len(c.Points) != 3 || c.Points[1].Text != "02-11 · 10 lượt" || c.Points[1].Sub != "Kỳ trước (02-10): 12" || c.First != "01-11" ||
		c.Last != "03-11" || c.Peak != "12 lượt" || c.PrevLine == "" {
		t.Fatalf("%+v", c)
	}
	if solo := newLineChart("y", []bar{{"a", 1}}, nil, "", groupDigits); solo.PrevLine != "" || solo.Points[0].Sub != "" {
		t.Error("no previous period: no dashed line, no second tooltip line")
	}
	for _, p := range c.Points {
		if p.TipX < 0 || p.TipX+tipW > chartW || p.TipY < 0 {
			t.Errorf("tooltip outside the chart: %+v", p)
		}
	}
	if c.Points[1].Y >= c.Points[2].Y || c.Points[0].Y <= c.Points[2].Y {
		t.Error("higher values must be higher on the chart")
	}
	if e := newLineChart("e", nil, nil, "", groupDigits); e.Line != "" || len(e.Points) != 0 {
		t.Error("empty series")
	}
}

func TestPager(t *testing.T) {
	url := func(n int) string { return "/p?page=" + strconv.Itoa(n) }
	p := newPager(2, 120, 50, url)
	if p.Count != 3 || p.Prev != "/p?page=1" || p.Next != "/p?page=3" || len(p.Pages) != 3 || !p.Pages[1].Current {
		t.Fatalf("%+v", p)
	}
	if first := newPager(1, 120, 50, url); first.Prev != "" || first.Next == "" {
		t.Fatalf("first page %+v", first)
	}
	if last := newPager(9, 120, 50, url); last.Next != "" || !last.Pages[2].Current {
		t.Fatalf("a page past the end shows the last %+v", last)
	}
	if none := newPager(1, 0, 50, url); none.Count != 1 {
		t.Fatalf("no rows: one page %+v", none)
	}
}

func TestFriendlyTime(t *testing.T) {
	now := time.Date(2026, 11, 16, 1, 30, 0, 0, time.UTC) // 08:30 in HCMC
	for in, want := range map[time.Time]string{
		time.Date(2026, 11, 16, 0, 37, 0, 0, time.UTC):  "Hôm nay, 07:37",
		time.Date(2026, 11, 15, 14, 5, 0, 0, time.UTC):  "Hôm qua, 21:05",
		time.Date(2026, 11, 15, 17, 30, 0, 0, time.UTC): "Hôm nay, 00:30", // already the 16th in HCMC
		time.Date(2026, 11, 1, 0, 37, 0, 0, time.UTC):   "01/11/2026, 07:37",
	} {
		if got := friendlyTime(in, now); got != want {
			t.Errorf("%v: %q, want %q", in, got, want)
		}
	}
}
