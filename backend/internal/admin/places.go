package admin

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
)

// PlaceStatuses (database CHECK). Only PUBLISHED places reach the app.
var PlaceStatuses = []string{"PUBLISHED", "HIDDEN", "DRAFT"}

// missingFilters are the "missing …" filters of the places list; the overview's attention lines link to them.
var missingFilters = map[string]string{
	"photo":       "NOT EXISTS (SELECT 1 FROM place_media m WHERE m.place_id = p.id)",
	"description": "btrim(coalesce(p.short_description, '')) = ''",
	"address":     "btrim(coalesce(p.address, '')) = ''",
	"category":    "p.primary_category_id IS NULL",
}

// PageSize of the places list.
const PageSize = 50

// PlaceQuery filters the places list. Zero values mean "any".
type PlaceQuery struct {
	Q, Status, Missing string
	Category           int64
	Page               int // 1-based
}

// PlaceRow is one line of the places list.
type PlaceRow struct {
	ID, Name, Category, Status, Source string
	HasPhoto, HasDescription, HasAddr  bool
	UpdatedAt                          time.Time
}

// Place is the edit form: editable fields plus read-only provenance.
type Place struct {
	ID, Name, NameEN, Description, Address, Status string
	CategoryID                                     int64 // 0 = none
	Slug, Source, SourceRef, SourceURL, License    string
	Lat, Lon                                       float64
	Photos                                         int
	EditedAt                                       *time.Time
	UpdatedAt                                      time.Time
}

// Category is one row of the categories page.
type Category struct {
	ID                 int64
	Name, NameEN, Slug string
	SortOrder, Places  int
	Active             bool
}

// ErrNotFound: no place with that id.
var ErrNotFound = errors.New("not found")

func (s *PG) Places(ctx context.Context, q PlaceQuery) ([]PlaceRow, int, error) {
	var where []string
	var args []any
	add := func(cond string, v any) {
		args = append(args, v)
		where = append(where, strings.ReplaceAll(cond, "$?", "$"+strconv.Itoa(len(args))))
	}
	if t := strings.TrimSpace(q.Q); t != "" {
		add("p.search_doc LIKE '%' || search_text($?) || '%'", t)
	}
	if q.Status != "" {
		add("p.status = $?", q.Status)
	}
	if q.Category != 0 {
		add("p.primary_category_id = $?", q.Category)
	}
	if cond, ok := missingFilters[q.Missing]; ok {
		where = append(where, cond)
	}
	filter := ""
	if len(where) > 0 {
		filter = "WHERE " + strings.Join(where, " AND ")
	}
	var total int
	if err := s.Pool.QueryRow(ctx, "SELECT count(*) FROM places p "+filter, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count places: %w", err)
	}
	args = append(args, PageSize, (max(q.Page, 1)-1)*PageSize)
	rows, err := s.Pool.Query(ctx, fmt.Sprintf(`
		SELECT p.id::text, p.name, coalesce(c.name, ''), p.status, p.source,
		       EXISTS (SELECT 1 FROM place_media m WHERE m.place_id = p.id),
		       btrim(coalesce(p.short_description, '')) <> '', btrim(coalesce(p.address, '')) <> '', p.updated_at
		FROM places p LEFT JOIN place_categories c ON c.id = p.primary_category_id
		%s ORDER BY p.name, p.id LIMIT $%d OFFSET $%d`, filter, len(args)-1, len(args)), args...)
	if err != nil {
		return nil, 0, fmt.Errorf("list places: %w", err)
	}
	list, err := pgx.CollectRows(rows, pgx.RowToStructByPos[PlaceRow])
	if err != nil {
		return nil, 0, fmt.Errorf("list places: %w", err)
	}
	return list, total, nil
}

func (s *PG) Place(ctx context.Context, id string) (Place, error) {
	var p Place
	err := s.Pool.QueryRow(ctx, `
		SELECT id::text, name, coalesce(name_en, ''), coalesce(short_description, ''), coalesce(address, ''), status,
		       coalesce(primary_category_id, 0), slug, source, source_ref, coalesce(source_url, ''), license,
		       ST_Y(location::geometry), ST_X(location::geometry),
		       (SELECT count(*) FROM place_media m WHERE m.place_id = places.id), edited_at, updated_at
		FROM places WHERE id::text = $1`, id).Scan(&p.ID, &p.Name, &p.NameEN, &p.Description, &p.Address, &p.Status,
		&p.CategoryID, &p.Slug, &p.Source, &p.SourceRef, &p.SourceURL, &p.License, &p.Lat, &p.Lon, &p.Photos, &p.EditedAt, &p.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return p, ErrNotFound
	}
	if err != nil {
		return p, fmt.Errorf("place: %w", err)
	}
	return p, nil
}

// validatePlace trims the editable fields and checks them against the column limits. The message is shown to the editor.
func validatePlace(p *Place) error {
	p.Name, p.NameEN = strings.TrimSpace(p.Name), strings.TrimSpace(p.NameEN)
	p.Description, p.Address = strings.TrimSpace(p.Description), strings.TrimSpace(p.Address)
	switch {
	case p.Name == "" || utf8.RuneCountInString(p.Name) > 200:
		return fmt.Errorf("%w: tên phải có, tối đa 200 ký tự", ErrBadInput)
	case utf8.RuneCountInString(p.NameEN) > 200:
		return fmt.Errorf("%w: tên tiếng Anh tối đa 200 ký tự", ErrBadInput)
	case utf8.RuneCountInString(p.Description) > 500:
		return fmt.Errorf("%w: mô tả ngắn tối đa 500 ký tự", ErrBadInput)
	case utf8.RuneCountInString(p.Address) > 300:
		return fmt.Errorf("%w: địa chỉ tối đa 300 ký tự", ErrBadInput)
	case !slices.Contains(PlaceStatuses, p.Status):
		return fmt.Errorf("%w: trạng thái không hợp lệ", ErrBadInput)
	}
	return nil
}

// orNull stores empty text and id 0 as NULL.
func orNull[T comparable](v T) any {
	var zero T
	if v == zero {
		return nil
	}
	return v
}

// UpdatePlace saves the editable fields, keeps the category links in step with the main category, marks the place as
// edited (a re-import then leaves it alone) and writes one audit line naming the changed fields.
func (s *PG) UpdatePlace(ctx context.Context, actorID string, p Place, requestID string) error {
	if err := validatePlace(&p); err != nil {
		return err
	}
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var old Place
		err := tx.QueryRow(ctx, `SELECT name, coalesce(name_en, ''), coalesce(short_description, ''), coalesce(address, ''),
			status, coalesce(primary_category_id, 0) FROM places WHERE id::text = $1 FOR UPDATE`, p.ID).
			Scan(&old.Name, &old.NameEN, &old.Description, &old.Address, &old.Status, &old.CategoryID)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return fmt.Errorf("lock place: %w", err)
		}
		if p.CategoryID != 0 && p.CategoryID != old.CategoryID {
			var active bool
			err := tx.QueryRow(ctx, `SELECT is_active FROM place_categories WHERE id = $1`, p.CategoryID).Scan(&active)
			if errors.Is(err, pgx.ErrNoRows) || (err == nil && !active) {
				return fmt.Errorf("%w: loại địa điểm không tồn tại hoặc đang tắt", ErrBadInput)
			}
			if err != nil {
				return fmt.Errorf("check category: %w", err)
			}
		}
		var changed []string
		for _, f := range []struct {
			name     string
			was, now any
		}{{"name", old.Name, p.Name}, {"name_en", old.NameEN, p.NameEN}, {"short_description", old.Description, p.Description},
			{"address", old.Address, p.Address}, {"status", old.Status, p.Status}, {"primary_category_id", old.CategoryID, p.CategoryID}} {
			if f.was != f.now {
				changed = append(changed, f.name)
			}
		}
		if len(changed) == 0 {
			return nil
		}
		if _, err := tx.Exec(ctx, `UPDATE places SET name = $2, name_en = $3, short_description = $4, address = $5, status = $6,
			primary_category_id = $7, edited_at = now(), updated_at = now() WHERE id::text = $1`,
			p.ID, p.Name, orNull(p.NameEN), orNull(p.Description), orNull(p.Address), p.Status, orNull(p.CategoryID)); err != nil {
			return fmt.Errorf("update place: %w", err)
		}
		// Map filters read the links: the old main category goes, the new one comes.
		if p.CategoryID != old.CategoryID {
			if _, err := tx.Exec(ctx, `DELETE FROM place_category_links WHERE place_id::text = $1 AND category_id = $2`, p.ID, old.CategoryID); err != nil {
				return fmt.Errorf("unlink category: %w", err)
			}
			if p.CategoryID != 0 {
				if _, err := tx.Exec(ctx, `INSERT INTO place_category_links (place_id, category_id) VALUES ($1::uuid, $2) ON CONFLICT DO NOTHING`, p.ID, p.CategoryID); err != nil {
					return fmt.Errorf("link category: %w", err)
				}
			}
		}
		meta := map[string]any{"fields": changed}
		if old.Status != p.Status {
			meta["status"] = old.Status + "→" + p.Status
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "place.update", EntityType: "place", EntityID: p.ID, RequestID: requestID, Metadata: meta})
	})
}

func (s *PG) Categories(ctx context.Context) ([]Category, error) {
	rows, err := s.Pool.Query(ctx, `
		SELECT c.id, c.name, coalesce(c.name_en, ''), c.slug, c.sort_order,
		       (SELECT count(*)::int FROM places p WHERE p.primary_category_id = c.id AND p.status = 'PUBLISHED'), c.is_active
		FROM place_categories c ORDER BY c.sort_order, c.name`)
	if err != nil {
		return nil, fmt.Errorf("categories: %w", err)
	}
	list, err := pgx.CollectRows(rows, pgx.RowToStructByPos[Category])
	if err != nil {
		return nil, fmt.Errorf("categories: %w", err)
	}
	return list, nil
}

// UpdateCategory renames, reorders or switches a category on/off (off removes it from the app's map filters).
func (s *PG) UpdateCategory(ctx context.Context, actorID string, c Category, requestID string) error {
	c.Name, c.NameEN = strings.TrimSpace(c.Name), strings.TrimSpace(c.NameEN)
	if c.Name == "" || utf8.RuneCountInString(c.Name) > 100 || utf8.RuneCountInString(c.NameEN) > 100 || c.SortOrder < 0 || c.SortOrder > 10000 {
		return fmt.Errorf("%w: tên phải có (tối đa 100 ký tự), thứ tự từ 0 đến 10000", ErrBadInput)
	}
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE place_categories SET name = $2, name_en = $3, sort_order = $4, is_active = $5, edited_at = now()
			WHERE id = $1 AND (name, coalesce(name_en, ''), sort_order, is_active) IS DISTINCT FROM ($2, $6, $4, $5)`,
			c.ID, c.Name, orNull(c.NameEN), c.SortOrder, c.Active, c.NameEN)
		if err != nil {
			return fmt.Errorf("update category: %w", err)
		}
		if tag.RowsAffected() == 0 {
			return nil // nothing changed, or an unknown id: nothing to record
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "category.update", EntityType: "place_category",
			EntityID: strconv.FormatInt(c.ID, 10), RequestID: requestID, Metadata: map[string]any{"name": c.Name, "active": c.Active}})
	})
}
