//go:build integration

// Runs the 10 route cases in data/test-cases/route-cases.csv through the Go API against a real OTP (PIPELINE.md M04 gate).
// Start OTP first (infrastructure/otp/scripts/serve.sh start), then:
//
//	go test -tags integration -v -run TestRouteCases ./cmd/api/
//
// Checks mirror infrastructure/otp/scripts/run_route_cases.py (M02). Fails, never skips, when OTP is down.
package main

import (
	"context"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net/http"
	"net/http/httptest"
	"os"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility/otp"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/config"
)

const (
	casesFile  = "../../../data/test-cases/route-cases.csv"
	feedPrefix = "1:" // OTP feed id for the single assumed feed
	toleranceS = 5
)

var weekStart = time.Date(2026, 11, 16, 0, 0, 0, 0, time.FixedZone("ICT", 7*3600)) // Monday, inside the feed period

type apiResponse struct {
	DataStatus string           `json:"dataStatus"`
	Routes     []mobility.Route `json:"routes"`
	Error      struct{ Code, Message string }
}

func TestRouteCases(t *testing.T) {
	cfg, err := config.Load(func(k string) string {
		if k == "DATABASE_URL" && os.Getenv(k) == "" {
			return "postgres://unused@127.0.0.1/unused" // route cases do not touch the database
		}
		return os.Getenv(k)
	})
	if err != nil {
		t.Fatal(err)
	}
	provider := otp.New(cfg.OTPURL, cfg.OTPTimeout)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if err := provider.Ready(ctx); err != nil {
		t.Fatalf("OTP at %s is not ready (start it with infrastructure/otp/scripts/serve.sh start): %v", cfg.OTPURL, err)
	}
	srv := httptest.NewServer(newHandler(cfg, slog.New(slog.NewTextHandler(io.Discard, nil)), provider, stubStore{}))
	defer srv.Close()

	f, err := os.Open(casesFile)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	rows, err := csv.NewReader(f).ReadAll()
	if err != nil {
		t.Fatal(err)
	}
	if len(rows) != 11 {
		t.Fatalf("%s: want header + 10 route cases, got %d rows", casesFile, len(rows))
	}
	header := rows[0]
	t.Logf("| Case | expect_kind | Kết quả | HTTP | Ghi chú | ms |")
	t.Logf("|---|---|---|---|---|---|")
	for _, row := range rows[1:] {
		c := map[string]string{}
		for i, h := range header {
			c[h] = row[i]
		}
		t.Run(c["case_id"], func(t *testing.T) {
			day := slices.Index([]string{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}, c["depart_day"])
			hm, err := time.Parse("15:04", c["depart_time"])
			if day < 0 || err != nil {
				t.Fatalf("bad depart_day/depart_time %q %q", c["depart_day"], c["depart_time"])
			}
			departAt := weekStart.AddDate(0, 0, day).Add(time.Duration(hm.Hour())*time.Hour + time.Duration(hm.Minute())*time.Minute)
			body := fmt.Sprintf(`{"origin":{"lat":%s,"lon":%s},"destination":{"lat":%s,"lon":%s},"dateTime":%q}`,
				c["from_lat"], c["from_lon"], c["to_lat"], c["to_lon"], departAt.Format(time.RFC3339))

			start := time.Now()
			resp, err := http.Post(srv.URL+"/api/v1/routes/plan", "application/json", strings.NewReader(body))
			if err != nil {
				t.Fatal(err)
			}
			defer resp.Body.Close()
			var out apiResponse
			if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
				t.Fatal(err)
			}
			ms := time.Since(start).Milliseconds()

			ok, notes := check(c, departAt, resp.StatusCode, out)
			verdict := "PASS"
			if !ok {
				verdict = "FAIL"
				t.Fail()
			}
			t.Logf("| %s | %s | %s | %d | %s | %d |", c["case_id"], c["expect_kind"], verdict, resp.StatusCode, strings.Join(notes, "; "), ms)
		})
	}
}

func transitLegs(r mobility.Route) []mobility.Leg {
	var out []mobility.Leg
	for _, l := range r.Legs {
		if l.Transit {
			out = append(out, l)
		}
	}
	return out
}

func check(c map[string]string, departAt time.Time, status int, out apiResponse) (bool, []string) {
	notes := []string{fmt.Sprintf("%d hành trình", len(out.Routes))}
	if out.Error.Code != "" {
		notes = append(notes, "error="+out.Error.Code)
	} else if out.DataStatus != "ASSUMED" {
		return false, append(notes, "dataStatus="+out.DataStatus+", kỳ vọng ASSUMED")
	}
	switch c["expect_kind"] {
	case "invalid_input":
		return status == 400 && out.Error.Code == "INVALID_REQUEST", notes
	case "outside_bounds":
		return status == 422 && out.Error.Code == "OUT_OF_COVERAGE", notes
	case "no_transit_same_day":
		if status == 422 && out.Error.Code == "NO_ROUTE_FOUND" {
			return true, notes
		}
		for _, r := range out.Routes {
			for _, l := range transitLegs(r) {
				if l.StartTime.In(departAt.Location()).YearDay() == departAt.YearDay() {
					return false, append(notes, "có tàu cùng ngày")
				}
			}
		}
		return status == 200, notes
	}
	if status != 200 {
		return false, notes
	}
	if c["expect_kind"] == "walk_only" {
		for _, r := range out.Routes {
			if !slices.ContainsFunc(r.Legs, func(l mobility.Leg) bool { return l.Mode != "WALK" }) {
				return true, notes
			}
		}
		return false, append(notes, "không có hành trình chỉ đi bộ")
	}
	if c["expect_kind"] != "transit" {
		return false, append(notes, "expect_kind lạ: "+c["expect_kind"])
	}

	board, alight := feedPrefix+c["expect_board_stop"], feedPrefix+c["expect_alight_stop"]
	var match *mobility.Route
	for i, r := range out.Routes {
		if tl := transitLegs(r); len(tl) > 0 && tl[0].From.StopID == board && tl[len(tl)-1].To.StopID == alight {
			match = &out.Routes[i]
			break
		}
	}
	if match == nil {
		return false, append(notes, fmt.Sprintf("không có hành trình lên %s xuống %s", c["expect_board_stop"], c["expect_alight_stop"]))
	}
	ok := true
	tl := transitLegs(*match)
	inVehicle := 0
	for _, l := range tl {
		inVehicle += l.DurationSec
	}
	notes = append(notes, fmt.Sprintf("%s→%s, trên tàu %d s", tl[0].From.Name, tl[len(tl)-1].To.Name, inVehicle))
	if s := c["expect_in_vehicle_s"]; s != "" {
		want, err := strconv.Atoi(s)
		if err != nil || math.Abs(float64(inVehicle-want)) > toleranceS {
			ok = false
			notes = append(notes, "kỳ vọng "+s+" s")
		}
	}
	access := 0
	for _, l := range match.Legs {
		if l.Transit {
			break
		}
		access += l.DurationSec
	}
	firstDep := tl[0].StartTime.In(departAt.Location())
	wait := int(firstDep.Sub(departAt).Seconds()) - access
	notes = append(notes, fmt.Sprintf("chờ %d s", wait))
	if s := c["expect_max_wait_s"]; s != "" {
		max, err := strconv.Atoi(s)
		if err != nil || wait > max {
			ok = false
			notes = append(notes, "> tối đa "+s+" s")
		}
	}
	if want := c["expect_first_departure"]; want != "" && (firstDep.Format("15:04:05") != want || firstDep.YearDay() != departAt.YearDay()) {
		ok = false
		notes = append(notes, "chuyến đầu "+firstDep.Format("2006-01-02 15:04:05")+", kỳ vọng "+want)
	}
	for _, l := range tl {
		if l.Polyline == "" {
			ok = false
			notes = append(notes, "thiếu polyline")
		}
		if l.Realtime {
			notes = append(notes, "có realtime")
		}
	}
	return ok, notes
}
