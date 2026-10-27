package engine

import (
	"encoding/base64"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestRealityHeaderAndRichTextImport(t *testing.T) {
	key := base64.RawURLEncoding.EncodeToString([]byte("0123456789abcdefghijklmnopqrstuv"))
	raw := "vless://" + id + "@example.org:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=example.org&fp=chrome&pbk=" + key + "&sid=1122334455667788&spx=%2Ftest&type=tcp&headerType=none#Reality"
	for _, input := range []string{raw, strings.Replace(raw, "sni=example.org", "[sni=example.org](http://sni=example.org/)", 1), raw + "<br />" + raw} {
		r, e := Import(input)
		if e != nil || len(r.Profiles) != 1 || r.Rejected != 0 {
			t.Fatalf("REALITY rich text/headerType result %d/%d/%v", len(r.Profiles), r.Rejected, e)
		}
		s := Defaults()
		s.Mode = "proxy"
		if _, e = Build(r.Profiles[0], s, "android"); e != nil {
			t.Fatal(e)
		}
	}
	if _, e := Import(strings.Replace(raw, "headerType=none", "headerType=http", 1)); e == nil {
		t.Fatal("unsupported TCP header silently accepted")
	}
}
func TestSubscriptionInfoEntries(t *testing.T) {
	encoded := base64.StdEncoding.EncodeToString([]byte("chacha20-ietf-poly1305:local-test-only"))
	encoded = strings.ReplaceAll(encoded, "=", "%3D")
	lines := []string{}
	for _, name := range []string{"Account", "Days%20remaining", "Quota%20remaining"} {
		lines = append(lines, "ss://"+encoded+"@0.0.0.0:1234#"+name)
	}
	r, e := Import(strings.Join(lines, "\n"))
	if e != nil || len(r.Profiles) != 0 || len(r.Info) != 3 || r.Rejected != 0 {
		t.Fatalf("metadata-only %d/%d/%d/%v", len(r.Profiles), len(r.Info), r.Rejected, e)
	}
	r, e = Import(strings.Join(lines, "\n") + "\nss://" + encoded + "@example.org:443#Real")
	if e != nil || len(r.Profiles) != 1 || len(r.Info) != 3 {
		t.Fatal("mixed subscription info lost")
	}
	p, _ := ParseLink(lines[0])
	s := Defaults()
	s.Mode = "proxy"
	if _, e = Build(p, s, "android"); e == nil {
		t.Fatal("unspecified placeholder became connectable")
	}
}
func TestIsolatedProfileProbeRequiresData(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer target.Close()
	addr := strings.TrimPrefix(target.URL, "http://")
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" || r.Host != addr {
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
	old := probeURL
	probeURL = target.URL
	defer func() { probeURL = old }()
	p, e := ParseLink("http://" + strings.TrimPrefix(upstream.URL, "http://"))
	if e != nil {
		t.Fatal(e)
	}
	before := snapshot()
	result, e := testProfile(p, Defaults())
	if e != nil || !result.Connected || result.Latency == nil || result.Up <= 0 || result.Down <= 0 {
		t.Fatalf("actual data probe %+v/%v", result, e)
	}
	if snapshot().State != before.State {
		t.Fatal("probe changed session state")
	}
	// A TCP acceptor with no proxy protocol must NOT be a successful configuration test.
	closed := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(403) }))
	defer closed.Close()
	p, _ = ParseLink("http://" + strings.TrimPrefix(closed.URL, "http://"))
	result, e = testProfile(p, Defaults())
	if e != nil || result.Connected || result.Latency != nil {
		t.Fatal("TCP reachability masqueraded as a working configuration")
	}
}
