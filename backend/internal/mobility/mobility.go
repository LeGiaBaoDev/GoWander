// Package mobility is the route planning module: domain types, the RoutingProvider port, input validation and the HTTP handler.
package mobility

import (
	"context"
	"errors"
	"net/http"
	"time"
)

type Coordinate struct {
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`
}

// Query is a validated route planning request.
type Query struct {
	Origin, Destination Coordinate
	DateTime            time.Time
	ArriveBy            bool // true: arrive before DateTime; false: depart after DateTime
	Walk, Transit       bool // allowed modes; at least one is true
	MaxResults          int
}

type Place struct {
	Name   string  `json:"name"`
	Lat    float64 `json:"lat"`
	Lon    float64 `json:"lon"`
	StopID string  `json:"stopId,omitempty"`
}

type Line struct {
	ID        string `json:"id"`
	ShortName string `json:"shortName,omitempty"`
	LongName  string `json:"longName,omitempty"`
}

type Leg struct {
	Mode        string    `json:"mode"`
	Transit     bool      `json:"transit"`
	From        Place     `json:"from"`
	To          Place     `json:"to"`
	StartTime   time.Time `json:"startTime"`
	EndTime     time.Time `json:"endTime"`
	DurationSec int       `json:"durationSec"`
	DistanceM   float64   `json:"distanceM"`
	Realtime    bool      `json:"realtime"`
	Headsign    string    `json:"headsign,omitempty"`
	Line        *Line     `json:"line,omitempty"`
	Polyline    string    `json:"polyline"`
}

type Route struct {
	StartTime     time.Time `json:"startTime"`
	EndTime       time.Time `json:"endTime"`
	DurationSec   int       `json:"durationSec"`
	WalkDistanceM float64   `json:"walkDistanceM"`
	Transfers     int       `json:"transfers"`
	Legs          []Leg     `json:"legs"`
}

// RoutingProvider is implemented by routing engine adapters (OTP today). Errors should be *Error.
type RoutingProvider interface {
	PlanRoute(ctx context.Context, q Query) ([]Route, error)
	Ready(ctx context.Context) error
}

// Code is a stable error code exposed to clients (ADR-027).
type Code string

const (
	InvalidRequest     Code = "INVALID_REQUEST"
	NoRouteFound       Code = "NO_ROUTE_FOUND"
	OutOfCoverage      Code = "OUT_OF_COVERAGE"
	DataUnavailable    Code = "DATA_UNAVAILABLE"
	RoutingTimeout     Code = "ROUTING_TIMEOUT"
	RoutingUnavailable Code = "ROUTING_UNAVAILABLE"
)

var codeInfo = map[Code]struct {
	status  int
	message string
}{
	InvalidRequest:     {http.StatusBadRequest, "Yêu cầu không hợp lệ."},
	NoRouteFound:       {http.StatusUnprocessableEntity, "Không tìm thấy tuyến phù hợp cho thời gian này."},
	OutOfCoverage:      {http.StatusUnprocessableEntity, "Điểm đi hoặc điểm đến nằm ngoài khu vực GoWander hỗ trợ."},
	DataUnavailable:    {http.StatusUnprocessableEntity, "Chưa có dữ liệu lịch chạy cho ngày này."},
	RoutingTimeout:     {http.StatusGatewayTimeout, "Tìm tuyến quá lâu, vui lòng thử lại."},
	RoutingUnavailable: {http.StatusServiceUnavailable, "Dịch vụ tìm tuyến tạm thời không hoạt động, vui lòng thử lại sau."},
}

// Error is a routing error with a client-safe Message; Err holds the internal cause for logs only.
type Error struct {
	Code    Code
	Message string
	Err     error
}

func (e *Error) Error() string {
	if e.Err != nil {
		return string(e.Code) + ": " + e.Message + ": " + e.Err.Error()
	}
	return string(e.Code) + ": " + e.Message
}

func (e *Error) Unwrap() error { return e.Err }

// Fail builds an Error with the default client message for code.
func Fail(code Code, cause error) *Error {
	return &Error{Code: code, Message: codeInfo[code].message, Err: cause}
}

func invalid(message string) *Error { return &Error{Code: InvalidRequest, Message: message} }

// ErrUnrecognized marks a provider answer the adapter could not classify; the handler logs it at Warn so new upstream codes are noticed.
var ErrUnrecognized = errors.New("unrecognized provider answer")
