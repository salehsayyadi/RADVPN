package engine

import (
	"encoding/json"
	"errors"
	"net"
	"net/netip"
	"runtime"
	"strings"
)

type Rule struct {
	Domains []string `json:"domains"`
	IPs     []string `json:"ips"`
	Action  string   `json:"action"`
}
type Settings struct {
	IPv6  string   `json:"ipv6"`
	Mode  string   `json:"mode"`
	Port  int      `json:"port"`
	Bind  string   `json:"bind"`
	MTU   int      `json:"mtu"`
	DNS   []string `json:"dns"`
	Rules []Rule   `json:"rules"`
}

func Defaults() Settings {
	return Settings{IPv6: "tunnel", Mode: "tunnel", Port: 10808, Bind: "127.0.0.1", MTU: 1500, DNS: []string{"1.1.1.1", "2606:4700:4700::1111"}}
}
func (s Settings) Validate() error {
	if s.IPv6 != "" && s.IPv6 != "tunnel" && s.IPv6 != "block" {
		return errors.New("invalid_settings")
	}
	if s.IPv6 == "block" {
		has4 := false
		for _, d := range s.DNS {
			if ip := net.ParseIP(d); ip != nil && ip.To4() != nil {
				has4 = true
			}
		}
		if !has4 {
			return errors.New("invalid_dns")
		}
	}
	if s.Mode != "tunnel" && s.Mode != "proxy" {
		return errors.New("invalid_settings")
	}
	ip := net.ParseIP(s.Bind)
	if ip == nil || !ip.IsLoopback() {
		return errors.New("loopback_only")
	}
	if s.Port < 1024 || s.Port > 65534 || s.MTU < 1280 || s.MTU > 9000 || len(s.DNS) == 0 || len(s.DNS) > 4 || len(s.Rules) > 128 {
		return errors.New("invalid_settings")
	}
	for _, d := range s.DNS {
		if net.ParseIP(d) == nil {
			return errors.New("invalid_dns")
		}
	}
	for _, r := range s.Rules {
		if r.Action != "proxy" && r.Action != "direct" && r.Action != "block" {
			return errors.New("invalid_rules")
		}
		if len(r.Domains)+len(r.IPs) == 0 || len(r.Domains)+len(r.IPs) > 256 {
			return errors.New("invalid_rules")
		}
		for _, d := range r.Domains {
			d = strings.TrimPrefix(strings.TrimPrefix(d, "domain:"), "full:")
			if !validHost(d) || strings.Contains(d, ":") {
				return errors.New("invalid_rules")
			}
		}
		for _, ip := range r.IPs {
			if _, e := netip.ParsePrefix(ip); e != nil {
				if _, e = netip.ParseAddr(ip); e != nil {
					return errors.New("invalid_rules")
				}
			}
		}
	}
	return nil
}

// Build creates all local listeners and routes itself. Imported input cannot set env,
// file paths, executables, API listeners, log outputs or additional inbounds.
func Build(p Profile, s Settings, platform string) ([]byte, error) {
	if e := s.Validate(); e != nil {
		return nil, e
	}
	if !p.Enabled {
		return nil, errors.New("profile_disabled")
	}
	if e := validateOutbound(p.Outbound); e != nil {
		return nil, e
	}
	raw, _ := json.Marshal(p.Outbound)
	var outbound map[string]any
	_ = json.Unmarshal(raw, &outbound)
	outbound["tag"] = "proxy"
	inbounds := []any{map[string]any{"tag": "socks", "listen": s.Bind, "port": s.Port, "protocol": "socks", "settings": map[string]any{"auth": "noauth", "udp": true}}, map[string]any{"tag": "http", "listen": s.Bind, "port": s.Port + 1, "protocol": "http", "settings": map[string]any{}}}
	if s.Mode == "tunnel" {
		if platform == "android" && s.IPv6 == "block" {
			return nil, errors.New("unsupported_feature")
		}
		if platform != "windows" && platform != "android" {
			return nil, errors.New("unsupported_platform")
		}
		tun := map[string]any{"name": "RADVPN", "mtu": s.MTU}
		if platform == "windows" {
			tun["gateway"] = []string{"172.29.83.1/30", "fdfe:dcba:9876::1/126"}
			tun["dns"] = s.DNS
			tun["autoSystemRoutingTable"] = []string{"0.0.0.0/1", "128.0.0.0/1", "::/1", "8000::/1"}
			tun["autoOutboundsInterface"] = "auto"
			tun["autoSystemWfpBlockLeak"] = []string{"dns", "misconfigtun"}
			if s.IPv6 == "block" {
				tun["gateway"] = []string{"172.29.83.1/30"}
				tun["autoSystemRoutingTable"] = []string{"0.0.0.0/1", "128.0.0.0/1"}
				resolvers := []string{}
				for _, d := range s.DNS {
					if net.ParseIP(d).To4() != nil {
						resolvers = append(resolvers, d)
					}
				}
				tun["dns"] = resolvers
			}
		}
		inbounds = append(inbounds, map[string]any{"tag": "tun", "protocol": "tun", "port": 0, "settings": tun})
	}
	rules := []any{map[string]any{"type": "field", "domain": []string{"full:cp.cloudflare.com"}, "outboundTag": "proxy"}}
	for _, r := range s.Rules {
		rule := map[string]any{"type": "field", "outboundTag": r.Action}
		if len(r.Domains) > 0 {
			rule["domain"] = r.Domains
		}
		if len(r.IPs) > 0 {
			rule["ip"] = r.IPs
		}
		rules = append(rules, rule)
	}
	config := map[string]any{
		"log":      map[string]any{"loglevel": "none", "access": "none", "error": "none"},
		"inbounds": inbounds, "outbounds": []any{outbound, map[string]any{"tag": "direct", "protocol": "freedom", "settings": map[string]any{}}, map[string]any{"tag": "block", "protocol": "blackhole", "settings": map[string]any{}}},
		"dns": map[string]any{"servers": s.DNS}, "routing": map[string]any{"domainStrategy": "IPIfNonMatch", "rules": rules}, "stats": map[string]any{}, "policy": map[string]any{"system": map[string]any{"statsOutboundUplink": true, "statsOutboundDownlink": true}},
	}
	return json.Marshal(config)
}
func validateOutbound(o map[string]any) error {
	if o == nil || placeholderOutbound(o) {
		return fail()
	}
	for k := range o {
		switch k {
		case "protocol", "settings", "streamSettings", "tag":
		default:
			return errors.New("unsafe_config")
		}
	}
	switch str(o, "protocol") {
	case "vless", "vmess", "trojan", "shadowsocks", "socks", "http":
	default:
		return errors.New("unsupported_feature")
	}
	// Whitelist stream fields, especially excluding sockopt (mark/interface/dialerProxy),
	// file-based TLS certificates and transport masks that can reference local files.
	if s, ok := o["streamSettings"].(map[string]any); ok {
		for k, v := range s {
			switch k {
			case "network", "security":
			case "tlsSettings":
				m, ok := v.(map[string]any)
				if !ok {
					return fail()
				}
				for key := range m {
					switch key {
					case "serverName", "fingerprint", "alpn":
					default:
						return errors.New("unsafe_config")
					}
				}
			case "realitySettings":
				m, ok := v.(map[string]any)
				if !ok {
					return fail()
				}
				for key := range m {
					switch key {
					case "serverName", "fingerprint", "publicKey", "shortId", "spiderX":
					default:
						return errors.New("unsafe_config")
					}
				}
			case "wsSettings", "grpcSettings", "xhttpSettings":
				m, ok := v.(map[string]any)
				if !ok {
					return fail()
				}
				for key := range m {
					switch key {
					case "path", "host", "headers", "serviceName", "multiMode", "mode":
					default:
						return errors.New("unsafe_config")
					}
				}
			default:
				return errors.New("unsafe_config")
			}
		}
	}
	return nil
}
func platformName() string { return runtime.GOOS }
