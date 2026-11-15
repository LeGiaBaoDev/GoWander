package streetview

import (
	"context"
	"errors"
	"fmt"
	"golang.org/x/sync/singleflight"
	"math"
	"net"
	"net/http"
	"sync"
	"time"
)

// Cached wraps a Finder with a TTL cache per ~11 m cell (lat/lon rounded to 4 decimals): route previews revisit the
// same turns, and every miss spends Mapillary quota (security + Go review 14-11-2026). Not-found answers are cached
// too; errors are not, so a Mapillary outage heals on its own.
type Cached struct {
	next    Finder
	ttl     time.Duration
	max     int
	mu      sync.Mutex
	entries map[string]cacheEntry
	flight  singleflight.Group // concurrent misses for one spot share one upstream call
}

type cacheEntry struct {
	img     *Image
	err     error
	expires time.Time
}

func NewCached(next Finder, ttl time.Duration, max int) *Cached {
	return &Cached{next: next, ttl: ttl, max: max, entries: map[string]cacheEntry{}}
}

func (c *Cached) Nearest(ctx context.Context, lat, lon float64) (*Image, error) {
	key := fmt.Sprintf("%.0f:%.0f", math.Round(lat*1e4), math.Round(lon*1e4))
	now := time.Now()
	c.mu.Lock()
	if e, ok := c.entries[key]; ok && now.Before(e.expires) {
		c.mu.Unlock()
		return e.img, e.err
	}
	c.mu.Unlock()
	v, err, _ := c.flight.Do(key, func() (any, error) { return c.next.Nearest(ctx, lat, lon) })
	img, _ := v.(*Image)
	if err == nil || errors.Is(err, ErrNotFound) {
		c.mu.Lock()
		if len(c.entries) >= c.max { // shortcut: drop everything when full instead of LRU; fine at pilot scale
			c.entries = map[string]cacheEntry{}
		}
		c.entries[key] = cacheEntry{img, err, now.Add(c.ttl)}
		c.mu.Unlock()
	}
	return img, err
}

// Limiter allows [n] requests per client IP per [window] (fixed window). In memory, per process.
// shortcut: per-instance and keyed on RemoteAddr; move to the gateway (or honour X-Forwarded-For from a trusted proxy) when
// the API runs behind one.
type Limiter struct {
	n      int
	window time.Duration
	mu     sync.Mutex
	start  time.Time
	counts map[string]int
}

func NewLimiter(n int, window time.Duration) *Limiter {
	return &Limiter{n: n, window: window, start: time.Now(), counts: map[string]int{}}
}

func (l *Limiter) Allow(r *http.Request) bool {
	ip, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		ip = r.RemoteAddr
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if time.Since(l.start) > l.window {
		l.start, l.counts = time.Now(), map[string]int{}
	}
	l.counts[ip]++
	return l.counts[ip] <= l.n
}
