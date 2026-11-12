// Package database opens the PostgreSQL pool and runs migrations (ADR-028).
package database

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/pgx/v5" // registers the pgx5:// driver
	"github.com/golang-migrate/migrate/v4/source/iofs"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/LeGiaBaoDev/GoWander/backend/migrations"
)

// NewPool connects with the runtime role and checks the connection once.
func NewPool(ctx context.Context, url string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, errors.New("DATABASE_URL is not a valid PostgreSQL URL") // the URL holds a password: never echo it
	}
	cfg.MaxConns = 10
	cfg.MaxConnIdleTime = 5 * time.Minute
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("open database pool: %w", err)
	}
	pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("connect to database: %w", err)
	}
	return pool, nil
}

// NewMigrator returns a migrator over the embedded migrations. url is a postgres:// URL for the owner role.
func NewMigrator(url string) (*migrate.Migrate, error) {
	src, err := iofs.New(migrations.FS, ".")
	if err != nil {
		return nil, err
	}
	if !strings.HasPrefix(url, "postgres://") && !strings.HasPrefix(url, "postgresql://") {
		return nil, errors.New("migration URL must start with postgres://")
	}
	m, err := migrate.NewWithSourceInstance("iofs", src, "pgx5://"+url[strings.Index(url, "://")+3:])
	if err != nil {
		return nil, fmt.Errorf("open migration database: %w", err)
	}
	return m, nil
}
