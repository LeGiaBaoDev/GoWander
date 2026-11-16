//go:build integration

package admin

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/golang-migrate/migrate/v4"
	"github.com/jackc/pgx/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

// emptyAdminTables runs as the owner role on the TEST database only (database.TestDatabaseURL).
const emptyAdminTables = `TRUNCATE admin_audit_logs, admin_sessions, user_roles, user_auth_identities, user_contacts, users`

// setup empties the admin tables of the TEST database and returns a store connected as gowander_admin.
func setup(t *testing.T) *PG {
	t.Helper()
	ctx := context.Background()
	owner, admin := os.Getenv("MIGRATE_DATABASE_URL"), os.Getenv("ADMIN_DATABASE_URL")
	if owner == "" || admin == "" {
		t.Fatal("MIGRATE_DATABASE_URL and ADMIN_DATABASE_URL must be set (load ../.env first)")
	}
	owner, admin = database.TestDatabaseURL(owner), database.TestDatabaseURL(admin)
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
	if _, err := conn.Exec(ctx, emptyAdminTables); err != nil {
		t.Fatal(err)
	}
	pool, err := database.NewPool(ctx, admin)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	return &PG{Pool: pool}
}

func google(sub, email string) Identity {
	return Identity{Issuer: googleIssuer, Subject: sub, Email: email, EmailVerified: true, Name: "Người " + sub}
}

func TestStore(t *testing.T) {
	s := setup(t)
	ctx := context.Background()

	t.Run("only the bootstrap email gets in while nobody has a role", func(t *testing.T) {
		if _, err := s.Login(ctx, google("1", "stranger@example.vn"), "owner@example.vn", "r1"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("stranger: %v", err)
		}
		unverified := google("2", "owner@example.vn")
		unverified.EmailVerified = false
		if _, err := s.Login(ctx, unverified, "owner@example.vn", "r2"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("unverified email: %v", err)
		}
		owner, err := s.Login(ctx, google("10", "Owner@Example.VN"), "owner@example.vn", "r3")
		if err != nil {
			t.Fatal(err)
		}
		token, err := s.CreateSession(ctx, owner, time.Hour)
		if err != nil {
			t.Fatal(err)
		}
		se, err := s.Session(ctx, token)
		if err != nil || !se.Has(RoleSystemAdmin) || se.Email != "owner@example.vn" || len(se.CSRF) < 32 {
			t.Fatalf("session: %+v %v", se, err)
		}
		// Bootstrap is a one-time door: once someone holds a role, the same email from another Google account is refused.
		if _, err := s.Login(ctx, google("11", "owner@example.vn"), "owner@example.vn", "r4"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("second google account for the bootstrap email: %v", err)
		}
	})

	t.Run("granted email links on first sign-in; accounts are never merged by email", func(t *testing.T) {
		ops, _ := s.Operators(ctx)
		ownerID := ops[0].UserID
		if err := s.Grant(ctx, ownerID, "Editor@Example.VN", RoleContentEditor, "r5"); err != nil {
			t.Fatal(err)
		}
		if err := s.Grant(ctx, ownerID, "not-an-email", RoleContentEditor, "r6"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("bad email: %v", err)
		}
		if err := s.Grant(ctx, ownerID, "x@example.vn", "ROOT", "r7"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("bad role: %v", err)
		}
		editor, err := s.Login(ctx, google("20", "editor@example.vn"), "", "r8")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := s.Login(ctx, google("21", "editor@example.vn"), "", "r9"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("second google account, same email: %v", err)
		}
		if again, err := s.Login(ctx, google("20", "editor-new@example.vn"), "", "r10"); err != nil || again != editor {
			t.Fatalf("linked account with a changed email: %v %v", again, err)
		}
		// Revoking the last role signs the person out and keeps them out.
		token, _ := s.CreateSession(ctx, editor, time.Hour)
		if err := s.Revoke(ctx, ownerID, editor, RoleContentEditor, "r11"); err != nil {
			t.Fatal(err)
		}
		if _, err := s.Session(ctx, token); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("session after revoke: %v", err)
		}
		if _, err := s.Login(ctx, google("20", "editor@example.vn"), "", "r12"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("login without a role: %v", err)
		}
	})

	t.Run("the last system admin cannot be removed", func(t *testing.T) {
		ops, _ := s.Operators(ctx)
		var ownerID string
		for _, o := range ops {
			if strings.Contains(strings.Join(o.Roles, ","), RoleSystemAdmin) {
				ownerID = o.UserID
			}
		}
		if err := s.Revoke(ctx, ownerID, ownerID, RoleSystemAdmin, "r13"); !errors.Is(err, ErrLastSystemAdmin) {
			t.Fatalf("last system admin: %v", err)
		}
		if err := s.Grant(ctx, ownerID, "second@example.vn", RoleSystemAdmin, "r14"); err != nil {
			t.Fatal(err)
		}
		if err := s.Revoke(ctx, ownerID, ownerID, RoleSystemAdmin, "r15"); err != nil {
			t.Fatalf("with a second system admin: %v", err)
		}
	})

	t.Run("the overview reads content counts as the admin role", func(t *testing.T) {
		o, err := s.Overview(ctx)
		if err != nil || o.Places < o.Published || o.WithPhoto > o.Places {
			t.Fatalf("%+v %v", o, err)
		}
	})

	t.Run("audit log records the changes and the admin role cannot change it", func(t *testing.T) {
		entries, err := s.AuditLog(ctx, 50)
		if err != nil {
			t.Fatal(err)
		}
		actions := map[string]int{}
		for _, e := range entries {
			actions[e.Action]++
		}
		if actions["admin.bootstrap"] != 1 || actions["role.grant"] != 2 || actions["role.revoke"] != 2 || actions["admin.login"] < 2 {
			t.Fatalf("audit actions: %v", actions)
		}
		// Expected to be refused: gowander_admin only has INSERT and SELECT on the log.
		for _, sql := range []string{`UPDATE admin_audit_logs SET action = 'x'`, `TRUNCATE admin_audit_logs`} {
			if _, err := s.Pool.Exec(ctx, sql); err == nil {
				t.Fatalf("the admin role could run %q", sql)
			}
		}
	})
}

func TestEditPlaces(t *testing.T) {
	s := setup(t)
	ctx := context.Background()
	owner, err := pgx.Connect(ctx, database.TestDatabaseURL(os.Getenv("MIGRATE_DATABASE_URL")))
	if err != nil {
		t.Fatal(err)
	}
	defer owner.Close(ctx)
	// Test database only: a fixture place in two categories, created by the owner role like the seed does.
	var placeID string
	var food, cafe int64
	err = owner.QueryRow(ctx, `
		WITH f AS (INSERT INTO place_categories (name, slug) VALUES ('Ăn uống T', 'food-t') ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name RETURNING id),
		     c AS (INSERT INTO place_categories (name, slug) VALUES ('Cà phê T', 'cafe-t') ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name RETURNING id),
		     p AS (INSERT INTO places (name, slug, location, primary_category_id, status, source, source_ref, license, attribution)
		           SELECT 'Quán Test', 'quan-test-' || floor(random() * 1e9)::bigint, ST_SetSRID(ST_MakePoint(106.7, 10.77), 4326)::geography, f.id,
		                  'PUBLISHED', 'FIXTURE', 'admin-test-' || floor(random() * 1e9)::bigint, 'test', 'test' FROM f RETURNING id)
		SELECT p.id::text, f.id, c.id FROM p, f, c`).Scan(&placeID, &food, &cafe)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := owner.Exec(ctx, `INSERT INTO place_category_links VALUES ($1::uuid, $2)`, placeID, food); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		owner.Exec(context.Background(), `UPDATE places SET status = 'HIDDEN' WHERE id = $1::uuid`, placeID)
	})

	ops, _ := s.Operators(ctx)
	if len(ops) == 0 { // TestStore may not have run first: make an operator for the audit actor.
		id, err := s.Login(ctx, google("e1", "edit@example.vn"), "edit@example.vn", "r")
		if err != nil {
			t.Fatal(err)
		}
		ops = []Operator{{UserID: id}}
	}
	actor := ops[0].UserID

	p, err := s.Place(ctx, placeID)
	if err != nil || p.Name != "Quán Test" || p.CategoryID != food || p.EditedAt != nil {
		t.Fatalf("read: %+v %v", p, err)
	}
	p.Name, p.Description, p.Status, p.CategoryID = "Quán Test mới", "Cà phê vợt", "HIDDEN", cafe
	if err := s.UpdatePlace(ctx, actor, p, "req-edit"); err != nil {
		t.Fatal(err)
	}
	got, _ := s.Place(ctx, placeID)
	if got.Name != "Quán Test mới" || got.Description != "Cà phê vợt" || got.Status != "HIDDEN" || got.CategoryID != cafe || got.EditedAt == nil {
		t.Fatalf("after save: %+v", got)
	}
	var links []int64
	rows, _ := owner.Query(ctx, `SELECT category_id FROM place_category_links WHERE place_id = $1::uuid`, placeID)
	links, _ = pgx.CollectRows(rows, pgx.RowTo[int64])
	if len(links) != 1 || links[0] != cafe {
		t.Fatalf("links follow the main category: %v", links)
	}
	if list, total, err := s.Places(ctx, PlaceQuery{Q: "quan test moi", Status: "HIDDEN", Missing: "photo"}); err != nil || total < 1 || list[0].ID == "" {
		t.Fatalf("search without accents: %v %d %v", list, total, err)
	}
	if err := s.UpdatePlace(ctx, actor, Place{ID: placeID, Name: "x", Status: "PUBLISHED", CategoryID: 999999}, "r"); !errors.Is(err, ErrBadInput) {
		t.Fatalf("unknown category: %v", err)
	}
	if err := s.UpdatePlace(ctx, actor, Place{ID: "00000000-0000-0000-0000-000000000000", Name: "x", Status: "DRAFT"}, "r"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unknown place: %v", err)
	}
	// Provenance is import-only: the admin role has no UPDATE on it.
	if _, err := s.Pool.Exec(ctx, `UPDATE places SET source_ref = 'x' WHERE id = $1::uuid`, placeID); err == nil {
		t.Fatal("the admin role could change source_ref")
	}
	if err := s.UpdateCategory(ctx, actor, Category{ID: cafe, Name: "Cà phê T2", SortOrder: 3, Active: false}, "req-cat"); err != nil {
		t.Fatal(err)
	}
	entries, _ := s.AuditLog(ctx, 20)
	seen := map[string]bool{}
	for _, e := range entries {
		seen[e.Action+":"+e.RequestID] = true
	}
	if !seen["place.update:req-edit"] || !seen["category.update:req-cat"] {
		t.Fatalf("audit: %v", seen)
	}
}

func TestUsageReadsCounters(t *testing.T) {
	s := setup(t)
	ctx := context.Background()
	owner, err := pgx.Connect(ctx, database.TestDatabaseURL(os.Getenv("MIGRATE_DATABASE_URL")))
	if err != nil {
		t.Fatal(err)
	}
	defer owner.Close(ctx)
	// Test database only: two wards, counters for today and one day past the 30-day window.
	_, err = owner.Exec(ctx, `
		DELETE FROM admin_areas; DELETE FROM usage_route_plans; DELETE FROM usage_bus_lines; DELETE FROM usage_place_routes;
		INSERT INTO admin_areas (code, name, geom) VALUES
		  ('rA', 'Phường A', ST_Multi(ST_MakeEnvelope(0, 0, 1, 1, 4326))), ('rB', 'Phường B', ST_Multi(ST_MakeEnvelope(1, 0, 2, 1, 4326)));
		INSERT INTO usage_route_plans VALUES
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'BUS', 'FROM', 'rA', 7), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'BUS', 'TO', 'rB', 7),
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'CAR', 'FROM', 'rB', 2), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'CAR', 'TO', '', 2),
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date - 30, 'WALK', 'FROM', 'rA', 50);
		INSERT INTO usage_bus_lines VALUES
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'b19', '19', 'rA', 6), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'b8', '8', 'rA', 4)`)
	if err != nil {
		t.Fatal(err)
	}
	u, err := s.Usage(ctx, UsageQuery{Days: 30})
	if err != nil {
		t.Fatal(err)
	}
	if len(u.Daily) != 30 || u.Total != 9 || len(u.Areas) != 2 {
		t.Fatalf("30 days, origin side only, older rows left out: days %d total %d areas %d", len(u.Daily), u.Total, len(u.Areas))
	}
	if len(u.Lines) != 1 || u.Lines[0].Label != "19" || len(u.From) != 1 || u.From[0].Label != "Phường A" || len(u.To) != 1 || u.To[0].Label != "Phường B" {
		t.Fatalf("items under 5 are left out of the rankings: lines %v from %v to %v", u.Lines, u.From, u.To)
	}
	if len(u.Modes) != 2 || u.Modes[0].Label != "Xe buýt" {
		t.Fatalf("modes %v", u.Modes)
	}
	b, err := s.Usage(ctx, UsageQuery{Days: 30, Area: "rB"})
	if err != nil || b.Total != 2 || b.AreaName != "Phường B" || b.To != nil {
		t.Fatalf("filtered by origin ward, no destinations: %+v %v", b, err)
	}
	if x, _ := s.Usage(ctx, UsageQuery{Days: 7, Area: "nope"}); x.Area != "" || x.Total != 9 {
		t.Fatalf("an unknown ward is ignored: %+v", x)
	}
}
