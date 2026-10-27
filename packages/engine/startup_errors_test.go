package engine

import (
	"errors"
	"strings"
	"testing"
)

func TestStartupErrorCodesAreSpecificAndCredentialFree(t *testing.T) {
	cases := []struct{ input, mode, want string }{
		{"bind: address already in use", "proxy", "ports_busy"}, {"Access is denied", "tunnel", "admin"}, {"wintun.dll: The system cannot find the file specified", "tunnel", "driver_missing"}, {"unable to set routes", "tunnel", "tunnel_routes_failed"}, {"unable to set DNS", "tunnel", "tunnel_dns_failed"}, {"unable to block dns outside the TUN", "tunnel", "tunnel_filter_failed"}, {"adapter creation failed", "tunnel", "tunnel_adapter_failed"}, {"unknown credential=fixture-secret@example.invalid", "tunnel", "tunnel_start_failed"}, {"unknown credential=fixture-secret@example.invalid", "proxy", "proxy_start_failed"},
	}
	for _, c := range cases {
		got := startupCode(errors.New(c.input), c.mode)
		if got != c.want || strings.Contains(got, "fixture-secret") {
			t.Fatalf("want %s got %s", c.want, got)
		}
	}
}
