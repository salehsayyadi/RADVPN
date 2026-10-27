// Package engine is the shared, native Xray boundary. It never executes profile content.
package engine

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

const Version = "2.0.1"
const CoreVersion = "26.10.21"
const MaxImport = 2 * 1024 * 1024

type Profile struct {
	ID       string         `json:"id"`
	Name     string         `json:"name"`
	Outbound map[string]any `json:"outbound"`
	Favorite bool           `json:"favorite"`
	Enabled  bool           `json:"enabled"`
	Group    string         `json:"group"`
}
type ImportResult struct {
	Profiles []Profile `json:"profiles"`
	Rejected int       `json:"rejected"`
	Info     []string  `json:"info,omitempty"`
}

func decode64(s string) ([]byte, error) {
	for _, e := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.URLEncoding, base64.RawURLEncoding} {
		if b, err := e.DecodeString(s); err == nil {
			return b, nil
		}
	}
	return nil, errors.New("invalid_base64")
}
func fail() error { return errors.New("invalid_profile") }
func str(m map[string]any, k string) string {
	v := m[k]
	if v == nil {
		return ""
	}
	return fmt.Sprint(v)
}
func identity(o map[string]any) string {
	b, _ := json.Marshal(o)
	h := sha256.Sum256(b)
	return hex.EncodeToString(h[:16])
}
func validHost(h string) bool {
	return h != "" && len(h) <= 253 && !strings.ContainsAny(h, " \r\n\t/\\\x00")
}
func uuid(s string) bool {
	b, e := hex.DecodeString(strings.ReplaceAll(s, "-", ""))
	return e == nil && len(b) == 16
}

// ParseLink deliberately rejects unknown transport parameters rather than silently changing semantics.
func ParseLink(raw string) (Profile, error) {
	if len(raw) > 65536 {
		return Profile{}, fail()
	}
	if strings.HasPrefix(raw, "vmess://") && !strings.Contains(strings.TrimPrefix(raw, "vmess://"), "@") {
		b, e := decode64(strings.TrimPrefix(raw, "vmess://"))
		if e != nil {
			return Profile{}, fail()
		}
		var v map[string]any
		if json.Unmarshal(b, &v) != nil {
			return Profile{}, fail()
		}
		if a := str(v, "aid"); a != "" && a != "0" {
			return Profile{}, errors.New("unsupported_feature")
		}
		host := str(v, "add")
		port := str(v, "port")
		if !validHost(host) || !uuid(str(v, "id")) {
			return Profile{}, fail()
		}
		u := &url.URL{Scheme: "vmess", Host: net.JoinHostPort(host, port), User: url.User(str(v, "id")), Fragment: str(v, "ps")}
		q := url.Values{}
		for k, vk := range map[string]string{"type": "net", "path": "path", "host": "host", "sni": "sni", "alpn": "alpn", "fp": "fp"} {
			if s := str(v, vk); s != "" {
				q.Set(k, s)
			}
		}
		if str(v, "tls") == "tls" {
			q.Set("security", "tls")
		}
		if scy := str(v, "scy"); scy != "" {
			q.Set("encryption", scy)
		}
		u.RawQuery = q.Encode()
		raw = u.String()
	}
	if strings.HasPrefix(raw, "ss://") {
		rest := strings.TrimPrefix(raw, "ss://")
		main := strings.SplitN(rest, "#", 2)
		if !strings.Contains(main[0], "@") {
			b, e := decode64(main[0])
			if e != nil {
				return Profile{}, fail()
			}
			raw = "ss://" + string(b)
			if len(main) == 2 {
				raw += "#" + main[1]
			}
		}
	}
	u, e := url.Parse(raw)
	if e != nil || !validHost(u.Hostname()) {
		return Profile{}, fail()
	}
	port, e := strconv.Atoi(u.Port())
	if e != nil || port < 1 || port > 65535 {
		return Profile{}, fail()
	}
	q := u.Query()
	allowed := map[string]bool{}
	for _, k := range []string{"type", "security", "encryption", "flow", "sni", "fp", "alpn", "pbk", "sid", "spx", "path", "host", "serviceName", "mode", "headerType"} {
		allowed[k] = true
	}
	for k, vs := range q {
		if !allowed[k] || len(vs) != 1 {
			return Profile{}, errors.New("unsupported_feature")
		}
	}
	transport := q.Get("type")
	if transport == "" {
		transport = "tcp"
	}
	if transport != "tcp" && transport != "ws" && transport != "grpc" && transport != "xhttp" {
		return Profile{}, errors.New("unsupported_feature")
	}
	if h := q.Get("headerType"); h != "" && (h != "none" || transport != "tcp") {
		return Profile{}, errors.New("unsupported_feature")
	}
	security := q.Get("security")
	if security == "" {
		security = "none"
		if u.Scheme == "trojan" {
			security = "tls"
		}
	}
	if security != "none" && security != "tls" && security != "reality" {
		return Profile{}, errors.New("unsupported_feature")
	}
	if security == "reality" && (u.Scheme != "vless" || transport != "tcp" || q.Get("pbk") == "" || q.Get("sni") == "") {
		return Profile{}, fail()
	}
	if q.Get("flow") != "" && u.Scheme != "vless" {
		return Profile{}, fail()
	}
	if (q.Get("path") != "" || q.Get("host") != "") && transport != "ws" && transport != "xhttp" {
		return Profile{}, fail()
	}
	if q.Get("serviceName") != "" && transport != "grpc" {
		return Profile{}, fail()
	}
	if q.Get("mode") != "" && transport != "grpc" && transport != "xhttp" {
		return Profile{}, fail()
	}
	if security == "none" && (q.Get("sni") != "" || q.Get("alpn") != "" || q.Get("fp") != "") {
		return Profile{}, fail()
	}
	if security != "reality" && (q.Get("pbk") != "" || q.Get("sid") != "" || q.Get("spx") != "") {
		return Profile{}, fail()
	}
	user, password := "", ""
	if u.User != nil {
		user = u.User.Username()
		password, _ = u.User.Password()
	}
	endpoint := map[string]any{"address": u.Hostname(), "port": port}
	o := map[string]any{"tag": "proxy", "protocol": u.Scheme}
	switch u.Scheme {
	case "vless", "vmess":
		if !uuid(user) {
			return Profile{}, fail()
		}
		cred := map[string]any{"id": user}
		if u.Scheme == "vless" {
			enc := q.Get("encryption")
			if enc == "" {
				enc = "none"
			}
			if enc != "none" {
				return Profile{}, errors.New("unsupported_feature")
			}
			cred["encryption"] = enc
		} else {
			enc := q.Get("encryption")
			if enc == "" {
				enc = "auto"
			}
			switch enc {
			case "auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero":
			default:
				return Profile{}, fail()
			}
			cred["security"] = enc
		}
		if flow := q.Get("flow"); flow != "" {
			if u.Scheme != "vless" || flow != "xtls-rprx-vision" || transport != "tcp" || security == "none" {
				return Profile{}, fail()
			}
			cred["flow"] = flow
		}
		endpoint["users"] = []any{cred}
		o["settings"] = map[string]any{"vnext": []any{endpoint}}
	case "trojan":
		if security == "none" || user == "" {
			return Profile{}, fail()
		}
		endpoint["password"] = user
		o["settings"] = map[string]any{"servers": []any{endpoint}}
	case "ss":
		if password == "" {
			b, err := decode64(user)
			if err != nil {
				return Profile{}, fail()
			}
			parts := strings.SplitN(string(b), ":", 2)
			if len(parts) != 2 {
				return Profile{}, fail()
			}
			user, password = parts[0], parts[1]
		}
		switch user {
		case "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305":
		default:
			return Profile{}, errors.New("unsupported_feature")
		}
		if password == "" {
			return Profile{}, fail()
		}
		o["protocol"] = "shadowsocks"
		endpoint["method"] = user
		endpoint["password"] = password
		o["settings"] = map[string]any{"servers": []any{endpoint}}
	case "socks", "http":
		if user != "" {
			endpoint["users"] = []any{map[string]any{"user": user, "pass": password}}
		}
		o["settings"] = map[string]any{"servers": []any{endpoint}}
	default:
		return Profile{}, errors.New("unsupported_feature")
	}
	if u.Scheme == "ss" || u.Scheme == "socks" || u.Scheme == "http" {
		if len(q) > 0 {
			return Profile{}, errors.New("unsupported_feature")
		}
	}
	stream := map[string]any{"network": transport, "security": security}
	if security == "tls" {
		sni := q.Get("sni")
		if sni == "" {
			sni = u.Hostname()
		}
		tls := map[string]any{"serverName": sni}
		if fp := q.Get("fp"); fp != "" {
			tls["fingerprint"] = fp
		}
		if a := q.Get("alpn"); a != "" {
			tls["alpn"] = strings.Split(a, ",")
		}
		stream["tlsSettings"] = tls
	}
	if security == "reality" {
		stream["realitySettings"] = map[string]any{"serverName": q.Get("sni"), "fingerprint": defaultString(q.Get("fp"), "chrome"), "publicKey": q.Get("pbk"), "shortId": q.Get("sid"), "spiderX": q.Get("spx")}
	}
	switch transport {
	case "ws":
		stream["wsSettings"] = map[string]any{"path": defaultString(q.Get("path"), "/"), "headers": map[string]any{"Host": defaultString(q.Get("host"), u.Hostname())}}
	case "grpc":
		if q.Get("mode") != "" && q.Get("mode") != "gun" && q.Get("mode") != "multi" {
			return Profile{}, fail()
		}
		stream["grpcSettings"] = map[string]any{"serviceName": q.Get("serviceName"), "multiMode": q.Get("mode") == "multi"}
	case "xhttp":
		mode := defaultString(q.Get("mode"), "auto")
		switch mode {
		case "auto", "packet-up", "stream-up", "stream-one":
		default:
			return Profile{}, fail()
		}
		stream["xhttpSettings"] = map[string]any{"path": defaultString(q.Get("path"), "/"), "host": q.Get("host"), "mode": mode}
	}
	o["streamSettings"] = stream
	name := u.Fragment
	if name == "" {
		name = u.Hostname()
	}
	if len(name) > 200 {
		return Profile{}, fail()
	}
	return Profile{ID: identity(o), Name: name, Outbound: o, Enabled: true}, nil
}
func defaultString(s, d string) string {
	if s == "" {
		return d
	}
	return s
}
func Import(text string) (ImportResult, error) {
	r := ImportResult{Profiles: []Profile{}}
	if len(text) > MaxImport {
		return r, errors.New("import_too_large")
	}
	text = normalizePastedLinks(strings.TrimSpace(text))
	if strings.HasPrefix(text, "{") || strings.HasPrefix(text, "[") {
		return ImportOutbounds(text)
	}
	if !strings.Contains(text, "://") {
		b, e := decode64(text)
		if e != nil {
			return r, fail()
		}
		text = normalizePastedLinks(string(b))
	}
	seen := map[string]bool{}
	for _, line := range strings.Fields(text) {
		p, e := ParseLink(line)
		if e != nil {
			r.Rejected++
			continue
		}
		if placeholderOutbound(p.Outbound) {
			if p.Name != "0.0.0.0" && p.Name != "::" && len(r.Info) < 64 {
				r.Info = append(r.Info, p.Name)
			}
			continue
		}
		if !seen[p.ID] {
			r.Profiles = append(r.Profiles, p)
			seen[p.ID] = true
		}
	}
	if len(r.Profiles) == 0 && len(r.Info) == 0 {
		return r, fail()
	}
	return r, nil
}

// Rich-text chat editors can wrap query values as Markdown or separate links with HTML breaks.
// Only unwrap a literal query assignment; never fetch a Markdown destination.
var pastedQuery = regexp.MustCompile(`\[([A-Za-z][A-Za-z0-9]*=[^\]\s]+)\]\(https?://[^)\s]+\)`)
var pastedBreak = regexp.MustCompile(`(?i)<br\s*/?>`)

func normalizePastedLinks(s string) string {
	s = pastedBreak.ReplaceAllString(s, "\n")
	return pastedQuery.ReplaceAllString(s, "$1")
}
func placeholderOutbound(o map[string]any) bool {
	settings, _ := o["settings"].(map[string]any)
	for _, key := range []string{"servers", "vnext"} {
		list, _ := settings[key].([]any)
		for _, value := range list {
			endpoint, _ := value.(map[string]any)
			if ip := net.ParseIP(str(endpoint, "address")); ip != nil && ip.IsUnspecified() {
				return true
			}
		}
	}
	return false
}
