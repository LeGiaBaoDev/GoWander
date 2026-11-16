// Command api is the GoWander HTTP API (M04: route planning over OpenTripPlanner).
package main

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/account"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/admin"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/analytics"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/discover"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/feedback"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/geocode"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/media"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility/otp"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/config"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/mail"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/streetview"
	"github.com/jackc/pgx/v5/pgxpool"
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
	var adminFeedback feedback.Store
	if cfg.AdminEnabled() {
		adminPool, err := database.NewPool(context.Background(), cfg.AdminDatabaseURL)
		if err != nil {
			return err
		}
		defer adminPool.Close()
		adminStore = &admin.PG{Pool: adminPool}
		adminFeedback = &feedback.PG{Pool: adminPool} // reads and handles feedbacks; the public pool can only add them
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	registerPoolGauges(pool)          // M09: pool numbers for /metrics
	usage := analytics.New(pool, log) // ADR-038: daily counters, written in the background
	go usage.Run(ctx)

	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           newHandler(cfg, log, otp.New(cfg.OTPURL, cfg.OTPTimeout), &discover.PG{Pool: pool}, adminStore, usage, &feedback.PG{Pool: pool}, adminFeedback, &account.PG{Pool: pool}),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      max(cfg.OTPTimeout, streetImageTimeout) + 5*time.Second, // the slowest upstream wait + margin (review 15-11-2026)
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 << 10,
	}

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
func newHandler(cfg config.Config, log *slog.Logger, provider mobility.RoutingProvider, places discover.Store, adminStore admin.Store, usage *analytics.Counter,
	feedbacks feedback.Creator, adminFeedback feedback.Store, accounts account.Store) http.Handler {
	mux := httpx.NewRouter(log)
	httpx.Default.RegisterGauge("gowander_ready", "1 when routing and the database answer within one second", func() float64 {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		if provider.Ready(ctx) != nil || places.Ready(ctx) != nil {
			return 0
		}
		return 1
	})
	// App accounts (ADR-040): a bearer token is read once, for every route that wants it; routes that need an account
	// ask for it themselves, so the map, Discover and route planning keep working for guests.
	mux.Use(account.Optional(accounts))
	// M09: numbers Prometheus can scrape. Loopback only for now (the API binds 127.0.0.1); a public deployment adds a
	// token or a private listener before exposing it.
	mux.Get("/metrics", func(w http.ResponseWriter, r *http.Request) {
		if !isLoopback(r) {
			httpx.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Không có đường dẫn này.")
			return
		}
		httpx.Default.ServeHTTP(w, r)
	})
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
	plan := mobility.Handler{Provider: provider, DataStatus: cfg.DataStatus, Log: log}
	if usage != nil { // tests run without counters
		plan.Usage = usage
		usage.Mount(mux)
	}
	mux.Post("/api/v1/routes/plan", plan.Plan)
	discover.Handler{Store: places, Log: log, MediaURL: cfg.MediaBaseURL}.Mount(mux)
	// Photos (ADR-035): the R2 bucket when configured (read from its public address), else this machine's folder
	// served at /media/ (development only; config refuses it behind https).
	var photos media.Storage = media.Local{Dir: cfg.MediaDir}
	if cfg.R2Enabled() {
		photos = media.R2{AccountID: cfg.R2AccountID, Bucket: cfg.R2Bucket, AccessKeyID: cfg.R2AccessKeyID, SecretAccessKey: cfg.R2SecretAccessKey}
		log.Info("photos stored in R2", "bucket", cfg.R2Bucket, "publicURL", cfg.MediaBaseURL)
	} else {
		mux.Handle("/media/*", media.Local{Dir: cfg.MediaDir})
	}
	// Feedbacks from the app (owner, 16-11-2026): guests may send, only the Admin reads (migration 0013). A signed-in
	// account with a verified email provides the sender name and email (ADR-040); the form's fields are then ignored.
	feedback.Handler{Store: feedbacks, Log: log, Viewer: func(r *http.Request) (feedback.Identity, bool) {
		u, ok := account.FromContext(r.Context())
		return feedback.Identity{ID: u.ID, Name: u.Name, Email: u.Email, EmailVerified: u.EmailVerified}, ok
	}}.Mount(mux)
	adminHandler := admin.Handler{Store: adminStore, Feedback: adminFeedback, BaseURL: cfg.AdminBaseURL, Media: photos, MediaURL: cfg.MediaBaseURL, SecureCookies: strings.HasPrefix(cfg.AdminBaseURL, "https://"), Log: log,
		DataStatus: cfg.DataStatus, Health: []admin.Health{{Name: "Tìm đường", Ready: provider.Ready}, {Name: "Cơ sở dữ liệu", Ready: places.Ready}}}
	if cfg.MailEnabled() {
		adminHandler.Mailer = admin.SMTP{Host: cfg.SMTPHost, Port: cfg.SMTPPort, Username: cfg.SMTPUsername, Password: cfg.SMTPPassword, From: cfg.SMTPFrom, FromName: "GoWander Admin"}
	}
	adminHandler.Mount(mux)
	if accounts != nil {
		signIn := account.Handler{Store: accounts, Auth: account.NewGoogleVerifier(cfg.GoogleClientID, cfg.GoogleAudiences...), Log: log}
		if cfg.MailEnabled() {
			signIn.Mailer = mail.SMTP{Host: cfg.SMTPHost, Port: cfg.SMTPPort, Username: cfg.SMTPUsername, Password: cfg.SMTPPassword, From: cfg.SMTPFrom}
		}
		signIn.Mount(mux)
	}
	geocode.Handler{Geocoder: geocode.New(cfg.PhotonURL, 3*time.Second), Log: log}.Mount(mux)
	streetview.Handler{
		Finder: streetview.NewCached(streetview.New(cfg.MapillaryURL, cfg.MapillaryToken, streetImageTimeout), 24*time.Hour, 5000),
		Log:    log,
		Limit:  streetview.NewLimiter(30, time.Minute), // a route preview needs ~15; 30/min per IP protects the Mapillary quota
	}.Mount(mux)
	return mux
}

// isLoopback keeps /metrics on the machine that runs the API: no token to leak, nothing exposed by accident.
func isLoopback(r *http.Request) bool {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	return host == "127.0.0.1" || host == "::1"
}

// registerPoolGauges exposes how the database pool is doing; a pool that is always full is the first sign of trouble.
func registerPoolGauges(pool *pgxpool.Pool) {
	stat := func(name, help string, pick func(*pgxpool.Stat) int32) {
		httpx.Default.RegisterGauge(name, help, func() float64 { return float64(pick(pool.Stat())) })
	}
	stat("gowander_db_pool_acquired", "Connections handed out right now", func(s *pgxpool.Stat) int32 { return s.AcquiredConns() })
	stat("gowander_db_pool_idle", "Connections waiting in the pool", func(s *pgxpool.Stat) int32 { return s.IdleConns() })
	stat("gowander_db_pool_total", "Connections open in the pool", func(s *pgxpool.Stat) int32 { return s.TotalConns() })
	stat("gowander_db_pool_max", "Connections the pool may open", func(s *pgxpool.Stat) int32 { return s.MaxConns() })
}
