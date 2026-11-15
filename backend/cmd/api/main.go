// Command api is the GoWander HTTP API (M04: route planning over OpenTripPlanner).
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/admin"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/discover"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/geocode"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility/otp"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/config"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/streetview"
)

// Mapillary answered a small bbox query in 3 to 9 s (measured 15-11-2026); 4 s failed almost every time. Answers are
// cached for a day, so only the first request for a spot is slow. Stays under the server WriteTimeout (OTP timeout + 5 s).
const streetImageTimeout = 12 * time.Second

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	if err := run(log); err != nil {
		log.Error("api stopped with error", "err", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger) error {
	cfg, err := config.Load(os.Getenv)
	if err != nil {
		return err
	}
	pool, err := database.NewPool(context.Background(), cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	// Admin pages (M07, ADR-037): their own pool with the role gowander_admin; the public API keeps the read-only pool.
	var adminStore admin.Store
	if cfg.AdminEnabled() {
		adminPool, err := database.NewPool(context.Background(), cfg.AdminDatabaseURL)
		if err != nil {
			return err
		}
		defer adminPool.Close()
		adminStore = &admin.PG{Pool: adminPool}
	}

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           newHandler(cfg, log, otp.New(cfg.OTPURL, cfg.OTPTimeout), &discover.PG{Pool: pool}, adminStore),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      max(cfg.OTPTimeout, streetImageTimeout) + 5*time.Second, // the slowest upstream wait + margin (review 15-11-2026)
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 << 10,
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	errc := make(chan error, 1)
	go func() { errc <- srv.ListenAndServe() }()
	log.Info("api listening", "addr", cfg.Addr, "otpUrl", cfg.OTPURL, "otpTimeout", cfg.OTPTimeout.String(), "dataStatus", cfg.DataStatus)

	select {
	case err := <-errc:
		return err // failed to start (port in use, ...)
	case <-ctx.Done():
	}
	stop() // a second Ctrl-C now kills the process instead of waiting for Shutdown
	log.Info("shutting down")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		_ = srv.Close()
		return err
	}
	if err := <-errc; !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	log.Info("stopped")
	return nil
}

// newHandler wires every route; integration tests use it with a real OTP.
func newHandler(cfg config.Config, log *slog.Logger, provider mobility.RoutingProvider, places discover.Store, adminStore admin.Store) http.Handler {
	mux := httpx.NewRouter(log)
	mux.Get("/health/live", func(w http.ResponseWriter, _ *http.Request) {
		httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.Get("/health/ready", func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), time.Second)
		defer cancel()
		ready := true
		for _, dep := range []struct {
			name  string
			check func(context.Context) error
		}{{"routing", provider.Ready}, {"database", places.Ready}} {
			if err := dep.check(ctx); err != nil {
				ready = false
				log.Warn("not ready", "dependency", dep.name, "requestId", httpx.RequestID(r), "err", err)
			}
		}
		if !ready {
			httpx.WriteJSON(w, http.StatusServiceUnavailable, map[string]string{"status": "unavailable"})
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.Post("/api/v1/routes/plan", mobility.Handler{Provider: provider, DataStatus: cfg.DataStatus, Log: log}.Plan)
	discover.Handler{Store: places, Log: log}.Mount(mux)
	adminHandler := admin.Handler{Store: adminStore, BootstrapEmail: cfg.AdminBootstrapEmail, SecureCookies: strings.HasPrefix(cfg.AdminBaseURL, "https://"), Log: log}
	if cfg.GoogleLogin() {
		adminHandler.Auth = admin.NewGoogle(cfg.GoogleClientID, cfg.GoogleClientSecret, cfg.AdminBaseURL+"/admin/auth/callback")
	}
	adminHandler.Mount(mux)
	geocode.Handler{Geocoder: geocode.New(cfg.PhotonURL, 3*time.Second), Log: log}.Mount(mux)
	streetview.Handler{
		Finder: streetview.NewCached(streetview.New(cfg.MapillaryURL, cfg.MapillaryToken, streetImageTimeout), 24*time.Hour, 5000),
		Log:    log,
		Limit:  streetview.NewLimiter(30, time.Minute), // a route preview needs ~15; 30/min per IP protects the Mapillary quota
	}.Mount(mux)
	return mux
}
