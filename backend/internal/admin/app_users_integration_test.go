//go:build integration

package admin

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"

	"github.com/golang-migrate/migrate/v4"
	"github.com/jackc/pgx/v5"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/feedback"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

// AUTH-3: the Admin reads the app accounts through its own role and suspends one; nothing private is reachable.
func TestAppUsersListAndSuspend(t *testing.T) {
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
	o, err := pgx.Connect(ctx, owner)
	if err != nil {
		t.Fatal(err)
	}
	defer o.Close(ctx)
	if _, err := o.Exec(ctx, `TRUNCATE feedback_reports, app_codes, app_sessions, app_users CASCADE`); err != nil {
		t.Fatal(err)
	}
	if _, err := o.Exec(ctx, `TRUNCATE admin_audit_logs`); err != nil {
		t.Fatal(err)
	}
	var id string
	if err := o.QueryRow(ctx, `INSERT INTO app_users (display_name, username, email, email_verified_at, phone)
		VALUES ('Lan Anh', 'lananh', 'lananh@example.vn', now(), '+84912345678') RETURNING id::text`).Scan(&id); err != nil {
		t.Fatal(err)
	}
	var feedbackID string
	if err := o.QueryRow(ctx, `INSERT INTO feedback_reports (id, kind, message, app_user_id) VALUES (uuidv7(), 'OTHER', 'Thử', $1) RETURNING id::text`, id).Scan(&feedbackID); err != nil {
		t.Fatal(err)
	}

	pool, err := database.NewPool(ctx, admin)
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	store := &PG{Pool: pool}

	// The feedback detail names the account behind it (AUTH-3), without any private content.
	report, err := (&feedback.PG{Pool: pool}).Get(ctx, feedbackID)
	if err != nil || report.AppUserID != id || report.AppUserName != "Lan Anh" || !report.AppUserEmailVerified || report.AppUserFeedbacks != 1 {
		t.Fatalf("feedback account block: %+v %v", report, err)
	}

	for _, search := range []string{"lananh", "Lan Anh", "0912", "lananh@example.vn"} {
		list, total, err := store.AppUsers(ctx, AppUserQuery{Q: search})
		if err != nil || total != 1 || len(list) != 1 {
			t.Fatalf("search %q: %d %v", search, total, err)
		}
		got := list[0]
		if got.Name != "Lan Anh" || !got.EmailVerified || got.PhoneVerified || got.Feedbacks != 1 || got.Status != "ACTIVE" {
			t.Fatalf("row = %+v", got)
		}
	}
	if list, total, err := store.AppUsers(ctx, AppUserQuery{Q: "không có ai"}); err != nil || total != 0 || len(list) != 0 {
		t.Fatalf("no match: %d %v", total, err)
	}

	actor := "00000000-0000-4000-8000-000000000001"
	// The audit log names the operator; the row must exist (foreign key), as it does in real use.
	if _, err := o.Exec(ctx, `INSERT INTO users (id, display_name) VALUES ($1, 'Kiểm thử') ON CONFLICT (id) DO NOTHING`, actor); err != nil {
		t.Fatal(err)
	}
	if err := store.SetAppUserStatus(ctx, id, "SUSPENDED", actor, "req-1"); err != nil {
		t.Fatal(err)
	}
	list, _, err := store.AppUsers(ctx, AppUserQuery{Status: "SUSPENDED"})
	if err != nil || len(list) != 1 || list[0].ID != id {
		t.Fatalf("after suspending: %v", err)
	}
	var action, meta string
	if err := o.QueryRow(ctx, `SELECT action, metadata::text FROM admin_audit_logs ORDER BY created_at DESC LIMIT 1`).Scan(&action, &meta); err != nil {
		t.Fatal(err)
	}
	if action != "app_user.status" || !strings.Contains(meta, "SUSPENDED") {
		t.Fatalf("audit: %s %s", action, meta)
	}

	if err := store.SetAppUserStatus(ctx, id, "ACTIVE", actor, "req-2"); err != nil {
		t.Fatal(err)
	}
	// A deleted account cannot be restored by hand: the person asked for it to be gone.
	if _, err := o.Exec(ctx, `UPDATE app_users SET status = 'DELETED' WHERE id = $1`, id); err != nil {
		t.Fatal(err)
	}
	if err := store.SetAppUserStatus(ctx, id, "ACTIVE", actor, "req-3"); !errors.Is(err, ErrBadInput) && err == nil {
		t.Fatal("a deleted account was restored")
	}
	if !appUserIDValid(id) || appUserIDValid("not-a-uuid") || appUserIDValid("11111111-1111-4111-8111-11111111111g") {
		t.Fatal("the id guard is wrong")
	}
}
