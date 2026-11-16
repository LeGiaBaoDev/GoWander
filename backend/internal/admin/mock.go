package admin

import (
	"fmt"
	"strings"
	"time"
)

// SAMPLE DATA for the dashboards (owner, 16-11-2026: "mock dữ liệu cái nào chưa có đi, và nhớ ghi vào để sau này nhớ
// và chỉnh lại dữ liệu thật"). Everything here is shown with a "Dữ liệu mẫu" badge and is listed in PIPELINE.md
// ("Admin sample data"). Replace each piece when its real source exists:
//
//   - Revenue / profit (sampleRevenue): no revenue tables yet. Real source: the business ADR (shop promotion, then
//     affiliate) and its transactions table.
//   - Customer usage (sampleUsage): used while the real counters hold fewer than MinShown searches in the period.
//     Real source: usage_* counters (ADR-038); switches over by itself once there is enough real use.
//
// The numbers are generated, deterministic (same page every load), never random and never stored.

// Revenue is what the overview shows about money.
type Revenue struct {
	Sample             bool
	Total, Prev        int64 // VND, this period and the one before
	Profit, PrevProfit int64
	Series, PrevSeries []bar // thousand VND per bucket of the grain
	Partial            bool  // the last bucket is still running
	ByChannel          []bar // thousand VND per channel
}

// sampleDays makes one value per day over the current and previous period of g: a steady rise (the app grows) and a
// weekly rhythm (weekends are busier). Deterministic: the same page on every load.
func sampleDays(g Grain, now time.Time, base, weekend int) map[time.Time]int {
	_, prevFrom := g.window(now)
	out := map[time.Time]int{}
	total := now.Sub(prevFrom).Hours() / 24
	for d := prevFrom; !d.After(now); d = d.AddDate(0, 0, 1) {
		pos := d.Sub(prevFrom).Hours() / 24
		w := []int{0, 2, 1, 3, 4, 10, 8}[int(d.Weekday())]
		v := base + w*weekend/10 + (d.Day()*7)%(base/8+1)
		out[d] = v * (100 + int(60*pos/total)) / 100 // about +60% from the start of the previous period to today
	}
	return out
}

// sampleRevenue: two channels from the business plan, growing over time.
func sampleRevenue(g Grain) Revenue {
	r := Revenue{Sample: true, Partial: g.partial()}
	r.Series, r.PrevSeries = g.buckets(sampleDays(g, time.Now(), 300, 450), time.Now()) // thousand VND
	cur, _ := sum(r.Series)
	prev, _ := sum(r.PrevSeries)
	r.ByChannel = []bar{{"Quảng bá quán ăn, điểm đến", cur * 64 / 100}, {"Affiliate (đặt vé, khách sạn)", cur * 36 / 100}}
	r.Total, r.Prev = int64(cur)*1000, int64(prev)*1000
	r.Profit, r.PrevProfit = r.Total*32/100, r.Prev*29/100
	return r
}

// sampleUsage fills a Usage with believable numbers for the period. q and the real line list are kept; with no real
// lines yet the sample lines fill the filter.
func sampleUsage(q UsageQuery, options []BusLine) Usage {
	u := Usage{UsageQuery: q, Sample: true, LineOptions: options}
	now := time.Now()
	u.OpensSeries, u.PrevOpensSeries = q.Grain.buckets(sampleDays(q.Grain, now, 60, 40), now)
	u.Series, u.PrevSeries = q.Grain.buckets(sampleDays(q.Grain, now, 42, 30), now)
	u.Opens, _ = sum(u.OpensSeries)
	u.PrevOpens, _ = sum(u.PrevOpensSeries)
	u.Total, _ = sum(u.Series)
	u.PrevTotal, _ = sum(u.PrevSeries)
	share := func(p int) int { return u.Total * p / 100 }
	u.Modes = []bar{{"Phương tiện công cộng", share(41)}, {"Xe buýt", share(27)}, {"Đi bộ", share(18)}, {"Xe đạp", share(8)}, {"Ô tô", share(6)}}
	u.Places = []bar{{"Chợ Bến Thành · Chợ và mua sắm", share(9)}, {"Dinh Độc Lập · Di tích lịch sử", share(7)},
		{"Bưu điện Trung tâm Sài Gòn · Điểm tham quan", share(5)}, {"Phố đi bộ Nguyễn Huệ · Điểm tham quan", share(4)},
		{"Bảo tàng Mỹ thuật · Bảo tàng", share(3)}, {"Ga Bến Thành · Ga metro", share(2)}}
	weights := map[string]int{"số 1": 22, "19": 9, "56": 6, "150": 4, "8": 3}
	for _, l := range []string{"số 1", "19", "56", "150", "8"} {
		v := "BUS"
		if l == "số 1" {
			v = "METRO"
		}
		if q.Kind == "" || q.Kind == v {
			u.Lines = append(u.Lines, BusLine{ID: "sample-" + l, Name: l, Vehicle: v, Value: share(weights[l])})
		}
	}
	if len(u.LineOptions) == 0 {
		u.LineOptions = []BusLine{{ID: "sample-8", Name: "8", Vehicle: "BUS"}, {ID: "sample-19", Name: "19", Vehicle: "BUS"},
			{ID: "sample-56", Name: "56", Vehicle: "BUS"}, {ID: "sample-150", Name: "150", Vehicle: "BUS"}, {ID: "sample-số 1", Name: "số 1", Vehicle: "METRO"}}
	}
	for _, l := range u.LineOptions {
		if l.ID == q.Line {
			u.LineName = l.Name
		}
	}
	if u.LineName == "" {
		u.Line = ""
		return u
	}
	w := weights[u.LineName] // a line filter: the line's share of the searches, nothing else
	if w == 0 {
		w = 2
	}
	for _, bs := range [][]bar{u.Series, u.PrevSeries} {
		for i := range bs {
			bs[i].Value = bs[i].Value * w / 100
		}
	}
	u.Total, _ = sum(u.Series)
	u.PrevTotal, _ = sum(u.PrevSeries)
	u.Modes, u.Places, u.Lines = nil, nil, nil
	return u
}

// sampleWards: searches ending in well-known central wards (names as in hcm-wards.sql), for the map while there is
// no real use. Wards missing from the loaded shapes are skipped.
func sampleWards(total int, shapes []WardShape) []WardCount {
	weights := []struct {
		name          string
		share, places int
	}{{"Phường Bến Thành", 14, 14}, {"Phường Sài Gòn", 12, 2}, {"Phường Tân Sơn Nhất", 8, 1}, {"Phường Cầu Ông Lãnh", 7, 9},
		{"Phường Xuân Hòa", 6, 5}, {"Phường An Khánh", 5, 1}, {"Phường Chợ Lớn", 5, 7}, {"Phường Tân Mỹ", 4, 3},
		{"Phường Bàn Cờ", 3, 4}, {"Phường Tân Định", 3, 6}, {"Phường Gia Định", 2, 2}, {"Phường Phú Nhuận", 2, 3}}
	code := map[string]string{}
	for _, s := range shapes {
		code[s.Name] = s.Code
	}
	var out []WardCount
	for _, w := range weights {
		if c, ok := code[w.name]; ok && total*w.share/100 >= MinShown {
			out = append(out, WardCount{Code: c, Name: w.name, Plans: total * w.share / 100, Places: w.places})
		}
	}
	return out
}

// vnd formats a VND amount for a card: 48.230.000 ₫ → "48,2 triệu ₫".
func vnd(v int64) string {
	switch {
	case v >= 1_000_000_000:
		return strings.Replace(fmt.Sprintf("%.1f tỷ ₫", float64(v)/1e9), ".", ",", 1)
	case v >= 1_000_000:
		return strings.Replace(fmt.Sprintf("%.1f triệu ₫", float64(v)/1e6), ".", ",", 1)
	default:
		return groupDigits(int(v)) + " ₫"
	}
}
