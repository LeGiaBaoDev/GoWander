package media

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// R2 stores photos in a Cloudflare R2 bucket through its S3 API (ADR-035; docs/runbooks/cloudflare-r2.md). Requests
// are signed with AWS Signature Version 4, region "auto", by hand: two calls (PUT, DELETE) do not justify an SDK.
// The token should only reach this one bucket ("Object Read & Write"). Readers fetch files from the bucket's
// public address (MEDIA_BASE_URL), never through here.
type R2 struct {
	AccountID, Bucket, AccessKeyID, SecretAccessKey string
	Client                                          *http.Client // nil: a client with a 30 s timeout
	Endpoint                                        string       // tests only; default https://<account>.r2.cloudflarestorage.com
	now                                             func() time.Time
}

func (r R2) Put(ctx context.Context, name string, data []byte) error {
	return r.do(ctx, http.MethodPut, name, data)
}

// Delete removes a file; one that is already gone is not an error.
func (r R2) Delete(ctx context.Context, name string) error {
	return r.do(ctx, http.MethodDelete, name, nil)
}

func (r R2) do(ctx context.Context, method, name string, body []byte) error {
	if !namePattern.MatchString(name) {
		return fmt.Errorf("media: refused name %q", name)
	}
	endpoint := r.Endpoint
	if endpoint == "" {
		endpoint = "https://" + r.AccountID + ".r2.cloudflarestorage.com"
	}
	path := "/" + url.PathEscape(r.Bucket) + "/" + name // name is [a-z0-9/.-] only: no escaping needed
	req, err := http.NewRequestWithContext(ctx, method, endpoint+path, bytes.NewReader(body))
	if err != nil {
		return err
	}
	if method == http.MethodPut {
		req.Header.Set("Content-Type", "image/jpeg")
		req.Header.Set("Cache-Control", "public, max-age=31536000, immutable") // names are content hashes
	}
	r.sign(req, body)
	client := r.Client
	if client == nil {
		client = &http.Client{Timeout: 30 * time.Second}
	}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("r2 %s: %w", method, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 == 2 || method == http.MethodDelete && resp.StatusCode == http.StatusNotFound {
		return nil
	}
	msg, _ := io.ReadAll(io.LimitReader(resp.Body, 512)) // S3 error XML: code and message, no secrets
	return fmt.Errorf("r2 %s %s: HTTP %d: %s", method, name, resp.StatusCode, strings.TrimSpace(string(msg)))
}

// sign adds the SigV4 headers (host, x-amz-date, x-amz-content-sha256 and Authorization).
func (r R2) sign(req *http.Request, body []byte) {
	now := time.Now
	if r.now != nil {
		now = r.now
	}
	t := now().UTC()
	stamp, day := t.Format("20060102T150405Z"), t.Format("20060102")
	payload := sha256.Sum256(body)
	payloadHex := hex.EncodeToString(payload[:])
	req.Header.Set("X-Amz-Date", stamp)
	req.Header.Set("X-Amz-Content-Sha256", payloadHex)
	const signed = "host;x-amz-content-sha256;x-amz-date"
	canonical := strings.Join([]string{req.Method, req.URL.EscapedPath(), req.URL.RawQuery,
		"host:" + req.URL.Host, "x-amz-content-sha256:" + payloadHex, "x-amz-date:" + stamp, "", signed, payloadHex}, "\n")
	scope := day + "/auto/s3/aws4_request"
	sum := sha256.Sum256([]byte(canonical))
	toSign := "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hex.EncodeToString(sum[:])
	key := []byte("AWS4" + r.SecretAccessKey)
	for _, part := range []string{day, "auto", "s3", "aws4_request"} {
		key = hmacSHA256(key, part)
	}
	req.Header.Set("Authorization", "AWS4-HMAC-SHA256 Credential="+r.AccessKeyID+"/"+scope+
		", SignedHeaders="+signed+", Signature="+hex.EncodeToString(hmacSHA256(key, toSign)))
}

func hmacSHA256(key []byte, data string) []byte {
	m := hmac.New(sha256.New, key)
	m.Write([]byte(data))
	return m.Sum(nil)
}
