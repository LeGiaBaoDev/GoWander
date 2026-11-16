//go:build integration

package analytics

import (
	"context"
	"errors"
	"os"
	"testing"

	"github.com/golang-migrate/migrate/v4"
	"github.com/jackc/pgx/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

// TEST database only (database.TestDatabaseURL): a province with an old one mapped inside it (as OSM does after the
// 2025 merger), one place, counters written as the API role.
func TestSaveCountsByProvinceAndPlace(t *testing.T) {
	ctx := context.Background()
	ownerURL, appURL := os.Getenv("MIGRATE_DATABASE_URL"), os.Getenv("DATABASE_URL")
	if ownerURL == "" || appURL == "" {
		t.Fatal("MIGRATE_DATABASE_URL and DATABASE_URL must be set (load ../.env first)")
	}
	ownerURL, appURL = database.TestDatabaseURL(ownerURL), database.TestDatabaseURL(appURL)
	m, err := database.NewMigrator(ownerURL)
	if err != nil {
		t.Fatal(err)
	}
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatal(err)
	}
	m.Close()
	owner, err := pgx.Connect(ctx, ownerURL)
	if err != nil {
		t.Fatal(err)
	}
	defer owner.Close(ctx)
	var placeID string
	err = owner.QueryRow(ctx, `
		WITH _a AS (DELETE FROM admin_areas), _u AS (DELETE FROM usage_route_plans), _b AS (DELETE FROM usage_bus_lines), _p AS (DELETE FROM usage_place_routes),
		     _o AS (DELETE FROM usage_app_opens),
		     _w AS (INSERT INTO admin_areas (code, name, geom) VALUES ('rT', 'Thành phố Thử', ST_Multi(ST_MakeEnvelope(106.6, 10.7, 106.75, 10.8, 4326))),
		                                                         ('rOld', 'Tỉnh Cũ', ST_Multi(ST_MakeEnvelope(106.69, 10.76, 106.71, 10.78, 4326))))
		INSERT INTO places (name, slug, location, status, source, source_ref, license, attribution)
		VALUES ('Chợ Thử', 'cho-thu-' || floor(random() * 1e9)::bigint, ST_SetSRID(ST_MakePoint(106.80, 10.85), 4326)::geography,
		        'PUBLISHED', 'FIXTURE', 'usage-test-' || floor(random() * 1e9)::bigint, 'test', 'test') RETURNING id::text`).Scan(&placeID)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		owner.Exec(context.Background(), `UPDATE places SET status = 'HIDDEN' WHERE id = $1::uuid`, placeID)
	})
	pool, err := database.NewPool(ctx, appURL)
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()

	// From inside the ward to ~10 m from the place, twice; the second search also suggests line 19.
	e := Event{Day: "2026-11-16", Mode: "BUS", From: mobility.Coordinate{Lat: 10.77, Lon: 106.70}, To: mobility.Coordinate{Lat: 10.85009, Lon: 106.80}}
	if err := Save(ctx, pool, e); err != nil {
		t.Fatal(err)
	}
	e.LineIDs, e.Lines = []string{"b19"}, []string{"19"}
	if err := Save(ctx, pool, e); err != nil {
		t.Fatal(err)
	}
	var from, to, line, place int
	err = owner.QueryRow(ctx, `SELECT
		(SELECT plans FROM usage_route_plans WHERE day = '2026-11-16' AND mode = 'BUS' AND side = 'FROM' AND area_code = 'rT'),
		(SELECT plans FROM usage_route_plans WHERE day = '2026-11-16' AND mode = 'BUS' AND side = 'TO' AND area_code = ''),
		(SELECT suggested FROM usage_bus_lines WHERE line_id = 'b19' AND area_code = 'rT'),
		(SELECT plans FROM usage_place_routes WHERE place_id = $1::uuid)`, placeID).Scan(&from, &to, &line, &place)
	if err != nil || from != 2 || to != 2 || line != 1 || place != 2 {
		t.Fatalf("from %d to %d line %d place %d: %v", from, to, line, place, err)
	}
	for range 2 {
		if err := Save(ctx, pool, Event{Day: "2026-11-16", Open: true}); err != nil {
			t.Fatal(err)
		}
	}
	var opens int
	if err := owner.QueryRow(ctx, `SELECT opens FROM usage_app_opens WHERE day = '2026-11-16'`).Scan(&opens); err != nil || opens != 2 {
		t.Fatalf("app opens %d %v", opens, err)
	}
	// The API role still cannot change content.
	if _, err := pool.Exec(ctx, `UPDATE places SET name = 'x' WHERE id = $1::uuid`, placeID); err == nil {
		t.Fatal("the API role could update places")
	}
}
