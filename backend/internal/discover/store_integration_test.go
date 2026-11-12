//go:build integration

// Store tests against gowander_test with FIXTURE rows (never dev data):
//
//	cd backend && set -a && . ../.env && set +a && go test -tags integration -p 1 ./...   (-p 1: packages share gowander_test)
package discover

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"

	"github.com/golang-migrate/migrate/v4"
	"github.com/jackc/pgx/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

// fixtures replaces every Discover row in the TEST database (URL forced through database.TestDatabaseURL).
const fixtures = `
DELETE FROM place_foods; DELETE FROM food_items; DELETE FROM place_media; DELETE FROM place_category_links;
DELETE FROM historical_stories; DELETE FROM places; DELETE FROM place_categories;
ALTER TABLE place_categories ALTER COLUMN id RESTART WITH 1;
INSERT INTO place_categories (name, name_en, slug, sort_order) VALUES
  ('Chợ và mua sắm', 'Markets', 'market', 60), ('Ăn uống', 'Food', 'food', 70), ('Bảo tàng', 'Museums', 'museum', 10),
  ('Ẩn', NULL, 'hidden-cat', 99);
UPDATE place_categories SET is_active = false WHERE slug = 'hidden-cat';
INSERT INTO places (id, name, name_en, slug, location, address, primary_category_id, status, source, source_ref, source_url, license, attribution) VALUES
  ('0190f0c2-0000-7000-8000-000000000001', 'Chợ Bến Thành', 'Ben Thanh Market', 'cho-ben-thanh', 'SRID=4326;POINT(106.69805 10.77253)', 'Lê Lợi', 1, 'PUBLISHED', 'FIXTURE', 'f/1', 'https://example.test/1', 'FIXTURE', 'FIXTURE'),
  ('0190f0c2-0000-7000-8000-000000000002', 'Bảo tàng Mỹ thuật', NULL, 'bao-tang-my-thuat', 'SRID=4326;POINT(106.69960 10.76990)', NULL, 3, 'PUBLISHED', 'FIXTURE', 'f/2', NULL, 'FIXTURE', 'FIXTURE'),
  ('0190f0c2-0000-7000-8000-000000000003', 'Phở 100% bò', NULL, 'pho-100-bo', 'SRID=4326;POINT(106.70000 10.77300)', NULL, 2, 'PUBLISHED', 'FIXTURE', 'f/3', NULL, 'FIXTURE', 'FIXTURE'),
  ('0190f0c2-0000-7000-8000-000000000004', 'Bản nháp gần chợ', NULL, 'ban-nhap', 'SRID=4326;POINT(106.69810 10.77255)', NULL, 2, 'DRAFT', 'FIXTURE', 'f/4', NULL, 'FIXTURE', 'FIXTURE'),
  ('0190f0c2-0000-7000-8000-000000000005', 'Đã ẩn gần chợ', NULL, 'da-an', 'SRID=4326;POINT(106.69815 10.77256)', NULL, 2, 'HIDDEN', 'FIXTURE', 'f/5', NULL, 'FIXTURE', 'FIXTURE'),
  ('0190f0c2-0000-7000-8000-000000000006', 'Quán xa ở Suối Tiên', NULL, 'quan-xa', 'SRID=4326;POINT(106.81417 10.87957)', NULL, 2, 'PUBLISHED', 'FIXTURE', 'f/6', NULL, 'FIXTURE', 'FIXTURE');
INSERT INTO place_category_links SELECT id, primary_category_id FROM places;
INSERT INTO place_category_links VALUES ('0190f0c2-0000-7000-8000-000000000001', 2);
INSERT INTO place_media (place_id, storage_key, media_type, alt_text, source_credit, license_info, sort_order, is_cover) VALUES
  ('0190f0c2-0000-7000-8000-000000000001', 'fixture/b.jpg', 'IMAGE', NULL, 'FIXTURE', 'FIXTURE', 2, false),
  ('0190f0c2-0000-7000-8000-000000000001', 'fixture/a.jpg', 'IMAGE', 'Cổng chợ', 'FIXTURE', 'FIXTURE', 1, true);
INSERT INTO food_items (id, name, name_en, slug, status) VALUES
  ('0190f0c2-0000-7000-8000-0000000000f1', 'Phở bò', 'Beef pho', 'pho-bo', 'PUBLISHED'),
  ('0190f0c2-0000-7000-8000-0000000000f2', 'Món nháp', NULL, 'mon-nhap', 'DRAFT');
INSERT INTO place_foods (place_id, food_id, price_min, price_max) VALUES
  ('0190f0c2-0000-7000-8000-000000000001', '0190f0c2-0000-7000-8000-0000000000f1', 45000, 70000),
  ('0190f0c2-0000-7000-8000-000000000001', '0190f0c2-0000-7000-8000-0000000000f2', 1, 2);
`

func setup(t *testing.T) *PG {
	t.Helper()
	ctx := context.Background()
	owner, app := os.Getenv("MIGRATE_DATABASE_URL"), os.Getenv("DATABASE_URL")
	if owner == "" || app == "" {
		t.Fatal("MIGRATE_DATABASE_URL and DATABASE_URL must be set (load ../.env first)")
	}
	owner, app = database.TestDatabaseURL(owner), database.TestDatabaseURL(app)
	m, err := database.NewMigrator(owner)
	if err != nil {
		t.Fatal(err)
	}
	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		t.Fatal(err)
	}
	m.Close()
	conn, err := pgx.Connect(ctx, owner)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close(ctx)
	if _, err := conn.Exec(ctx, fixtures); err != nil {
		t.Fatalf("fixtures: %v", err)
	}
	pool, err := database.NewPool(ctx, app) // runtime role: SELECT only
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	return &PG{Pool: pool}
}

func names(ps []PlaceSummary) string {
	out := make([]string, len(ps))
	for i, p := range ps {
		out[i] = p.Name
	}
	return strings.Join(out, " | ")
}

func TestStore(t *testing.T) {
	ctx := context.Background()
	s := setup(t)

	t.Run("categories count only published places and skip inactive", func(t *testing.T) {
		cats, err := s.Categories(ctx)
		if err != nil {
			t.Fatal(err)
		}
		got := map[string]int{}
		for _, c := range cats {
			got[c.Slug] = c.PlaceCount
		}
		if len(cats) != 3 || got["museum"] != 1 || got["food"] != 3 || got["market"] != 1 || cats[0].Slug != "museum" {
			t.Fatalf("%+v", cats)
		}
	})

	t.Run("list pages through published places by name without gaps", func(t *testing.T) {
		var all []PlaceSummary
		var after *Cursor
		for page := 0; page < 10; page++ {
			ps, next, err := s.ListPlaces(ctx, ListQuery{Limit: 2, After: after})
			if err != nil {
				t.Fatal(err)
			}
			all = append(all, ps...)
			if next == nil {
				break
			}
			after = next
		}
		if got := names(all); got != "Bảo tàng Mỹ thuật | Chợ Bến Thành | Phở 100% bò | Quán xa ở Suối Tiên" {
			t.Fatalf("got %s", got)
		}
		if all[1].Category == nil || all[1].Category.Slug != "market" || all[1].Source.Kind != "FIXTURE" || all[1].Lat < 10.77 {
			t.Fatalf("summary fields: %+v", all[1])
		}
	})

	t.Run("list filters by category link and accent-free name", func(t *testing.T) {
		for q, want := range map[ListQuery]string{
			{Category: "food", Limit: 10}: "Chợ Bến Thành | Phở 100% bò | Quán xa ở Suối Tiên",
			{Q: "ben thanh", Limit: 10}:   "Chợ Bến Thành",
			{Q: "MARKET", Limit: 10}:      "Chợ Bến Thành",
			{Q: "100%", Limit: 10}:        "Phở 100% bò",
			{Q: "%", Limit: 10}:           "Phở 100% bò", // % is literal, not a wildcard
			{Q: "_", Limit: 10}:           "",
			{Category: "nope", Limit: 10}: "",
			{Q: "nháp", Limit: 10}:        "", // drafts never leak
		} {
			ps, _, err := s.ListPlaces(ctx, q)
			if err != nil {
				t.Fatal(err)
			}
			if got := names(ps); got != want {
				t.Errorf("%+v: got %q, want %q", q, got, want)
			}
		}
	})

	t.Run("nearby uses radius, orders by distance, excludes unpublished", func(t *testing.T) {
		ps, err := s.Nearby(ctx, NearbyQuery{Lat: 10.77253, Lon: 106.69805, RadiusM: 500, Limit: 10})
		if err != nil {
			t.Fatal(err)
		}
		if got := names(ps); got != "Chợ Bến Thành | Phở 100% bò | Bảo tàng Mỹ thuật" {
			t.Fatalf("got %s", got)
		}
		if ps[0].DistanceM == nil || *ps[0].DistanceM > 1 || *ps[2].DistanceM < 300 || *ps[2].DistanceM > 400 {
			t.Fatalf("distances: %v %v", *ps[0].DistanceM, *ps[2].DistanceM)
		}
		ps, _ = s.Nearby(ctx, NearbyQuery{Lat: 10.77253, Lon: 106.69805, RadiusM: 500, Category: "museum", Limit: 10})
		if names(ps) != "Bảo tàng Mỹ thuật" {
			t.Fatalf("category: %s", names(ps))
		}
		ps, _ = s.Nearby(ctx, NearbyQuery{Lat: 10.77253, Lon: 106.69805, RadiusM: 500, Limit: 1})
		if len(ps) != 1 {
			t.Fatalf("limit: %d", len(ps))
		}
	})

	t.Run("detail has categories, ordered media, published foods only", func(t *testing.T) {
		d, err := s.Place(ctx, "0190f0c2-0000-7000-8000-000000000001")
		if err != nil {
			t.Fatal(err)
		}
		if len(d.Categories) != 2 || d.Categories[0].Slug != "market" || len(d.Media) != 2 || d.Media[0].StorageKey != "fixture/a.jpg" ||
			!d.Media[0].IsCover || len(d.Foods) != 1 || d.Foods[0].Slug != "pho-bo" || *d.Foods[0].PriceMin != 45000 || d.Foods[0].Currency != "VND" {
			t.Fatalf("%+v", d)
		}
		for _, id := range []string{"0190f0c2-0000-7000-8000-000000000004", "0190f0c2-0000-7000-8000-000000000005", "0190f0c2-0000-7000-8000-0000000000ff"} {
			if _, err := s.Place(ctx, id); !errors.Is(err, ErrNotFound) {
				t.Errorf("%s: want ErrNotFound, got %v", id, err)
			}
		}
	})

	t.Run("ready", func(t *testing.T) {
		if err := s.Ready(ctx); err != nil {
			t.Fatal(err)
		}
	})
}
