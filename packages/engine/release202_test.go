package engine

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"github.com/xtls/xray-core/infra/conf/serial"
	"strings"
	"testing"
)

func Test202RealityTransportsAndShadowsocks(t *testing.T) {
	key := base64.RawURLEncoding.EncodeToString([]byte("0123456789abcdefghijklmnopqrstuv"))
	for _, transport := range []string{"raw", "tcp", "grpc", "xhttp"} {
		raw := "vless://" + id + "@example.org:443?security=reality&sni=example.org&pbk=" + key + "&sid=1122334455667788&type=" + transport + "#%D9%87%D9%84%D9%86%D8%AF%20%F0%9F%87%B3%F0%9F%87%B1"
		p, e := ParseLink(raw)
		if e != nil {
			t.Fatalf("%s: %v", transport, e)
		}
		if p.Name != "هلند 🇳🇱" {
			t.Fatal("UTF8 label lost")
		}
		s := Defaults()
		s.Mode = "proxy"
		cfg, e := Build(p, s, "windows")
		if e != nil {
			t.Fatal(e)
		}
		if _, e = serial.LoadJSONConfig(bytes.NewReader(cfg)); e != nil {
			t.Fatalf("native REALITY %s: %v", transport, e)
		}
	}
	for _, codec := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.RawURLEncoding} {
		credentials := codec.EncodeToString([]byte("chacha20-ietf-poly1305:fixture-only-password"))
		for _, suffix := range []string{"#SS", "/#SS"} {
			p, e := ParseLink("ss://" + credentials + "@example.org:443" + suffix)
			if e != nil || p.Outbound["protocol"] != "shadowsocks" {
				t.Fatalf("SS %v", e)
			}
		}
	}
}
func Test202StrictProfileWireBoundary(t *testing.T) {
	p, e := ParseLink("socks://127.0.0.1:1080#Fixture")
	if e != nil {
		t.Fatal(e)
	}
	s := Defaults()
	s.Mode = "proxy"
	raw, _ := json.Marshal(map[string]any{"method": "validate", "profile": p, "settings": s})
	if !strings.Contains(Invoke(string(raw)), `"ok":true`) {
		t.Fatal("canonical profile rejected")
	}
	var request map[string]any
	_ = json.Unmarshal(raw, &request)
	request["profile"].(map[string]any)["probeLatency"] = 42
	dirty, _ := json.Marshal(request)
	if !strings.Contains(Invoke(string(dirty)), "invalid_request") {
		t.Fatal("strict native boundary widened")
	}
}
