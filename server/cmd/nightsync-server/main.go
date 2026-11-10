package main

import (
    "encoding/json"
    "errors"
    "io"
    "log"
    "net/http"
    "os"
    "path/filepath"
    "runtime"
    "strings"
    "time"
)

const version = "0.4.0"
var startedAt = time.Now()
var dataDir string

type apiError struct { Error string `json:"error"` }

func writeJSON(w http.ResponseWriter, code int, v any) {
    w.Header().Set("Content-Type", "application/json")
    w.Header().Set("Cache-Control", "no-store")
    w.WriteHeader(code)
    _ = json.NewEncoder(w).Encode(v)
}
func method(w http.ResponseWriter, r *http.Request, allowed string) bool {
    if r.Method != allowed {
        w.Header().Set("Allow", allowed)
        writeJSON(w, http.StatusMethodNotAllowed, apiError{Error:"method not allowed"})
        return false
    }
    return true
}
func safeName(name string) (string, error) {
    name = strings.TrimSpace(name)
    if name == "" || name == "." || name == ".." || strings.ContainsAny(name, `/\:`) || strings.ContainsRune(name, 0) {
        return "", errors.New("invalid file name; use a simple file name without folders")
    }
    return name, nil
}
func filesHandler(w http.ResponseWriter, r *http.Request) {
    switch r.Method {
    case http.MethodGet:
        entries, err := os.ReadDir(dataDir)
        if err != nil { writeJSON(w, 500, apiError{Error:"could not read storage directory"}); return }
        files := make([]map[string]any, 0, len(entries))
        for _, e := range entries {
            if e.IsDir() { continue }
            info, err := e.Info(); if err != nil { continue }
            files = append(files, map[string]any{
                "name": e.Name(), "size_bytes": info.Size(), "modified": info.ModTime().UTC().Format(time.RFC3339),
            })
        }
        writeJSON(w, 200, map[string]any{"items":files, "count":len(files)})
    case http.MethodPut:
        name, err := safeName(r.URL.Query().Get("name"))
        if err != nil { writeJSON(w, 400, apiError{Error:err.Error()}); return }
        if r.ContentLength > 50*1024*1024 { writeJSON(w, 413, apiError{Error:"maximum test upload size is 50 MB"}); return }
        dst := filepath.Join(dataDir, name)
        tmp, err := os.CreateTemp(dataDir, ".upload-*")
        if err != nil { writeJSON(w, 500, apiError{Error:"could not create temporary upload"}); return }
        defer os.Remove(tmp.Name())
        if _, err = io.Copy(tmp, http.MaxBytesReader(w, r.Body, 50*1024*1024)); err != nil {
            _ = tmp.Close(); writeJSON(w, 413, apiError{Error:"upload failed or exceeded 50 MB"}); return
        }
        if err = tmp.Close(); err != nil { writeJSON(w, 500, apiError{Error:"could not finish upload"}); return }
        if err = os.Rename(tmp.Name(), dst); err != nil { writeJSON(w, 500, apiError{Error:"could not save file"}); return }
        log.Printf("file uploaded: %s", name)
        writeJSON(w, 201, map[string]any{"saved":true,"name":name})
    default:
        w.Header().Set("Allow", "GET, PUT")
        writeJSON(w, 405, apiError{Error:"method not allowed"})
    }
}
func fileHandler(w http.ResponseWriter, r *http.Request) {
    name, err := safeName(strings.TrimPrefix(r.URL.Path, "/api/v1/files/"))
    if err != nil { writeJSON(w, 400, apiError{Error:err.Error()}); return }
    path := filepath.Join(dataDir, name)
    switch r.Method {
    case http.MethodGet:
        f, err := os.Open(path)
        if err != nil { http.NotFound(w,r); return }
        defer f.Close()
        w.Header().Set("Content-Disposition", `attachment; filename="`+name+`"`)
        http.ServeContent(w,r,name,time.Time{},f)
    case http.MethodDelete:
        if err := os.Remove(path); err != nil {
            if os.IsNotExist(err) { http.NotFound(w,r) } else { writeJSON(w,500,apiError{Error:"delete failed"}) }
            return
        }
        log.Printf("file deleted: %s", name)
        writeJSON(w,200,map[string]any{"deleted":true,"name":name})
    default:
        w.Header().Set("Allow","GET, DELETE")
        writeJSON(w,405,apiError{Error:"method not allowed"})
    }
}
func main() {
    if len(os.Args) > 1 { dataDir = os.Args[1] } else {
        exe, err := os.Executable()
        if err != nil { log.Fatal(err) }
        dataDir = filepath.Join(filepath.Dir(exe), "NightSyncData")
    }
    abs, err := filepath.Abs(dataDir); if err != nil { log.Fatal(err) }
    dataDir = abs
    if err := os.MkdirAll(dataDir, 0700); err != nil { log.Fatalf("cannot create data folder: %v",err) }

    mux := http.NewServeMux()
    mux.HandleFunc("/health", func(w http.ResponseWriter,r *http.Request) {
        if !method(w,r,http.MethodGet) { return }
        writeJSON(w,200,map[string]any{
            "name":"NightSync Server","status":"online","version":version,
            "time":time.Now().UTC().Format(time.RFC3339),
            "uptime_seconds":int64(time.Since(startedAt).Seconds()),
        })
    })
    mux.HandleFunc("/api/v1/status", func(w http.ResponseWriter,r *http.Request) {
        if !method(w,r,http.MethodGet) { return }
        var m runtime.MemStats; runtime.ReadMemStats(&m)
        total, free, err := diskSpace(dataDir)
        storage := map[string]any{"path":dataDir,"total_bytes":total,"free_bytes":free,"used_bytes":total-free}
        if err != nil { storage["warning"] = err.Error() }
        writeJSON(w,200,map[string]any{
            "name":"NightSync Server","status":"online","version":version,
            "time":time.Now().UTC().Format(time.RFC3339),
            "uptime_seconds":int64(time.Since(startedAt).Seconds()),
            "process_memory_bytes":m.Sys,"heap_alloc_bytes":m.HeapAlloc,
            "goroutines":runtime.NumGoroutine(),"go_version":runtime.Version(),
            "storage":storage,"mode":"local-development",
        })
    })
    mux.HandleFunc("/api/v1/files", filesHandler)
    mux.HandleFunc("/api/v1/files/", fileHandler)

    addr := "127.0.0.1:8787"
    log.Printf("NightSync Server %s starting",version)
    log.Printf("Storage directory: %s",dataDir)
    log.Printf("Health: http://%s/health",addr)
    log.Printf("Status: http://%s/api/v1/status",addr)
    log.Printf("Files:  http://%s/api/v1/files",addr)
    log.Printf("WARNING: local development only; no authentication/TLS; loopback-only")
    log.Fatal(http.ListenAndServe(addr,mux))
}
