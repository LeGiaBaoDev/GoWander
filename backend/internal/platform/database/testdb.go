package database

import "net/url"

// TestDatabaseURL points a postgres:// URL at <db>_test. Integration tests use it so they can never touch dev data.
// An unparsable URL is returned unchanged; connecting with it then fails loudly.
func TestDatabaseURL(raw string) string {
	u, err := url.Parse(raw)
	if err != nil || len(u.Path) < 2 {
		return raw
	}
	if name := u.Path[1:]; len(name) < 5 || name[len(name)-5:] != "_test" {
		u.Path += "_test"
	}
	return u.String()
}
