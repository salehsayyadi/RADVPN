package engine

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"sync"
	"syscall"
	"time"

	xlog "github.com/xtls/xray-core/common/log"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/stats"
	"github.com/xtls/xray-core/infra/conf/serial"
	_ "github.com/xtls/xray-core/main/distro/all"
	"github.com/xtls/xray-core/transport/internet"
	"golang.org/x/net/proxy"
)

type Protector interface{ Protect(fd int64) bool }

var guardMu sync.RWMutex
var protector Protector

// SetProtector must be called by Android before a tunnel starts. The Go socket
// controller applies VpnService.protect to TCP and UDP sockets, not just TCP.
func SetProtector(p Protector) { guardMu.Lock(); protector = p; guardMu.Unlock() }

type discardLogs struct{}

func (discardLogs) Handle(xlog.Message) {}

func init() {
	// Config parsing can log before the core installs its own disabled logger.
	// Drop those messages too: stdout is a strict JSON IPC channel.
	xlog.RegisterHandler(discardLogs{})
	_ = internet.RegisterDialerController(func(_, _ string, c syscall.RawConn) error {
		guardMu.RLock()
		p := protector
		guardMu.RUnlock()
		if p == nil {
			return nil
		}
		ok := false
		e := c.Control(func(fd uintptr) { ok = p.Protect(int64(fd)) })
		if e != nil || !ok {
			return errors.New("socket_protect_failed")
		}
		return nil
	})
}

type Snapshot struct {
	State    string `json:"state"`
	Error    string `json:"error"`
	Mode     string `json:"mode"`
	Up       int64  `json:"up"`
	Down     int64  `json:"down"`
	Seconds  int64  `json:"seconds"`
	Latency  *int64 `json:"latency"`
	Listener string `json:"listener"`
}

var mu sync.Mutex
var instance *core.Instance
var state = Snapshot{State: "disconnected"}
var started time.Time
var cancel context.CancelFunc
var probeURL = "https://cp.cloudflare.com/generate_204"

func Stop() error { mu.Lock(); defer mu.Unlock(); return stopLocked() }
func stopLocked() error {
	if cancel != nil {
		cancel()
		cancel = nil
	}
	state.State = "disconnecting"
	var e error
	if instance != nil {
		e = instance.Close()
		instance = nil
	}
	_ = os.Unsetenv("xray.tun.fd")
	state = Snapshot{State: "disconnected"}
	if e != nil {
		state.State = "error"
		state.Error = "cleanup_failed"
	}
	return e
}
func start(p Profile, s Settings, fd int) error {
	mu.Lock()
	defer mu.Unlock()
	if instance != nil || len(probeCancels) != 0 {
		return errors.New("already_running")
	}
	state = Snapshot{State: "connecting", Mode: s.Mode}
	failed := func(code string) error {
		if instance != nil {
			_ = instance.Close()
			instance = nil
		}
		_ = os.Unsetenv("xray.tun.fd")
		state.State = "error"
		state.Error = code
		return errors.New(code)
	}
	if platformName() == "android" && s.Mode == "tunnel" {
		guardMu.RLock()
		has := protector != nil
		guardMu.RUnlock()
		if fd < 0 || !has {
			return failed("vpn_permission_required")
		}
		if e := os.Setenv("xray.tun.fd", strconv.Itoa(fd)); e != nil {
			return failed("core_start_failed")
		}
	}
	config, e := Build(p, s, platformName())
	if e != nil {
		return failed(e.Error())
	}
	// Xray's own parser and config builder are authoritative, including cryptographic fields.
	c, e := serial.LoadJSONConfig(bytes.NewReader(config))
	if e != nil {
		return failed("core_config_invalid")
	}
	x, e := core.New(c)
	if e != nil {
		return failed("core_config_invalid")
	}
	instance = x
	if e = x.Start(); e != nil {
		return failed(startupCode(e, s.Mode))
	}
	started = time.Now()
	state.Listener = net.JoinHostPort(s.Bind, strconv.Itoa(s.Port))
	state.State = "connecting"
	// Starting a core/listener does NOT prove server reachability. An actual HTTPS
	// request must pass through the selected outbound before reporting Connected.
	ctx, cancelFn := context.WithCancel(context.Background())
	cancel = cancelFn
	go verifyConnection(ctx, x, s)
	return nil
}
func proxyClient(s Settings) (*http.Client, error) {
	d, e := proxy.SOCKS5("tcp", net.JoinHostPort(s.Bind, strconv.Itoa(s.Port)), nil, &net.Dialer{Timeout: 5 * time.Second})
	if e != nil {
		return nil, e
	}
	cd, ok := d.(proxy.ContextDialer)
	if !ok {
		return nil, errors.New("probe_failed")
	}
	return &http.Client{Timeout: 10 * time.Second, Transport: &http.Transport{DialContext: cd.DialContext, DisableKeepAlives: true}, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}, nil
}
func verifyConnection(ctx context.Context, x *core.Instance, s Settings) {
	c, e := proxyClient(s)
	var latency int64
	if e == nil {
		req, _ := http.NewRequestWithContext(ctx, "GET", probeURL, nil)
		t := time.Now()
		var resp *http.Response
		resp, e = c.Do(req)
		latency = time.Since(t).Milliseconds()
		if resp != nil {
			_ = resp.Body.Close()
			if resp.StatusCode != 204 {
				e = errors.New("probe_failed")
			}
		}
	}
	mu.Lock()
	defer mu.Unlock()
	if ctx.Err() != nil || instance != x {
		return
	}
	if e != nil {
		_ = x.Close()
		instance = nil
		_ = os.Unsetenv("xray.tun.fd")
		state.State = "error"
		state.Error = "probe_failed"
		return
	}
	state.Latency = &latency
	state.State = "connected"
}
func snapshot() Snapshot {
	mu.Lock()
	defer mu.Unlock()
	s := state
	if instance != nil {
		s.Seconds = int64(time.Since(started).Seconds())
		if sm, ok := instance.GetFeature(stats.ManagerType()).(stats.Manager); ok {
			if c := sm.GetCounter("outbound>>>proxy>>>traffic>>>uplink"); c != nil {
				s.Up = c.Value()
			}
			if c := sm.GetCounter("outbound>>>proxy>>>traffic>>>downlink"); c != nil {
				s.Down = c.Value()
			}
		}
	}
	return s
}
func fetchSubscription(address string) (ImportResult, error) {
	u, e := url.Parse(address)
	if e != nil || u.Scheme != "https" || u.Hostname() == "" || u.User != nil || len(address) > 8192 {
		return ImportResult{}, errors.New("invalid_url")
	}
	// No cookies, third-party API or implicit proxy. Each redirect must remain HTTPS.
	c := &http.Client{Timeout: 20 * time.Second, Transport: &http.Transport{Proxy: nil, DisableCompression: true}, CheckRedirect: func(req *http.Request, via []*http.Request) error {
		if len(via) >= 3 || req.URL.Scheme != "https" || req.URL.User != nil {
			return errors.New("invalid_url")
		}
		return nil
	}}
	resp, e := c.Get(u.String())
	if e != nil {
		return ImportResult{}, errors.New("subscription_failed")
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return ImportResult{}, errors.New("subscription_failed")
	}
	b, e := io.ReadAll(io.LimitReader(resp.Body, MaxImport+1))
	if e != nil {
		return ImportResult{}, errors.New("subscription_failed")
	}
	if len(b) > MaxImport {
		return ImportResult{}, errors.New("import_too_large")
	}
	return Import(string(b))
}

// Invoke is the only IPC/gomobile entrypoint. Errors are codes, never upstream
// diagnostics that could contain credentials. No arbitrary raw Xray JSON execution.
func Invoke(request string) string {
	result := func(data any, e error) string {
		r := map[string]any{"ok": e == nil}
		if e != nil {
			r["error"] = e.Error()
		} else {
			r["data"] = data
		}
		b, _ := json.Marshal(r)
		return string(b)
	}
	if len(request) > 4*MaxImport {
		return result(nil, errors.New("import_too_large"))
	}
	var r struct {
		Method   string   `json:"method"`
		Text     string   `json:"text"`
		Profile  Profile  `json:"profile"`
		Settings Settings `json:"settings"`
		FD       int      `json:"fd"`
	}
	dec := json.NewDecoder(bytes.NewBufferString(request))
	dec.DisallowUnknownFields()
	if dec.Decode(&r) != nil {
		return result(nil, errors.New("invalid_request"))
	}
	switch r.Method {
	case "version":
		return result(map[string]string{"app": Version, "core": core.Version()}, nil)
	case "defaults":
		return result(Defaults(), nil)
	case "settings":
		return result(nil, r.Settings.Validate())
	case "import":
		v, e := Import(r.Text)
		return result(v, e)
	case "subscription":
		v, e := fetchSubscription(r.Text)
		return result(v, e)
	case "start":
		e := start(r.Profile, r.Settings, r.FD)
		return result(snapshot(), e)
	case "stop":
		e := Stop()
		return result(snapshot(), e)
	case "probe":
		v, e := testProfile(r.Profile, r.Settings)
		return result(v, e)
	case "cancel_tests":
		cancelProfileTests()
		return result(nil, nil)
	case "status":
		return result(snapshot(), nil)
	case "validate":
		b, e := Build(r.Profile, r.Settings, platformName())
		if e == nil {
			_, e = serial.LoadJSONConfig(bytes.NewReader(b))
			if e != nil {
				e = errors.New("core_config_invalid")
			}
		}
		return result(nil, e)
	default:
		return result(nil, errors.New("invalid_request"))
	}
}
