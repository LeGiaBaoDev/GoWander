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
	if g := greeting(time.Date(2026, 11, 16, 1, 0, 0, 0, time.UTC)); g != "Chào buổi sáng" { // 08:00 in HCMC
		t.Error(g)
	}
}

func TestSampleDataIsMarkedAndConsistent(t *testing.T) {
	for _, g := range Grains {
		u := sampleUsage(UsageQuery{Grain: g}, nil)
		if !u.Sample || len(u.Series) != g.Count || len(u.PrevSeries) != g.Count || len(u.OpensSeries) != g.Count || u.Total <= 0 ||
			u.Opens <= u.Total || u.Total <= u.PrevTotal || len(u.Lines) != 5 || u.Lines[0].Value <= u.Lines[1].Value || len(u.LineOptions) != 5 {
			t.Fatalf("%s: sample usage %+v", g.Key, u)
		}
		r := sampleRevenue(g)
		if !r.Sample || r.Total <= r.Prev || r.Profit >= r.Total || len(r.Series) != g.Count || len(r.ByChannel) != 2 {
			t.Fatalf("%s: sample revenue %+v", g.Key, r)
		}
	}
	day := grainOf("day")
	if a, b := sampleUsage(UsageQuery{Grain: day}, nil), sampleUsage(UsageQuery{Grain: day}, nil); a.Total != b.Total {
		t.Error("sample numbers must be the same on every load")
	}
	f := sampleUsage(UsageQuery{Grain: day, Line: "sample-19"}, nil)
	if m := sampleUsage(UsageQuery{Grain: day, Kind: "METRO"}, nil); len(m.Lines) != 1 || m.Lines[0].Title() != "Metro số 1" {
		t.Fatalf("the metro tab of the sample: %+v", m.Lines)
	}
	if f.LineName != "19" || f.Lines != nil || f.Modes != nil || f.Total >= sampleUsage(UsageQuery{Grain: day}, nil).Total {
		t.Fatalf("a line filter shows that line's share only: %+v", f)
	}
	if x := sampleUsage(UsageQuery{Grain: day, Line: "nope"}, nil); x.Line != "" {
		t.Fatal("an unknown line is ignored")
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
