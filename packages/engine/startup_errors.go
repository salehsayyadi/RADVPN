package engine

import (
	"errors"
	"net"
	"strings"
	"syscall"
)

func startupCode(err error, mode string) string {
	if err == nil {
		return "core_start_failed"
	}
	var op *net.OpError
	if errors.As(err, &op) && (op.Op == "listen" || op.Op == "bind") {
		return "ports_busy"
	}
	text := strings.ToLower(err.Error())
	if errors.Is(err, syscall.EACCES) || errors.Is(err, syscall.EPERM) || strings.Contains(text, "access is denied") || strings.Contains(text, "requires elevation") {
		return "admin"
	}
	if strings.Contains(text, "address already in use") || strings.Contains(text, "only one usage of each socket") {
		return "ports_busy"
	}
	if mode == "tunnel" {
		if strings.Contains(text, "wintun.dll") && (strings.Contains(text, "not found") || strings.Contains(text, "cannot find")) {
			return "driver_missing"
		}
		if strings.Contains(text, "unable to block") || strings.Contains(text, "wfp") {
			return "tunnel_filter_failed"
		}
		if strings.Contains(text, "unable to set routes") {
			return "tunnel_routes_failed"
		}
		if strings.Contains(text, "unable to set dns") {
			return "tunnel_dns_failed"
		}
		if strings.Contains(text, "adapter") || strings.Contains(text, "wintun") || strings.Contains(text, "interface") {
			return "tunnel_adapter_failed"
		}
		return "tunnel_start_failed"
	}
	return "proxy_start_failed"
}
