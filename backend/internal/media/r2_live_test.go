//go:build r2

package media

// Against the real bucket (docs/runbooks/cloudflare-r2.md): upload a small photo, read it from the public address,
// delete it. Run with the R2_* settings and the public address loaded:
//
//	go test -tags r2 -run TestR2Live ./internal/media/

import (
	"bytes"
	"context"
	"image"
	"image/png"
	"io"
	"net/http"
	"os"
	"strings"
	"testing"
)

func TestR2Live(t *testing.T) {
	public := strings.TrimRight(os.Getenv("R2_PUBLIC_URL"), "/")
	r := R2{AccountID: os.Getenv("R2_ACCOUNT_ID"), Bucket: os.Getenv("R2_BUCKET"), AccessKeyID: os.Getenv("R2_ACCESS_KEY_ID"), SecretAccessKey: os.Getenv("R2_SECRET_ACCESS_KEY")}
	if r.Bucket == "" || public == "" {
		t.Fatal("set R2_* and R2_PUBLIC_URL")
	}
	var b bytes.Buffer
	png.Encode(&b, image.NewRGBA(image.Rect(0, 0, 64, 48)))
	p, err := Process(&b)
	if err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	for name, data := range p.Files {
		if err := r.Put(ctx, name, data); err != nil {
			t.Fatal(err)
		}
	}
	defer func() {
		for name := range p.Files {
			if err := r.Delete(ctx, name); err != nil {
				t.Errorf("delete %s: %v", name, err)
			}
		}
	}()
	resp, err := http.Get(public + "/" + FileName(p.Key, 480))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	got, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 || resp.Header.Get("Content-Type") != "image/jpeg" || !bytes.Equal(got, p.Files[FileName(p.Key, 480)]) {
		t.Fatalf("public read: HTTP %d %s, %d bytes", resp.StatusCode, resp.Header.Get("Content-Type"), len(got))
	}
	t.Logf("uploaded, read back from %s and deleted %d files", public, len(p.Files))
}
