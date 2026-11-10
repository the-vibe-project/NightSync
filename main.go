package main

import (
 "context"
 "database/sql"
 "encoding/json"
 "log"
 "net/http"
 "os"
 "os/signal"
 "path/filepath"
 "strings"
 "syscall"
 "time"
 _ "modernc.org/sqlite"
)

type Health struct {
 Status string ` + "`json:"status"`" + `
 Database string ` + "`json:"database"`" + `
 Mode string ` + "`json:"mode"`" + `
 Version string ` + "`json:"version"`" + `
}

func main() {
 addr := env("NIGHTSYNC_ADDR", "127.0.0.1:8765")
 if !strings.HasPrefix(addr, "127.0.0.1:") && !strings.HasPrefix(addr, "localhost:") { log.Fatal("development engine only permits localhost binding") }
 dataDir := env("NIGHTSYNC_DATA_DIR", filepath.Join(os.Getenv("LOCALAPPDATA"), "NightSync", "data"))
 if err := os.MkdirAll(dataDir, 0700); err != nil { log.Fatal(err) }
 db, err := sql.Open("sqlite", filepath.Join(dataDir, "nightsync.db")); if err != nil { log.Fatal(err) }
 defer db.Close(); db.SetMaxOpenConns(1)
 ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second); defer cancel()
 if _, err := db.ExecContext(ctx, "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;"); err != nil { log.Fatal(err) }
 if err := migrate(ctx, db); err != nil { log.Fatal(err) }

 mux := http.NewServeMux()
 mux.HandleFunc("GET /api/v1/health", func(w http.ResponseWriter, r *http.Request) {
  c, stop := context.WithTimeout(r.Context(), 2*time.Second); defer stop()
  if err := db.PingContext(c); err != nil { respond(w, 503, Health{"degraded","unavailable","host","0.1.0"}); return }
  respond(w, 200, Health{"ok","connected","host","0.1.0"})
 })
 mux.HandleFunc("GET /api/v1/server/info", func(w http.ResponseWriter, r *http.Request) {
  respond(w, 200, map[string]any{"product":"NightSync","apiVersion":"v1","capabilities":[]string{"health"},"stage":"windows-foundation"})
 })
 server := &http.Server{Addr:addr, Handler:headers(mux), ReadHeaderTimeout:5*time.Second, ReadTimeout:15*time.Second, WriteTimeout:15*time.Second, IdleTimeout:60*time.Second}
 stop := make(chan os.Signal, 1); signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
 go func(){ <-stop; c, done := context.WithTimeout(context.Background(), 8*time.Second); defer done(); _ = server.Shutdown(c) }()
 log.Printf("NightSync engine 0.1.0 listening on %s", addr)
 if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed { log.Fatal(err) }
}

func migrate(ctx context.Context, db *sql.DB) error {
 schema := `
 CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
 CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('owner','admin','group_manager','member')), status TEXT NOT NULL CHECK(status IN ('invited','active','suspended','deleted')), quota_bytes INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
 CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), device_name TEXT NOT NULL, platform TEXT NOT NULL, last_seen_at TEXT, revoked_at TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
 CREATE TABLE IF NOT EXISTS files(id TEXT PRIMARY KEY, owner_user_id TEXT NOT NULL REFERENCES users(id), parent_id TEXT REFERENCES files(id), display_name TEXT NOT NULL, storage_key TEXT NOT NULL UNIQUE, mime_type TEXT, size_bytes INTEGER NOT NULL DEFAULT 0, content_hash TEXT, is_directory INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted_at TEXT, purge_after TEXT);
 CREATE INDEX IF NOT EXISTS idx_files_owner_parent ON files(owner_user_id,parent_id);
 CREATE INDEX IF NOT EXISTS idx_files_recycle ON files(owner_user_id,deleted_at,purge_after);
 CREATE TABLE IF NOT EXISTS audit_events(id INTEGER PRIMARY KEY AUTOINCREMENT, actor_user_id TEXT REFERENCES users(id), event_type TEXT NOT NULL, target_type TEXT, target_id TEXT, occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, metadata_json TEXT);
 INSERT OR IGNORE INTO schema_migrations(version) VALUES (1);`
 _, err := db.ExecContext(ctx, schema); return err
}
func respond(w http.ResponseWriter, status int, v any) { w.Header().Set("Content-Type","application/json; charset=utf-8"); w.Header().Set("Cache-Control","no-store"); w.WriteHeader(status); _ = json.NewEncoder(w).Encode(v) }
func headers(next http.Handler) http.Handler { return http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){ w.Header().Set("X-Content-Type-Options","nosniff"); w.Header().Set("Referrer-Policy","no-referrer"); next.ServeHTTP(w,r) }) }
func env(k, fallback string) string { if v:=os.Getenv(k); v!="" { return v }; return fallback }
