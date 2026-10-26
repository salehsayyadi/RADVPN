// radcore is an app-owned child process. IPC is inherited stdin/stdout, not an
// unauthenticated network API. EOF always closes the native engine and TUN.
package main

import (
	"bufio"
	"fmt"
	"os"
	"os/signal"
	"radvpn/packages/engine"
	"syscall"
)

func main() {
	defer engine.Stop()
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
	go func() { <-sig; _ = engine.Stop(); os.Exit(0) }()
	s := bufio.NewScanner(os.Stdin)
	s.Buffer(make([]byte, 4096), 4*engine.MaxImport)
	out := bufio.NewWriter(os.Stdout)
	for s.Scan() {
		fmt.Fprintln(out, engine.Invoke(s.Text()))
		_ = out.Flush()
	}
}
