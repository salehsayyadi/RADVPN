package engine

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"strings"

	"github.com/xtls/xray-core/infra/conf/serial"
)

// ImportOutbounds imports node data only. Full configuration sections are rejected,
// never silently discarded or allowed to install listeners/env/log file paths.
func ImportOutbounds(text string) (ImportResult, error) {
	result := ImportResult{Profiles: []Profile{}}
	if len(text) > MaxImport {
		return result, errors.New("import_too_large")
	}
	var value any
	decoder := json.NewDecoder(strings.NewReader(text))
	if decoder.Decode(&value) != nil {
		return result, fail()
	}
	if decoder.Decode(new(any)) != io.EOF {
		return result, fail()
	}
	var candidates []any
	switch v := value.(type) {
	case []any:
		candidates = v
	case map[string]any:
		if out, ok := v["outbounds"]; ok {
			if len(v) != 1 {
				return result, errors.New("unsafe_config")
			}
			var valid bool
			candidates, valid = out.([]any)
			if !valid {
				return result, fail()
			}
		} else {
			candidates = []any{v}
		}
	default:
		return result, fail()
	}
	if len(candidates) > 2048 {
		return result, errors.New("import_too_large")
	}
	seen := map[string]bool{}
	for _, candidate := range candidates {
		o, ok := candidate.(map[string]any)
		if !ok || validateOutbound(o) != nil {
			result.Rejected++
			continue
		}
		name := str(o, "tag")
		if name == "" {
			name = str(o, "protocol")
		}
		if len(name) > 200 {
			result.Rejected++
			continue
		}
		o["tag"] = "proxy"
		profile := Profile{ID: identity(o), Name: name, Outbound: o, Enabled: true}
		settings := Defaults()
		settings.Mode = "proxy"
		config, e := Build(profile, settings, "windows")
		if e == nil {
			_, e = serial.LoadJSONConfig(bytes.NewReader(config))
		}
		if e != nil {
			result.Rejected++
			continue
		}
		if !seen[profile.ID] {
			result.Profiles = append(result.Profiles, profile)
			seen[profile.ID] = true
		}
	}
	if len(result.Profiles) == 0 {
		return result, fail()
	}
	return result, nil
}
