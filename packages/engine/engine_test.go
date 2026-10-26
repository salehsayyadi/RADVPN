package engine

import (
	"bufio"
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/xtls/xray-core/infra/conf/serial"
	"golang.org/x/net/proxy"
)

const id = "aabbccdd-aabb-ccdd-aabb-aabbccddeeff"

func TestProfiles(t *testing.T) {
	cases := []string{
		"vless://" + id + "@example.com:443?security=tls&sni=example.com&type=ws&path=%2Fvpn#Orange",
		"vless://" + id + "@example.com:443?security=tls&flow=xtls-rprx-vision&type=tcp",
		"trojan://secret@example.com:443?security=tls&type=grpc&serviceName=rad",
		"ss://" + base64.RawURLEncoding.EncodeToString([]byte("aes-128-gcm:secret")) + "@example.com:443#SS",
		"socks://user:pass@127.0.0.1:1080", "http://user:pass@example.com:3128",
		"vmess://" + base64.StdEncoding.EncodeToString([]byte(`{"v":"2","ps":"legacy","add":"example.com","port":"443","id":"`+id+`","aid":"0","net":"ws","path":"/","tls":"tls"}`)),
	}
	for _, link := range cases {
		t.Run(strings.Split(link, ":")[0], func(t *testing.T) {
			p, e := ParseLink(link)
			if e != nil {
				t.Fatal(e)
			}
			s := Defaults()
			s.Mode = "proxy"
			b, e := Build(p, s, "windows")
			if e != nil {
				t.Fatal(e)
			}
			if _, e = serial.LoadJSONConfig(bytes.NewReader(b)); e != nil {
				t.Fatalf("upstream schema rejected generated config: %v", e)
			}
		})
	}
}
func TestRejectUnsafe(t *testing.T) {
	for _, s := range []string{
		"vless://x@example.com:443", "trojan://secret@example.com:0", "trojan://secret@example.com:443?allowInsecure=1", "vless://" + id + "@example.com:443?type=quic", "vless://" + id + "@example.com:443?security=tls&security=none", "vless://" + id + "@example.com:443?type=ws&flow=xtls-rprx-vision", "ss://broken", "file:///etc/passwd", "vmess://not-base64", "trojan://secret@example.com:65536", "vless://" + id + "@example.com:443?security=reality"} {
		if _, e := ParseLink(s); e == nil {
			t.Errorf("unsafe link accepted: %s", strings.Split(s, ":")[0])
		}
	}
	p, _ := ParseLink("trojan://secret@example.com:443")
	p.Outbound["proxySettings"] = map[string]any{"tag": "direct"}
	if _, e := Build(p, Defaults(), "windows"); e == nil {
		t.Fatal("arbitrary outbound field accepted")
	}
	p, _ = ParseLink("trojan://secret@example.com:443")
	p.Outbound["streamSettings"].(map[string]any)["tlsSettings"].(map[string]any)["allowInsecure"] = true
	if _, e := Build(p, Defaults(), "windows"); e == nil {
		t.Fatal("insecure TLS accepted")
	}
}
func TestImport(t *testing.T) {
	link := "trojan://secret@example.com:443#one"
	for _, s := range []string{link + "\n" + link + "\nbad", base64.StdEncoding.EncodeToString([]byte(link + "\n" + link + "\nbad"))} {
		r, e := Import(s)
		if e != nil || len(r.Profiles) != 1 || r.Rejected != 1 {
			t.Fatalf("dedup result %d / %d / %v", len(r.Profiles), r.Rejected, e)
		}
	}
	if _, e := Import(strings.Repeat("x", MaxImport+1)); e == nil {
		t.Fatal("limit ignored")
	}
}
func TestSettings(t *testing.T) {
	s := Defaults()
	if s.Mode != "tunnel" || s.Port != 10808 {
		t.Fatal("unsafe defaults")
	}
	if e := s.Validate(); e != nil {
		t.Fatal(e)
	}
	tests := []func(*Settings){func(s *Settings) { s.Bind = "0.0.0.0" }, func(s *Settings) { s.Port = 65535 }, func(s *Settings) { s.Port = 0 }, func(s *Settings) { s.DNS = []string{"https://example.com"} }, func(s *Settings) { s.MTU = 1000 }, func(s *Settings) { s.Rules = []Rule{{IPs: []string{"bad"}, Action: "direct"}} }, func(s *Settings) { s.Rules = []Rule{{Domains: []string{"evil\n.com"}, Action: "direct"}} }}
	for i, f := range tests {
		s := Defaults()
		f(&s)
		if s.Validate() == nil {
			t.Errorf("invalid settings accepted %d", i)
		}
	}
	s.Rules = []Rule{{Domains: []string{"domain:example.org"}, Action: "direct"}, {IPs: []string{"10.0.0.0/8", "2001:db8::/32"}, Action: "block"}}
	if e := s.Validate(); e != nil {
		t.Fatal(e)
	}
	p, _ := ParseLink("trojan://secret@example.com:443")
	b, e := Build(p, s, "windows")
	if e != nil {
		t.Fatal(e)
	}
	if _, e = serial.LoadJSONConfig(bytes.NewReader(b)); e != nil {
		t.Fatal(e)
	}
	if _, e = Build(p, s, "linux"); e == nil {
		t.Fatal("unsupported TUN enabled")
	}
}
func TestLocalization(t *testing.T) {
	var en, fa map[string]string
	for name, target := range map[string]*map[string]string{"en": &en, "fa": &fa} {
		b, e := os.ReadFile(filepath.Join("..", "localization", name+".json"))
		if e != nil {
			t.Fatal(e)
		}
		if e = json.Unmarshal(b, target); e != nil {
			t.Fatal(e)
		}
	}
	if len(en) != len(fa) {
		t.Fatal("locale parity mismatch")
	}
	for k, v := range en {
		if strings.TrimSpace(v) == "" || strings.TrimSpace(fa[k]) == "" {
			t.Errorf("missing %s", k)
		}
	}
}
func TestErrorsAreRedacted(t *testing.T) {
	r := Invoke(`{"method":"import","text":"trojan://super-secret@example.com:443?unsafe=secret"}`)
	if strings.Contains(r, "super-secret") || strings.Contains(r, "example.com") {
		t.Fatal("credential leak")
	}
	if strings.Contains(Invoke(`{"method":"import","text":"bad","command":"rm"}`), `"ok":true`) {
		t.Fatal("extra request fields accepted")
	}
}
func freePair(t *testing.T) int {
	t.Helper()
	for i := 0; i < 40; i++ {
		l, e := net.Listen("tcp", "127.0.0.1:0")
		if e != nil {
			t.Fatal(e)
		}
		p := l.Addr().(*net.TCPAddr).Port
		l2, e := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", p+1))
		if e == nil {
			l.Close()
			l2.Close()
			return p
		}
		l.Close()
	}
	t.Fatal("no port pair")
	return 0
}

// A real local HTTP CONNECT upstream and HTTP target exercise Xray's SOCKS5
// handshake, outbound path, byte counters and cleanup. This is not remote VPN QA.
func TestCoreTrafficAndCleanup(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/probe" {
			w.WriteHeader(204)
			return
		}
		_, _ = io.WriteString(w, "RADVPN actual traffic")
	}))
	defer target.Close()
	targetAddr := strings.TrimPrefix(target.URL, "http://")
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" || r.Host != targetAddr {
			w.WriteHeader(403)
			return
		}
		remote, e := net.DialTimeout("tcp", r.Host, time.Second)
		if e != nil {
			w.WriteHeader(502)
			return
		}
		client, _, e := w.(http.Hijacker).Hijack()
		if e != nil {
			remote.Close()
			return
		}
		_, _ = io.WriteString(client, "HTTP/1.1 200 Connection established\r\n\r\n")
		go func() { defer remote.Close(); _, _ = io.Copy(remote, client) }()
		go func() { defer client.Close(); _, _ = io.Copy(client, remote) }()
	}))
	defer upstream.Close()
	u := strings.TrimPrefix(upstream.URL, "http://")
	p, e := ParseLink("http://user:pass@" + u)
	if e != nil {
		t.Fatal(e)
	}
	s := Defaults()
	s.Mode = "proxy"
	s.Port = freePair(t)
	oldProbe := probeURL
	probeURL = target.URL + "/probe"
	defer func() { probeURL = oldProbe; _ = Stop() }()
	if e = start(p, s, -1); e != nil {
		t.Fatal(e)
	}
	deadline := time.Now().Add(5 * time.Second)
	for snapshot().State == "connecting" && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	if snapshot().State != "connected" {
		t.Fatalf("probe state: %+v", snapshot())
	}
	dialer, e := proxy.SOCKS5("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(s.Port)), nil, proxy.Direct)
	if e != nil {
		t.Fatal(e)
	}
	conn, e := dialer.Dial("tcp", targetAddr)
	if e != nil {
		t.Fatal(e)
	}
	_ = conn.SetDeadline(time.Now().Add(3 * time.Second))
	_, _ = fmt.Fprintf(conn, "GET / HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n", targetAddr)
	resp, e := http.ReadResponse(bufio.NewReader(conn), nil)
	if e != nil {
		t.Fatal(e)
	}
	b, e := io.ReadAll(resp.Body)
	_ = conn.Close()
	if e != nil || string(b) != "RADVPN actual traffic" {
		t.Fatalf("actual data %q %v", b, e)
	}
	snap := snapshot()
	if snap.Up == 0 || snap.Down == 0 {
		t.Fatalf("no real counters: %+v", snap)
	}
	if e = Stop(); e != nil {
		t.Fatal(e)
	}
	if snapshot().State != "disconnected" {
		t.Fatal("did not stop")
	}
	for _, port := range []int{s.Port, s.Port + 1} {
		c, e := net.DialTimeout("tcp", net.JoinHostPort(s.Bind, strconv.Itoa(port)), 100*time.Millisecond)
		if e == nil {
			c.Close()
			t.Fatal("stale listener")
		}
	}
}
func TestPortConflict(t *testing.T) {
	p, _ := ParseLink("trojan://secret@example.com:443")
	s := Defaults()
	s.Mode = "proxy"
	s.Port = freePair(t)
	l, e := net.Listen("tcp", net.JoinHostPort(s.Bind, strconv.Itoa(s.Port)))
	if e != nil {
		t.Fatal(e)
	}
	defer l.Close()
	defer Stop()
	if e = start(p, s, -1); e == nil {
		t.Fatal("port conflict not detected")
	}
	if snapshot().State != "error" {
		t.Fatal("fabricated connected status")
	}
}
func TestStopIdempotent(t *testing.T) {
	r := Invoke(`{"method":"stop"}`)
	if !strings.Contains(r, `"ok":true`) {
		t.Fatal(r)
	}
}
func FuzzImport(f *testing.F) {
	for _, s := range []string{"bad", "trojan://secret@example.com:443", "vmess://AA==", "ss://bad", "vless://x@[::1]:9"} {
		f.Add(s)
	}
	f.Fuzz(func(t *testing.T, s string) {
		if len(s) > MaxImport {
			return
		}
		_, _ = Import(s)
	})
}

func TestJSONImportBoundary(t *testing.T) {
	safe := `{"protocol":"http","tag":"local node","settings":{"servers":[{"address":"127.0.0.1","port":3128}]}}`
	for _, text := range []string{safe, `[` + safe + `]`, `{"outbounds":[` + safe + `]}`} {
		r, e := Import(text)
		if e != nil || len(r.Profiles) != 1 {
			t.Fatalf("safe outbound rejected: %v", e)
		}
	}
	for _, text := range []string{`{"outbounds":[` + safe + `],"env":{"xray.tun.fd":"5"}}`, `{"protocol":"freedom","settings":{}}`, `{"protocol":"http","settings":{},"streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"allowInsecure":true}}}`} {
		if _, e := Import(text); e == nil {
			t.Fatal("unsafe JSON imported")
		}
	}
}
func TestWindowsIPv6Policy(t *testing.T) {
	p, _ := ParseLink("trojan://test@example.com:443")
	s := Defaults()
	s.IPv6 = "block"
	b, e := Build(p, s, "windows")
	if e != nil {
		t.Fatal(e)
	}
	if _, e = serial.LoadJSONConfig(bytes.NewReader(b)); e != nil {
		t.Fatal(e)
	}
	if bytes.Contains(b, []byte(`"::/1"`)) {
		t.Fatal("IPv6 route installed in block policy")
	}
	s.DNS = []string{"2606:4700:4700::1111"}
	if s.Validate() == nil {
		t.Fatal("IPv4 DNS missing in IPv6 block policy")
	}
}
