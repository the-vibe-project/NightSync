package main

import (
    "encoding/json"
    "log"
    "net/http"
    "time"
)

type healthResponse struct {
    Name    string `json:"name"`
    Status  string `json:"status"`
    Version string `json:"version"`
    Time    string `json:"time"`
}

func main() {
    mux := http.NewServeMux()
    mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodGet {
            http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
            return
        }
        w.Header().Set("Content-Type", "application/json")
        _ = json.NewEncoder(w).Encode(healthResponse{
            Name: "NightSync Server", Status: "development-scaffold",
            Version: "0.1.0", Time: time.Now().UTC().Format(time.RFC3339),
        })
    })

    // Deliberately loopback-only. Do not change to 0.0.0.0 until
    // authentication, TLS, authorization and security review are complete.
    addr := "127.0.0.1:8787"
    log.Printf("NightSync development health endpoint: http://%s/health", addr)
    log.Fatal(http.ListenAndServe(addr, mux))
}
