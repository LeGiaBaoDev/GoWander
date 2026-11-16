// Package analytics keeps the daily usage counters (ADR-038). It never stores coordinates, IPs or device ids: a search's
// points are turned into province codes inside the database and only the per-day counts are written.
package analytics

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"slices"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
)

// Vietnam has no daylight saving time; a fixed zone avoids depending on the host's tzdata.
var hcm = time.FixedZone("ICT", 7*3600)

// RetentionMonths: counters older than this are deleted (ADR-038 §6).
const RetentionMonths = 25

// Event is one app start (Open) or one successful route search, as far as the counters need it.
type Event struct {
	Day      string // YYYY-MM-DD in Asia/Ho_Chi_Minh
	Open     bool   // an app start: only the day is counted
	Mode     string // WALK, TRANSIT, BUS, CAR, BICYCLE
	From, To mobility.Coordinate
	LineIDs  []string // transit lines suggested in any of the returned routes, each once
	Lines    []string // display names, same order
}

// EventOf reduces a search and its answer to what is counted.
func EventOf(q mobility.Query, routes []mobility.Route, now time.Time) Event {
	e := Event{Day: now.In(hcm).Format(time.DateOnly), From: q.Origin, To: q.Destination}
	switch {
	case q.Direct != "":
		e.Mode = q.Direct
	case q.BusOnly:
		e.Mode = "BUS"
	case q.Transit:
		e.Mode = "TRANSIT"
	default:
		e.Mode = "WALK"
	}
	for _, r := range routes {
		for _, l := range r.Legs {
			if !l.Transit || l.Line == nil || l.Line.ID == "" || slices.Contains(e.LineIDs, l.Line.ID) {
				continue
			}
			name := l.Line.ShortName
			if name == "" {
				name = l.Line.LongName
			}
			if name == "" {
				name = l.Line.ID
			}
			e.LineIDs, e.Lines = append(e.LineIDs, l.Line.ID), append(e.Lines, name)
		}
	}
	return e
}

// Counter writes events in the background. Route planning never waits for it (ADR-038 §7).
type Counter struct {
	Pool  *pgxpool.Pool
	Log   *slog.Logger
	queue chan Event
}

func New(pool *pgxpool.Pool, log *slog.Logger) *Counter {
	return &Counter{Pool: pool, Log: log, queue: make(chan Event, 1000)}
}

// Record queues a search; when the queue is full the search is not counted.
func (c *Counter) Record(q mobility.Query, routes []mobility.Route) {
	select {
	case c.queue <- EventOf(q, routes, time.Now()):
	default:
		c.Log.Warn("usage counter queue full, search not counted")
	}
}

// RecordOpen queues one app start for today.
func (c *Counter) RecordOpen() {
	select {
	case c.queue <- Event{Day: time.Now().In(hcm).Format(time.DateOnly), Open: true}:
	default:
		c.Log.Warn("usage counter queue full, app start not counted")
	}
}

// Mount serves POST /api/v1/usage/open: the app calls it once per start. No body, nothing stored but the day count.
func (c *Counter) Mount(r chi.Router) {
	r.Post("/api/v1/usage/open", func(w http.ResponseWriter, _ *http.Request) {
		c.RecordOpen()
		w.WriteHeader(http.StatusNoContent)
	})
}

// Run writes queued events and deletes expired counters once a day, until ctx ends. Events still queued then are lost.
func (c *Counter) Run(ctx context.Context) {
	c.expire(ctx)
	day := time.NewTicker(24 * time.Hour)
	defer day.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-day.C:
			c.expire(ctx)
		case e := <-c.queue:
			wctx, cancel := context.WithTimeout(ctx, 5*time.Second)
			if err := Save(wctx, c.Pool, e); err != nil {
				c.Log.Warn("usage counter write failed", "err", err) // no coordinates in the log
			}
			cancel()
		}
	}
}

func (c *Counter) expire(ctx context.Context) {
	cutoff := time.Now().In(hcm).AddDate(0, -RetentionMonths, 0).Format(time.DateOnly)
	for _, table := range []string{"usage_route_plans", "usage_bus_lines", "usage_place_routes", "usage_app_opens"} {
		if _, err := c.Pool.Exec(ctx, "DELETE FROM "+table+" WHERE day < $1::date", cutoff); err != nil {
			c.Log.Warn("usage counter expiry failed", "table", table, "err", err)
		}
	}
}

// areaOf picks the province containing a point; where OSM still maps an old province inside its merged successor,
// the largest (the new one) wins.
const areaOf = `coalesce((SELECT code FROM admin_areas WHERE ST_Intersects(geom, ST_SetSRID(ST_MakePoint(%s, %s), 4326)) ORDER BY ST_Area(geom) DESC LIMIT 1), '')`

// saveSQL turns the two points into province codes and a nearby published place, then bumps the counters, in one round trip.
var saveSQL = `
WITH f AS (SELECT ` + fmt.Sprintf(areaOf, "$3", "$2") + ` AS code),
     t AS (SELECT ` + fmt.Sprintf(areaOf, "$5", "$4") + ` AS code),
     plans AS (
       INSERT INTO usage_route_plans AS u (day, mode, side, area_code, plans)
       SELECT $1::date, $6, 'FROM', code, 1 FROM f UNION ALL SELECT $1::date, $6, 'TO', code, 1 FROM t
       ON CONFLICT (day, mode, side, area_code) DO UPDATE SET plans = u.plans + 1),
     place AS (
       INSERT INTO usage_place_routes AS u (day, place_id, plans)
       SELECT $1::date, p.id, 1 FROM (
         SELECT id FROM places WHERE status = 'PUBLISHED' AND ST_DWithin(location, ST_SetSRID(ST_MakePoint($5, $4), 4326)::geography, 30)
         ORDER BY location <-> ST_SetSRID(ST_MakePoint($5, $4), 4326)::geography LIMIT 1) p
       ON CONFLICT (day, place_id) DO UPDATE SET plans = u.plans + 1)
INSERT INTO usage_bus_lines AS u (day, line_id, line_name, area_code, suggested)
SELECT $1::date, l.id, l.name, f.code, 1 FROM f, unnest($7::text[], $8::text[]) AS l(id, name)
ON CONFLICT (day, line_id, area_code) DO UPDATE SET suggested = u.suggested + 1, line_name = EXCLUDED.line_name`

// Save counts one event.
func Save(ctx context.Context, pool *pgxpool.Pool, e Event) error {
	if e.Open {
		_, err := pool.Exec(ctx, `INSERT INTO usage_app_opens AS u (day, opens) VALUES ($1::date, 1)
			ON CONFLICT (day) DO UPDATE SET opens = u.opens + 1`, e.Day)
		return err
	}
	_, err := pool.Exec(ctx, saveSQL, e.Day, e.From.Lat, e.From.Lon, e.To.Lat, e.To.Lon, e.Mode, e.LineIDs, e.Lines)
	return err
}
