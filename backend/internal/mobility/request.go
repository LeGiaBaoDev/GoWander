package mobility

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"
)

type coordinateIn struct {
	Lat *float64 `json:"lat"`
	Lon *float64 `json:"lon"`
}

type planRequest struct {
	Origin      *coordinateIn `json:"origin"`
	Destination *coordinateIn `json:"destination"`
	DateTime    string        `json:"dateTime"`
	TimeType    string        `json:"timeType"`
	Modes       []string      `json:"modes"`
	MaxResults  *int          `json:"maxResults"`
}

// ParseQuery decodes and validates a plan request body (schema PlanRequest in openapi.yaml).
// An empty dateTime means now. Every failure is an *Error with code INVALID_REQUEST.
func ParseQuery(body io.Reader, now time.Time) (Query, error) {
	var req planRequest
	dec := json.NewDecoder(body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil {
		if _, ok := errors.AsType[*http.MaxBytesError](err); ok {
			return Query{}, invalid("Nội dung yêu cầu quá lớn.")
		}
		return Query{}, &Error{Code: InvalidRequest, Message: "JSON không hợp lệ hoặc sai kiểu dữ liệu.", Err: err}
	}
	if err := dec.Decode(&struct{}{}); err != io.EOF {
		return Query{}, invalid("Chỉ được gửi một đối tượng JSON.")
	}

	q := Query{DateTime: now, Walk: true, Transit: true, MaxResults: 3}
	var err error
	if q.Origin, err = coordinate("origin", req.Origin); err != nil {
		return Query{}, err
	}
	if q.Destination, err = coordinate("destination", req.Destination); err != nil {
		return Query{}, err
	}
	if req.DateTime != "" {
		if q.DateTime, err = time.Parse(time.RFC3339, req.DateTime); err != nil {
			return Query{}, invalid("dateTime phải theo RFC 3339 và có múi giờ, ví dụ 2026-11-17T09:00:00+07:00.")
		}
	}
	switch req.TimeType {
	case "", "DEPART":
	case "ARRIVE":
		q.ArriveBy = true
	default:
		return Query{}, invalid("timeType chỉ nhận DEPART hoặc ARRIVE.")
	}
	if req.Modes != nil {
		q.Walk, q.Transit = false, false
		if len(req.Modes) == 0 {
			return Query{}, invalid("modes cần ít nhất một giá trị.")
		}
		for _, m := range req.Modes {
			seen := map[string]*bool{"WALK": &q.Walk, "TRANSIT": &q.Transit}[m]
			if seen == nil || *seen {
				return Query{}, invalid("modes chỉ nhận WALK, TRANSIT, mỗi giá trị một lần.")
			}
			*seen = true
		}
	}
	if req.MaxResults != nil {
		if *req.MaxResults < 1 || *req.MaxResults > 5 {
			return Query{}, invalid("maxResults phải từ 1 đến 5.")
		}
		q.MaxResults = *req.MaxResults
	}
	return q, nil
}

func coordinate(field string, c *coordinateIn) (Coordinate, error) {
	switch {
	case c == nil || c.Lat == nil || c.Lon == nil:
		return Coordinate{}, invalid(fmt.Sprintf("Thiếu %s.lat hoặc %s.lon.", field, field))
	case *c.Lat < -90 || *c.Lat > 90:
		return Coordinate{}, invalid(field + ".lat phải trong khoảng -90 đến 90.")
	case *c.Lon < -180 || *c.Lon > 180:
		return Coordinate{}, invalid(field + ".lon phải trong khoảng -180 đến 180.")
	}
	return Coordinate{Lat: *c.Lat, Lon: *c.Lon}, nil
}
