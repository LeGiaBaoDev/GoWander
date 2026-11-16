package analytics

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"slices"
	"testing"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
)

func TestOpenCountsADayAndNothingElse(t *testing.T) {
	c := New(nil, slog.New(slog.NewTextHandler(io.Discard, nil)))
	r := chi.NewRouter()
	c.Mount(r)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/api/v1/usage/open", nil))
	if rec.Code != http.StatusNoContent || rec.Body.Len() != 0 {
		t.Fatalf("%d %q", rec.Code, rec.Body)
	}
	if e := <-c.queue; !e.Open || len(e.Day) != 10 || e.Mode != "" || e.From != (mobility.Coordinate{}) {
		t.Fatalf("event %+v", e)
	}
}

func TestEventOf(t *testing.T) {
	bus := func(id, short, long string) mobility.Leg {
		return mobility.Leg{Transit: true, Line: &mobility.Line{ID: id, ShortName: short, LongName: long}}
	}
	metro := bus("m1", "", "Metro số 1")
	metro.Mode = "SUBWAY"
	routes := []mobility.Route{
		{Legs: []mobility.Leg{{Mode: "WALK"}, bus("b19", "19", "Bến Thành - Đại học Quốc gia"), {Mode: "WALK"}}},
		{Legs: []mobility.Leg{bus("b19", "19", ""), metro, bus("x", "", "")}},
	}
	// 23:30 UTC is already the next day in Ho Chi Minh City.
	e := EventOf(mobility.Query{Transit: true, Walk: true}, routes, time.Date(2026, 11, 15, 23, 30, 0, 0, time.UTC))
	if e.Day != "2026-11-16" || e.Mode != "TRANSIT" {
		t.Fatalf("day/mode: %+v", e)
	}
	if !slices.Equal(e.LineIDs, []string{"b19", "m1", "x"}) || !slices.Equal(e.Lines, []string{"19", "Metro số 1", "x"}) {
		t.Fatalf("lines counted once each, named for people: %v %v", e.LineIDs, e.Lines)
	}
	if !slices.Equal(e.Vehicles, []string{"BUS", "METRO", "BUS"}) {
		t.Fatalf("vehicles %v", e.Vehicles)
	}
	for q, want := range map[*mobility.Query]string{
		{Direct: "CAR"}: "CAR", {Direct: "BICYCLE"}: "BICYCLE", {BusOnly: true}: "BUS", {Walk: true}: "WALK",
	} {
		if got := EventOf(*q, nil, time.Now()).Mode; got != want {
			t.Errorf("%+v: mode %s, want %s", *q, got, want)
		}
	}
}
