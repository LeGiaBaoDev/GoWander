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
const emptyAdminTables = `TRUNCATE admin_audit_logs, admin_sessions, admin_password_resets, admin_credentials, user_roles, user_contacts, users`

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

// account creates an operator with a known password (via CreateAccount, then a change) and returns its id.
func account(t *testing.T, s *PG, login, role string) string {
	t.Helper()
	ctx := context.Background()
	id, temp, err := s.CreateAccount(ctx, login, login+"@example.vn", "", role)
	if err != nil {
		t.Fatal(err)
	}
	if err := s.ChangePassword(ctx, id, temp, "mật khẩu của "+login, "", "r"); err != nil {
		t.Fatal(err)
	}
	return id
}

func TestAccounts(t *testing.T) {
	s := setup(t)
	ctx := context.Background()

	t.Run("create-account gives a temporary password that must be changed", func(t *testing.T) {
		id, temp, err := s.CreateAccount(ctx, " BaoLe ", "Owner@Example.VN", "Bảo", RoleSystemAdmin)
		if err != nil || len(temp) != 16 {
			t.Fatalf("%v %q", err, temp)
		}
		if _, _, err := s.CreateAccount(ctx, "baole", "other@example.vn", "", RoleSystemAdmin); !errors.Is(err, ErrBadInput) {
			t.Fatalf("a login is unique whatever the case: %v", err)
		}
		if _, _, err := s.CreateAccount(ctx, "khac", "owner@example.vn", "", RoleSystemAdmin); !errors.Is(err, ErrBadInput) {
			t.Fatalf("one login per person: %v", err)
		}
		for _, bad := range [][3]string{{"ab", "a@b.vn", RoleSystemAdmin}, {"ok-login", "not-an-email", RoleSystemAdmin}, {"ok-login", "a@b.vn", "ROOT"}} {
			if _, _, err := s.CreateAccount(ctx, bad[0], bad[1], "", bad[2]); !errors.Is(err, ErrBadInput) {
				t.Errorf("%v: %v", bad, err)
			}
		}
		got, err := s.PasswordLogin(ctx, "BAOLE", temp, "r1")
		if err != nil || got != id {
			t.Fatalf("sign-in with the temporary password: %v", err)
		}
		token, _ := s.CreateSession(ctx, id, time.Hour)
		se, err := s.Session(ctx, token)
		if err != nil || !se.MustChange || se.Login != "baole" || !se.Has(RoleSystemAdmin) || se.Email != "owner@example.vn" {
			t.Fatalf("session %+v %v", se, err)
		}
		other, _ := s.CreateSession(ctx, id, time.Hour)
		if err := s.ChangePassword(ctx, id, "sai", "mật khẩu mới dài", token, "r2"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("wrong current password: %v", err)
		}
		if err := s.ChangePassword(ctx, id, temp, "baole", token, "r3"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("weak password: %v", err)
		}
		if err := s.ChangePassword(ctx, id, temp, "mật khẩu mới dài", token, "r4"); err != nil {
			t.Fatal(err)
		}
		if se, err := s.Session(ctx, token); err != nil || se.MustChange {
			t.Fatalf("this session stays and is unlocked: %+v %v", se, err)
		}
		if _, err := s.Session(ctx, other); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("other sessions are signed out: %v", err)
		}
	})

	t.Run("wrong passwords lock the account for 15 minutes", func(t *testing.T) {
		if _, err := s.PasswordLogin(ctx, "ai-do", "x", "r"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("unknown login: %v", err)
		}
		for i := range maxFailedLogins {
			if _, err := s.PasswordLogin(ctx, "baole", "sai lần "+string(rune('0'+i)), "r"); !errors.Is(err, ErrNotAllowed) {
				t.Fatal(err)
			}
		}
		if _, err := s.PasswordLogin(ctx, "baole", "mật khẩu mới dài", "r"); !errors.Is(err, ErrNotAllowed) {
			t.Fatalf("locked account let the right password in: %v", err)
		}
	})

	t.Run("a reset link works once, unlocks and signs out", func(t *testing.T) {
		if _, _, err := s.StartPasswordReset(ctx, "ai-do", "r"); !errors.Is(err, ErrNotFound) {
			t.Fatalf("unknown login: %v", err)
		}
		email, token, err := s.StartPasswordReset(ctx, "BaoLe", "r5")
		if err != nil || email != "owner@example.vn" || token == "" {
			t.Fatalf("%q %v", email, err)
		}
		if _, _, err := s.StartPasswordReset(ctx, "baole", "r6"); !errors.Is(err, ErrNotFound) {
			t.Fatalf("a second email within 2 minutes: %v", err)
		}
		if err := s.ResetPassword(ctx, token, "ngắn", "r7"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("weak password: %v", err)
		}
		if err := s.ResetPassword(ctx, token, "mật khẩu sau khi quên", "r8"); err != nil {
			t.Fatal(err)
		}
		if err := s.ResetPassword(ctx, token, "mật khẩu lần nữa nha", "r9"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("link used twice: %v", err)
		}
		if _, err := s.PasswordLogin(ctx, "baole", "mật khẩu sau khi quên", "r10"); err != nil {
			t.Fatalf("new password after a lock: %v", err)
		}
	})

	t.Run("reset-password from the server sets a temporary password", func(t *testing.T) {
		temp, err := s.SetTemporaryPassword(ctx, "baole")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := s.PasswordLogin(ctx, "baole", temp, "r"); err != nil {
			t.Fatal(err)
		}
		if _, err := s.SetTemporaryPassword(ctx, "ai-do"); !errors.Is(err, ErrNotFound) {
			t.Fatalf("unknown login: %v", err)
		}
	})

	t.Run("display name and the filtered audit log", func(t *testing.T) {
		var id string
		if err := s.Pool.QueryRow(ctx, `SELECT user_id::text FROM admin_credentials WHERE login = 'baole'`).Scan(&id); err != nil {
			t.Fatal(err)
		}
		if err := s.SetDisplayName(ctx, id, "  Gia Bảo  ", "r-name2"); err != nil {
			t.Fatal(err)
		}
		if err := s.SetDisplayName(ctx, id, " ", "r"); !errors.Is(err, ErrBadInput) {
			t.Fatalf("empty name: %v", err)
		}
		now := time.Now()
		q := AuditQuery{From: now.Add(-time.Hour), To: now.Add(time.Minute), Action: "account.rename", Limit: 10}
		list, total, err := s.AuditPage(ctx, q)
		if err != nil || total != 1 || list[0].ActorName != "Gia Bảo" || list[0].EntityName != "Gia Bảo" {
			t.Fatalf("named actor and object, filtered by action: %d %+v %v", total, list, err)
		}
		all, _, _ := s.AuditPage(ctx, AuditQuery{From: now.Add(-time.Hour), To: now.Add(time.Minute), Limit: 500})
		for _, e := range all {
			if strings.Contains(e.ActorName+e.EntityName, "@") {
				t.Fatalf("an email in the audit log: %+v", e)
			}
		}
		day := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, hcm)
		hours, err := s.AuditActivity(ctx, AuditQuery{From: day, To: day.AddDate(0, 0, 1)})
		if err != nil || len(hours) != 24 {
			t.Fatalf("one day = 24 hourly buckets: %d %v", len(hours), err)
		}
		days, err := s.AuditActivity(ctx, AuditQuery{From: day.AddDate(0, 0, -6), To: day.AddDate(0, 0, 1)})
		sum := 0
		for _, d := range days {
			sum += d.Value
		}
		if err != nil || len(days) != 7 || sum == 0 {
			t.Fatalf("seven days = 7 daily buckets with today's lines: %d %d %v", len(days), sum, err)
		}
	})

	t.Run("the overview reads content counts as the admin role", func(t *testing.T) {
		o, err := s.Overview(ctx)
		if err != nil || o.Places < o.Published || o.WithPhoto > o.Places {
			t.Fatalf("%+v %v", o, err)
		}
	})

	t.Run("audit log records the changes and the admin role cannot change it", func(t *testing.T) {
		entries, err := s.AuditLog(ctx, 100)
		if err != nil {
			t.Fatal(err)
		}
		actions := map[string]int{}
		for _, e := range entries {
			actions[e.Action]++
		}
		if actions["account.create"] != 1 || actions["admin.login_failed"] < maxFailedLogins || actions["admin.password_reset"] != 1 || actions["admin.password_change"] != 1 {
			t.Fatalf("audit actions: %v", actions)
		}
		// Expected to be refused: gowander_admin only has INSERT and SELECT on the log, and cannot rename a login.
		for _, sql := range []string{`UPDATE admin_audit_logs SET action = 'x'`, `TRUNCATE admin_audit_logs`, `UPDATE admin_credentials SET login = 'x'`} {
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
		WITH f AS (INSERT INTO place_categories (name, slug) VALUES ('Ăn uống T', 'food-t') ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, is_active = true RETURNING id),
		     c AS (INSERT INTO place_categories (name, slug) VALUES ('Cà phê T', 'cafe-t') ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, is_active = true RETURNING id),
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

	actor := account(t, s, "editor", RoleContentEditor)

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
	// Test database only: two provinces, app starts, counters for today and one day past the 30-day window.
	_, err = owner.Exec(ctx, `
		DELETE FROM admin_areas; DELETE FROM usage_route_plans; DELETE FROM usage_bus_lines; DELETE FROM usage_place_routes; DELETE FROM usage_app_opens;
		INSERT INTO usage_app_opens VALUES ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 12), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date - 30, 99);
		INSERT INTO admin_areas (code, name, geom) VALUES
		  ('rA', 'Tỉnh A', ST_Multi(ST_MakeEnvelope(0, 0, 1, 1, 4326))), ('rB', 'Tỉnh B', ST_Multi(ST_MakeEnvelope(1, 0, 2, 1, 4326)));
		INSERT INTO usage_route_plans VALUES
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'BUS', 'FROM', 'rA', 7), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'BUS', 'TO', 'rB', 7),
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'CAR', 'FROM', 'rB', 2), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'CAR', 'TO', '', 2),
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date - 30, 'WALK', 'FROM', 'rA', 50);
		INSERT INTO usage_bus_lines VALUES
		  ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'b19', '19', 'rA', 6), ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'b8', '8', 'rA', 4)`)
	if err != nil {
		t.Fatal(err)
	}
	u, err := s.Usage(ctx, UsageQuery{Grain: grainOf("day")})
	if err != nil {
		t.Fatal(err)
	}
	if len(u.Series) != 30 || u.Total != 9 || u.PrevTotal != 50 || len(u.Areas) != 2 || u.Opens != 12 || u.PrevOpens != 99 {
		t.Fatalf("30 days, origin side only; the day 30 days ago is in the previous period: days %d total %d prev %d opens %d/%d",
			len(u.Series), u.Total, u.PrevTotal, u.Opens, u.PrevOpens)
	}
	if m, err := s.Usage(ctx, UsageQuery{Grain: grainOf("month")}); err != nil || len(m.Series) != 12 || m.Series[11].Value < 9 {
		t.Fatalf("by month: %+v %v", m.Series, err)
	}
	if len(u.Lines) != 1 || u.Lines[0].Label != "19" || len(u.From) != 1 || u.From[0].Label != "Tỉnh A" || len(u.To) != 1 || u.To[0].Label != "Tỉnh B" {
		t.Fatalf("items under 5 are left out of the rankings: lines %v from %v to %v", u.Lines, u.From, u.To)
	}
	if len(u.Modes) != 2 || u.Modes[0].Label != "Xe buýt" {
		t.Fatalf("modes %v", u.Modes)
	}
	if u.AreaCounts["rA"] != 7 || len(u.AreaCounts) != 1 {
		t.Fatalf("map counts leave out provinces under 5: %v", u.AreaCounts)
	}
	shapes, err := s.AreaShapes(ctx)
	if err != nil || len(shapes) != 2 || !strings.HasPrefix(shapes[0].Path, "M") || shapes[0].Y > 0 {
		t.Fatalf("shapes for the map (SVG path, y = -latitude): %+v %v", shapes, err)
	}
	b, err := s.Usage(ctx, UsageQuery{Grain: grainOf("day"), Area: "rB"})
	if err != nil || b.Total != 2 || b.AreaName != "Tỉnh B" || b.To != nil {
		t.Fatalf("filtered by origin ward, no destinations: %+v %v", b, err)
	}
	if x, _ := s.Usage(ctx, UsageQuery{Grain: grainOf("week"), Area: "nope"}); x.Area != "" || x.Total != 59 { // 12 weeks hold both days
		t.Fatalf("an unknown ward is ignored: %+v", x)
	}
}

func TestGuideItems(t *testing.T) {
	s := setup(t)
	ctx := context.Background()
	owner, err := pgx.Connect(ctx, database.TestDatabaseURL(os.Getenv("MIGRATE_DATABASE_URL")))
	if err != nil {
		t.Fatal(err)
	}
	defer owner.Close(ctx)
	// Test database only.
	if _, err := owner.Exec(ctx, `DELETE FROM place_foods; DELETE FROM food_items; DELETE FROM historical_stories`); err != nil {
		t.Fatal(err)
	}
	actor := account(t, s, "guide", RoleContentEditor)
	dish := GuideItem{Title: "Bánh xèo Đà Lạt", Summary: "Giòn", Extra: "Quán A | 1 Lê Lợi", Sources: "Nguồn", Status: "PUBLISHED", SortOrder: 5}
	id1, err := s.SaveGuideItem(ctx, actor, "foods", dish, "req-f1")
	if err != nil {
		t.Fatal(err)
	}
	id2, err := s.SaveGuideItem(ctx, actor, "foods", dish, "req-f2")
	if err != nil {
		t.Fatal(err)
	}
	a, _ := s.GuideItem(ctx, "foods", id1)
	b, _ := s.GuideItem(ctx, "foods", id2)
	if a.Slug != "hcm-food-banh-xeo-da-lat" || b.Slug != "hcm-food-banh-xeo-da-lat-2" || a.EditedAt == nil || a.Extra != "Quán A | 1 Lê Lợi" {
		t.Fatalf("slugs from the Vietnamese title, unique: %q %q %+v", a.Slug, b.Slug, a)
	}
	a.Title, a.Status = "Bánh xèo", "HIDDEN"
	if _, err := s.SaveGuideItem(ctx, actor, "foods", a, "req-f3"); err != nil {
		t.Fatal(err)
	}
	if got, _ := s.GuideItem(ctx, "foods", id1); got.Title != "Bánh xèo" || got.Status != "HIDDEN" || got.Slug != a.Slug {
		t.Fatalf("update keeps the slug: %+v", got)
	}
	if _, err := s.SaveGuideItem(ctx, actor, "foods", GuideItem{ID: "00000000-0000-0000-0000-000000000000", Title: "x", Status: "DRAFT"}, "r"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("unknown dish: %v", err)
	}

	story := GuideItem{Title: "Chuyện bờ sông", Body: "Một.\n\nHai.", Sources: "Cục Du lịch", Status: "DRAFT"}
	sid, err := s.SaveGuideItem(ctx, actor, "stories", story, "req-s1")
	if err != nil {
		t.Fatal(err)
	}
	st, _ := s.GuideItem(ctx, "stories", sid)
	st.Status = "PUBLISHED"
	if _, err := s.SaveGuideItem(ctx, actor, "stories", st, "req-s2"); err != nil {
		t.Fatalf("publishing sets the date the CHECK needs: %v", err)
	}
	if list, err := s.GuideItems(ctx, "stories"); err != nil || len(list) != 1 || list[0].Status != "PUBLISHED" {
		t.Fatalf("stories %v %v", list, err)
	}
	// Saving without a change writes no audit line.
	if _, err := s.SaveGuideItem(ctx, actor, "stories", st, "req-s3"); err != nil {
		t.Fatal(err)
	}
	// The slug is the app's id: the Admin role cannot change it.
	if _, err := s.Pool.Exec(ctx, `UPDATE food_items SET slug = 'x' WHERE id::text = $1`, id1); err == nil {
		t.Fatal("the admin role could change a slug")
	}
	entries, _ := s.AuditLog(ctx, 50)
	seen := map[string]bool{}
	for _, e := range entries {
		seen[e.Action+":"+e.RequestID] = true
	}
	if !seen["food.create:req-f1"] || !seen["food.update:req-f3"] || !seen["story.update:req-s2"] || seen["story.update:req-s3"] {
		t.Fatalf("audit: %v", seen)
	}
}
