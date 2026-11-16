package discover

import (
	"context"
	"fmt"
	"net/http"
	"strings"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

// The region guide's dishes and stories (M07 slice 3), edited in the Admin. Only TP.HCM has a guide so far.
// shortcut: one region, no region column; add one when a second region's guide is written.

type Shop struct {
	Name    string `json:"name"`
	Address string `json:"address"`
}

type Dish struct {
	ID          string `json:"id"` // slug, stable across edits
	Name        string `json:"name"`
	Summary     string `json:"summary"`
	Description string `json:"description"`
	Shops       []Shop `json:"shops"`
	Sources     string `json:"sources"`
}

type Story struct {
	ID         string   `json:"id"` // slug
	Title      string   `json:"title"`
	Summary    string   `json:"summary"`
	Paragraphs []string `json:"paragraphs"`
	TryThis    string   `json:"tryThis"`
	Sources    string   `json:"sources"`
}

type Guide struct {
	Dishes  []Dish  `json:"dishes"`
	Stories []Story `json:"stories"`
}

// ParseShops reads the Admin's "Name | Address" lines; a line without "|" is a name only. Blank lines are skipped.
func ParseShops(text string) []Shop {
	shops := []Shop{}
	for line := range strings.SplitSeq(text, "\n") {
		name, addr, _ := strings.Cut(line, "|")
		if name, addr = strings.TrimSpace(name), strings.TrimSpace(addr); name != "" {
			shops = append(shops, Shop{name, addr})
		}
	}
	return shops
}

// Paragraphs splits story text on blank lines.
func Paragraphs(text string) []string {
	out := []string{}
	for p := range strings.SplitSeq(strings.ReplaceAll(text, "\r\n", "\n"), "\n\n") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}

func (s *PG) Guide(ctx context.Context) (Guide, error) {
	g := Guide{Dishes: []Dish{}, Stories: []Story{}}
	rows, err := s.Pool.Query(ctx, `SELECT slug, name, coalesce(summary, ''), coalesce(description, ''), coalesce(shops, ''), coalesce(sources, '')
		FROM food_items WHERE status = 'PUBLISHED' ORDER BY sort_order, name`)
	if err != nil {
		return g, fmt.Errorf("guide dishes: %w", err)
	}
	for rows.Next() {
		var d Dish
		var shops string
		if err := rows.Scan(&d.ID, &d.Name, &d.Summary, &d.Description, &shops, &d.Sources); err != nil {
			rows.Close()
			return g, fmt.Errorf("guide dishes: %w", err)
		}
		d.Shops = ParseShops(shops)
		g.Dishes = append(g.Dishes, d)
	}
	if err := rows.Err(); err != nil {
		return g, fmt.Errorf("guide dishes: %w", err)
	}
	// Stories without a slug (made outside the Admin) are left out: the app needs a stable id.
	rows, err = s.Pool.Query(ctx, `SELECT slug, title, coalesce(summary, ''), content, coalesce(try_this, ''),
		coalesce(nullif(btrim(sources), ''), source_url, '')
		FROM historical_stories WHERE status = 'PUBLISHED' AND language_code = 'vi' AND slug IS NOT NULL ORDER BY sort_order, title`)
	if err != nil {
		return g, fmt.Errorf("guide stories: %w", err)
	}
	for rows.Next() {
		var st Story
		var content string
		if err := rows.Scan(&st.ID, &st.Title, &st.Summary, &content, &st.TryThis, &st.Sources); err != nil {
			rows.Close()
			return g, fmt.Errorf("guide stories: %w", err)
		}
		st.Paragraphs = Paragraphs(content)
		g.Stories = append(g.Stories, st)
	}
	if err := rows.Err(); err != nil {
		return g, fmt.Errorf("guide stories: %w", err)
	}
	return g, nil
}

func (h Handler) guide(w http.ResponseWriter, r *http.Request) {
	g, err := h.Store.Guide(r.Context())
	if err != nil {
		h.fail(w, r, err)
		return
	}
	httpx.WriteJSON(w, http.StatusOK, g)
}
