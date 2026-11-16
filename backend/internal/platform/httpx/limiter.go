package httpx

import (
	"net"
	"net/http"
	"sync"
	"time"
)

// Limiter is a per-process request limit per peer address, shared by the public write endpoints (feedbacks,
// sign-in codes). It uses only the TCP peer address (never untrusted forwarded headers), retains no IP anywhere,
// bounds memory and fails closed when its capacity is reached. A gateway must limit clustered deployments.
type Limiter struct {
	mu     sync.Mutex
	counts map[string]int
	start  time.Time
	n      int
	window time.Duration
}

// NewLimiter allows n requests per window and peer address.
func NewLimiter(n int, window time.Duration) *Limiter {
	return &Limiter{counts: map[string]int{}, start: time.Now(), n: n, window: window}
}

// Allow records one request for the peer of r and reports whether it may proceed.
func (l *Limiter) Allow(r *http.Request) bool {
	ip, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		ip = r.RemoteAddr
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if time.Since(l.start) >= l.window {
		l.start = time.Now()
		l.counts = map[string]int{}
	}
	if _, ok := l.counts[ip]; !ok && len(l.counts) >= 10000 {
		return false
	}
	if l.counts[ip] >= l.n {
		return false
	}
	l.counts[ip]++
	return true
}
