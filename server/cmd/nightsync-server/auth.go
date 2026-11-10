package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/bcrypt"
)

type userRecord struct {
	Username           string    `json:"username"`
	Role               string    `json:"role"`
	PasswordHash       string    `json:"password_hash"`
	MustChangePassword bool      `json:"must_change_password"`
	CreatedAt          time.Time `json:"created_at"`
}
type sessionRecord struct {
	Username  string
	ExpiresAt time.Time
}
type authStore struct {
	mu       sync.Mutex
	path     string
	users    map[string]userRecord
	sessions map[string]sessionRecord
}

var auth *authStore

func newAuthStore(dir string) (*authStore, error) {
	a := &authStore{path: filepath.Join(dir, "users.json"), users: map[string]userRecord{}, sessions: map[string]sessionRecord{}}
	b, e := os.ReadFile(a.path)
	if e == nil {
		if e = json.Unmarshal(b, &a.users); e != nil {
			return nil, e
		}
	} else if !os.IsNotExist(e) {
		return nil, e
	}
	return a, nil
}
func (a *authStore) saveLocked() error {
	b, e := json.MarshalIndent(a.users, "", "  ")
	if e != nil {
		return e
	}
	tmp := a.path + ".tmp"
	if e = os.WriteFile(tmp, b, 0600); e != nil {
		return e
	}
	return os.Rename(tmp, a.path)
}
func validUsername(s string) bool {
	if len(s) < 3 || len(s) > 32 {
		return false
	}
	for _, r := range s {
		if !(r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '_' || r == '-' || r == '.') {
			return false
		}
	}
	return true
}
func strongPassword(s string) bool { return len(s) >= 12 && len(s) <= 128 }
func randomPassword() (string, error) {
	b := make([]byte, 18)
	if _, e := rand.Read(b); e != nil {
		return "", e
	}
	return "Ns!" + hex.EncodeToString(b) + "a7", nil
}
func (a *authStore) count() int { a.mu.Lock(); defer a.mu.Unlock(); return len(a.users) }
func (a *authStore) create(username, password, role string, must bool) error {
	username = strings.TrimSpace(username)
	if !validUsername(username) {
		return errors.New("username must be 3-32 characters")
	}
	if !strongPassword(password) {
		return errors.New("password must be at least 12 characters")
	}
	if role != "admin" && role != "member" {
		return errors.New("role must be admin or member")
	}
	h, e := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if e != nil {
		return e
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	k := strings.ToLower(username)
	if _, ok := a.users[k]; ok {
		return errors.New("username already exists")
	}
	a.users[k] = userRecord{Username: username, Role: role, PasswordHash: string(h), MustChangePassword: must, CreatedAt: time.Now().UTC()}
	return a.saveLocked()
}
func (a *authStore) createFirstAdmin(username, password string) error {
	username = strings.TrimSpace(username)
	if !validUsername(username) {
		return errors.New("username must be 3-32 characters")
	}
	if !strongPassword(password) {
		return errors.New("password must be at least 12 characters")
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}

	a.mu.Lock()
	defer a.mu.Unlock()
	if len(a.users) != 0 {
		return errors.New("setup already completed")
	}
	key := strings.ToLower(username)
	a.users[key] = userRecord{
		Username: username,
		Role: "admin",
		PasswordHash: string(hash),
		MustChangePassword: false,
		CreatedAt: time.Now().UTC(),
	}
	return a.saveLocked()
}

func (a *authStore) logout(token string) bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	session, ok := a.sessions[token]
	if !ok || time.Now().After(session.ExpiresAt) {
		delete(a.sessions, token)
		return false
	}
	delete(a.sessions, token)
	return true
}

func (a *authStore) login(username, password string) (userRecord, string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	u, ok := a.users[strings.ToLower(strings.TrimSpace(username))]
	if !ok || bcrypt.CompareHashAndPassword([]byte(u.PasswordHash), []byte(password)) != nil {
		return userRecord{}, "", errors.New("invalid username or password")
	}
	b := make([]byte, 32)
	if _, e := rand.Read(b); e != nil {
		return userRecord{}, "", e
	}
	token := hex.EncodeToString(b)
	a.sessions[token] = sessionRecord{Username: strings.ToLower(u.Username), ExpiresAt: time.Now().Add(12 * time.Hour)}
	return u, token, nil
}
func (a *authStore) requestUser(r *http.Request) (userRecord, bool) {
	h := strings.TrimSpace(r.Header.Get("Authorization"))
	if !strings.HasPrefix(h, "Bearer ") {
		return userRecord{}, false
	}
	t := strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
	a.mu.Lock()
	defer a.mu.Unlock()
	s, ok := a.sessions[t]
	if !ok || time.Now().After(s.ExpiresAt) {
		delete(a.sessions, t)
		return userRecord{}, false
	}
	u, ok := a.users[s.Username]
	return u, ok
}
func (a *authStore) change(username, oldp, newp string) error {
	if !strongPassword(newp) {
		return errors.New("new password must be at least 12 characters")
	}
	h, e := bcrypt.GenerateFromPassword([]byte(newp), bcrypt.DefaultCost)
	if e != nil {
		return e
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	k := strings.ToLower(username)
	u, ok := a.users[k]
	if !ok || bcrypt.CompareHashAndPassword([]byte(u.PasswordHash), []byte(oldp)) != nil {
		return errors.New("current password is incorrect")
	}
	u.PasswordHash = string(h)
	u.MustChangePassword = false
	a.users[k] = u
	if e = a.saveLocked(); e != nil {
		return e
	}
	for t, s := range a.sessions {
		if s.Username == k {
			delete(a.sessions, t)
		}
	}
	return nil
}
func (a *authStore) reset(username string) (string, error) {
	p, e := randomPassword()
	if e != nil {
		return "", e
	}
	h, e := bcrypt.GenerateFromPassword([]byte(p), bcrypt.DefaultCost)
	if e != nil {
		return "", e
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	k := strings.ToLower(username)
	u, ok := a.users[k]
	if !ok {
		return "", errors.New("user not found")
	}
	u.PasswordHash = string(h)
	u.MustChangePassword = true
	a.users[k] = u
	if e = a.saveLocked(); e != nil {
		return "", e
	}
	for t, s := range a.sessions {
		if s.Username == k {
			delete(a.sessions, t)
		}
	}
	return p, nil
}
func authRoutes(mux *http.ServeMux) {
	mux.HandleFunc("/api/v1/setup", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		var q struct {
			Username string `json:"username"`
			Password string `json:"password"`
		}
		if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&q) != nil {
			writeJSON(w, 400, apiError{Error: "invalid request"})
			return
		}
		if e := auth.createFirstAdmin(q.Username, q.Password); e != nil {
			if e.Error() == "setup already completed" {
				writeJSON(w, 409, apiError{Error: e.Error()})
			} else {
				writeJSON(w, 400, apiError{Error: e.Error()})
			}
			return
		}
		writeJSON(w, 201, map[string]any{"created": true, "role": "admin"})
	})
	mux.HandleFunc("/api/v1/login", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		var q struct {
			Username string `json:"username"`
			Password string `json:"password"`
		}
		if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&q) != nil {
			writeJSON(w, 400, apiError{Error: "invalid request"})
			return
		}
		u, t, e := auth.login(q.Username, q.Password)
		if e != nil {
			writeJSON(w, 401, apiError{Error: "invalid username or password"})
			return
		}
		writeJSON(w, 200, map[string]any{"token": t, "username": u.Username, "role": u.Role, "must_change_password": u.MustChangePassword})
	})
	mux.HandleFunc("/api/v1/logout", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		header := strings.TrimSpace(r.Header.Get("Authorization"))
		if !strings.HasPrefix(header, "Bearer ") {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		token := strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
		if !auth.logout(token) {
			writeJSON(w, 401, apiError{Error: "session expired"})
			return
		}
		writeJSON(w, 200, map[string]any{"logged_out": true})
	})
	mux.HandleFunc("/api/v1/me", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodGet) {
			return
		}
		u, ok := auth.requestUser(r)
		if !ok {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		writeJSON(w, 200, map[string]any{"username": u.Username, "role": u.Role, "must_change_password": u.MustChangePassword})
	})
	mux.HandleFunc("/api/v1/change-password", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		u, ok := auth.requestUser(r)
		if !ok {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		var q struct {
			Current string `json:"current_password"`
			New     string `json:"new_password"`
		}
		if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&q) != nil {
			writeJSON(w, 400, apiError{Error: "invalid request"})
			return
		}
		if e := auth.change(u.Username, q.Current, q.New); e != nil {
			writeJSON(w, 400, apiError{Error: e.Error()})
			return
		}
		writeJSON(w, 200, map[string]any{"changed": true, "message": "Password changed. Sign in again."})
	})
	mux.HandleFunc("/api/v1/admin/users", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		u, ok := auth.requestUser(r)
		if !ok {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		if u.Role != "admin" {
			writeJSON(w, 403, apiError{Error: "admin permission required"})
			return
		}
		var q struct {
			Username          string `json:"username"`
			Role              string `json:"role"`
			TemporaryPassword string `json:"temporary_password"`
		}
		if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&q) != nil {
			writeJSON(w, 400, apiError{Error: "invalid request"})
			return
		}
		if q.Role == "" {
			q.Role = "member"
		}
		if e := auth.create(q.Username, q.TemporaryPassword, q.Role, true); e != nil {
			writeJSON(w, 400, apiError{Error: e.Error()})
			return
		}
		writeJSON(w, 201, map[string]any{"created": true, "username": q.Username, "role": q.Role, "must_change_password": true})
	})
	mux.HandleFunc("/api/v1/admin/users/", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		u, ok := auth.requestUser(r)
		if !ok {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		if u.Role != "admin" {
			writeJSON(w, 403, apiError{Error: "admin permission required"})
			return
		}
		name := strings.TrimSuffix(strings.TrimPrefix(r.URL.Path, "/api/v1/admin/users/"), "/reset")
		p, e := auth.reset(name)
		if e != nil {
			writeJSON(w, 404, apiError{Error: e.Error()})
			return
		}
		writeJSON(w, 200, map[string]any{"username": name, "temporary_password": p, "must_change_password": true})
	})
	mux.HandleFunc("/api/v1/admin/server/stop", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, http.MethodPost) {
			return
		}
		u, ok := auth.requestUser(r)
		if !ok {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		if u.Role != "admin" {
			writeJSON(w, 403, apiError{Error: "admin permission required"})
			return
		}
		writeJSON(w, 202, map[string]any{"stopping": true, "message": "Server is shutting down"})
		go func() {
			time.Sleep(150 * time.Millisecond)
			select {
			case shutdownRequested <- struct{}{}:
			default:
			}
		}()
	})
}
func authMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		u, ok := auth.requestUser(r)
		if !ok {
			writeJSON(w, 401, apiError{Error: "authentication required"})
			return
		}
		if u.MustChangePassword && r.URL.Path != "/api/v1/change-password" && r.URL.Path != "/api/v1/me" && r.URL.Path != "/api/v1/logout" {
			writeJSON(w, 403, apiError{Error: "password change required"})
			return
		}
		next.ServeHTTP(w, r)
	})
}
