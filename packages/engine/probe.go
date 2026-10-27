package engine

import (
	"bytes"
	"context"
	"errors"
	"net"
	"strconv"
	"sync"
	"time"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/stats"
	"github.com/xtls/xray-core/infra/conf/serial"
)

type ProbeResult struct {
	Connected bool   `json:"connected"`
	Latency   *int64 `json:"latency"`
	Up        int64  `json:"up"`
	Down      int64  `json:"down"`
	Error     string `json:"error,omitempty"`
}

var probeCancels = map[int]context.CancelFunc{}
var nextProbe int
var probeStartMu sync.Mutex

func cancelProfileTests() {
	mu.Lock()
	defer mu.Unlock()
	for _, c := range probeCancels {
		c()
	}
}

// testProfile uses an isolated real Xray instance and a real response through that outbound.
// It never changes the selected profile, global session state, TUN FD or system routes.
func testProfile(p Profile, s Settings) (ProbeResult, error) {
	result := ProbeResult{}
	ctx, cancelFn := context.WithTimeout(context.Background(), 12*time.Second)
	mu.Lock()
	if instance != nil {
		mu.Unlock()
		cancelFn()
		return result, errors.New("already_running")
	}
	nextProbe++
	id := nextProbe
	probeCancels[id] = cancelFn
	mu.Unlock()
	defer func() { cancelFn(); mu.Lock(); delete(probeCancels, id); mu.Unlock() }()
	s.Mode = "proxy"
	s.Bind = "127.0.0.1"
	s.Rules = nil
	if e := s.Validate(); e != nil {
		return result, e
	}
	x, e := startIsolatedProbe(p, &s)
	if e != nil {
		return result, e
	}
	defer x.Close()
	client, e := proxyClient(s)
	if e != nil {
		result.Error = "probe_failed"
		return result, nil
	}
	defer client.CloseIdleConnections()
	latency, e := verifyHTTPS(ctx, client, connectionProbeTargets())
	if e != nil {
		result.Error = "probe_failed"
		return result, nil
	}
	if sm, ok := x.GetFeature(stats.ManagerType()).(stats.Manager); ok {
		if c := sm.GetCounter("outbound>>>proxy>>>traffic>>>uplink"); c != nil {
			result.Up = c.Value()
		}
		if c := sm.GetCounter("outbound>>>proxy>>>traffic>>>downlink"); c != nil {
			result.Down = c.Value()
		}
	}
	if result.Up <= 0 || result.Down <= 0 {
		result.Error = "probe_failed"
		return result, nil
	}
	result.Latency = &latency
	result.Connected = true
	return result, nil
}
func startIsolatedProbe(p Profile, s *Settings) (*core.Instance, error) {
	probeStartMu.Lock()
	defer probeStartMu.Unlock()
	for attempt := 0; attempt < 12; attempt++ {
		first, e := net.Listen("tcp", "127.0.0.1:0")
		if e != nil {
			return nil, errors.New("ports_busy")
		}
		port := first.Addr().(*net.TCPAddr).Port
		if port >= 65535 {
			_ = first.Close()
			continue
		}
		second, e := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port+1)))
		_ = first.Close()
		if e != nil {
			continue
		}
		_ = second.Close()
		s.Port = port
		raw, e := Build(p, *s, platformName())
		if e != nil {
			return nil, e
		}
		config, e := serial.LoadJSONConfig(bytes.NewReader(raw))
		if e != nil {
			return nil, errors.New("core_config_invalid")
		}
		x, e := core.New(config)
		if e != nil {
			return nil, errors.New("core_config_invalid")
		}
		if e = x.Start(); e != nil {
			_ = x.Close()
			continue
		}
		return x, nil
	}
	return nil, errors.New("ports_busy")
}
