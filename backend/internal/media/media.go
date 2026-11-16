// Package media takes uploaded photos (ADR-035): checks them, re-encodes them in fixed sizes and stores them.
//
// Upload is input from people, so: the file is decoded to prove it is an image (the name and type sent are ignored),
// its size is checked before decoding (no decompression bombs), and it is re-encoded as JPEG, which drops all metadata
// including EXIF GPS. Files are named by a hash of their content, never by anything the uploader chose.
package media

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/jpeg"
	_ "image/png" // decoders for image.Decode
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"
)

const (
	MaxBytes  = 10 << 20   // an upload larger than 10 MB is refused
	maxPixels = 40_000_000 // about 7300×5500: anything bigger is refused before decoding
	maxSide   = 12_000
)

// Sizes made for every photo: a thumbnail for cards and lists, a large one for detail pages.
var Sizes = []int{480, 1600}

var (
	ErrNotImage = errors.New("not a JPEG, PNG or WebP image")
	ErrTooLarge = errors.New("image too large")
)

// Processed is one upload, ready to store: Key is the base ("img/<hash>"), Files the encoded sizes by name.
type Processed struct {
	Key   string
	Files map[string][]byte // "img/<hash>-480.jpg" -> bytes
	W, H  int               // of the original
}

// Process reads at most MaxBytes from r and returns the re-encoded sizes.
func Process(r io.Reader) (Processed, error) {
	raw, err := io.ReadAll(io.LimitReader(r, MaxBytes+1))
	if err != nil {
		return Processed{}, err
	}
	if len(raw) > MaxBytes {
		return Processed{}, ErrTooLarge
	}
	cfg, format, err := image.DecodeConfig(bytes.NewReader(raw))
	if err != nil || (format != "jpeg" && format != "png" && format != "webp") {
		return Processed{}, ErrNotImage
	}
	if cfg.Width <= 0 || cfg.Height <= 0 || cfg.Width > maxSide || cfg.Height > maxSide || cfg.Width*cfg.Height > maxPixels {
		return Processed{}, ErrTooLarge
	}
	src, _, err := image.Decode(bytes.NewReader(raw))
	if err != nil {
		return Processed{}, ErrNotImage
	}
	sum := sha256.Sum256(raw)
	key := "img/" + hex.EncodeToString(sum[:16])
	p := Processed{Key: key, Files: map[string][]byte{}, W: cfg.Width, H: cfg.Height}
	for _, side := range Sizes {
		out, err := encode(src, side)
		if err != nil {
			return Processed{}, err
		}
		p.Files[FileName(key, side)] = out
	}
	return p, nil
}

// FileName is where one size of a photo lives.
func FileName(key string, side int) string { return fmt.Sprintf("%s-%d.jpg", key, side) }

// encode scales src so its longer side is at most side (never up), flattens transparency on white, writes JPEG.
func encode(src image.Image, side int) ([]byte, error) {
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	if longest := max(w, h); longest > side {
		w, h = max(w*side/longest, 1), max(h*side/longest, 1)
	}
	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	draw.Draw(dst, dst.Bounds(), &image.Uniform{C: color.White}, image.Point{}, draw.Src)
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, b, draw.Over, nil)
	var out bytes.Buffer
	quality := 82
	if side < 1000 {
		quality = 78
	}
	if err := jpeg.Encode(&out, dst, &jpeg.Options{Quality: quality}); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

// Storage keeps photo files. Local is for development; production uses R2 (ADR-035).
type Storage interface {
	Put(ctx context.Context, name string, data []byte) error
	Delete(ctx context.Context, name string) error
}

// Local stores files under Dir and serves them at /media/. Development only: ADR-035 keeps production photos off the
// API server (backups, no CDN).
type Local struct{ Dir string }

var namePattern = regexp.MustCompile(`^img/[0-9a-f]{32}-(480|1600)\.jpg$`)

func (l Local) path(name string) (string, error) {
	if !namePattern.MatchString(name) {
		return "", fmt.Errorf("bad media name %q", name)
	}
	return filepath.Join(l.Dir, filepath.FromSlash(name)), nil
}

func (l Local) Put(_ context.Context, name string, data []byte) error {
	p, err := l.path(name)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(p), 0o750); err != nil {
		return err
	}
	tmp := p + ".tmp"
	if err := os.WriteFile(tmp, data, 0o640); err != nil {
		return err
	}
	return os.Rename(tmp, p)
}

func (l Local) Delete(_ context.Context, name string) error {
	p, err := l.path(name)
	if err != nil {
		return err
	}
	if err := os.Remove(p); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return nil
}

// ServeHTTP answers GET /media/<name>: only names this package makes, as JPEG, cached for a year (names never change).
func (l Local) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	name := r.PathValue("name")
	if name == "" {
		name = r.URL.Path[len("/media/"):]
	}
	p, err := l.path(name)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	f, err := os.Open(p)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	http.ServeContent(w, r, "", st.ModTime(), f)
}

// URL builds a photo's address from the public base (MEDIA_BASE_URL) and its key; "" without a key.
func URL(base, key string, side int) string {
	if key == "" {
		return ""
	}
	return base + "/" + FileName(key, side)
}
