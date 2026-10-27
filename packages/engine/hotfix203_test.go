package engine

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/xtls/xray-core/infra/conf/serial"
)

func TestRealityPQVerificationImport(t *testing.T) {
	public := base64.RawURLEncoding.EncodeToString(bytes.Repeat([]byte{7}, 1952))
	key := base64.RawURLEncoding.EncodeToString(bytes.Repeat([]byte{3}, 32))
	link := "vless://" + id + "@example.invalid:443?encryption=none&type=tcp&security=reality&flow=xtls-rprx-vision&sni=example.invalid&fp=chrome&pbk=" + key + "&sid=1122334455667788&pqv=" + public + "&spx=%2F#Synthetic"
	for _, input := range []string{link, base64.StdEncoding.EncodeToString([]byte(link))} {
		r, e := Import(input)
		if e != nil || len(r.Profiles) != 1 || r.Rejected != 0 {
			t.Fatal("PQ import rejected")
		}
		reality := r.Profiles[0].Outbound["streamSettings"].(map[string]any)["realitySettings"].(map[string]any)
		if reality["mldsa65Verify"] != public {
			t.Fatal("PQ verification silently lost")
		}
		s := Defaults()
		s.Mode = "proxy"
		raw, e := Build(r.Profiles[0], s, "windows")
		if e != nil {
			t.Fatal(e)
		}
		if _, e = serial.LoadJSONConfig(bytes.NewReader(raw)); e != nil {
			t.Fatal("Xray rejected synthetic PQ configuration")
		}
	}
	for _, changed := range []string{strings.Replace(link, public, "bad", 1), strings.Replace(link, "security=reality", "security=tls", 1), strings.Replace(link, "&pqv=", "&pqv="+public+"&pqv=", 1)} {
		if _, e := ParseLink(changed); e == nil {
			t.Fatal("unsafe/ambiguous PQ input accepted")
		}
	}
}
func TestSubscriptionMixedTransportsWithPQ(t *testing.T) {
	public := base64.RawURLEncoding.EncodeToString(bytes.Repeat([]byte{7}, 1952))
	key := base64.RawURLEncoding.EncodeToString(bytes.Repeat([]byte{3}, 32))
	lines := []string{"vless://" + id + "@example.invalid:443?security=tls&type=ws&host=example.invalid&path=%2F#WS", "vless://" + id + "@example.invalid:8443?security=reality&type=tcp&sni=example.invalid&pbk=" + key + "&pqv=" + public + "#PQ"}
	r, e := Import(base64.StdEncoding.EncodeToString([]byte(strings.Join(lines, "\n"))))
	if e != nil || len(r.Profiles) != 2 || r.Rejected != 0 {
		t.Fatal("mixed subscription dropped a supported protocol")
	}
}
func TestTunnelEndpointBootstrapPreservesIdentity(t *testing.T) {
	p, e := ParseLink("vless://" + id + "@upstream.invalid:443?security=tls&sni=identity.invalid&type=ws&host=web.invalid&path=%2F")
	if e != nil {
		t.Fatal(e)
	}
	before, _ := json.Marshal(p)
	calls := 0
	lookup := func(ctx context.Context, host string) ([]net.IPAddr, error) {
		calls++
		if host != "upstream.invalid" {
			t.Fatal("wrong endpoint")
		}
		if _, ok := ctx.Deadline(); !ok {
			t.Fatal("unbounded DNS")
		}
		return []net.IPAddr{{IP: net.ParseIP("2001:db8::1")}, {IP: net.ParseIP("192.0.2.42")}}, nil
	}
	s := Defaults()
	pinned, e := prepareTunnelEndpoint(p, s, lookup)
	if e != nil || calls != 1 {
		t.Fatal("bootstrap failed")
	}
	after, _ := json.Marshal(p)
	if !bytes.Equal(before, after) || pinned.ID != p.ID || pinned.SourceLink != p.SourceLink {
		t.Fatal("persisted profile rewritten")
	}
	o := pinned.Outbound
	endpoint := o["settings"].(map[string]any)["vnext"].([]any)[0].(map[string]any)
	stream := o["streamSettings"].(map[string]any)
	if endpoint["address"] != "192.0.2.42" || stream["tlsSettings"].(map[string]any)["serverName"] != "identity.invalid" || stream["wsSettings"].(map[string]any)["headers"].(map[string]any)["Host"] != "web.invalid" {
		t.Fatal("transport identity changed")
	}
	raw, e := Build(pinned, s, "windows")
	if e != nil {
		t.Fatal(e)
	}
	if _, e = serial.LoadJSONConfig(bytes.NewReader(raw)); e != nil {
		t.Fatal("pinned config invalid")
	}
	failing := func(context.Context, string) ([]net.IPAddr, error) {
		return nil, errors.New("private resolver details")
	}
	if _, e = prepareTunnelEndpoint(p, s, failing); e == nil || e.Error() != "endpoint_dns_failed" {
		t.Fatal("DNS error exposed details")
	}
	s.IPv6 = "block"
	only6 := func(context.Context, string) ([]net.IPAddr, error) {
		return []net.IPAddr{{IP: net.ParseIP("2001:db8::1")}}, nil
	}
	if _, e = prepareTunnelEndpoint(p, s, only6); e == nil || e.Error() != "tunnel_endpoint_ipv6_blocked" {
		t.Fatal("blocked IPv6 silently used")
	}
}
func TestConnectivityProviderFallbackAndStrictValidation(t *testing.T) {
	bad := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(403) }))
	defer bad.Close()
	good := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer good.Close()
	c := &http.Client{Timeout: time.Second, Transport: &http.Transport{Proxy: nil}}
	if _, e := verifyHTTPS(context.Background(), c, []string{bad.URL, good.URL}); e != nil {
		t.Fatal("blocked primary prevented working fallback")
	}
	if _, e := verifyHTTPS(context.Background(), c, []string{bad.URL}); e == nil {
		t.Fatal("HTTP rejection became success")
	}
	untrusted := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer untrusted.Close()
	if _, e := verifyHTTPS(context.Background(), c, []string{untrusted.URL}); e == nil {
		t.Fatal("TLS verification disabled")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, e := verifyHTTPS(ctx, c, []string{good.URL}); e == nil {
		t.Fatal("cancel ignored")
	}
}
