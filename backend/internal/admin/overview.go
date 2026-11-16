package admin

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
)

// Overview counts content in two small queries (a few thousand places at pilot size).
func (s *PG) Overview(ctx context.Context) (Overview, error) {
	var o Overview
	err := s.Pool.QueryRow(ctx, `
		SELECT count(*),
		       count(*) FILTER (WHERE status = 'PUBLISHED'),
		       count(*) FILTER (WHERE status = 'HIDDEN'),
		       count(*) FILTER (WHERE status = 'DRAFT'),
		       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM place_media m WHERE m.place_id = p.id)),
		       count(*) FILTER (WHERE btrim(coalesce(short_description, '')) <> ''),
		       count(*) FILTER (WHERE btrim(coalesce(address, '')) <> ''),
		       count(*) FILTER (WHERE primary_category_id IS NOT NULL),
		       max(updated_at),
		       (SELECT count(*) FROM place_categories WHERE is_active),
		       (SELECT count(*) FROM food_items),
		       (SELECT count(*) FROM historical_stories)
		FROM places p`).Scan(&o.Places, &o.Published, &o.Hidden, &o.Draft, &o.WithPhoto, &o.WithDescription, &o.WithAddress,
		&o.WithCategory, &o.LastPlaceUpdate, &o.Categories, &o.Foods, &o.Stories)
	if err != nil {
		return o, fmt.Errorf("overview counts: %w", err)
	}
	rows, err := s.Pool.Query(ctx, `
		SELECT c.name, count(p.id)::int FROM place_categories c
		LEFT JOIN places p ON p.primary_category_id = c.id AND p.status = 'PUBLISHED'
		WHERE c.is_active GROUP BY c.id, c.name ORDER BY 2 DESC, c.name`)
	if err != nil {
		return o, fmt.Errorf("overview categories: %w", err)
	}
	o.ByCategory, err = pgx.CollectRows(rows, pgx.RowToStructByPos[CategoryCount])
	if err != nil {
		return o, fmt.Errorf("overview categories: %w", err)
	}
	return o, nil
}

// Health is one dependency the public app needs, checked when the home page opens.
type Health struct {
	Name  string
	Ready func(context.Context) error
}

// Attention is one line of the "needs attention" list: a problem with its size, worst first.
type Attention struct {
	Text  string
	Count int // 0 when the line is not a count
	Bad   bool
	Link  string // the list that fixes it, when there is one
}

// attention turns the counts into the to-do list. Only problems are listed; nothing to do gives an empty list.
func attention(o Overview, down []string, dataStatus string) []Attention {
	var out []Attention
	for _, name := range down {
		out = append(out, Attention{Text: name + " không phản hồi: người dùng app đang bị ảnh hưởng", Bad: true})
	}
	if dataStatus != "OFFICIAL" {
		out = append(out, Attention{Text: "Dữ liệu tuyến và giờ xe buýt là dữ liệu giả định, chưa phải nguồn chính thức (ADR-027)"})
	}
	for _, gap := range []struct {
		missing    int
		text, link string
	}{
		{o.Places - o.WithPhoto, "địa điểm chưa có ảnh", "/admin/places?missing=photo"},
		{o.Places - o.WithDescription, "địa điểm chưa có mô tả ngắn", "/admin/places?missing=description"},
		{o.Places - o.WithAddress, "địa điểm chưa có địa chỉ", "/admin/places?missing=address"},
		{o.Places - o.WithCategory, "địa điểm chưa có loại", "/admin/places?missing=category"},
		{o.Draft, "địa điểm đang là bản nháp, chưa hiển thị", "/admin/places?status=DRAFT"},
	} {
		if gap.missing > 0 {
			out = append(out, Attention{Text: gap.text, Count: gap.missing, Link: gap.link})
		}
	}
	if o.Foods == 0 && o.Stories == 0 {
		out = append(out, Attention{Text: "Món ăn và câu chuyện chưa có trên server: nội dung hiện nằm sẵn trong app, sửa phải phát hành bản mới"})
	}
	return out
}
