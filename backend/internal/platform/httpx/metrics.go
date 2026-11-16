package httpx

import (
	"fmt"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Metrics is a small Prometheus-text registry, written by hand so the API needs no client library and no extra
// process: one line per series, evaluated when Prometheus scrapes /metrics (M09).
//
// Cardinality is bounded on purpose: the route label is chi's pattern ("/api/v1/places/{id}"), never a raw URL.
type Metrics struct {
	mu        sync.Mutex
	requests  map[seriesKey]uint64
	durations map[routeKey]*histogram
	counters  map[string]*counter
	gauges    map[string]*gauge
}

type seriesKey struct{ route, method, class string }
type routeKey struct{ route, method string }

type histogram struct {
	buckets [4]uint64 // 0.1s, 0.5s, 2s, 5s
	sum     float64
	count   uint64
}

var bucketBounds = [...]float64{0.1, 0.5, 2, 5}

type counter struct {
	help  string
	value uint64
}
type gauge struct {
	help  string
	value func() float64
}

// Default is the registry the router writes to and /metrics reads; a process has exactly one.
var Default = NewMetrics()

// NewMetrics returns an empty registry (tests make their own).
func NewMetrics() *Metrics {
	return &Metrics{
		requests:  make(map[seriesKey]uint64),
		durations: make(map[routeKey]*histogram),
		counters:  make(map[string]*counter),
		gauges:    make(map[string]*gauge),
	}
}

// RegisterCounter declares a counter with its help text; Inc counts one event.
func (m *Metrics) RegisterCounter(name, help string) *Counter {
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.counters[name]
	if !ok {
		c = &counter{help: help}
		m.counters[name] = c
	}
	return &Counter{m: m, name: name}
}

// RegisterGauge declares a value read at scrape time (pool statistics, readiness).
func (m *Metrics) RegisterGauge(name, help string, value func() float64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.gauges[name] = &gauge{help: help, value: value}
}

// Counter is one named counter.
type Counter struct {
	m    *Metrics
	name string
}

func (c *Counter) Inc() { c.m.inc(c.name, 1) }

func (m *Metrics) inc(name string, n uint64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if c, ok := m.counters[name]; ok {
		c.value += n
	}
}

// Observe records one served request.
func (m *Metrics) Observe(route, method string, status int, d time.Duration) {
	if m == nil {
		return
	}
	if route == "" {
		route = "unmatched"
	}
	if method == "" {
		method = "OTHER"
	}
	class := strconv.Itoa(status/100) + "xx"
	if status < 100 || status > 599 {
		class = "other"
	}
	key := seriesKey{route: route, method: method, class: class}
	rk := routeKey{route: route, method: method}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.requests[key]++
	h, ok := m.durations[rk]
	if !ok {
		h = &histogram{}
		m.durations[rk] = h
	}
	sec := d.Seconds()
	for i, bound := range bucketBounds {
		if sec <= bound {
			h.buckets[i]++
		}
	}
	h.sum += sec
	h.count++
}

// ServeHTTP writes the Prometheus text exposition; the caller decides who may read it.
func (m *Metrics) ServeHTTP(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_, _ = w.Write([]byte(m.Render()))
}

// Render is the exposition as a string (tests read this).
func (m *Metrics) Render() string {
	m.mu.Lock()
	defer m.mu.Unlock()
	var b strings.Builder

	b.WriteString("# HELP gowander_http_requests_total Requests served, by route pattern, method and status class.\n")
	b.WriteString("# TYPE gowander_http_requests_total counter\n")
	reqKeys := make([]seriesKey, 0, len(m.requests))
	for k := range m.requests {
		reqKeys = append(reqKeys, k)
	}
	sort.Slice(reqKeys, func(i, j int) bool {
		a, c := reqKeys[i], reqKeys[j]
		if a.route != c.route {
			return a.route < c.route
		}
		if a.method != c.method {
			return a.method < c.method
		}
		return a.class < c.class
	})
	for _, k := range reqKeys {
		fmt.Fprintf(&b, "gowander_http_requests_total{route=%q,method=%q,status=%q} %d\n", k.route, k.method, k.class, m.requests[k])
	}

	b.WriteString("# HELP gowander_http_request_duration_seconds How long the handler took, by route pattern and method.\n")
	b.WriteString("# TYPE gowander_http_request_duration_seconds histogram\n")
	durKeys := make([]routeKey, 0, len(m.durations))
	for k := range m.durations {
		durKeys = append(durKeys, k)
	}
	sort.Slice(durKeys, func(i, j int) bool {
		if durKeys[i].route != durKeys[j].route {
			return durKeys[i].route < durKeys[j].route
		}
		return durKeys[i].method < durKeys[j].method
	})
	for _, k := range durKeys {
		h := m.durations[k]
		for i, bound := range bucketBounds {
			fmt.Fprintf(&b, "gowander_http_request_duration_seconds_bucket{route=%q,method=%q,le=%q} %d\n",
				k.route, k.method, strconv.FormatFloat(bound, 'g', -1, 64), h.buckets[i])
		}
		fmt.Fprintf(&b, "gowander_http_request_duration_seconds_bucket{route=%q,method=%q,le=\"+Inf\"} %d\n", k.route, k.method, h.count)
		fmt.Fprintf(&b, "gowander_http_request_duration_seconds_sum{route=%q,method=%q} %g\n", k.route, k.method, h.sum)
		fmt.Fprintf(&b, "gowander_http_request_duration_seconds_count{route=%q,method=%q} %d\n", k.route, k.method, h.count)
	}

	names := make([]string, 0, len(m.counters))
	for name := range m.counters {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		c := m.counters[name]
		fmt.Fprintf(&b, "# HELP %s %s\n# TYPE %s counter\n%s %d\n", name, c.help, name, name, c.value)
	}

	names = names[:0]
	for name := range m.gauges {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		g := m.gauges[name]
		fmt.Fprintf(&b, "# HELP %s %s\n# TYPE %s gauge\n%s %g\n", name, g.help, name, name, g.value())
	}
	return b.String()
}
