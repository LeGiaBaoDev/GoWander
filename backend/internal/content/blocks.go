// Package content holds the long-form text of the app: Editor.js blocks (ADR-041) validated on the server, plus the
// plain text that search and older app versions still read.
package content

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"unicode/utf8"
)

// Limits of one document. They are deliberately generous for an article and small enough that a bad save cannot bloat
// the database or a phone's memory.
const (
	MaxBlocks    = 200
	MaxBlockText = 4000
	MaxTotalText = 20000
	MaxListItems = 50
	MaxItemText  = 500
	MaxCaption   = 300
)

// Block types the server accepts. Anything else is refused rather than stored and ignored later.
const (
	TypeParagraph = "paragraph"
	TypeHeader    = "header"
	TypeList      = "list"
	TypeQuote     = "quote"
	TypeDelimiter = "delimiter"
	TypeImage     = "image"
)

// Document is the Editor.js output the Admin saves; the editor's own metadata (time, version, ids) is dropped.
type Document struct {
	Blocks []Block `json:"blocks"`
}

// Block is one piece of the article. Only the fields of its type are kept.
type Block struct {
	Type string    `json:"type"`
	Data BlockData `json:"data"`
}

// BlockData holds every field any block type uses; the validator clears the ones that do not belong.
type BlockData struct {
	Text    string   `json:"text,omitempty"`
	Level   int      `json:"level,omitempty"`   // header: 2 or 3
	Style   string   `json:"style,omitempty"`   // list: ordered | unordered
	Items   []string `json:"items,omitempty"`   // list
	Caption string   `json:"caption,omitempty"` // quote: who said it
	// image: the key of a row in content_images, with the credit and licence copied from the upload so a block stays
	// readable even if the row is removed later. URL is filled in when the API answers, never stored.
	Key     string `json:"key,omitempty"`
	Alt     string `json:"alt,omitempty"`
	Credit  string `json:"credit,omitempty"`
	License string `json:"license,omitempty"`
	URL     string `json:"url,omitempty"`
}

var (
	tag      = regexp.MustCompile(`<[^>]*>`)
	validKey = regexp.MustCompile(`^[a-z0-9][a-z0-9/_-]*$`)
)

// plainText removes the inline HTML Editor.js writes inside a paragraph (bold, links) and any stray tag, because the app
// renders text, not HTML (ADR-041 §6). Formatting inside a paragraph is therefore lost on purpose; headings, lists and
// quotes keep their structure.
func plainText(s string) string {
	s = strings.ReplaceAll(s, "<br>", "\n")
	s = strings.ReplaceAll(s, "<br/>", "\n")
	s = strings.ReplaceAll(s, "<br />", "\n")
	s = tag.ReplaceAllString(s, "")
	s = strings.ReplaceAll(s, "&nbsp;", " ")
	s = strings.ReplaceAll(s, "&amp;", "&")
	s = strings.ReplaceAll(s, "&lt;", "<")
	s = strings.ReplaceAll(s, "&gt;", ">")
	s = strings.ReplaceAll(s, "&quot;", `"`)
	s = strings.ReplaceAll(s, "&#39;", "'")
	return strings.TrimSpace(strings.ReplaceAll(s, "\r\n", "\n"))
}

// Parse reads the Admin's JSON and validates it; the error sentence is shown to the editor as it is.
func Parse(raw string) (Document, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return Document{}, nil
	}
	var parsed doc
	if err := json.Unmarshal([]byte(raw), &parsed); err != nil {
		return Document{}, fmt.Errorf("blocks: JSON không đọc được (%v)", err)
	}
	return parsed.normalise()
}

// doc is what the editor sends: blocks plus metadata we ignore.
type doc struct {
	Blocks []struct {
		Type string          `json:"type"`
		Data json.RawMessage `json:"data"`
	} `json:"blocks"`
}

func (d doc) normalise() (Document, error) {
	if len(d.Blocks) > MaxBlocks {
		return Document{}, fmt.Errorf("blocks: tối đa %d khối", MaxBlocks)
	}
	out := Document{Blocks: make([]Block, 0, len(d.Blocks))}
	total := 0
	for i, b := range d.Blocks {
		var data BlockData
		if len(b.Data) > 0 {
			if err := json.Unmarshal(b.Data, &data); err != nil {
				return Document{}, fmt.Errorf("blocks: khối %d có dữ liệu không đọc được", i+1)
			}
		}
		block, err := normaliseBlock(b.Type, data)
		if err != nil {
			return Document{}, fmt.Errorf("blocks: khối %d: %w", i+1, err)
		}
		total += block.length()
		out.Blocks = append(out.Blocks, block)
	}
	if total > MaxTotalText {
		return Document{}, fmt.Errorf("blocks: toàn bài dài quá %d ký tự", MaxTotalText)
	}
	return out, nil
}

func (b Block) length() int {
	n := utf8.RuneCountInString(b.Data.Text) + utf8.RuneCountInString(b.Data.Caption)
	for _, it := range b.Data.Items {
		n += utf8.RuneCountInString(it)
	}
	return n
}

func normaliseBlock(kind string, d BlockData) (Block, error) {
	switch kind {
	case TypeParagraph, TypeQuote:
		text := plainText(d.Text)
		if text == "" {
			return Block{}, fmt.Errorf("%s rỗng", kind)
		}
		if utf8.RuneCountInString(text) > MaxBlockText {
			return Block{}, fmt.Errorf("đoạn dài quá %d ký tự", MaxBlockText)
		}
		b := Block{Type: kind, Data: BlockData{Text: text}}
		if kind == TypeQuote {
			b.Data.Caption = plainText(d.Caption)
			if utf8.RuneCountInString(b.Data.Caption) > MaxCaption {
				b.Data.Caption = string([]rune(b.Data.Caption)[:MaxCaption])
			}
		}
		return b, nil
	case TypeHeader:
		text := plainText(d.Text)
		if text == "" {
			return Block{}, fmt.Errorf("tiêu đề rỗng")
		}
		if utf8.RuneCountInString(text) > 200 {
			return Block{}, fmt.Errorf("tiêu đề dài quá 200 ký tự")
		}
		level := d.Level
		if level != 2 && level != 3 {
			level = 2
		}
		return Block{Type: kind, Data: BlockData{Text: text, Level: level}}, nil
	case TypeList:
		style := d.Style
		if style != "ordered" && style != "unordered" {
			style = "unordered"
		}
		items := make([]string, 0, len(d.Items))
		for _, it := range d.Items {
			if t := plainText(it); t != "" {
				if utf8.RuneCountInString(t) > MaxItemText {
					return Block{}, fmt.Errorf("một mục dài quá %d ký tự", MaxItemText)
				}
				items = append(items, t)
			}
		}
		if len(items) == 0 {
			return Block{}, fmt.Errorf("danh sách rỗng")
		}
		if len(items) > MaxListItems {
			return Block{}, fmt.Errorf("danh sách có quá %d mục", MaxListItems)
		}
		return Block{Type: kind, Data: BlockData{Style: style, Items: items}}, nil
	case TypeDelimiter:
		return Block{Type: kind, Data: BlockData{}}, nil
	case TypeImage:
		key := strings.TrimSpace(d.Key)
		credit, license := plainText(d.Credit), strings.TrimSpace(d.License)
		switch {
		// Media keys look like "img/<hex>": letters, digits, "-", "_" and the folder slash, never a way out of the folder.
		case key == "" || len(key) > 200 || strings.HasPrefix(key, "/") || strings.Contains(key, "..") ||
			strings.ContainsAny(key, " \\") || !validKey.MatchString(key):
			return Block{}, fmt.Errorf("ảnh thiếu khoá lưu trữ hợp lệ")
		case credit == "" || utf8.RuneCountInString(credit) > 200:
			return Block{}, fmt.Errorf("ảnh phải có ghi công (tối đa 200 ký tự)")
		case license == "" || utf8.RuneCountInString(license) > 40:
			return Block{}, fmt.Errorf("ảnh phải có giấy phép")
		}
		alt := plainText(d.Alt)
		if utf8.RuneCountInString(alt) > 300 {
			alt = string([]rune(alt)[:300])
		}
		return Block{Type: kind, Data: BlockData{Key: key, Alt: alt, Credit: credit, License: license}}, nil
	default:
		return Block{}, fmt.Errorf("loại khối %q không được hỗ trợ", kind)
	}
}

// Text is the plain version of the document: what search matches and what the app shows when it cannot render blocks.
// One paragraph per line, a blank line around headings and quotes so the shape is still readable.
func (d Document) Text() string {
	var parts []string
	for _, b := range d.Blocks {
		switch b.Type {
		case TypeParagraph:
			parts = append(parts, b.Data.Text)
		case TypeHeader:
			parts = append(parts, b.Data.Text)
		case TypeList:
			for _, it := range b.Data.Items {
				parts = append(parts, "• "+it)
			}
		case TypeQuote:
			q := "“" + b.Data.Text + "”"
			if b.Data.Caption != "" {
				q += " — " + b.Data.Caption
			}
			parts = append(parts, q)
		case TypeImage:
			if b.Data.Alt != "" {
				parts = append(parts, b.Data.Alt)
			}
		}
	}
	return strings.TrimSpace(strings.Join(parts, "\n\n"))
}

// JSON is the document as stored in JSONB (compact, no nulls).
func (d Document) JSON() string {
	if len(d.Blocks) == 0 {
		return ""
	}
	b, err := json.Marshal(d)
	if err != nil {
		return ""
	}
	return string(b)
}
