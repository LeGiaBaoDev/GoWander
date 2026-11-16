package admin

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
)

// The bell (owner, 16-11-2026: "thiếu nút cái chuông thông báo"). Notifications are lines of the audit log worth a
// look: anything touching sign-in or accounts (someone trying passwords, a reset link asked for), and content changes
// made by another operator. What the operator did themself is not news to them.

// securityActions always notify, whoever did them.
var securityActions = []string{"admin.login_failed", "admin.password_reset_requested", "admin.password_reset",
	"account.create", "account.temporary_password", "account.email_change"}

// alarming ones show in red.
var alarming = map[string]bool{"admin.login_failed": true, "admin.password_reset_requested": true, "account.temporary_password": true}

// Notice is one notification. Repeats in a row on the same day (four wrong passwords) are one notice with a Count.
type Notice struct {
	Action, Actor, Object string
	At                    time.Time
	Unread, Bad           bool
	Count                 int
	Title, Sub, Icon      string // what the bell shows, from describe
}

// Bell is what the sidebar shows: the newest notices and how many are unread.
type Bell struct {
	Notices []Notice
	Unread  int
}

// group merges repeats on the same day (same action, actor and object: four wrong passwords) into the newest one,
// and keeps at most limit notices.
func group(rows []Notice, limit int) Bell {
	var b Bell
	seen := map[string]int{} // key -> index in b.Notices
	for _, n := range rows {
		key := n.Action + "\x00" + n.Actor + "\x00" + n.Object + "\x00" + n.At.In(hcm).Format(time.DateOnly)
		if i, ok := seen[key]; ok {
			p := &b.Notices[i]
			p.Count++
			if n.Unread && !p.Unread {
				p.Unread = true
				b.Unread++
			}
			continue
		}
		if len(b.Notices) == limit {
			continue
		}
		n.Count = 1
		seen[key] = len(b.Notices)
		b.Notices = append(b.Notices, n)
		if n.Unread {
			b.Unread++
		}
	}
	for i := range b.Notices {
		describe(&b.Notices[i])
	}
	return b
}

const noticeWindow = 7 * 24 * time.Hour

func (s *PG) Notifications(ctx context.Context, userID string, limit int) (Bell, error) {
	rows, err := s.Pool.Query(ctx, `
		WITH me AS (SELECT coalesce(notifications_seen_at, '-infinity') AS seen FROM admin_credentials WHERE user_id = $1)
		SELECT a.action, coalesce(nullif(u.display_name, ''), ac.login, ''),
		       coalesce(CASE a.entity_type
		         WHEN 'user' THEN (SELECT coalesce(nullif(u2.display_name, ''), ac2.login) FROM users u2 LEFT JOIN admin_credentials ac2 ON ac2.user_id = u2.id WHERE u2.id::text = a.entity_id)
		         WHEN 'place' THEN (SELECT name FROM places WHERE id::text = a.entity_id)
		         WHEN 'food' THEN (SELECT name FROM food_items WHERE id::text = a.entity_id)
		         WHEN 'story' THEN (SELECT title FROM historical_stories WHERE id::text = a.entity_id)
		         WHEN 'place_category' THEN (SELECT name FROM place_categories WHERE id::text = a.entity_id)
		       END, ''),
		       a.created_at, a.created_at > (SELECT seen FROM me)
		  FROM admin_audit_logs a LEFT JOIN users u ON u.id = a.actor_id LEFT JOIN admin_credentials ac ON ac.user_id = a.actor_id
		 WHERE a.created_at > now() - $2::interval
		   AND (a.action = ANY($3) OR (a.actor_id IS DISTINCT FROM $1::uuid AND a.action NOT IN ('admin.login', 'admin.logout')))
		 ORDER BY a.created_at DESC LIMIT 300`, userID, noticeWindow.String(), securityActions)
	if err != nil {
		return Bell{}, fmt.Errorf("notifications: %w", err)
	}
	var list []Notice
	var n Notice
	_, err = pgx.ForEachRow(rows, []any{&n.Action, &n.Actor, &n.Object, &n.At, &n.Unread}, func() error {
		n.Bad = alarming[n.Action]
		list = append(list, n)
		return nil
	})
	if err != nil {
		return Bell{}, fmt.Errorf("notifications: %w", err)
	}
	return group(list, limit), nil
}

// MarkNotificationsSeen: the operator opened the notifications page.
func (s *PG) MarkNotificationsSeen(ctx context.Context, userID string) error {
	_, err := s.Pool.Exec(ctx, `UPDATE admin_credentials SET notifications_seen_at = now() WHERE user_id = $1`, userID)
	return err
}

// describe sets the short title, the line under it and the icon.
func describe(n *Notice) {
	who, what := n.Actor, n.Object
	if who == "" {
		who = "Hệ thống"
	}
	times := ""
	if n.Count > 1 {
		times = " · " + strconv.Itoa(n.Count) + " lần"
	}
	n.Sub, n.Icon = "Tài khoản "+what+times, "key"
	switch n.Action {
	case "admin.login_failed":
		n.Title, n.Icon = "Đăng nhập sai mật khẩu", "lock"
	case "admin.password_reset_requested":
		n.Title = "Yêu cầu link đặt lại mật khẩu"
	case "admin.password_reset":
		n.Title = "Đã đặt lại mật khẩu qua email"
	case "account.temporary_password":
		n.Title = "Cấp mật khẩu tạm (lệnh trên server)"
	case "account.create":
		n.Title, n.Icon = "Tài khoản mới", "user"
	case "account.email_change":
		n.Title, n.Icon = "Đổi email khôi phục", "user"
	default:
		n.Title, n.Icon = actionNames[n.Action], "edit"
		if n.Title == "" {
			n.Title = n.Action
		}
		n.Sub = who
		if what != "" && what != who {
			n.Sub += " · " + what
		}
		n.Sub += times
	}
}

// ago: "Vừa xong", "12 phút trước", "3 giờ trước", then the date.
func ago(t, now time.Time) string {
	switch d := now.Sub(t); {
	case d < time.Minute:
		return "Vừa xong"
	case d < time.Hour:
		return strconv.Itoa(int(d.Minutes())) + " phút trước"
	case d < 12*time.Hour:
		return strconv.Itoa(int(d.Hours())) + " giờ trước"
	}
	return friendlyTime(t, now)
}
