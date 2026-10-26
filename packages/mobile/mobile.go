// Package engine provides the small gomobile-compatible boundary to RADVPN.
package engine

import shared "radvpn/packages/engine"

type Protector interface{ Protect(fd int64) bool }

func SetProtector(p Protector)     { shared.SetProtector(p) }
func Invoke(request string) string { return shared.Invoke(request) }
