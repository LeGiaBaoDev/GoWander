package feedback

import (
	"bytes"
	"context"
	"errors"
	"image"
	"image/png"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type captureStore struct {
	input Input
	calls int
	err   error
}

func (s *captureStore) Create(_ context.Context, in Input) (string, error) {
	s.input = in
	s.calls++
	return "d5136832-5673-48d8-b6c9-5ecea7e2ed5b", s.err
}
func request(fields map[string]string, imageData []byte) *http.Request {
	var b bytes.Buffer
	m := multipart.NewWriter(&b)
	for k, v := range fields {
		_ = m.WriteField(k, v)
	}
	if imageData != nil {
		p, _ := m.CreateFormFile("image", "screen.png")
		_, _ = p.Write(imageData)
	}
	_ = m.Close()
	r := httptest.NewRequest("POST", "/api/v1/feedbacks", &b)
	r.Header.Set("Content-Type", m.FormDataContentType())
	r.RemoteAddr = "192.0.2.1:1234"
	return r
}
func TestCreateFeedback(t *testing.T) {
	var img bytes.Buffer
	_ = png.Encode(&img, image.NewRGBA(image.Rect(0, 0, 12, 12)))
	s := &captureStore{}
	h := Handler{Store: s, Limit: NewLimiter(5, time.Hour)}
	w := httptest.NewRecorder()
	h.Create(w, request(map[string]string{"kind": "BUG", "message": "  Không mở được ảnh  ", "context": "Chi tiết món", "senderName": " Bảo ", "senderEmail": "bao@example.com"}, img.Bytes()))
	if w.Code != 201 || s.calls != 1 {
		t.Fatalf("status=%d body=%s calls=%d", w.Code, w.Body, s.calls)
	}
	if s.input.Message != "Không mở được ảnh" || s.input.SenderName != "Bảo" {
		t.Fatalf("not normalized: %+v", s.input)
	}
	if len(s.input.Image) == 0 || http.DetectContentType(s.input.Image) != "image/jpeg" {
		t.Fatal("attachment was not reencoded")
	}
	if strings.Contains(w.Body.String(), "bao@example.com") {
		t.Fatal("response leaked contact")
	}
}
func TestRejectFeedback(t *testing.T) {
	cases := []struct {
		name   string
		fields map[string]string
		img    []byte
	}{
		{"empty", map[string]string{"kind": "BUG"}, nil},
		{"kind", map[string]string{"kind": "ADMIN", "message": "hello"}, nil},
		{"email", map[string]string{"kind": "BUG", "message": "hello", "senderEmail": "Name <me@example.com>"}, nil},
		{"long", map[string]string{"kind": "BUG", "message": strings.Repeat("ạ", 1001)}, nil},
		{"not image", map[string]string{"kind": "BUG", "message": "hello"}, []byte("<svg onload='alert(1)'>")},
		{"unknown", map[string]string{"kind": "BUG", "message": "hello", "userId": "someone"}, nil},
	}
	for _, tt := range cases {
		t.Run(tt.name, func(t *testing.T) {
			s := &captureStore{}
			w := httptest.NewRecorder()
			(&Handler{Store: s}).Create(w, request(tt.fields, tt.img))
			if w.Code != 400 || s.calls != 0 {
				t.Fatalf("status %d calls %d", w.Code, s.calls)
			}
		})
	}
}
func TestFeedbackFailureAndLimit(t *testing.T) {
	s := &captureStore{err: errors.New("secret SQL error")}
	h := Handler{Store: s, Limit: NewLimiter(1, time.Hour)}
	w := httptest.NewRecorder()
	h.Create(w, request(map[string]string{"kind": "OTHER", "message": "hello"}, nil))
	if w.Code != 503 || strings.Contains(w.Body.String(), "secret") {
		t.Fatalf("%d %s", w.Code, w.Body)
	}
	w = httptest.NewRecorder()
	h.Create(w, request(map[string]string{"kind": "OTHER", "message": "hello"}, nil))
	if w.Code != 429 || s.calls != 1 {
		t.Fatalf("%d calls %d", w.Code, s.calls)
	}
}
func TestQueryValidation(t *testing.T) {
	for _, q := range []Query{{Status: "unknown"}, {Kind: "unknown"}, {From: "2026-02-30"}, {From: "2026-11-16", To: "2026-11-15"}, {Page: -1}} {
		if ValidateQuery(q) == nil {
			t.Fatalf("accepted %+v", q)
		}
	}
	if err := ValidateQuery(Query{From: "2026-11-15", To: "2026-11-16", Status: "NEW", Page: 1}); err != nil {
		t.Fatal(err)
	}
}
