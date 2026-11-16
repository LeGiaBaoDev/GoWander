package feedback

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type PG struct{ Pool *pgxpool.Pool }

func newID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	b[6] = b[6]&15 | 64
	b[8] = b[8]&63 | 128
	s := hex.EncodeToString(b[:])
	return s[:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:]
}
func validID(s string) bool {
	if len(s) != 36 || s[8] != '-' || s[13] != '-' || s[18] != '-' || s[23] != '-' {
		return false
	}
	_, err := hex.DecodeString(strings.ReplaceAll(s, "-", ""))
	return err == nil
}
func (s *PG) Create(ctx context.Context, in Input) (string, error) {
	if err := Validate(in); err != nil {
		return "", err
	}
	id := newID()
	_, err := s.Pool.Exec(ctx, `INSERT INTO feedback_reports(id,kind,message,context,sender_name,sender_email,image,app_user_id)
		VALUES($1,$2,$3,$4,$5,$6,$7,NULLIF($8,'')::uuid)`, id, in.Kind, in.Message, in.Context, in.SenderName, in.SenderEmail, in.Image, in.AppUserID)
	if err != nil {
		return "", err
	}
	return id, nil
}

// List excludes contacts and attachments. Date filters are calendar dates in Asia/Ho_Chi_Minh, inclusive on both ends.
func (s *PG) List(ctx context.Context, q Query) ([]Report, int, error) {
	if err := ValidateQuery(q); err != nil {
		return nil, 0, err
	}
	const where = ` WHERE ($1='' OR status=$1) AND ($2='' OR kind=$2)
 AND ($3='' OR strpos(lower(message || ' ' || context || ' ' || sender_name),lower($3))>0)
 AND ($4='' OR created_at >= (NULLIF($4,'')::date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'))
 AND ($5='' OR created_at < ((NULLIF($5,'')::date+1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'))`
	args := []any{q.Status, q.Kind, q.Search, q.From, q.To}
	var total int
	if err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM feedback_reports`+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	args = append(args, PageSize, max(q.Page-1, 0)*PageSize)
	rows, err := s.Pool.Query(ctx, `SELECT id::text,kind,message,context,sender_name,status,note,created_at,updated_at,image IS NOT NULL FROM feedback_reports`+where+` ORDER BY created_at DESC,id LIMIT $6 OFFSET $7`, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	reports := []Report{}
	for rows.Next() {
		var f Report
		if err := rows.Scan(&f.ID, &f.Kind, &f.Message, &f.Context, &f.SenderName, &f.Status, &f.Note, &f.CreatedAt, &f.UpdatedAt, &f.HasImage); err != nil {
			return nil, 0, err
		}
		reports = append(reports, f)
	}
	return reports, total, rows.Err()
}
func (s *PG) Get(ctx context.Context, id string) (Report, error) {
	if !validID(id) {
		return Report{}, ErrNotFound
	}
	var f Report
	err := s.Pool.QueryRow(ctx, `SELECT r.id::text, r.kind, r.message, r.context, r.sender_name, r.sender_email, r.status, r.note,
		r.created_at, r.updated_at, r.image IS NOT NULL,
		coalesce(u.id::text,''), coalesce(u.display_name,''), coalesce(u.email,''), u.email_verified_at IS NOT NULL,
		(SELECT count(*) FROM feedback_reports f2 WHERE f2.app_user_id = u.id)
		FROM feedback_reports r LEFT JOIN app_users u ON u.id = r.app_user_id WHERE r.id=$1`, id).Scan(
		&f.ID, &f.Kind, &f.Message, &f.Context, &f.SenderName, &f.SenderEmail, &f.Status, &f.Note, &f.CreatedAt, &f.UpdatedAt, &f.HasImage,
		&f.AppUserID, &f.AppUserName, &f.AppUserEmail, &f.AppUserEmailVerified, &f.AppUserFeedbacks)
	if errors.Is(err, pgx.ErrNoRows) {
		return f, ErrNotFound
	}
	if err != nil {
		return f, err
	}
	rows, err := s.Pool.Query(ctx, `SELECT COALESCE(NULLIF(u.display_name,''),c.login,'Quản trị viên'),e.from_status,e.to_status,e.note,e.created_at FROM feedback_events e LEFT JOIN users u ON u.id=e.actor_id LEFT JOIN admin_credentials c ON c.user_id=e.actor_id WHERE e.report_id=$1 ORDER BY e.created_at,e.id`, id)
	if err != nil {
		return f, err
	}
	defer rows.Close()
	for rows.Next() {
		var e Event
		if err := rows.Scan(&e.ActorName, &e.FromStatus, &e.ToStatus, &e.Note, &e.At); err != nil {
			return f, err
		}
		f.History = append(f.History, e)
	}
	return f, rows.Err()
}
func (s *PG) Image(ctx context.Context, id string) ([]byte, error) {
	if !validID(id) {
		return nil, ErrNotFound
	}
	var b []byte
	err := s.Pool.QueryRow(ctx, `SELECT image FROM feedback_reports WHERE id=$1 AND image IS NOT NULL`, id).Scan(&b)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	return b, err
}
func (s *PG) Update(ctx context.Context, id, status, note, expectedStatus, actorID, requestID string) error {
	if !validID(id) || !validID(actorID) || !ValidStatus(status) || !ValidStatus(expectedStatus) || (note != PreserveNote && !textOK(note, 2000)) {
		return ErrBadInput
	}
	tx, err := s.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	var old, oldNote string
	err = tx.QueryRow(ctx, `SELECT status,note FROM feedback_reports WHERE id=$1 FOR UPDATE`, id).Scan(&old, &oldNote)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return err
	}
	if old != expectedStatus {
		return ErrConflict
	}
	if note == PreserveNote {
		note = oldNote
	}
	if old == status && note == oldNote {
		return nil
	}
	if _, err = tx.Exec(ctx, `UPDATE feedback_reports SET status=$2,note=$3,updated_at=now() WHERE id=$1`, id, status, note); err != nil {
		return err
	}
	if _, err = tx.Exec(ctx, `INSERT INTO feedback_events(report_id,actor_id,from_status,to_status,note) VALUES($1,$2,$3,$4,$5)`, id, actorID, old, status, note); err != nil {
		return err
	}
	// Audit contains status only, never the feedback body, private note, screenshot or sender contact.
	if _, err = tx.Exec(ctx, `INSERT INTO admin_audit_logs(actor_id,action,entity_type,entity_id,metadata,request_id) VALUES($1,'feedback.update','feedback',$2,jsonb_build_object('from',$3::text,'to',$4::text),$5)`, actorID, id, old, status, requestID); err != nil {
		return fmt.Errorf("audit feedback: %w", err)
	}
	return tx.Commit(ctx)
}
