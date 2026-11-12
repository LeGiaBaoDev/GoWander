// Command api is the GoWander HTTP API (M04: route planning over OpenTripPlanner).
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/mobility/otp"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/config"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

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
	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           newHandler(cfg, log, otp.New(cfg.OTPURL, cfg.OTPTimeout)),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      cfg.OTPTimeout + 5*time.Second,
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
func newHandler(cfg config.Config, log *slog.Logger, provider mobility.RoutingProvider) http.Handler {
	mux := httpx.NewRouter(log)
	mux.Get("/health/live", func(w http.ResponseWriter, _ *http.Request) {
		httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.Get("/health/ready", func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), time.Second)
		defer cancel()
		if err := provider.Ready(ctx); err != nil {
			log.Warn("not ready: routing provider", "requestId", httpx.RequestID(r), "err", err)
			httpx.WriteJSON(w, http.StatusServiceUnavailable, map[string]string{"status": "unavailable"})
			return
		}
		httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.Post("/api/v1/routes/plan", mobility.Handler{Provider: provider, DataStatus: cfg.DataStatus, Log: log}.Plan)
	return mux
}
