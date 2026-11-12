package mobility

import (
	"errors"
	"strings"
	"testing"
	"time"
)

var testNow = time.Date(2026, 11, 17, 9, 0, 0, 0, time.FixedZone("ICT", 7*3600))

func TestParseQueryDefaults(t *testing.T) {
	q, err := ParseQuery(strings.NewReader(`{"origin":{"lat":10.77,"lon":106.69},"destination":{"lat":10.87,"lon":106.81}}`), testNow)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := Query{
		Origin: Coordinate{10.77, 106.69}, Destination: Coordinate{10.87, 106.81},
		DateTime: testNow, ArriveBy: false, Walk: true, Transit: true, MaxResults: 3,
	}
	if q != want {
		t.Fatalf("got %+v, want %+v", q, want)
	}
}

func TestParseQueryAllFields(t *testing.T) {
	body := `{"origin":{"lat":-90,"lon":180},"destination":{"lat":90,"lon":-180},
		"dateTime":"2026-11-17T18:30:00+07:00","timeType":"ARRIVE","modes":["WALK"],"maxResults":5}`
	q, err := ParseQuery(strings.NewReader(body), testNow)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !q.ArriveBy || !q.Walk || q.Transit || q.MaxResults != 5 {
		t.Fatalf("flags wrong: %+v", q)
	}
	if want := time.Date(2026, 11, 17, 11, 30, 0, 0, time.UTC); !q.DateTime.Equal(want) {
		t.Fatalf("dateTime %v, want %v", q.DateTime, want)
	}
}

func TestParseQueryInvalid(t *testing.T) {
	const od = `"origin":{"lat":10.77,"lon":106.69},"destination":{"lat":10.87,"lon":106.81}`
	cases := map[string]string{
		"empty body":          ``,
		"not json":            `hello`,
		"unknown field":       `{` + od + `,"via":1}`,
		"two objects":         `{` + od + `}{}`,
		"missing origin":      `{"destination":{"lat":10.87,"lon":106.81}}`,
		"missing lon":         `{"origin":{"lat":10.77},"destination":{"lat":10.87,"lon":106.81}}`,
		"lat out of range":    `{"origin":{"lat":95,"lon":106.69},"destination":{"lat":10.87,"lon":106.81}}`,
		"lon out of range":    `{"origin":{"lat":10.77,"lon":106.69},"destination":{"lat":10.87,"lon":-181}}`,
		"lat as string":       `{"origin":{"lat":"10","lon":106.69},"destination":{"lat":10.87,"lon":106.81}}`,
		"dateTime no zone":    `{` + od + `,"dateTime":"2026-11-17T09:00:00"}`,
		"dateTime garbage":    `{` + od + `,"dateTime":"tomorrow"}`,
		"bad timeType":        `{` + od + `,"timeType":"SOON"}`,
		"empty modes":         `{` + od + `,"modes":[]}`,
		"unknown mode":        `{` + od + `,"modes":["BIKE"]}`,
		"duplicate mode":      `{` + od + `,"modes":["WALK","WALK"]}`,
		"maxResults zero":     `{` + od + `,"maxResults":0}`,
		"maxResults too big":  `{` + od + `,"maxResults":6}`,
		"maxResults fraction": `{` + od + `,"maxResults":1.5}`,
	}
	for name, body := range cases {
		t.Run(name, func(t *testing.T) {
			_, err := ParseQuery(strings.NewReader(body), testNow)
			var e *Error
			if !errors.As(err, &e) || e.Code != InvalidRequest || e.Message == "" {
				t.Fatalf("want INVALID_REQUEST with message, got %v", err)
			}
			if strings.Contains(e.Message, "Go struct") || strings.Contains(e.Message, "planRequest") {
				t.Fatalf("message leaks Go internals: %q", e.Message)
			}
		})
	}
}
