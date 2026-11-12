package otp

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
)

var ict = time.FixedZone("ICT", 7*3600)

var baseQuery = mobility.Query{
	Origin:      mobility.Coordinate{Lat: 10.770775, Lon: 106.697569},
	Destination: mobility.Coordinate{Lat: 10.879569, Lon: 106.81417},
	DateTime:    time.Date(2026, 11, 17, 9, 0, 0, 0, ict),
	Walk:        true, Transit: true, MaxResults: 3,
}

type captured struct {
	path, contentType string
	body              struct {
		Query     string         `json:"query"`
		Variables map[string]any `json:"variables"`
	}
}

func newClient(t *testing.T, timeout time.Duration, h http.HandlerFunc) (*Client, *captured) {
	t.Helper()
	c := &captured{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		c.path, c.contentType = r.URL.Path, r.Header.Get("Content-Type")
		_ = json.NewDecoder(r.Body).Decode(&c.body)
		h(w, r)
	}))
	t.Cleanup(srv.Close)
	return New(srv.URL, timeout), c
}

func respond(body string) http.HandlerFunc {
	return func(w http.ResponseWriter, _ *http.Request) { _, _ = io.WriteString(w, body) }
}

func fixture(t *testing.T, name string) string {
	t.Helper()
	b, err := os.ReadFile("testdata/" + name)
	if err != nil {
		t.Fatal(err)
	}
	return string(b)
}

func TestPlanRouteMapsItinerary(t *testing.T) {
	cl, got := newClient(t, time.Second, respond(fixture(t, "rc01-transit.json")))
	routes, err := cl.PlanRoute(context.Background(), baseQuery)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got.path != "/otp/gtfs/v1" || got.contentType != "application/json" || got.body.Query == "" {
		t.Fatalf("bad request to OTP: %+v", got)
	}
	v := got.body.Variables
	if _, sent := v["modes"]; v["first"] != 3.0 || sent { // OTP 2.10 throws NullPointerException on "modes": null
		t.Fatalf("variables %v", v)
	}
	if dt := v["dateTime"].(map[string]any); dt["earliestDeparture"] != "2026-11-17T09:00:00+07:00" {
		t.Fatalf("dateTime %v", dt)
	}
	coord := v["origin"].(map[string]any)["location"].(map[string]any)["coordinate"].(map[string]any)
	if coord["latitude"] != 10.770775 || coord["longitude"] != 106.697569 {
		t.Fatalf("origin %v", coord)
	}

	if len(routes) != 1 || len(routes[0].Legs) != 2 {
		t.Fatalf("routes %+v", routes)
	}
	r := routes[0]
	if r.DurationSec != 1771 || r.Transfers != 0 || r.WalkDistanceM != 12.5 ||
		!r.StartTime.Equal(time.Date(2026, 11, 17, 9, 5, 0, 0, ict)) {
		t.Fatalf("route %+v", r)
	}
	metro, walk := r.Legs[0], r.Legs[1]
	if metro.Mode != "SUBWAY" || !metro.Transit || metro.From.StopID != "1:L1-01" || metro.To.StopID != "1:L1-14" ||
		metro.Line == nil || metro.Line.ShortName != "L1" || metro.Realtime || metro.DurationSec != 1771 ||
		metro.Polyline != "abc~d" || metro.Headsign != "Bến xe Suối Tiên" {
		t.Fatalf("metro leg %+v", metro)
	}
	if walk.Transit || walk.Line != nil || walk.To.StopID != "" || !walk.Realtime || walk.DurationSec != 10 ||
		!walk.StartTime.Equal(time.Date(2026, 11, 17, 9, 34, 40, 0, ict)) { // estimated time wins over scheduled
		t.Fatalf("walk leg %+v", walk)
	}
}

func TestPlanRouteVariables(t *testing.T) {
	cases := []struct {
		name          string
		arrive        bool
		walk, transit bool
		timeKey       string
		modes         string
	}{
		{"arrive walk-only", true, true, false, "latestArrival", `{"direct":["WALK"],"directOnly":true}`},
		{"depart transit-only", false, false, true, "earliestDeparture", `{"transitOnly":true}`},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			cl, got := newClient(t, time.Second, respond(fixture(t, "rc01-transit.json")))
			q := baseQuery
			q.ArriveBy, q.Walk, q.Transit = c.arrive, c.walk, c.transit
			if _, err := cl.PlanRoute(context.Background(), q); err != nil {
				t.Fatal(err)
			}
			if _, ok := got.body.Variables["dateTime"].(map[string]any)[c.timeKey]; !ok {
				t.Fatalf("dateTime %v, want key %s", got.body.Variables["dateTime"], c.timeKey)
			}
			modes, _ := json.Marshal(got.body.Variables["modes"])
			if string(modes) != c.modes {
				t.Fatalf("modes %s, want %s", modes, c.modes)
			}
		})
	}
}

func TestPlanRouteWarningsStillReturnRoutes(t *testing.T) {
	body := `{"data":{"planConnection":{"routingErrors":[{"code":"WALKING_BETTER_THAN_TRANSIT"}],"edges":[{"node":
		{"start":"2026-11-17T10:00:00+07:00","end":"2026-11-17T10:10:40+07:00","duration":640,"walkDistance":753,"numberOfTransfers":0,
		"legs":[{"mode":"WALK","transitLeg":false,"distance":753,"duration":640,"start":{"scheduledTime":"2026-11-17T10:00:00+07:00"},
		"end":{"scheduledTime":"2026-11-17T10:10:40+07:00"},"from":{"name":"Origin","lat":1,"lon":2},"to":{"name":"Destination","lat":3,"lon":4},
		"legGeometry":{"points":"p"}}]}}]}}}`
	cl, _ := newClient(t, time.Second, respond(body))
	routes, err := cl.PlanRoute(context.Background(), baseQuery)
	if err != nil || len(routes) != 1 {
		t.Fatalf("routes %v, err %v", routes, err)
	}
}

func TestUnknownRoutingCodeIsFlagged(t *testing.T) {
	for body, unrecognized := range map[string]bool{
		`{"data":{"planConnection":{"routingErrors":[{"code":"SOMETHING_NEW"}],"edges":[]}}}`:                          true,
		`{"data":{"planConnection":{"routingErrors":[],"edges":[]}}}`:                                                  true,
		`{"data":{"planConnection":{"routingErrors":[{"code":"NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW"}],"edges":[]}}}`: false,
	} {
		cl, _ := newClient(t, time.Second, respond(body))
		_, err := cl.PlanRoute(context.Background(), baseQuery)
		if errors.Is(err, mobility.ErrUnrecognized) != unrecognized {
			t.Errorf("body %s: ErrUnrecognized=%v, want %v (err %v)", body, !unrecognized, unrecognized, err)
		}
	}
}

func TestPlanRouteErrors(t *testing.T) {
	rc01 := fixture(t, "rc01-transit.json")
	noEdges := func(codes string) string {
		return `{"data":{"planConnection":{"routingErrors":[` + codes + `],"edges":[]}}}`
	}
	cases := []struct {
		name    string
		handler http.HandlerFunc
		want    mobility.Code
	}{
		{"outside bounds", respond(noEdges(`{"code":"OUTSIDE_BOUNDS"}`)), mobility.OutOfCoverage},
		{"outside service period", respond(noEdges(`{"code":"OUTSIDE_SERVICE_PERIOD"}`)), mobility.DataUnavailable},
		{"no transit in window", respond(noEdges(`{"code":"NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW"}`)), mobility.NoRouteFound},
		{"unknown routing error", respond(noEdges(`{"code":"SOMETHING_NEW"}`)), mobility.NoRouteFound},
		{"no edges no errors", respond(noEdges(``)), mobility.NoRouteFound},
		{"graphql errors", respond(`{"errors":[{"message":"Validation error"}],"data":null}`), mobility.RoutingUnavailable},
		{"null data", respond(`{"data":null}`), mobility.RoutingUnavailable},
		{"broken json", respond(`{"data":`), mobility.RoutingUnavailable},
		{"bad time format", respond(`{"data":{"planConnection":{"edges":[{"node":{"start":"yesterday","end":"x","legs":[]}}]}}}`), mobility.RoutingUnavailable},
		{"itinerary without legs", respond(`{"data":{"planConnection":{"edges":[{"node":{"start":"2026-11-17T09:00:00+07:00","end":"2026-11-17T09:10:00+07:00","legs":[]}}]}}}`), mobility.RoutingUnavailable},
		{"transit leg without geometry", respond(strings.Replace(rc01, `"legGeometry":{"points":"abc~d"}`, `"legGeometry":null`, 1)), mobility.RoutingUnavailable},
		{"leg without mode", respond(strings.Replace(rc01, `"mode":"SUBWAY"`, `"mode":""`, 1)), mobility.RoutingUnavailable},
		{"body stalls after headers", func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(200)
			_, _ = io.WriteString(w, `{"data":`)
			w.(http.Flusher).Flush()
			<-r.Context().Done()
		}, mobility.RoutingTimeout},
		{"http 500", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(500) }, mobility.RoutingUnavailable},
		{"slow server", func(w http.ResponseWriter, r *http.Request) {
			select {
			case <-r.Context().Done():
			case <-time.After(2 * time.Second):
			}
		}, mobility.RoutingTimeout},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			cl, _ := newClient(t, 300*time.Millisecond, c.handler)
			_, err := cl.PlanRoute(context.Background(), baseQuery)
			e, ok := errors.AsType[*mobility.Error](err)
			if !ok || e.Code != c.want {
				t.Fatalf("got %v, want %s", err, c.want)
			}
		})
	}
}

func TestPlanRouteConnectionRefused(t *testing.T) {
	srv := httptest.NewServer(http.NotFoundHandler())
	url := srv.URL
	srv.Close()
	_, err := New(url, time.Second).PlanRoute(context.Background(), baseQuery)
	if e, ok := errors.AsType[*mobility.Error](err); !ok || e.Code != mobility.RoutingUnavailable {
		t.Fatalf("got %v", err)
	}
}

func TestPlanRouteCallerCancelledIsNotTimeout(t *testing.T) {
	cl, _ := newClient(t, time.Second, func(w http.ResponseWriter, r *http.Request) { <-r.Context().Done() })
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()
	go func() { time.Sleep(10 * time.Millisecond); cancel() }()
	_, err := cl.PlanRoute(ctx, baseQuery)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("want context.Canceled, got %v", err)
	}
	if e, ok := errors.AsType[*mobility.Error](err); ok && e.Code == mobility.RoutingTimeout {
		t.Fatal("caller cancellation reported as ROUTING_TIMEOUT")
	}
}

func TestReady(t *testing.T) {
	up, got := newClient(t, time.Second, respond(`{"status":"UP"}`))
	if err := up.Ready(context.Background()); err != nil || got.path != "/otp/actuators/health" {
		t.Fatalf("err %v path %s", err, got.path)
	}
	down, _ := newClient(t, time.Second, func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(503) })
	if err := down.Ready(context.Background()); err == nil {
		t.Fatal("want error when OTP health is 503")
	}
}
