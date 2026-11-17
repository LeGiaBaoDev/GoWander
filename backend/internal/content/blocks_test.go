package content

import (
	"strings"
	"testing"
)

func TestParseNormalises(t *testing.T) {
	raw := `{"time":1,"version":"2.31","blocks":[
		{"type":"header","data":{"text":"Chợ Lớn","level":2}},
		{"type":"paragraph","data":{"text":"Một <b>khu phố</b> lâu đời &amp; đông đúc."}},
		{"type":"list","data":{"style":"ordered","items":["<i>Bến</i> Thành","Hà Tôn Quyền"]}},
		{"type":"quote","data":{"text":"Đi một ngày đàng","caption":"Tục ngữ"}},
		{"type":"delimiter","data":{}}
	]}`
	doc, err := Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	if len(doc.Blocks) != 5 {
		t.Fatalf("blocks = %d", len(doc.Blocks))
	}
	if doc.Blocks[1].Data.Text != "Một khu phố lâu đời & đông đúc." {
		t.Fatalf("inline HTML survived: %q", doc.Blocks[1].Data.Text)
	}
	if doc.Blocks[2].Data.Style != "ordered" || doc.Blocks[2].Data.Items[0] != "Bến Thành" {
		t.Fatalf("list = %+v", doc.Blocks[2].Data)
	}
	text := doc.Text()
	for _, want := range []string{"Chợ Lớn", "Một khu phố lâu đời & đông đúc.", "• Bến Thành", "“Đi một ngày đàng” — Tục ngữ"} {
		if !strings.Contains(text, want) {
			t.Errorf("plain text is missing %q\n%s", want, text)
		}
	}
	if !strings.Contains(doc.JSON(), `"header"`) || strings.Contains(doc.JSON(), "<b>") {
		t.Fatalf("stored JSON = %s", doc.JSON())
	}
}

func TestParseRefuses(t *testing.T) {
	cases := map[string]string{
		"not JSON":        `{`,
		"unknown type":    `{"blocks":[{"type":"embed","data":{"text":"x"}}]}`,
		"empty paragraph": `{"blocks":[{"type":"paragraph","data":{"text":"   "}}]}`,
		"empty list":      `{"blocks":[{"type":"list","data":{"items":[]}}]}`,
		"huge paragraph":  `{"blocks":[{"type":"paragraph","data":{"text":"` + strings.Repeat("a", MaxBlockText+1) + `"}}]}`,
	}
	for name, raw := range cases {
		if _, err := Parse(raw); err == nil {
			t.Errorf("%s was accepted", name)
		}
	}
	// Too many blocks.
	var b strings.Builder
	b.WriteString(`{"blocks":[`)
	for i := 0; i <= MaxBlocks; i++ {
		if i > 0 {
			b.WriteString(",")
		}
		b.WriteString(`{"type":"delimiter","data":{}}`)
	}
	b.WriteString(`]}`)
	if _, err := Parse(b.String()); err == nil {
		t.Error("a document with too many blocks was accepted")
	}
}

func TestEmptyIsAnEmptyDocument(t *testing.T) {
	doc, err := Parse("  ")
	if err != nil || len(doc.Blocks) != 0 || doc.Text() != "" || doc.JSON() != "" {
		t.Fatalf("empty: %+v %v", doc, err)
	}
}

func TestHeaderLevelAndDefaults(t *testing.T) {
	doc, err := Parse(`{"blocks":[{"type":"header","data":{"text":"Mục","level":7}},{"type":"list","data":{"items":["a"]}}]}`)
	if err != nil {
		t.Fatal(err)
	}
	if doc.Blocks[0].Data.Level != 2 {
		t.Fatalf("level = %d, want 2", doc.Blocks[0].Data.Level)
	}
	if doc.Blocks[1].Data.Style != "unordered" {
		t.Fatalf("style = %q", doc.Blocks[1].Data.Style)
	}
}

func TestImageBlocksNeedCreditAndLicence(t *testing.T) {
	ok := `{"blocks":[{"type":"image","data":{"key":"abc-1700","alt":"Chợ Bình Tây","credit":"Ảnh: Lan Anh","license":"CC BY 4.0"}}]}`
	doc, err := Parse(ok)
	if err != nil {
		t.Fatal(err)
	}
	b := doc.Blocks[0]
	if b.Data.Key != "abc-1700" || b.Data.Credit != "Ảnh: Lan Anh" || b.Data.License != "CC BY 4.0" || b.Data.Alt != "Chợ Bình Tây" {
		t.Fatalf("image block = %+v", b.Data)
	}
	if _, err := Parse(`{"blocks":[{"type":"image","data":{"key":"k","alt":"x","license":"CC0"}}]}`); err == nil {
		t.Error("an image without credit was accepted")
	}
	if _, err := Parse(`{"blocks":[{"type":"image","data":{"key":"k","credit":"me"}}]}`); err == nil {
		t.Error("an image without a licence was accepted")
	}
	if _, err := Parse(`{"blocks":[{"type":"image","data":{"key":"../etc/passwd","credit":"me","license":"CC0"}}]}`); err == nil {
		t.Error("a key with a path was accepted")
	}
}
