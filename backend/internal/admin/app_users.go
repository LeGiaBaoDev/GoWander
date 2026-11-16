package admin

import (
	"context"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

// AppUserQuery filters the app-account list (AUTH-3, ADR-040 §8). Zero values mean "any".
type AppUserQuery struct {
	Q, Status string
	Page      int // 1-based
}

// AppUserRow is one line of the list: enough to recognise a person and their use of the app, no private content.
type AppUserRow struct {
	ID, Name, Username, Email, Phone, Status string
	EmailVerified, PhoneVerified             bool
	CreatedAt                                time.Time
	LastLoginAt                              *time.Time
	Feedbacks                                int
}

// appStatusNames are the words the page shows for each state.
var appStatusNames = map[string]string{"ACTIVE": "Đang dùng", "SUSPENDED": "Tạm khoá", "DELETED": "Đã xoá"}

// AppUserStatuses are the states an operator may set; DELETED is done by the person, never from here.
var AppUserStatuses = []string{"ACTIVE", "SUSPENDED"}

// AppUsers lists accounts of the app. Only the columns granted to gowander_admin are read (0014): never a hash, a code
// or a session.
func (s *PG) AppUsers(ctx context.Context, q AppUserQuery) ([]AppUserRow, int, error) {
	// The phone is matched both as stored (+84…) and as people type it (0912…).
	const where = ` WHERE ($1 = '' OR u.status = $1)
	  AND ($2 = '' OR search_text(coalesce(u.display_name,'') || ' ' || coalesce(u.username,'') || ' ' ||
	    coalesce(u.email,'') || ' ' || coalesce(u.phone,'') || ' ' || replace(coalesce(u.phone,''), '+84', '0'))
	    LIKE '%' || search_text($2) || '%')`
	var total int
	if err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM app_users u`+where, q.Status, strings.TrimSpace(q.Q)).Scan(&total); err != nil {
		return nil, 0, err
	}
	rows, err := s.Pool.Query(ctx, `SELECT u.id::text, u.display_name, coalesce(u.username,''), coalesce(u.email,''),
		coalesce(u.phone,''), u.status, u.email_verified_at IS NOT NULL, u.phone_verified_at IS NOT NULL,
		u.created_at, u.last_login_at,
		(SELECT count(*) FROM feedback_reports f WHERE f.app_user_id = u.id)
	  FROM app_users u`+where+` ORDER BY u.created_at DESC, u.id LIMIT $3 OFFSET $4`,
		q.Status, strings.TrimSpace(q.Q), PageSize, max(q.Page-1, 0)*PageSize)
	if err != nil {
		return nil, 0, err
	}
	list, err := pgx.CollectRows(rows, func(r pgx.CollectableRow) (AppUserRow, error) {
		var u AppUserRow
		err := r.Scan(&u.ID, &u.Name, &u.Username, &u.Email, &u.Phone, &u.Status, &u.EmailVerified, &u.PhoneVerified, &u.CreatedAt, &u.LastLoginAt, &u.Feedbacks)
		return u, err
	})
	return list, total, err
}

// SetAppUserStatus suspends or restores an account (ADR-040 §8). No session row is touched: every request asks for
// status = 'ACTIVE' (account.Me), so a suspended person is out at the next call — and the Admin role needs no right
// on app_sessions at all.
func (s *PG) SetAppUserStatus(ctx context.Context, id, status, actorID, requestID string) error {
	if status != "ACTIVE" && status != "SUSPENDED" {
		return bad("trạng thái không hợp lệ")
	}
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var old string
		if err := tx.QueryRow(ctx, `SELECT status FROM app_users WHERE id = $1 FOR UPDATE`, id).Scan(&old); err != nil {
			if err == pgx.ErrNoRows {
				return ErrNotFound
			}
			return err
		}
		if old == "DELETED" {
			return bad("tài khoản đã bị chính người dùng xoá, không đổi được")
		}
		if old == status {
			return nil
		}
		if _, err := tx.Exec(ctx, `UPDATE app_users SET status = $2, updated_at = now() WHERE id = $1`, id, status); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "app_user.status", EntityType: "app_user", EntityID: id,
			RequestID: requestID, Metadata: map[string]any{"from": old, "to": status}})
	})
}

// appUserIDValid guards the URL parameter (UUID as text; the database has the final word).
func appUserIDValid(id string) bool {
	if len(id) != 36 {
		return false
	}
	for i, r := range id {
		switch i {
		case 8, 13, 18, 23:
			if r != '-' {
				return false
			}
		default:
			if !strings.ContainsRune("0123456789abcdef", r) && !strings.ContainsRune("0123456789ABCDEF", r) {
				return false
			}
		}
	}
	return true
}
