package engine

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"time"
)

type endpointLookup func(context.Context, string) ([]net.IPAddr, error)

func prepareTunnelEndpoint(p Profile, s Settings, lookup endpointLookup) (Profile, error) {
	// Detach maps: the encrypted profile and its identity must not be rewritten.
	raw, err := json.Marshal(p.Outbound)
	if err != nil {
		return Profile{}, fail()
	}
	var outbound map[string]any
	if json.Unmarshal(raw, &outbound) != nil {
		return Profile{}, fail()
	}
	settings, _ := outbound["settings"].(map[string]any)
	for _, key := range []string{"vnext", "servers"} {
		entries, _ := settings[key].([]any)
		for _, entry := range entries {
			endpoint, _ := entry.(map[string]any)
			host := str(endpoint, "address")
			if !validHost(host) {
				return Profile{}, fail()
			}
			if ip := net.ParseIP(host); ip != nil {
				if s.IPv6 == "block" && ip.To4() == nil {
					return Profile{}, errors.New("tunnel_endpoint_ipv6_blocked")
				}
				continue
			}
			ctx, cancel := context.WithTimeout(context.Background(), 7*time.Second)
			addresses, e := lookup(ctx, host)
			cancel()
			if e != nil || len(addresses) == 0 {
				return Profile{}, errors.New("endpoint_dns_failed")
			}
			// Prefer IPv4 where available, but do not silently downgrade an IPv6-only server.
			var selected net.IP
			for _, a := range addresses {
				if a.IP.To4() != nil {
					selected = a.IP
					break
				}
			}
			if selected == nil && s.IPv6 != "block" {
				for _, a := range addresses {
					if a.IP != nil {
						selected = a.IP
						break
					}
				}
			}
			if selected == nil {
				return Profile{}, errors.New("tunnel_endpoint_ipv6_blocked")
			}
			endpoint["address"] = selected.String()
			stream, _ := outbound["streamSettings"].(map[string]any)
			if str(stream, "security") == "tls" {
				tls, _ := stream["tlsSettings"].(map[string]any)
				if tls == nil {
					tls = map[string]any{}
					stream["tlsSettings"] = tls
				}
				if str(tls, "serverName") == "" {
					tls["serverName"] = host
				}
			}
			// Preserve domain-dependent Host/:authority semantics after temporary pinning.
			if ws, ok := stream["wsSettings"].(map[string]any); ok {
				headers, _ := ws["headers"].(map[string]any)
				if headers == nil {
					headers = map[string]any{}
					ws["headers"] = headers
				}
				if str(headers, "Host") == "" {
					headers["Host"] = host
				}
			}
			if http, ok := stream["xhttpSettings"].(map[string]any); ok && str(http, "host") == "" {
				http["host"] = host
			}
		}
	}
	p.Outbound = outbound
	return p, nil
}
