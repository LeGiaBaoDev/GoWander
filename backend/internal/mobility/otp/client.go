// Package otp adapts OpenTripPlanner 2.10 (GTFS GraphQL API) to mobility.RoutingProvider.
// It is the only package that knows OTP's schema.
package otp

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"strings"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
)

//go:embed plan.graphql
var planQuery string

const (
	maxResponseBytes = 8 << 20
	maxConnsToOTP    = 16 // bounds concurrent planning calls; extra requests wait and hit OTP_TIMEOUT instead of piling onto OTP
)

type Client struct {
	baseURL string
	timeout time.Duration
	http    *http.Client
}

// New returns an OTP client. timeout bounds each planning call (ROUTING_TIMEOUT when exceeded).
func New(baseURL string, timeout time.Duration) *Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.MaxConnsPerHost, transport.MaxIdleConnsPerHost = maxConnsToOTP, maxConnsToOTP
	return &Client{baseURL: strings.TrimRight(baseURL, "/"), timeout: timeout, http: &http.Client{
		Transport:     transport,
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}}
}

// Ready reports whether OTP answers its actuator health endpoint with 200.
func (c *Client) Ready(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/otp/actuators/health", nil)
	if err != nil {
		return err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer drainClose(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("otp health returned HTTP %d", resp.StatusCode)
	}
	return nil
}

func (c *Client) PlanRoute(ctx context.Context, q mobility.Query) ([]mobility.Route, error) {
	callCtx, cancel := context.WithTimeout(ctx, c.timeout)
	defer cancel()

	body, err := json.Marshal(map[string]any{"query": planQuery, "variables": variables(q)})
	if err != nil {
		return nil, mobility.Fail(mobility.RoutingUnavailable, err)
	}
	req, err := http.NewRequestWithContext(callCtx, http.MethodPost, c.baseURL+"/otp/gtfs/v1", bytes.NewReader(body))
	if err != nil {
		return nil, mobility.Fail(mobility.RoutingUnavailable, err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.http.Do(req)
	if err != nil {
		switch {
		case ctx.Err() != nil: // the caller gave up; not an OTP problem
			return nil, ctx.Err()
		case errors.Is(err, context.DeadlineExceeded):
			return nil, mobility.Fail(mobility.RoutingTimeout, err)
		}
		return nil, mobility.Fail(mobility.RoutingUnavailable, err)
	}
	defer drainClose(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return nil, mobility.Fail(mobility.RoutingUnavailable, fmt.Errorf("otp returned HTTP %d", resp.StatusCode))
	}

	var out planResponse
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxResponseBytes)).Decode(&out); err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		if errors.Is(err, context.DeadlineExceeded) {
			return nil, mobility.Fail(mobility.RoutingTimeout, err)
		}
		return nil, mobility.Fail(mobility.RoutingUnavailable, fmt.Errorf("decode otp response: %w", err))
	}
	if len(out.Errors) > 0 || out.Data == nil || out.Data.PlanConnection == nil {
		msgs := make([]string, len(out.Errors))
		for i, e := range out.Errors {
			msgs[i] = e.Message
		}
		return nil, mobility.Fail(mobility.RoutingUnavailable, fmt.Errorf("otp graphql errors: %v", msgs))
	}
	return c.routes(*out.Data.PlanConnection)
}

// drainClose lets the transport reuse the connection to OTP.
func drainClose(body io.ReadCloser) {
	_, _ = io.Copy(io.Discard, io.LimitReader(body, 64<<10))
	_ = body.Close()
}

func variables(q mobility.Query) map[string]any {
	place := func(p mobility.Coordinate) map[string]any {
		return map[string]any{"location": map[string]any{"coordinate": map[string]any{"latitude": p.Lat, "longitude": p.Lon}}}
	}
	timeKey := "earliestDeparture"
	if q.ArriveBy {
		timeKey = "latestArrival"
	}
	v := map[string]any{
		"origin":      place(q.Origin),
		"destination": place(q.Destination),
		"dateTime":    map[string]any{timeKey: q.DateTime.Format(time.RFC3339)},
		"first":       q.MaxResults,
	}
	// No "modes" key means OTP's default (walking plus every transit mode); OTP 2.10 throws on an explicit null.
	switch {
	case q.Walk && !q.Transit:
		v["modes"] = map[string]any{"directOnly": true, "direct": []string{"WALK"}}
	case q.Transit && !q.Walk:
		v["modes"] = map[string]any{"transitOnly": true}
	}
	return v
}

func (c *Client) routes(pc planConnection) ([]mobility.Route, error) {
	if len(pc.Edges) == 0 {
		codes := make([]string, len(pc.RoutingErrors))
		for i, e := range pc.RoutingErrors {
			codes[i] = e.Code
		}
		code, known := noRouteCode(codes)
		cause := fmt.Errorf("otp routingErrors %v", codes)
		if !known {
			cause = fmt.Errorf("%w: %w", mobility.ErrUnrecognized, cause)
		}
		return nil, mobility.Fail(code, cause)
	}
	routes := make([]mobility.Route, 0, len(pc.Edges))
	for _, e := range pc.Edges {
		r, err := route(e.Node)
		if err != nil {
			return nil, mobility.Fail(mobility.RoutingUnavailable, err)
		}
		routes = append(routes, r)
	}
	return routes, nil
}

// noRouteCode maps OTP routingErrors (when there is no itinerary) to a client code.
// known is false when no code was recognised; the answer then falls back to NO_ROUTE_FOUND.
func noRouteCode(codes []string) (code mobility.Code, known bool) {
	code = mobility.NoRouteFound
	for _, c := range codes {
		switch c {
		case "OUTSIDE_BOUNDS":
			return mobility.OutOfCoverage, true
		case "OUTSIDE_SERVICE_PERIOD":
			return mobility.DataUnavailable, true
		case "NO_TRANSIT_CONNECTION", "NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW", "NO_STOPS_IN_RANGE",
			"WALKING_BETTER_THAN_TRANSIT", "LOCATION_NOT_FOUND", "NO_DIRECT_MODE_CONNECTION":
			known = true
		}
	}
	return code, known
}

func route(n itinerary) (mobility.Route, error) {
	start, err1 := time.Parse(time.RFC3339, n.Start)
	end, err2 := time.Parse(time.RFC3339, n.End)
	if err := errors.Join(err1, err2); err != nil {
		return mobility.Route{}, fmt.Errorf("itinerary time: %w", err)
	}
	if len(n.Legs) == 0 {
		return mobility.Route{}, errors.New("itinerary without legs")
	}
	r := mobility.Route{StartTime: start, EndTime: end, DurationSec: int(n.Duration),
		WalkDistanceM: n.WalkDistance, Transfers: n.NumberOfTransfers, Legs: make([]mobility.Leg, 0, len(n.Legs))}
	for _, l := range n.Legs {
		ls, err1 := time.Parse(time.RFC3339, l.Start.best())
		le, err2 := time.Parse(time.RFC3339, l.End.best())
		if err := errors.Join(err1, err2); err != nil {
			return mobility.Route{}, fmt.Errorf("leg time: %w", err)
		}
		if l.Mode == "" || (l.TransitLeg && (l.LegGeometry == nil || l.LegGeometry.Points == "")) {
			return mobility.Route{}, fmt.Errorf("incomplete leg: mode %q, transit %v, geometry %v", l.Mode, l.TransitLeg, l.LegGeometry != nil)
		}
		leg := mobility.Leg{
			Mode: l.Mode, Transit: l.TransitLeg, From: l.From.place(), To: l.To.place(),
			StartTime: ls, EndTime: le, DurationSec: int(math.Round(l.Duration)), DistanceM: l.Distance,
			Realtime: l.Start.Estimated != nil || l.End.Estimated != nil, Headsign: deref(l.Headsign),
		}
		if l.Route != nil {
			leg.Line = &mobility.Line{ID: l.Route.GtfsID, ShortName: deref(l.Route.ShortName), LongName: deref(l.Route.LongName)}
		}
		if l.LegGeometry != nil {
			leg.Polyline = l.LegGeometry.Points
		}
		r.Legs = append(r.Legs, leg)
	}
	return r, nil
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// Response shapes of plan.graphql.
type planResponse struct {
	Data *struct {
		PlanConnection *planConnection `json:"planConnection"`
	} `json:"data"`
	Errors []struct {
		Message string `json:"message"`
	} `json:"errors"`
}

type planConnection struct {
	RoutingErrors []struct {
		Code string `json:"code"`
	} `json:"routingErrors"`
	Edges []struct {
		Node itinerary `json:"node"`
	} `json:"edges"`
}

type itinerary struct {
	Start             string  `json:"start"`
	End               string  `json:"end"`
	Duration          int64   `json:"duration"`
	WalkDistance      float64 `json:"walkDistance"`
	NumberOfTransfers int     `json:"numberOfTransfers"`
	Legs              []leg   `json:"legs"`
}

type legTime struct {
	ScheduledTime string `json:"scheduledTime"`
	Estimated     *struct {
		Time string `json:"time"`
	} `json:"estimated"`
}

// best returns the real-time estimate when OTP has one, like itinerary start/end do.
func (t legTime) best() string {
	if t.Estimated != nil && t.Estimated.Time != "" {
		return t.Estimated.Time
	}
	return t.ScheduledTime
}

type otpPlace struct {
	Name string  `json:"name"`
	Lat  float64 `json:"lat"`
	Lon  float64 `json:"lon"`
	Stop *struct {
		GtfsID string `json:"gtfsId"`
	} `json:"stop"`
}

func (p otpPlace) place() mobility.Place {
	out := mobility.Place{Name: p.Name, Lat: p.Lat, Lon: p.Lon}
	if p.Stop != nil {
		out.StopID = p.Stop.GtfsID
	}
	return out
}

type leg struct {
	Mode       string   `json:"mode"`
	TransitLeg bool     `json:"transitLeg"`
	Distance   float64  `json:"distance"`
	Duration   float64  `json:"duration"`
	Headsign   *string  `json:"headsign"`
	Start      legTime  `json:"start"`
	End        legTime  `json:"end"`
	From       otpPlace `json:"from"`
	To         otpPlace `json:"to"`
	Route      *struct {
		GtfsID    string  `json:"gtfsId"`
		ShortName *string `json:"shortName"`
		LongName  *string `json:"longName"`
	} `json:"route"`
	LegGeometry *struct {
		Points string `json:"points"`
	} `json:"legGeometry"`
}
