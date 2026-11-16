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
//   - "Bắt đầu" presses (SampleStarts): not measured. Real source: app events (ADR-038 step 3).
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
	r.Total, r.Prev = int64(cur)*1000, int64(prev)*1000
	r.Profit, r.PrevProfit = r.Total*32/100, r.Prev*29/100
	r.ByChannel = []bar{{"Quảng bá quán ăn, điểm đến", cur * 64 / 100}, {"Affiliate (đặt vé, khách sạn)", cur * 36 / 100}}
	return r
}

// SampleStarts is the sample count of "Bắt đầu" presses for the funnel: about a third of the searches.
func SampleStarts(searches int) int { return searches * 34 / 100 }

// sampleUsage fills a Usage with believable numbers for the period; the province codes come from the real shapes so
// the map lights up. q and the real Areas list are kept.
func sampleUsage(q UsageQuery, areas []Area, shapes []AreaShape) Usage {
	u := Usage{UsageQuery: q, Areas: areas, Sample: true, AreaCounts: map[string]int{}}
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
	u.Lines = []bar{{"L1", share(22)}, {"19", share(9)}, {"56", share(6)}, {"150", share(4)}, {"8", share(3)}}
	weights := map[string]int{"Thành phố Hồ Chí Minh": 62, "Thành phố Hà Nội": 14, "Thành phố Đà Nẵng": 7, "Tỉnh Khánh Hòa": 4,
		"Tỉnh Lâm Đồng": 3, "Thành phố Hải Phòng": 2, "Thành phố Cần Thơ": 2, "Thành phố Huế": 2, "Tỉnh Quảng Ninh": 1, "Thành phố Đồng Nai": 1}
	for _, s := range shapes {
		if w, ok := weights[s.Name]; ok {
			u.AreaCounts[s.Code] = share(w)
		}
	}
	for _, name := range []string{"Thành phố Hồ Chí Minh", "Thành phố Hà Nội", "Thành phố Đà Nẵng", "Tỉnh Khánh Hòa", "Tỉnh Lâm Đồng"} {
		u.From = append(u.From, bar{name, share(weights[name])})
	}
	u.To = []bar{{"Thành phố Hồ Chí Minh", share(60)}, {"Thành phố Hà Nội", share(15)}, {"Thành phố Đà Nẵng", share(9)}, {"Tỉnh Khánh Hòa", share(6)}}
	if q.Area != "" { // a province filter: scale the searches to that province's share
		for _, a := range areas {
			if a.Code == q.Area {
				u.AreaName = a.Name
			}
		}
		w := max(weights[u.AreaName], 1)
		for _, bs := range [][]bar{u.Series, u.PrevSeries, u.Modes, u.Lines} {
			for i := range bs {
				bs[i].Value = bs[i].Value * w / 100
			}
		}
		u.Total, _ = sum(u.Series)
		u.PrevTotal, _ = sum(u.PrevSeries)
		u.From, u.To, u.Places = nil, nil, nil
	}
	return u
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
