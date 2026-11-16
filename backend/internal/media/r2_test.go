package media

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"
	"time"
)

func TestR2SignsPutAndDelete(t *testing.T) {
	var got []*http.Request
	var body []byte
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = append(got, r)
		body, _ = io.ReadAll(r.Body)
		switch {
		case r.Method == http.MethodDelete && strings.Contains(r.URL.Path, "ffff"):
			w.WriteHeader(http.StatusNotFound)
		case strings.Contains(r.URL.Path, "eeee"):
			w.WriteHeader(http.StatusForbidden)
			io.WriteString(w, "<Error><Code>SignatureDoesNotMatch</Code></Error>")
		}
	}))
	defer srv.Close()
	r := R2{AccountID: "acc", Bucket: "gowander-media", AccessKeyID: "AKID", SecretAccessKey: "very-secret", Endpoint: srv.URL,
		now: func() time.Time { return time.Date(2026, 11, 16, 7, 30, 0, 0, time.UTC) }}
	name := "img/" + strings.Repeat("a", 32) + "-480.jpg"
	if err := r.Put(context.Background(), name, []byte("jpeg")); err != nil {
		t.Fatal(err)
	}
	put := got[0]
	auth := put.Header.Get("Authorization")
	hash := sha256.Sum256([]byte("jpeg"))
	if put.Method != http.MethodPut || put.URL.Path != "/gowander-media/"+name || string(body) != "jpeg" ||
		put.Header.Get("Content-Type") != "image/jpeg" || !strings.Contains(put.Header.Get("Cache-Control"), "immutable") ||
		put.Header.Get("X-Amz-Date") != "20261116T073000Z" || put.Header.Get("X-Amz-Content-Sha256") != hex.EncodeToString(hash[:]) ||
		!regexp.MustCompile(`^AWS4-HMAC-SHA256 Credential=AKID/20261116/auto/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=[0-9a-f]{64}$`).MatchString(auth) {
		t.Fatalf("put %s %s %q", put.Method, put.URL.Path, auth)
	}
	if strings.Contains(auth, "very-secret") {
		t.Fatal("the secret must never be sent")
	}
	other := r
	other.SecretAccessKey = "other"
	other.Put(context.Background(), name, []byte("jpeg"))
	if got[1].Header.Get("Authorization") == auth {
		t.Fatal("the signature must depend on the secret")
	}
	if err := r.Delete(context.Background(), "img/"+strings.Repeat("f", 32)+"-1600.jpg"); err != nil {
		t.Fatalf("deleting a file that is already gone: %v", err)
	}
	err := r.Put(context.Background(), "img/"+strings.Repeat("e", 32)+"-480.jpg", []byte("x"))
	if err == nil || !strings.Contains(err.Error(), "HTTP 403") || !strings.Contains(err.Error(), "SignatureDoesNotMatch") || strings.Contains(err.Error(), "very-secret") {
		t.Fatalf("refused upload: %v", err)
	}
	calls := len(got)
	for _, bad := range []string{"../x.jpg", "img/abc.jpg", name + "?x=1"} {
		if r.Put(context.Background(), bad, nil) == nil {
			t.Errorf("name %q accepted", bad)
		}
	}
	if len(got) != calls {
		t.Fatal("a refused name must not reach the network")
	}
}
