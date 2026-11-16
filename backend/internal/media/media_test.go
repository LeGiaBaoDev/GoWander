package media

import (
	"bytes"
	"context"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func sample(t *testing.T, w, h int) image.Image {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	for x := 0; x < w; x++ {
		img.Set(x, x%h, color.RGBA{200, 30, 60, 255})
	}
	return img
}

func TestProcessReencodesAndDropsMetadata(t *testing.T) {
	var src bytes.Buffer
	if err := jpeg.Encode(&src, sample(t, 2400, 1200), nil); err != nil {
		t.Fatal(err)
	}
	// Put an EXIF block (where cameras keep GPS) right after the JPEG start marker.
	exif := append([]byte{0xFF, 0xE1, 0x00, 0x16}, []byte("Exif\x00\x00GPS 10.77 106.70")...)
	withExif := append(append([]byte{0xFF, 0xD8}, exif...), src.Bytes()[2:]...)
	p, err := Process(bytes.NewReader(withExif))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(p.Key, "img/") || len(p.Key) != 36 || p.W != 2400 || len(p.Files) != 2 {
		t.Fatalf("%+v", p)
	}
	for name, data := range p.Files {
		if bytes.Contains(data, []byte("Exif")) || bytes.Contains(data, []byte("GPS")) {
			t.Errorf("%s keeps metadata", name)
		}
		cfg, format, err := image.DecodeConfig(bytes.NewReader(data))
		if err != nil || format != "jpeg" {
			t.Fatalf("%s: %v %s", name, err, format)
		}
		want := map[bool]int{true: 480, false: 1600}[strings.HasSuffix(name, "-480.jpg")]
		if cfg.Width != want || cfg.Height != want/2 {
			t.Errorf("%s is %dx%d, want %dx%d", name, cfg.Width, cfg.Height, want, want/2)
		}
	}
	again, _ := Process(bytes.NewReader(withExif))
	if again.Key != p.Key {
		t.Error("the same file must get the same name")
	}
}

func TestProcessRefuses(t *testing.T) {
	var small bytes.Buffer
	png.Encode(&small, sample(t, 300, 200))
	if p, err := Process(&small); err != nil || p.W != 300 {
		t.Fatalf("PNG accepted and never scaled up: %+v %v", p, err)
	}
	for name, data := range map[string][]byte{
		"text":    []byte("<?php echo 1; ?>"),
		"svg":     []byte(`<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>`),
		"too big": bytes.Repeat([]byte{0}, MaxBytes+1),
		"empty":   nil,
		"gif":     []byte("GIF89a\x01\x00\x01\x00\x00\x00\x00;"),
	} {
		if _, err := Process(bytes.NewReader(data)); err == nil {
			t.Errorf("%s accepted", name)
		}
	}
}

func TestLocalStorageAndServing(t *testing.T) {
	dir := t.TempDir()
	l := Local{Dir: dir}
	name := "img/0123456789abcdef0123456789abcdef-480.jpg"
	if err := l.Put(context.Background(), name, []byte("jpeg")); err != nil {
		t.Fatal(err)
	}
	for _, bad := range []string{"../etc/passwd", "img/../../x-480.jpg", "img/abc-480.jpg", "img/0123456789abcdef0123456789abcdef-480.png"} {
		if err := l.Put(context.Background(), bad, nil); err == nil {
			t.Errorf("wrote %q", bad)
		}
	}
	mux := http.NewServeMux()
	mux.Handle("GET /media/{name...}", l)
	for path, want := range map[string]int{"/media/" + name: 200, "/media/img/nope-480.jpg": 404, "/media/../go.mod": 307} {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest("GET", path, nil))
		if rec.Code != want { // "..": the mux cleans the path and redirects out of /media; no file is served
			t.Errorf("%s: %d, want %d", path, rec.Code, want)
		}
		if want == 200 && (rec.Header().Get("Content-Type") != "image/jpeg" || rec.Header().Get("X-Content-Type-Options") != "nosniff") {
			t.Errorf("headers %v", rec.Header())
		}
	}
	if err := l.Delete(context.Background(), name); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dir, name)); !os.IsNotExist(err) {
		t.Error("file still there")
	}
	if URL("http://h/media", "img/k", 480) != "http://h/media/img/k-480.jpg" || URL("x", "", 480) != "" {
		t.Error("URL")
	}
}
