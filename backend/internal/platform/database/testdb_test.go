package database

import "testing"

func TestTestDatabaseURL(t *testing.T) {
	for in, want := range map[string]string{
		"postgres://u:p@h:5432/gowander?sslmode=disable":      "postgres://u:p@h:5432/gowander_test?sslmode=disable",
		"postgres://u:p@h:5432/gowander":                      "postgres://u:p@h:5432/gowander_test",
		"postgres://u:p@h:5432/gowander_test?sslmode=disable": "postgres://u:p@h:5432/gowander_test?sslmode=disable",
		"postgres://u:p@h/gowander?sslrootcert=/etc/ca.pem":   "postgres://u:p@h/gowander_test?sslrootcert=/etc/ca.pem",
	} {
		if got := TestDatabaseURL(in); got != want {
			t.Errorf("%s: got %s, want %s", in, got, want)
		}
	}
}
