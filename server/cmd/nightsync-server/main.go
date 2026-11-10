package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const version = "0.9.0"

var startedAt = time.Now()
var dataDir string
var backupDir string
var syncDir string
var shutdownRequested = make(chan struct{}, 1)

type apiError struct {
	Error string `json:"error"`
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}
func method(w http.ResponseWriter, r *http.Request, allowed string) bool {
	if r.Method != allowed {
		w.Header().Set("Allow", allowed)
		writeJSON(w, http.StatusMethodNotAllowed, apiError{Error: "method not allowed"})
		return false
	}
	return true
}
func safeRelativePath(root, raw string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return root, nil
	}
	if strings.HasPrefix(raw, "/") || strings.ContainsAny(raw, "\\:") || strings.ContainsRune(raw, 0) {
		return "", errors.New("invalid storage path")
	}
	raw = strings.TrimPrefix(raw, "/")
	parts := strings.Split(raw, "/")
	for _, part := range parts {
		if part == "" || part == "." || part == ".." ||
			strings.EqualFold(part, "users.json") || strings.EqualFold(part, "users.json.tmp") ||
			strings.HasPrefix(part, ".upload-") ||
			strings.ContainsAny(part, "<>\"|?*") ||
			strings.HasSuffix(part, ".") || strings.HasSuffix(part, " ") {
			return "", errors.New("invalid storage path")
		}
		base := strings.ToUpper(strings.TrimRight(strings.SplitN(part, ".", 2)[0], " "))
		switch base {
		case "CON", "PRN", "AUX", "NUL",
			"COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
			"LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9":
			return "", errors.New("invalid storage path")
		}
	}
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	target := filepath.Join(rootAbs, filepath.FromSlash(strings.Join(parts, "/")))
	rel, err := filepath.Rel(rootAbs, target)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) || filepath.IsAbs(rel) {
		return "", errors.New("storage path is outside the user folder")
	}

	current := rootAbs
	for _, part := range parts {
		current = filepath.Join(current, part)
		info, statErr := os.Lstat(current)
		if statErr != nil {
			if os.IsNotExist(statErr) {
				continue
			}
			return "", statErr
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return "", errors.New("symbolic links are not allowed in cloud storage")
		}
	}
	return target, nil
}

func requestFileRoot(r *http.Request) (string, error) {
	if auth == nil {
		return "", errors.New("authentication is not ready")
	}
	user, ok := auth.requestUser(r)
	if !ok {
		return "", errors.New("authentication required")
	}
	root := filepath.Join(dataDir, "users", strings.ToLower(user.Username))
	if err := os.MkdirAll(root, 0700); err != nil {
		return "", fmt.Errorf("cannot prepare private storage: %w", err)
	}
	return root, nil
}

func relativeStoragePath(root, target string) string {
	rel, err := filepath.Rel(root, target)
	if err != nil || rel == "." {
		return ""
	}
	return filepath.ToSlash(rel)
}

func filesHandler(w http.ResponseWriter, r *http.Request) {
	root, err := requestFileRoot(r)
	if err != nil {
		writeJSON(w, 401, apiError{Error: "authentication required"})
		return
	}

	switch r.Method {
	case http.MethodGet:
		folder, err := safeRelativePath(root, r.URL.Query().Get("path"))
		if err != nil {
			writeJSON(w, 400, apiError{Error: "invalid folder path"})
			return
		}
		info, err := os.Stat(folder)
		if err != nil || !info.IsDir() {
			writeJSON(w, 404, apiError{Error: "folder not found"})
			return
		}
		entries, err := os.ReadDir(folder)
		if err != nil {
			writeJSON(w, 500, apiError{Error: "could not read folder"})
			return
		}
		items := make([]map[string]any, 0, len(entries))
		for _, entry := range entries {
			entryInfo, statErr := os.Lstat(filepath.Join(folder, entry.Name()))
			if statErr != nil || entryInfo.Mode()&os.ModeSymlink != 0 {
				continue
			}
			itemPath := filepath.Join(folder, entry.Name())
			item := map[string]any{
				"name": entry.Name(),
				"path": relativeStoragePath(root, itemPath),
				"is_dir": entryInfo.IsDir(),
				"size_bytes": int64(0),
				"modified": entryInfo.ModTime().UTC().Format(time.RFC3339),
			}
			if !entryInfo.IsDir() && entryInfo.Mode().IsRegular() {
				item["size_bytes"] = entryInfo.Size()
			} else if !entryInfo.IsDir() {
				continue
			}
			items = append(items, item)
		}
		writeJSON(w, 200, map[string]any{
			"items": items,
			"count": len(items),
			"path": relativeStoragePath(root, folder),
			"storage_scope": "private-user",
		})

	case http.MethodPut:
		rel := r.URL.Query().Get("path")
		target, err := safeRelativePath(root, rel)
		if err != nil || target == root || strings.TrimSpace(rel) == "" {
			writeJSON(w, 400, apiError{Error: "choose a valid file name and folder"})
			return
		}
		if r.ContentLength > 100*1024*1024 {
			writeJSON(w, 413, apiError{Error: "maximum upload size is 100 MB"})
			return
		}
		parent := filepath.Dir(target)
		parentInfo, statErr := os.Stat(parent)
		if statErr != nil || !parentInfo.IsDir() {
			writeJSON(w, 404, apiError{Error: "destination folder not found"})
			return
		}
		if _, statErr := os.Lstat(target); statErr == nil {
			writeJSON(w, 409, apiError{Error: "a file or folder with that name already exists"})
			return
		} else if !os.IsNotExist(statErr) {
			writeJSON(w, 500, apiError{Error: "could not check destination"})
			return
		}
		tmp, err := os.CreateTemp(parent, ".upload-*")
		if err != nil {
			writeJSON(w, 500, apiError{Error: "could not create temporary upload"})
			return
		}
		defer os.Remove(tmp.Name())
		written, copyErr := io.Copy(tmp, http.MaxBytesReader(w, r.Body, 100*1024*1024))
		if copyErr != nil {
			_ = tmp.Close()
			writeJSON(w, 413, apiError{Error: "upload failed or exceeded 100 MB"})
			return
		}
		if err = tmp.Close(); err != nil {
			writeJSON(w, 500, apiError{Error: "could not finish upload"})
			return
		}
		if err = os.Rename(tmp.Name(), target); err != nil {
			writeJSON(w, 500, apiError{Error: "could not save uploaded file"})
			return
		}
		log.Printf("file uploaded by user: path=%s bytes=%d", relativeStoragePath(root, target), written)
		writeJSON(w, 201, map[string]any{"saved": true, "path": relativeStoragePath(root, target), "size_bytes": written})

	default:
		w.Header().Set("Allow", "GET, PUT")
		writeJSON(w, 405, apiError{Error: "method not allowed"})
	}
}

func fileHandler(w http.ResponseWriter, r *http.Request) {
	root, err := requestFileRoot(r)
	if err != nil {
		writeJSON(w, 401, apiError{Error: "authentication required"})
		return
	}
	rel := r.URL.Query().Get("path")
	if rel == "" {
		rel = strings.TrimPrefix(r.URL.Path, "/api/v1/files/")
	}
	target, err := safeRelativePath(root, rel)
	if err != nil || target == root {
		writeJSON(w, 400, apiError{Error: "invalid file path"})
		return
	}
	info, statErr := os.Lstat(target)
	if statErr != nil {
		http.NotFound(w, r)
		return
	}
	if info.Mode()&os.ModeSymlink != 0 || info.IsDir() || !info.Mode().IsRegular() {
		writeJSON(w, 400, apiError{Error: "path is not a regular file"})
		return
	}

	switch r.Method {
	case http.MethodGet:
		f, err := os.Open(target)
		if err != nil {
			http.NotFound(w, r)
			return
		}
		defer f.Close()
		name := filepath.Base(target)
		disposition := mime.FormatMediaType("attachment", map[string]string{"filename": name})
		w.Header().Set("Content-Disposition", disposition)
		http.ServeContent(w, r, name, info.ModTime(), f)
	case http.MethodDelete:
		if err := os.Remove(target); err != nil {
			writeJSON(w, 500, apiError{Error: "could not delete file"})
			return
		}
		log.Printf("file deleted by user: path=%s", relativeStoragePath(root, target))
		writeJSON(w, 200, map[string]any{"deleted": true, "path": relativeStoragePath(root, target)})
	default:
		w.Header().Set("Allow", "GET, DELETE")
		writeJSON(w, 405, apiError{Error: "method not allowed"})
	}
}

func foldersHandler(w http.ResponseWriter, r *http.Request) {
	root, err := requestFileRoot(r)
	if err != nil {
		writeJSON(w, 401, apiError{Error: "authentication required"})
		return
	}

	switch r.Method {
	case http.MethodPost:
		var request struct {
			Path string `json:"path"`
		}
		if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&request) != nil {
			writeJSON(w, 400, apiError{Error: "invalid folder request"})
			return
		}
		target, err := safeRelativePath(root, request.Path)
		if err != nil || target == root {
			writeJSON(w, 400, apiError{Error: "enter a valid folder name"})
			return
		}
		parentInfo, statErr := os.Stat(filepath.Dir(target))
		if statErr != nil || !parentInfo.IsDir() {
			writeJSON(w, 404, apiError{Error: "parent folder not found"})
			return
		}
		if err := os.Mkdir(target, 0700); err != nil {
			if os.IsExist(err) {
				writeJSON(w, 409, apiError{Error: "a file or folder with that name already exists"})
			} else {
				writeJSON(w, 500, apiError{Error: "could not create folder"})
			}
			return
		}
		writeJSON(w, 201, map[string]any{"created": true, "path": relativeStoragePath(root, target)})

	case http.MethodDelete:
		target, err := safeRelativePath(root, r.URL.Query().Get("path"))
		if err != nil || target == root {
			writeJSON(w, 400, apiError{Error: "invalid folder path"})
			return
		}
		info, statErr := os.Lstat(target)
		if statErr != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
			writeJSON(w, 404, apiError{Error: "folder not found"})
			return
		}
		if err := os.Remove(target); err != nil {
			writeJSON(w, 409, apiError{Error: "folder is not empty; delete its contents first"})
			return
		}
		writeJSON(w, 200, map[string]any{"deleted": true, "path": relativeStoragePath(root, target)})

	default:
		w.Header().Set("Allow", "POST, DELETE")
		writeJSON(w, 405, apiError{Error: "method not allowed"})
	}
}
func main() {
	isService, serviceErr := runAsWindowsService()
	if serviceErr != nil {
		log.Printf("cannot start Windows service host: %v", serviceErr)
		os.Exit(1)
	}
	if isService {
		return
	}
	if err := runServer(); err != nil {
		log.Fatal(err)
	}
}

func runServer() error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	defaultData := filepath.Join(filepath.Dir(exe), "NightSyncData")
	storageFlag := flag.String("storage", defaultData, "Primary NightSync file storage directory")
	backupFlag := flag.String("backup", "", "Configured backup destination (backup jobs are not active in this build)")
	syncFlag := flag.String("sync", "", "Configured sync destination (sync jobs are not active in this build)")
	logFileFlag := flag.String("log-file", "", "Optional log file path")
	addrFlag := flag.String("addr", "127.0.0.1:8787", "HTTP listen address; development builds must remain loopback-only")
	flag.Parse()
	dataDir, err = filepath.Abs(*storageFlag)
	if err != nil {
		return err
	}
	backupDir = *backupFlag
	syncDir = *syncFlag
	if *addrFlag != "127.0.0.1:8787" && *addrFlag != "localhost:8787" {
		return errors.New("this development build is restricted to localhost; remote/LAN binding is disabled")
	}
	if err = os.MkdirAll(dataDir, 0700); err != nil {
		return fmt.Errorf("cannot create storage folder: %w", err)
	}
	if *logFileFlag != "" {
		if err = os.MkdirAll(filepath.Dir(*logFileFlag), 0700); err != nil {
			return fmt.Errorf("cannot create log directory: %w", err)
		}
		lf, openErr := os.OpenFile(*logFileFlag, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
		if openErr != nil {
			return fmt.Errorf("cannot open log file: %w", openErr)
		}
		defer lf.Close()
		log.SetOutput(lf)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodGet) {
			return
		}
		writeJSON(w, 200, map[string]any{"name": "NightSync Server", "status": "online", "version": version,
			"time": time.Now().UTC().Format(time.RFC3339), "uptime_seconds": int64(time.Since(startedAt).Seconds())})
	})
	mux.HandleFunc("/api/v1/status", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodGet) {
			return
		}
		var m runtime.MemStats
		runtime.ReadMemStats(&m)
		total, free, err := diskSpace(dataDir)
		storage := map[string]any{"path": dataDir, "total_bytes": total, "free_bytes": free, "used_bytes": total - free}
		if err != nil {
			storage["warning"] = err.Error()
		}
		writeJSON(w, 200, map[string]any{"name": "NightSync Server", "status": "online", "version": version,
			"time": time.Now().UTC().Format(time.RFC3339), "uptime_seconds": int64(time.Since(startedAt).Seconds()),
			"process_memory_bytes": m.Sys, "heap_alloc_bytes": m.HeapAlloc, "goroutines": runtime.NumGoroutine(),
			"go_version": runtime.Version(), "storage": storage, "mode": "local-development",
			"destinations": map[string]any{"backup_path": backupDir, "sync_path": syncDir,
				"backup_state": "configured-not-active", "sync_state": "configured-not-active"}})
	})
	mux.HandleFunc("/api/v1/files", filesHandler)
	mux.HandleFunc("/api/v1/files/", fileHandler)
	mux.HandleFunc("/api/v1/file", fileHandler)
	mux.HandleFunc("/api/v1/folders", foldersHandler)

	auth, err = newAuthStore(dataDir)
	if err != nil {
		return fmt.Errorf("cannot load users: %w", err)
	}
	authRoutes(mux)
	protected := http.NewServeMux()
	protected.Handle("/api/v1/status", authMiddleware(mux))
	protected.Handle("/api/v1/files", authMiddleware(mux))
	protected.Handle("/api/v1/files/", authMiddleware(mux))
	protected.Handle("/api/v1/file", authMiddleware(mux))
	protected.Handle("/api/v1/folders", authMiddleware(mux))
	protected.Handle("/api/v1/logout", authMiddleware(mux))
	protected.Handle("/api/v1/setup", mux)
	protected.Handle("/api/v1/login", mux)
	protected.Handle("/api/v1/me", mux)
	protected.Handle("/api/v1/change-password", mux)
	protected.Handle("/api/v1/admin/users", mux)
	protected.Handle("/api/v1/admin/users/", mux)
	protected.Handle("/api/v1/admin/server/stop", mux)
	protected.Handle("/health", mux)

	addr := "127.0.0.1:8787"
	srv := &http.Server{Addr: addr, Handler: protected, ReadHeaderTimeout: 5 * time.Second}
	log.Printf("NightSync Server %s starting", version)
	log.Printf("Primary storage: %s", dataDir)
	log.Printf("Backup destination (configured only): %s", backupDir)
	log.Printf("Sync destination (configured only): %s", syncDir)
	log.Printf("Health: http://%s/health", addr)
	log.Printf("WARNING: localhost-only development build; do not expose to LAN/internet")
	go func() {
		<-shutdownRequested
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(ctx)
	}()
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return err
	}
	log.Printf("NightSync Server stopped")
	return nil
}
