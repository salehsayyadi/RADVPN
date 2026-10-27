package engine

import (
	"context"
	"errors"
	"io"
	"net/http"
	"time"
)

func connectionProbeTargets() []string {
	// Local tests override the primary URL; never let them contact public services.
	if probeURL != defaultProbeURL {
		return []string{probeURL}
	}
	return []string{defaultProbeURL, "https://www.gstatic.com/generate_204"}
}

// Every attempt uses the same SOCKS dialer, keeps TLS verification enabled and
// requires a complete 204 response. A blocked provider is not the only oracle.
func verifyHTTPS(ctx context.Context, client *http.Client, targets []string) (int64, error) {
	for _, target := range targets {
		if ctx.Err() != nil {
			break
		}
		request, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
		if err != nil {
			continue
		}
		begin := time.Now()
		response, err := client.Do(request)
		if response == nil {
			continue
		}
		body, readErr := io.ReadAll(io.LimitReader(response.Body, 4097))
		_ = response.Body.Close()
		if err == nil && readErr == nil && response.StatusCode == http.StatusNoContent && len(body) == 0 {
			return time.Since(begin).Milliseconds(), nil
		}
	}
	return 0, errors.New("probe_failed")
}
