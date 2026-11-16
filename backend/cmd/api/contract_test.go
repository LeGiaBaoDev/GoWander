package main

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/getkin/kin-openapi/openapi3filter"
	"github.com/getkin/kin-openapi/routers"
	"github.com/getkin/kin-openapi/routers/legacy"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/discover"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/config"
)

func contractRouter(t *testing.T) routers.Router {
	t.Helper()
	// External references stay disabled: CI must not fetch arbitrary URLs from a spec.
	doc, err := openapi3.NewLoader().LoadFromFile("../../api/openapi/openapi.yaml")
	if err != nil {
		t.Fatal(err)
	}
	router, err := legacy.NewRouter(doc) // validates the entire document, including references
	if err != nil {
		t.Fatal(err)
	}
	return router
}

func validateContractResponse(router routers.Router, req *http.Request, rec *httptest.ResponseRecorder) error {
	route, params, err := router.FindRoute(req)
	if err != nil {
		return err
	}
	input := &openapi3filter.ResponseValidationInput{
		RequestValidationInput: &openapi3filter.RequestValidationInput{Request: req, PathParams: params, Route: route},
		Status:                 rec.Code,
		Header:                 rec.Header(),
		Options:                &openapi3filter.Options{IncludeResponseStatus: true},
	}
	input.SetBodyBytes(rec.Body.Bytes())
	return openapi3filter.ValidateResponse(req.Context(), input)
}

func TestOpenAPIDocument(t *testing.T) { contractRouter(t) }

// Nonempty fixtures exercise nested item schemas as well as the outer envelopes.
type contractStore struct{ stubStore }

func (contractStore) Categories(context.Context) ([]discover.Category, error) {
	return []discover.Category{{CategoryRef: discover.CategoryRef{Slug: "museum", Name: "Museum"}, PlaceCount: 1}}, nil
}

func (contractStore) ListPlaces(context.Context, discover.ListQuery) ([]discover.PlaceSummary, *discover.Cursor, error) {
	return []discover.PlaceSummary{{ID: "0190f0c2-0000-7000-8000-000000000001", Name: "Fixture museum", Slug: "fixture-museum",
			Lat: 10.77, Lon: 106.69, Source: discover.Source{Kind: "CURATED", License: "CC0", Attribution: "Test fixture"}}},
		&discover.Cursor{Name: "Fixture museum", ID: "0190f0c2-0000-7000-8000-000000000001"}, nil
}

// This is representative response coverage, not an end-to-end or all-endpoints gate.
func TestOpenAPIResponses(t *testing.T) {
	router := contractRouter(t)
	for _, tc := range []struct {
		name, method, path, body string
		status                   int
		provider                 stubProvider
		store                    discover.Store
	}{
		{name: "live", method: "GET", path: "/health/live", status: 200},
		{name: "ready", method: "GET", path: "/health/ready", status: 200},
		{name: "routing unavailable", method: "GET", path: "/health/ready", status: 503, provider: stubProvider{readyErr: errors.New("offline")}},
		{name: "database unavailable", method: "GET", path: "/health/ready", status: 503, store: stubStore{readyErr: errors.New("offline")}},
		{name: "categories", method: "GET", path: "/api/v1/place-categories", status: 200, store: contractStore{}},
		{name: "places", method: "GET", path: "/api/v1/places", status: 200, store: contractStore{}},
		{name: "empty places", method: "GET", path: "/api/v1/places", status: 200},
		{name: "place not found", method: "GET", path: "/api/v1/places/0190f0c2-0000-7000-8000-000000000001", status: 404},
		{name: "invalid routing input", method: "POST", path: "/api/v1/routes/plan", body: `{}`, status: 400},
		{name: "routing panic", method: "POST", path: "/api/v1/routes/plan", body: `{"origin":{"lat":10.77,"lon":106.69},"destination":{"lat":10.87,"lon":106.81}}`, status: 500},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if tc.store == nil {
				tc.store = stubStore{}
			}
			h := newHandler(config.Config{DataStatus: "ASSUMED"}, slog.New(slog.NewTextHandler(io.Discard, nil)), tc.provider, tc.store, nil, nil, nil, nil, nil)
			req := httptest.NewRequest(tc.method, tc.path, strings.NewReader(tc.body))
			req.Header.Set("Content-Type", "application/json")
			rec := httptest.NewRecorder()
			h.ServeHTTP(rec, req)
			if rec.Code != tc.status {
				t.Fatalf("status = %d, want %d", rec.Code, tc.status)
			}
			if err := validateContractResponse(router, req, rec); err != nil {
				t.Fatal(err)
			}
		})
	}
}

// Prove that the gate rejects drift, rather than merely parsing the YAML.
func TestOpenAPIRejectsResponseDrift(t *testing.T) {
	router := contractRouter(t)
	for _, tc := range []struct {
		name, path, body string
		status           int
	}{
		{"missing required status", "/health/live", `{}`, 200},
		{"wrong status type", "/health/live", `{"status":123}`, 200},
		{"undocumented status", "/health/live", `{"status":"ok"}`, 201},
		{"invalid nested category", "/api/v1/place-categories", `{"categories":[{"name":"Museum","slug":"museum","placeCount":"one"}]}`, 200},
		{"null instead of array", "/api/v1/places", `{"places":null}`, 200},
	} {
		t.Run(tc.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			rec.Header().Set("Content-Type", "application/json")
			rec.WriteHeader(tc.status)
			_, _ = rec.WriteString(tc.body)
			if err := validateContractResponse(router, httptest.NewRequest("GET", tc.path, nil), rec); err == nil {
				t.Fatal("invalid response accepted")
			}
		})
	}
}
