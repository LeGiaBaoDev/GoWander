package discover

import (
	"context"
	"errors"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// PG is the PostgreSQL/PostGIS Store. Every query filters on status = 'PUBLISHED'.
type PG struct {
	Pool *pgxpool.Pool
}

const summaryColumns = `p.id::text, p.name, coalesce(p.name_en, ''), p.slug, coalesce(p.short_description, ''),
	ST_Y(p.location::geometry), ST_X(p.location::geometry), coalesce(p.address, ''),
	c.slug, c.name, c.name_en, p.source, coalesce(p.source_url, ''), p.license, p.attribution`

const categoryFilter = `($1::text = '' OR EXISTS (
	SELECT 1 FROM place_category_links l JOIN place_categories lc ON lc.id = l.category_id
	WHERE l.place_id = p.id AND lc.slug = $1 AND lc.is_active))`

const (
	qCategories = `
SELECT c.slug, c.name, coalesce(c.name_en, ''),
       (SELECT count(*) FROM place_category_links l JOIN places p ON p.id = l.place_id
         WHERE l.category_id = c.id AND p.status = 'PUBLISHED')
  FROM place_categories c
 WHERE c.is_active
 ORDER BY c.sort_order, c.id`

	// Keyset pagination on (name, id) matches places_published_name_idx. $2 is LIKE-escaped; search_text() folds it like search_doc.
	qListPlaces = `
SELECT ` + summaryColumns + `
  FROM places p LEFT JOIN place_categories c ON c.id = p.primary_category_id
 WHERE p.status = 'PUBLISHED'
   AND ` + categoryFilter + `
   AND ($2::text = '' OR p.search_doc LIKE '%' || search_text($2) || '%')
   AND ($3::text IS NULL OR (p.name, p.id) > ($3::text, $4::uuid))
 ORDER BY p.name, p.id
 LIMIT $5`

	// ST_DWithin on geography uses places_location_gix; distances are in metres on the spheroid.
	qNearby = `
WITH here AS (SELECT ST_SetSRID(ST_MakePoint($3, $2), 4326)::geography AS g)
SELECT ` + summaryColumns + `, ST_Distance(p.location, here.g)
  FROM places p CROSS JOIN here LEFT JOIN place_categories c ON c.id = p.primary_category_id
 WHERE p.status = 'PUBLISHED'
   AND ST_DWithin(p.location, here.g, $4)
   AND ` + categoryFilter + `
 ORDER BY ST_Distance(p.location, here.g), p.id
 LIMIT $5`

	qPlace = `
SELECT ` + summaryColumns + `
  FROM places p LEFT JOIN place_categories c ON c.id = p.primary_category_id
 WHERE p.id = $1 AND p.status = 'PUBLISHED'`

	qPlaceCategories = `
SELECT c.slug, c.name, coalesce(c.name_en, '')
  FROM place_category_links l JOIN place_categories c ON c.id = l.category_id
 WHERE l.place_id = $1 AND c.is_active
 ORDER BY c.sort_order, c.id`

	qPlaceMedia = `
SELECT storage_key, media_type, coalesce(alt_text, ''), source_credit, license_info, is_cover
  FROM place_media WHERE place_id = $1
 ORDER BY sort_order, id`

	qPlaceFoods = `
SELECT f.name, coalesce(f.name_en, ''), f.slug, pf.price_min::float8, pf.price_max::float8, pf.currency_code
  FROM place_foods pf JOIN food_items f ON f.id = pf.food_id
 WHERE pf.place_id = $1 AND f.status = 'PUBLISHED'
 ORDER BY f.name, f.id`
)

func (s *PG) Ready(ctx context.Context) error { return s.Pool.Ping(ctx) }

func (s *PG) Categories(ctx context.Context) ([]Category, error) {
	rows, err := s.Pool.Query(ctx, qCategories)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Category, error) {
		var c Category
		err := r.Scan(&c.Slug, &c.Name, &c.NameEn, &c.PlaceCount)
		return c, err
	})
}

func (s *PG) ListPlaces(ctx context.Context, q ListQuery) ([]PlaceSummary, *Cursor, error) {
	var afterName, afterID *string
	if q.After != nil {
		afterName, afterID = &q.After.Name, &q.After.ID
	}
	rows, err := s.Pool.Query(ctx, qListPlaces, q.Category, likeEscape(q.Q), afterName, afterID, q.Limit+1)
	if err != nil {
		return nil, nil, err
	}
	places, err := pgx.CollectRows(rows, scanSummary)
	if err != nil {
		return nil, nil, err
	}
	if len(places) <= q.Limit {
		return places, nil, nil
	}
	places = places[:q.Limit]
	last := places[len(places)-1]
	return places, &Cursor{Name: last.Name, ID: last.ID}, nil
}

func (s *PG) Nearby(ctx context.Context, q NearbyQuery) ([]PlaceSummary, error) {
	rows, err := s.Pool.Query(ctx, qNearby, q.Category, q.Lat, q.Lon, q.RadiusM, q.Limit)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (PlaceSummary, error) {
		var p PlaceSummary
		var d float64
		err := r.Scan(append(summaryDest(&p), &d)...)
		p.DistanceM = &d
		return finishSummary(p, err)
	})
}

func (s *PG) Place(ctx context.Context, id string) (PlaceDetail, error) {
	var d PlaceDetail
	err := s.Pool.QueryRow(ctx, qPlace, id).Scan(summaryDest(&d.PlaceSummary)...)
	if errors.Is(err, pgx.ErrNoRows) {
		return PlaceDetail{}, ErrNotFound
	}
	if d.PlaceSummary, err = finishSummary(d.PlaceSummary, err); err != nil {
		return PlaceDetail{}, err
	}
	// Three small reads in one round trip. Not one transaction with the read above: a place unpublished
	// in between returns with empty lists, acceptable for read-only Discover data.
	// Errors from br.Query surface through CollectRows (rows.Err).
	batch := &pgx.Batch{}
	batch.Queue(qPlaceCategories, id)
	batch.Queue(qPlaceMedia, id)
	batch.Queue(qPlaceFoods, id)
	br := s.Pool.SendBatch(ctx, batch)
	defer br.Close()

	rows, _ := br.Query()
	if d.Categories, err = pgx.CollectRows(rows, func(r pgx.CollectableRow) (CategoryRef, error) {
		var c CategoryRef
		return c, r.Scan(&c.Slug, &c.Name, &c.NameEn)
	}); err != nil {
		return PlaceDetail{}, err
	}
	rows, _ = br.Query()
	if d.Media, err = pgx.CollectRows(rows, func(r pgx.CollectableRow) (Media, error) {
		var m Media
		return m, r.Scan(&m.StorageKey, &m.MediaType, &m.AltText, &m.SourceCredit, &m.License, &m.IsCover)
	}); err != nil {
		return PlaceDetail{}, err
	}
	rows, _ = br.Query()
	if d.Foods, err = pgx.CollectRows(rows, func(r pgx.CollectableRow) (Food, error) {
		var f Food
		return f, r.Scan(&f.Name, &f.NameEn, &f.Slug, &f.PriceMin, &f.PriceMax, &f.Currency)
	}); err != nil {
		return PlaceDetail{}, err
	}
	if err := br.Close(); err != nil { // the deferred Close is only a safety net
		return PlaceDetail{}, err
	}
	return d, nil
}

func summaryDest(p *PlaceSummary) []any {
	p.Category = &CategoryRef{}
	return []any{&p.ID, &p.Name, &p.NameEn, &p.Slug, &p.ShortDescription, &p.Lat, &p.Lon, &p.Address,
		nullable{&p.Category.Slug}, nullable{&p.Category.Name}, nullable{&p.Category.NameEn},
		&p.Source.Kind, &p.Source.URL, &p.Source.License, &p.Source.Attribution}
}

func scanSummary(r pgx.CollectableRow) (PlaceSummary, error) {
	var p PlaceSummary
	return finishSummary(p, r.Scan(summaryDest(&p)...))
}

// finishSummary drops the category when the place has no primary category (LEFT JOIN gave NULLs).
func finishSummary(p PlaceSummary, err error) (PlaceSummary, error) {
	if err != nil {
		return PlaceSummary{}, err
	}
	if p.Category != nil && p.Category.Slug == "" {
		p.Category = nil
	}
	return p, nil
}

// nullable scans a possibly-NULL text column into a string ("" for NULL).
type nullable struct{ s *string }

func (n nullable) Scan(src any) error {
	switch v := src.(type) {
	case nil:
		*n.s = ""
	case string:
		*n.s = v
	default:
		return errors.New("nullable: unexpected type")
	}
	return nil
}

// likeEscape makes % and _ literal in a LIKE pattern (default escape character is backslash).
func likeEscape(s string) string {
	return strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(s)
}
