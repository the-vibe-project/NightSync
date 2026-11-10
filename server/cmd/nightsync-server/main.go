package main

import (
    "encoding/json"
    "log"
    "net/http"
    "runtime"
    "time"
)

var startedAt = time.Now()

type healthResponse struct {
    Name       string `json:"name"`
    Status     string `json:"status"`
    Version    string `json:"version"`
    Time       string `json:"time"`
    UptimeSecs int64  `json:"uptime_seconds"`
    Goroutines int    `json:"goroutines"`
    GoVersion  string `json:"go_version"`
}

func main() {
    mux := http.NewServeMux()
    mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodGet {
            http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
            return
        }
        w.Header().Set("Content-Type", "application/json")
        w.Header().Set("Cache-Control", "no-store")
        _ = json.NewEncoder(w).Encode(healthResponse{
            Name: "NightSync Server", Status: "development", Version: "0.3.0",
            Time: time.Now().UTC().Format(time.RFC3339),
            UptimeSecs: int64(time.Since(startedAt).Seconds()),
            Goroutines: runtime.NumGoroutine(), GoVersion: runtime.Version(),
        })
    })

    // Intentionally localhost-only until authentication, TLS and authorization are implemented.
    addr := "127.0.0.1:8787"
    log.Printf("NightSync Server 0.3.0 starting")
    log.Printf("Health endpoint: http://%s/health", addr)
    log.Printf("WARNING: development mode; loopback only; do not expose to LAN or internet")
    log.Fatal(http.ListenAndServe(addr, mux))
}
