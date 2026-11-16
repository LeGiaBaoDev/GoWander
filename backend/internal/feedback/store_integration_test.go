//go:build integration

package feedback

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

// The public role can only add feedbacks; the Admin role reads and handles them, with history and audit.
func TestStoreRoles(t *testing.T) {
	ctx := context.Background()
	owner, app, admin := os.Getenv("MIGRATE_DATABASE_URL"), os.Getenv("DATABASE_URL"), os.Getenv("ADMIN_DATABASE_URL")
	if owner == "" || app == "" || admin == "" {
		t.Fatal("MIGRATE_DATABASE_URL, DATABASE_URL and ADMIN_DATABASE_URL must be set (load ../.env first)")
	}
	owner, app, admin = database.TestDatabaseURL(owner), database.TestDatabaseURL(app), database.TestDatabaseURL(admin)
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
	if _, err := o.Exec(ctx, `TRUNCATE feedback_reports CASCADE`); err != nil {
		t.Fatal(err)
	}
	var actor string
	if err := o.QueryRow(ctx, `INSERT INTO users (display_name) VALUES ('Người kiểm duyệt thử') RETURNING id::text`).Scan(&actor); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		o.Exec(context.Background(), `DELETE FROM feedback_events WHERE actor_id = $1::uuid`, actor)
		o.Exec(context.Background(), `DELETE FROM admin_audit_logs WHERE actor_id = $1::uuid`, actor)
		o.Exec(context.Background(), `DELETE FROM users WHERE id = $1::uuid`, actor)
	})
	appPool, err := database.NewPool(ctx, app)
	if err != nil {
		t.Fatal(err)
	}
	defer appPool.Close()
	adminPool, err := database.NewPool(ctx, admin)
	if err != nil {
		t.Fatal(err)
	}
	defer adminPool.Close()
	public, ops := &PG{Pool: appPool}, &PG{Pool: adminPool}

	id, err := public.Create(ctx, Input{Kind: "INFORMATION", Message: "Giờ mở cửa chưa đúng", Context: "Chợ Bến Thành", SenderName: "Lan", SenderEmail: "lan@example.vn", Image: []byte{0xff, 0xd8}})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := public.Create(ctx, Input{Kind: "BUG", Message: "   "}); !errors.Is(err, ErrBadInput) {
		t.Fatalf("empty message: %v", err)
	}
	if _, _, err := public.List(ctx, Query{}); err == nil {
		t.Fatal("the public role must not read feedbacks")
	}
	if _, err := public.Image(ctx, id); err == nil {
		t.Fatal("the public role must not read attachments")
	}

	list, total, err := ops.List(ctx, Query{Search: "giờ mở", Status: "NEW"})
	if err != nil || total != 1 || len(list) != 1 || list[0].ID != id || !list[0].HasImage || list[0].SenderEmail != "" {
		t.Fatalf("list (no contact in lists): %+v %d %v", list, total, err)
	}
	if got, _, _ := ops.List(ctx, Query{Kind: "BUG"}); len(got) != 0 {
		t.Fatal("kind filter")
	}
	if err := ops.Update(ctx, id, "IN_REVIEW", "Đang gọi quán hỏi lại", "NEW", actor, "req-1"); err != nil {
		t.Fatal(err)
	}
	if err := ops.Update(ctx, id, "RESOLVED", "x", "NEW", actor, "req-2"); !errors.Is(err, ErrConflict) {
		t.Fatalf("a stale status must be refused: %v", err)
	}
	if err := ops.Update(ctx, id, "RESOLVED", PreserveNote, "IN_REVIEW", actor, "req-3"); err != nil {
		t.Fatal(err)
	}
	r, err := ops.Get(ctx, id)
	if err != nil || r.Status != "RESOLVED" || r.Note != "Đang gọi quán hỏi lại" || r.SenderEmail != "lan@example.vn" || len(r.History) != 2 ||
		r.History[0].ActorName != "Người kiểm duyệt thử" || r.History[1].FromStatus != "IN_REVIEW" {
		t.Fatalf("detail and history: %+v %v", r, err)
	}
	if img, err := ops.Image(ctx, id); err != nil || len(img) != 2 {
		t.Fatalf("attachment: %v", err)
	}
	var meta string
	if err := o.QueryRow(ctx, `SELECT metadata::text FROM admin_audit_logs WHERE entity_id = $1 AND request_id = 'req-3'`, id).Scan(&meta); err != nil ||
		strings.Contains(meta, "Giờ") || strings.Contains(meta, "lan@") || !strings.Contains(meta, "RESOLVED") {
		t.Fatalf("audit holds the status only: %q %v", meta, err)
	}
	if _, err := ops.Get(ctx, "not-an-id"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("bad id: %v", err)
	}
}
