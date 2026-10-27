package engine

import (
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

// Only opt-in on the disposable hosted Windows runner, after the signed vendor
// DLL is staged beside this test executable. Never changes a developer's routes by default.
func TestWindowsTunnelAcceptance(t *testing.T) {
	if os.Getenv("RADVPN_NATIVE_TUN_TEST") != "1" || os.Getenv("RUNNER_ENVIRONMENT") != "github-hosted" {
		t.Skip("requires opt-in disposable hosted Windows runner")
	}
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer target.Close()
	addr := strings.TrimPrefix(target.URL, "http://")
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" || (r.Host != addr && r.Host != "198.18.0.1:19876") {
			w.WriteHeader(403)
			return
		}
		remote, e := net.DialTimeout("tcp", addr, time.Second)
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
	defer Stop()
	p, e := ParseLink("http://" + strings.Replace(strings.TrimPrefix(upstream.URL, "http://"), "127.0.0.1", "localhost", 1))
	if e != nil {
		t.Fatal(e)
	}
	s := Defaults()
	if e = start(p, s, -1); e != nil {
		t.Fatalf("actual Windows tunnel startup: %s", e)
	}
	deadline := time.Now().Add(15 * time.Second)
	for snapshot().State == "connecting" && time.Now().Before(deadline) {
		time.Sleep(100 * time.Millisecond)
	}
	if v := snapshot(); v.State != "connected" {
		t.Fatalf("local verification failed: %s/%s", v.State, v.Error)
	}
	client := &http.Client{Timeout: 8 * time.Second, Transport: &http.Transport{Proxy: nil}}
	response, e := client.Get("http://198.18.0.1:19876/")
	if e != nil {
		t.Fatal("actual routed TUN data response failed")
	}
	defer response.Body.Close()
	if response.StatusCode != 204 {
		t.Fatal("unexpected TUN response")
	}
	if e = Stop(); e != nil {
		t.Fatal("tunnel cleanup failed")
	}
	if snapshot().State != "disconnected" {
		t.Fatal("tunnel remained active")
	}
	t.Log("PASS real Wintun startup, configured routes/DNS/WFP, local data through TUN, and shutdown; remote VPN/device leak tests are not implied")
}
