//go:build integration

// Migration tests against the dedicated test database (never the dev one):
//
//	cd backend && set -a && . ../.env && set +a && go test -tags integration -p 1 ./...   (-p 1: packages share gowander_test)
package database

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"

	"github.com/golang-migrate/migrate/v4"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

var discoverTables = []string{"food_items", "historical_stories", "place_categories", "place_category_links", "place_foods", "place_media", "places"}

func testURL(t *testing.T, env string) string {
	t.Helper()
	u := os.Getenv(env)
	if u == "" {
		t.Fatalf("%s is not set (load ../.env first)", env)
	}
	return TestDatabaseURL(u)
}

func tables(t *testing.T, conn *pgx.Conn) []string {
	t.Helper()
	rows, err := conn.Query(context.Background(),
		`SELECT table_name FROM information_schema.tables WHERE table_schema = 'gowander' AND table_name <> 'schema_migrations' ORDER BY 1`)
	if err != nil {
		t.Fatal(err)
	}
	got, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		t.Fatal(err)
	}
	return got
}

func TestMigrationsUpDownUp(t *testing.T) {
	ctx := context.Background()
	url := testURL(t, "MIGRATE_DATABASE_URL")
	m, err := NewMigrator(url)
	if err != nil {
		t.Fatal(err)
	}
	defer m.Close()
	if err := m.Down(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatalf("reset: %v", err)
	}
	conn, err := pgx.Connect(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close(ctx)

	if err := m.Up(); err != nil {
		t.Fatalf("fresh up: %v", err)
	}
	if got := tables(t, conn); strings.Join(got, ",") != strings.Join(discoverTables, ",") {
		t.Fatalf("tables after up: %v", got)
	}
	// Least privilege: the runtime role can SELECT exactly the Discover tables and nothing else.
	rows, err := conn.Query(ctx, `SELECT table_name || ':' || privilege_type FROM information_schema.role_table_grants
		WHERE grantee = 'gowander_app' ORDER BY 1`)
	if err != nil {
		t.Fatal(err)
	}
	grants, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		t.Fatal(err)
	}
	var want []string
	for _, tbl := range discoverTables {
		want = append(want, tbl+":SELECT")
	}
	if strings.Join(grants, ",") != strings.Join(want, ",") {
		t.Fatalf("gowander_app grants:\n got %v\nwant %v", grants, want)
	}
	if v, dirty, err := m.Version(); err != nil || dirty || v == 0 {
		t.Fatalf("version %d dirty %v err %v", v, dirty, err)
	}
	// Existing, already-migrated database: up again is a no-op.
	if err := m.Up(); !errors.Is(err, migrate.ErrNoChange) {
		t.Fatalf("second up: want ErrNoChange, got %v", err)
	}
	if err := m.Down(); err != nil {
		t.Fatalf("down: %v", err)
	}
	if got := tables(t, conn); len(got) != 0 {
		t.Fatalf("tables left after down: %v", got)
	}
	if err := m.Up(); err != nil {
		t.Fatalf("up after down: %v", err)
	}
}

func TestSchemaConstraints(t *testing.T) {
	ctx := context.Background()
	url := testURL(t, "MIGRATE_DATABASE_URL")
	m, err := NewMigrator(url)
	if err != nil {
		t.Fatal(err)
	}
	defer m.Close()
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatal(err)
	}
	conn, err := pgx.Connect(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close(ctx)
	tx, err := conn.Begin(ctx)
	if err != nil {
		t.Fatal(err)
	}
	defer tx.Rollback(ctx) // leave the test database empty

	exec := func(sql string) error {
		_, err := tx.Exec(ctx, "SAVEPOINT s")
		if err != nil {
			t.Fatal(err)
		}
		_, err = tx.Exec(ctx, sql)
		if err != nil {
			_, _ = tx.Exec(ctx, "ROLLBACK TO SAVEPOINT s")
		}
		return err
	}
	const place = `INSERT INTO places (id, name, slug, location, status, source, source_ref, license, attribution)
		VALUES ('00000000-0000-7000-8000-000000000001', 'Chợ Bến Thành', 'cho-ben-thanh',
		'SRID=4326;POINT(106.6981 10.7725)', 'PUBLISHED', 'FIXTURE', 'test/1', 'FIXTURE', 'FIXTURE')`
	if err := exec(place); err != nil {
		t.Fatalf("valid place rejected: %v", err)
	}
	mustFail := map[string]string{
		"bad status":         strings.Replace(place, "'PUBLISHED'", "'LIVE'", 1),
		"bad source":         strings.Replace(strings.Replace(place, "'FIXTURE', 'test/1'", "'WIKI', 'test/2'", 1), "cho-ben-thanh", "x2", 1),
		"duplicate slug":     strings.Replace(place, "test/1", "test/3", 1),
		"duplicate source":   strings.Replace(place, "cho-ben-thanh", "x4", 1),
		"blank name":         strings.Replace(strings.Replace(place, "'Chợ Bến Thành'", "'  '", 1), "cho-ben-thanh", "x5", 1),
		"bad slug":           strings.Replace(strings.Replace(place, "cho-ben-thanh", "Chợ Bến", 1), "test/1", "test/6", 1),
		"self parent":        `INSERT INTO place_categories (id, parent_id, name, slug) OVERRIDING SYSTEM VALUE VALUES (99, 99, 'x', 'x')`,
		"negative price":     `INSERT INTO food_items (id, name, slug, status) VALUES ('00000000-0000-7000-8000-0000000000f1','Phở','pho','PUBLISHED'); INSERT INTO place_foods (place_id, food_id, price_min) VALUES ('00000000-0000-7000-8000-000000000001','00000000-0000-7000-8000-0000000000f1',-1)`,
		"min above max":      `INSERT INTO food_items (id, name, slug, status) VALUES ('00000000-0000-7000-8000-0000000000f2','Cơm','com','PUBLISHED'); INSERT INTO place_foods (place_id, food_id, price_min, price_max) VALUES ('00000000-0000-7000-8000-000000000001','00000000-0000-7000-8000-0000000000f2',50000,40000)`,
		"bad currency":       `INSERT INTO food_items (id, name, slug, status) VALUES ('00000000-0000-7000-8000-0000000000f3','Bún','bun','PUBLISHED'); INSERT INTO place_foods (place_id, food_id, currency_code) VALUES ('00000000-0000-7000-8000-000000000001','00000000-0000-7000-8000-0000000000f3','vnd')`,
		"media no license":   `INSERT INTO place_media (place_id, storage_key, media_type, source_credit) VALUES ('00000000-0000-7000-8000-000000000001','k','IMAGE','me')`,
		"two covers":         `INSERT INTO place_media (place_id, storage_key, media_type, source_credit, license_info, is_cover) VALUES ('00000000-0000-7000-8000-000000000001','a','IMAGE','me','CC-BY',true), ('00000000-0000-7000-8000-000000000001','b','IMAGE','me','CC-BY',true)`,
		"story no source":    `INSERT INTO historical_stories (title, content, language_code, status) VALUES ('t','c','vi','DRAFT')`,
		"story bad lang":     `INSERT INTO historical_stories (title, content, language_code, status, source_url) VALUES ('t','c','fr','DRAFT','https://x')`,
		"published undated":  `INSERT INTO historical_stories (title, content, language_code, status, source_url) VALUES ('t','c','vi','PUBLISHED','https://x')`,
		"link unknown place": `INSERT INTO place_categories (name, slug) VALUES ('Bảo tàng','museum'); INSERT INTO place_category_links (place_id, category_id) VALUES ('00000000-0000-7000-8000-00000000dead', currval(pg_get_serial_sequence('place_categories','id')))`,
	}
	for name, sql := range mustFail {
		err := exec(sql)
		pgErr, ok := errors.AsType[*pgconn.PgError](err)
		if !ok || !strings.HasPrefix(pgErr.Code, "23") { // class 23 = integrity constraint violation, not a typo in the test SQL
			t.Errorf("%s: want constraint violation, got %v", name, err)
		}
	}
	// Name search ignores Vietnamese diacritics.
	var n int
	if err := tx.QueryRow(ctx, `SELECT count(*) FROM places WHERE search_text(name) LIKE '%' || search_text('ben thanh') || '%'`).Scan(&n); err != nil || n != 1 {
		t.Fatalf("unaccent search: n=%d err=%v", n, err)
	}
}
