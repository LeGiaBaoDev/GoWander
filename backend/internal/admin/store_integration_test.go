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
