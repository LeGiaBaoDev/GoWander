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

// The region guide's dishes ("foods") and stories (M07 slice 3). Both are a title, a summary, a body, one extra text
// and sources, so one form and one set of handlers serve both; the SQL differs per table.

// GuideItem is the edit form of a dish or a story.
type GuideItem struct {
	ID, Slug, Title, NameEN, Summary, Body, Extra, Sources, SourceURL, Status string
	ImageKey, ImageCredit, ImageLicense                                       string
	// Blocks is the Editor.js document of a story (ADR-041), stored as JSONB; empty for dishes and for stories written
	// before the editor existed (Body is then the text the app shows).
	Blocks    string
	SortOrder int
	UpdatedAt time.Time
	EditedAt  *time.Time
}

// GuideRow is one line of a guide list.
type GuideRow struct {
	ID, Slug, Title, Status string
	SortOrder               int
	UpdatedAt               time.Time
}

type guideKind struct {
	Path, Noun, Nouns, Audit, SlugPrefix string
	TitleMax, SummaryMax, BodyMax        int
	BodyRequired, SourceAlways           bool
	list, get, insert, update            string
	args                                 func(GuideItem) []any // $2.. of insert (after slug) and update (after id)
}

// GuideKinds are the two kinds, by URL path segment.
var GuideKinds = map[string]guideKind{
	"foods": {
		Path: "foods", Noun: "món ăn", Nouns: "Món ăn", Audit: "food", SlugPrefix: "hcm-food-", TitleMax: 150, SummaryMax: 300, BodyMax: 3000,
		list: `SELECT id::text, slug, name, status, sort_order, updated_at FROM food_items ORDER BY sort_order, name`,
		get: `SELECT id::text, slug, name, coalesce(name_en, ''), coalesce(summary, ''), coalesce(description, ''), coalesce(shops, ''),
			coalesce(sources, ''), '', status, sort_order, updated_at, edited_at,
			coalesce(image_key, ''), coalesce(image_credit, ''), coalesce(image_license, '') FROM food_items WHERE id::text = $1`,
		insert: `INSERT INTO food_items (slug, name, name_en, summary, description, shops, sources, status, sort_order, edited_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, now()) RETURNING id::text`,
		update: `UPDATE food_items SET name = $2, name_en = $3, summary = $4, description = $5, shops = $6, sources = $7, status = $8,
			sort_order = $9, edited_at = now(), updated_at = now() WHERE id::text = $1`,
		args: func(g GuideItem) []any {
			return []any{g.Title, orNull(g.NameEN), orNull(g.Summary), orNull(g.Body), orNull(g.Extra), orNull(g.Sources), g.Status, g.SortOrder}
		},
	},
	"stories": {
		Path: "stories", Noun: "câu chuyện", Nouns: "Câu chuyện", Audit: "story", SlugPrefix: "hcm-story-", TitleMax: 220, SummaryMax: 500, BodyMax: 20000,
		BodyRequired: true, SourceAlways: true,
		list: `SELECT id::text, coalesce(slug, ''), title, status, sort_order, updated_at FROM historical_stories WHERE language_code = 'vi' ORDER BY sort_order, title`,
		get: `SELECT id::text, coalesce(slug, ''), title, '', coalesce(summary, ''), content, coalesce(try_this, ''), coalesce(sources, ''),
			coalesce(source_url, ''), status, sort_order, updated_at, edited_at,
			coalesce(image_key, ''), coalesce(image_credit, ''), coalesce(image_license, ''), coalesce(blocks::text, '') FROM historical_stories WHERE id::text = $1 AND language_code = 'vi'`,
		// A story is published with a date (database CHECK): set it the first time, keep it afterwards.
		insert: `INSERT INTO historical_stories (slug, title, summary, content, try_this, sources, source_url, status, sort_order, language_code, published_at, edited_at, blocks)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8::varchar, $9, 'vi', CASE WHEN $8::varchar = 'PUBLISHED' THEN now() END, now(), $10::jsonb) RETURNING id::text`,
		update: `UPDATE historical_stories SET title = $2, summary = $3, content = $4, try_this = $5, sources = $6, source_url = $7, status = $8::varchar,
			sort_order = $9, blocks = $10::jsonb,
			published_at = CASE WHEN $8::varchar = 'PUBLISHED' THEN coalesce(published_at, now()) ELSE published_at END,
			edited_at = now(), updated_at = now() WHERE id::text = $1`,
		args: func(g GuideItem) []any {
			return []any{g.Title, orNull(g.Summary), g.Body, orNull(g.Extra), orNull(g.Sources), orNull(g.SourceURL), g.Status, g.SortOrder, orNull(g.Blocks)}
		},
	},
}

// editable keeps only the fields the form changes, to tell whether a save changed anything.
func (g GuideItem) editable() GuideItem {
	return GuideItem{Title: g.Title, NameEN: g.NameEN, Summary: g.Summary, Body: g.Body, Extra: g.Extra, Sources: g.Sources,
		SourceURL: g.SourceURL, Status: g.Status, SortOrder: g.SortOrder, Blocks: g.Blocks}
}

func bad(format string, a ...any) error {
	return fmt.Errorf("%w: "+format, append([]any{ErrBadInput}, a...)...)
}

// validateGuide trims the fields and checks them; the message is shown to the editor.
func validateGuide(k guideKind, g *GuideItem) error {
	for _, f := range []*string{&g.Title, &g.NameEN, &g.Summary, &g.Body, &g.Extra, &g.Sources, &g.SourceURL} {
		*f = strings.TrimSpace(strings.ReplaceAll(*f, "\r\n", "\n"))
	}
	n := utf8.RuneCountInString
	switch {
	case g.Title == "" || n(g.Title) > k.TitleMax:
		return bad("tên phải có, tối đa %d ký tự", k.TitleMax)
	case n(g.NameEN) > 150:
		return bad("tên tiếng Anh tối đa 150 ký tự")
	case n(g.Summary) > k.SummaryMax:
		return bad("tóm tắt tối đa %d ký tự", k.SummaryMax)
	case k.BodyRequired && g.Body == "":
		return bad("nội dung phải có")
	case n(g.Body) > k.BodyMax:
		return bad("nội dung tối đa %d ký tự", k.BodyMax)
	case n(g.Extra) > 2000 || n(g.Sources) > 500 || n(g.SourceURL) > 500:
		return bad("phần thêm tối đa 2000 ký tự, nguồn tối đa 500 ký tự")
	case g.SourceURL != "" && !strings.HasPrefix(g.SourceURL, "https://") && !strings.HasPrefix(g.SourceURL, "http://"):
		return bad("link nguồn phải bắt đầu bằng https://")
	case g.Sources == "" && g.SourceURL == "" && (k.SourceAlways || g.Status == "PUBLISHED"):
		return bad("cần ghi nguồn trước khi hiển thị (không đăng nội dung không có nguồn)")
	case !slices.Contains(PlaceStatuses, g.Status):
		return bad("trạng thái không hợp lệ")
	case g.SortOrder < 0 || g.SortOrder > 10000:
		return bad("thứ tự từ 0 đến 10000")
	}
	return nil
}

func (s *PG) GuideItems(ctx context.Context, kind string) ([]GuideRow, error) {
	rows, err := s.Pool.Query(ctx, GuideKinds[kind].list)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", kind, err)
	}
	return pgx.CollectRows(rows, pgx.RowToStructByPos[GuideRow])
}

// scanGuide reads one row; stories carry the Editor.js blocks column, dishes do not.
func scanGuide(row pgx.Row, withBlocks bool) (GuideItem, error) {
	var g GuideItem
	var err error
	if withBlocks {
		err = row.Scan(&g.ID, &g.Slug, &g.Title, &g.NameEN, &g.Summary, &g.Body, &g.Extra, &g.Sources, &g.SourceURL, &g.Status, &g.SortOrder, &g.UpdatedAt, &g.EditedAt,
			&g.ImageKey, &g.ImageCredit, &g.ImageLicense, &g.Blocks)
	} else {
		err = row.Scan(&g.ID, &g.Slug, &g.Title, &g.NameEN, &g.Summary, &g.Body, &g.Extra, &g.Sources, &g.SourceURL, &g.Status, &g.SortOrder, &g.UpdatedAt, &g.EditedAt,
			&g.ImageKey, &g.ImageCredit, &g.ImageLicense)
	}
	if errors.Is(err, pgx.ErrNoRows) {
		return g, ErrNotFound
	}
	return g, err
}

func (s *PG) GuideItem(ctx context.Context, kind, id string) (GuideItem, error) {
	return scanGuide(s.Pool.QueryRow(ctx, GuideKinds[kind].get, id), kind == "stories")
}

// SaveGuideItem creates (empty ID) or updates a dish or story, marks it edited (the guide seed then leaves it alone)
// and writes one audit line. It returns the item's id.
func (s *PG) SaveGuideItem(ctx context.Context, actorID, kind string, g GuideItem, requestID string) (string, error) {
	k, ok := GuideKinds[kind]
	if !ok {
		return "", ErrNotFound
	}
	if err := validateGuide(k, &g); err != nil {
		return "", err
	}
	err := pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		action := k.Audit + ".update"
		if g.ID == "" {
			action = k.Audit + ".create"
			slug, err := freeSlug(ctx, tx, k, g.Title)
			if err != nil {
				return err
			}
			if err := tx.QueryRow(ctx, k.insert, append([]any{slug}, k.args(g)...)...).Scan(&g.ID); err != nil {
				return fmt.Errorf("insert %s: %w", kind, err)
			}
		} else {
			old, err := scanGuide(tx.QueryRow(ctx, k.get+" FOR UPDATE", g.ID), kind == "stories")
			if err != nil {
				return err
			}
			if old.editable() == g.editable() {
				return nil // nothing changed: nothing to record
			}
			if _, err := tx.Exec(ctx, k.update, append([]any{g.ID}, k.args(g)...)...); err != nil {
				return fmt.Errorf("update %s: %w", kind, err)
			}
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: action, EntityType: k.Audit, EntityID: g.ID, RequestID: requestID,
			Metadata: map[string]any{"title": g.Title, "status": g.Status}})
	})
	return g.ID, err
}

// freeSlug makes a URL-safe id from the title ("Bánh xèo" -> hcm-food-banh-xeo), adding -2, -3 ... when taken.
// The slug is the app's id for the item, so it never changes after creation.
func freeSlug(ctx context.Context, tx pgx.Tx, k guideKind, title string) (string, error) {
	var base string
	if err := tx.QueryRow(ctx, `SELECT trim(both '-' from regexp_replace(search_text($1), '[^a-z0-9]+', '-', 'g'))`, title).Scan(&base); err != nil {
		return "", fmt.Errorf("slug: %w", err)
	}
	if base = strings.Trim(base[:min(len(base), 120)], "-"); base == "" {
		base = "muc"
	}
	base = k.SlugPrefix + base
	table := map[string]string{"foods": "food_items", "stories": "historical_stories"}[k.Path]
	for i := 1; i < 1000; i++ {
		slug := base
		if i > 1 {
			slug += "-" + strconv.Itoa(i)
		}
		var taken bool
		if err := tx.QueryRow(ctx, "SELECT EXISTS (SELECT 1 FROM "+table+" WHERE slug = $1)", slug).Scan(&taken); err != nil {
			return "", fmt.Errorf("slug: %w", err)
		}
		if !taken {
			return slug, nil
		}
	}
	return "", bad("quá nhiều mục cùng tên")
}
