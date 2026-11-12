// Package discover serves published places from PostgreSQL/PostGIS (M05, ADR-028).
package discover

import (
	"context"
	"errors"
)

type CategoryRef struct {
	Slug   string `json:"slug"`
	Name   string `json:"name"`
	NameEn string `json:"nameEn,omitempty"`
}

type Category struct {
	CategoryRef
	PlaceCount int `json:"placeCount"`
}

// Source must be shown to users next to the place (licence and attribution, ADR-028 §4).
type Source struct {
	Kind        string `json:"kind"`
	URL         string `json:"url,omitempty"`
	License     string `json:"license"`
	Attribution string `json:"attribution"`
}

type PlaceSummary struct {
	ID               string       `json:"id"`
	Name             string       `json:"name"`
	NameEn           string       `json:"nameEn,omitempty"`
	Slug             string       `json:"slug"`
	ShortDescription string       `json:"shortDescription,omitempty"`
	Lat              float64      `json:"lat"`
	Lon              float64      `json:"lon"`
	Address          string       `json:"address,omitempty"`
	Category         *CategoryRef `json:"category,omitempty"`
	DistanceM        *float64     `json:"distanceM,omitempty"`
	Source           Source       `json:"source"`
}

type Media struct {
	StorageKey   string `json:"storageKey"`
	MediaType    string `json:"mediaType"`
	AltText      string `json:"altText,omitempty"`
	SourceCredit string `json:"sourceCredit"`
	License      string `json:"license"`
	IsCover      bool   `json:"isCover"`
}

type Food struct {
	Name     string   `json:"name"`
	NameEn   string   `json:"nameEn,omitempty"`
	Slug     string   `json:"slug"`
	PriceMin *float64 `json:"priceMin,omitempty"`
	PriceMax *float64 `json:"priceMax,omitempty"`
	Currency string   `json:"currency"`
}

type PlaceDetail struct {
	PlaceSummary
	Categories []CategoryRef `json:"categories"`
	Media      []Media       `json:"media"`
	Foods      []Food        `json:"foods"`
}

// Cursor is the keyset position after the last place of a page (places are ordered by name, then id).
type Cursor struct {
	Name string `json:"n"`
	ID   string `json:"i"`
}

type ListQuery struct {
	Category string // slug; empty = all
	Q        string // name search, VI or EN, accent-insensitive; empty = all
	Limit    int
	After    *Cursor
}

type NearbyQuery struct {
	Lat, Lon float64
	RadiusM  int
	Category string
	Limit    int
}

var ErrNotFound = errors.New("place not found")

// Store reads published Discover data. Implementations return ErrNotFound for unknown or unpublished places.
type Store interface {
	Categories(ctx context.Context) ([]Category, error)
	ListPlaces(ctx context.Context, q ListQuery) (places []PlaceSummary, next *Cursor, err error)
	Nearby(ctx context.Context, q NearbyQuery) ([]PlaceSummary, error)
	Place(ctx context.Context, id string) (PlaceDetail, error)
	Ready(ctx context.Context) error
}
