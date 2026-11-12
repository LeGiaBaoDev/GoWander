package mobility

import (
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/httpx"
)

const maxBodyBytes = 64 << 10

// Handler serves POST /api/v1/routes/plan.
type Handler struct {
	Provider   RoutingProvider
	DataStatus string // ASSUMED or OFFICIAL (ADR-027)
	Log        *slog.Logger
}

type planResponse struct {
	DataStatus string  `json:"dataStatus"`
	Routes     []Route `json:"routes"`
}

func (h Handler) Plan(w http.ResponseWriter, r *http.Request) {
	q, err := ParseQuery(http.MaxBytesReader(w, r.Body, maxBodyBytes), time.Now())
	if err == nil {
		var routes []Route
		routes, err = h.Provider.PlanRoute(r.Context(), q)
		if err == nil && len(routes) == 0 {
			err = Fail(NoRouteFound, errors.New("provider returned no routes and no error"))
		}
		if err == nil {
			httpx.WriteJSON(w, http.StatusOK, planResponse{DataStatus: h.DataStatus, Routes: routes})
			return
		}
	}
	if r.Context().Err() != nil {
		h.Log.Info("route plan cancelled by client", "requestId", httpx.RequestID(r), "err", err)
		return
	}
	e, ok := errors.AsType[*Error](err)
	if !ok || codeInfo[e.Code].status == 0 {
		e = Fail(RoutingUnavailable, err)
	}
	level := slog.LevelWarn
	if (e.Code == InvalidRequest || codeInfo[e.Code].status == http.StatusUnprocessableEntity) && !errors.Is(err, ErrUnrecognized) {
		level = slog.LevelInfo // expected outcomes of user input
	}
	h.Log.Log(r.Context(), level, "route plan failed", "requestId", httpx.RequestID(r), "code", e.Code, "err", err)
	httpx.WriteError(w, r, codeInfo[e.Code].status, string(e.Code), e.Message)
}
