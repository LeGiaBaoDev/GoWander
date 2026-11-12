// Command migrate applies the embedded SQL migrations with the owner role (ADR-028).
//
//	go run ./cmd/migrate up        apply all pending migrations
//	go run ./cmd/migrate down      roll back ONE migration
//	go run ./cmd/migrate version   print the current version
//	go run ./cmd/migrate force N   mark version N as clean after fixing a failed migration by hand
//
// Reads MIGRATE_DATABASE_URL (postgres://gowander_owner:...).
package main

import (
	"errors"
	"fmt"
	"os"
	"strconv"

	"github.com/golang-migrate/migrate/v4"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "migrate:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	url := os.Getenv("MIGRATE_DATABASE_URL")
	if url == "" {
		return errors.New("MIGRATE_DATABASE_URL is not set")
	}
	if len(args) == 0 {
		return errors.New("usage: migrate up | down | version | force N")
	}
	m, err := database.NewMigrator(url)
	if err != nil {
		return err
	}
	defer m.Close()

	switch args[0] {
	case "up":
		err = m.Up()
	case "down":
		err = m.Steps(-1)
	case "version":
	case "force":
		if len(args) != 2 {
			return errors.New("usage: migrate force N")
		}
		v, convErr := strconv.Atoi(args[1])
		if convErr != nil {
			return fmt.Errorf("force: %q is not a version number", args[1])
		}
		err = m.Force(v)
	default:
		return fmt.Errorf("unknown command %q", args[0])
	}
	if err != nil && !errors.Is(err, migrate.ErrNoChange) {
		return err
	}
	v, dirty, verr := m.Version()
	switch {
	case errors.Is(verr, migrate.ErrNilVersion):
		fmt.Println("version: none (empty database)")
	case verr != nil:
		return verr
	default:
		fmt.Printf("version: %d dirty: %v\n", v, dirty)
	}
	return nil
}
