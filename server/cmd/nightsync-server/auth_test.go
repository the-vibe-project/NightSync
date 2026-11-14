package main

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"testing"
)

func TestFirstAdminSetupIsAtomic(t *testing.T) {
	store, err := newAuthStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}

	var successes atomic.Int32
	var wait sync.WaitGroup
	errors := make(chan error, 2)
	for _, username := range []string{"primary", "secondary"} {
		wait.Add(1)
		go func(name string) {
			defer wait.Done()
			err := store.createFirstAdmin(name, "A-Strong-Password-123!")
			if err == nil {
				successes.Add(1)
				return
			}
			errors <- err
		}(username)
	}
	wait.Wait()
	close(errors)

	if successes.Load() != 1 {
		t.Fatalf("expected exactly one initial administrator, got %d", successes.Load())
	}
	for err := range errors {
		if err.Error() != "setup already completed" {
			t.Fatalf("unexpected setup error: %v", err)
		}
	}
	if store.count() != 1 {
		t.Fatalf("expected one user in store, got %d", store.count())
	}

	reloaded, err := newAuthStore(filepath.Dir(store.path))
	if err != nil {
		t.Fatal(err)
	}
	if reloaded.count() != 1 {
		t.Fatalf("expected first admin to persist, got %d users", reloaded.count())
	}
}

func TestLogoutRevokesSession(t *testing.T) {
	store, err := newAuthStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if err := store.createFirstAdmin("owner", "A-Strong-Password-123!"); err != nil {
		t.Fatal(err)
	}
	_, token, err := store.login("owner", "A-Strong-Password-123!")
	if err != nil {
		t.Fatal(err)
	}
	if !store.logout(token) {
		t.Fatal("expected active session to be revoked")
	}

	request := httptest.NewRequest("GET", "/api/v1/me", nil)
	request.Header.Set("Authorization", "Bearer "+token)
	if _, ok := store.requestUser(request); ok {
		t.Fatal("revoked token still authenticated")
	}
	if store.logout(token) {
		t.Fatal("second logout for a revoked token should fail")
	}
}


func TestNullUserDatabaseCanBeInitialized(t *testing.T) {
	directory := t.TempDir()
	if err := os.WriteFile(filepath.Join(directory, "users.json"), []byte("null"), 0600); err != nil {
		t.Fatal(err)
	}
	store, err := newAuthStore(directory)
	if err != nil {
		t.Fatal(err)
	}
	if err := store.createFirstAdmin("owner", "A-Strong-Password-123!"); err != nil {
		t.Fatalf("first admin setup should recover from a null user map: %v", err)
	}
	if store.count() != 1 {
		t.Fatalf("expected one initialized user, got %d", store.count())
	}
}


func TestAccountChangesRollBackWhenDatabaseWriteFails(t *testing.T) {
	store, err := newAuthStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if err := store.createFirstAdmin("owner", "A-Strong-Password-123!"); err != nil {
		t.Fatal(err)
	}
	if err := store.create("member", "Member-Strong-Password-1!", "member", true); err != nil {
		t.Fatal(err)
	}
	originalCount := store.count()
	store.path = filepath.Join(t.TempDir(), "missing-parent", "users.json")

	if err := store.create("another", "Another-Strong-Password-1!", "member", true); err == nil {
		t.Fatal("expected create to fail when the database path is unavailable")
	}
	if store.count() != originalCount {
		t.Fatal("failed user creation remained in memory")
	}

	if err := store.change("owner", "A-Strong-Password-123!", "A-New-Strong-Password-123!"); err == nil {
		t.Fatal("expected password change to fail when the database path is unavailable")
	}
	if _, _, err := store.login("owner", "A-Strong-Password-123!"); err != nil {
		t.Fatalf("old password should remain valid after failed change: %v", err)
	}

	if _, err := store.reset("member"); err == nil {
		t.Fatal("expected password reset to fail when the database path is unavailable")
	}
	if _, _, err := store.login("member", "Member-Strong-Password-1!"); err != nil {
		t.Fatalf("old password should remain valid after failed reset: %v", err)
	}
	member := store.users["member"]
	if !member.MustChangePassword {
		t.Fatal("failed reset changed the user's temporary-password flag")
	}
}


func TestAdminPasswordResetRequiresExactResetRoute(t *testing.T) {
	store, err := newAuthStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if err := store.createFirstAdmin("owner", "A-Strong-Password-123!"); err != nil {
		t.Fatal(err)
	}
	if err := store.create("member", "Member-Strong-Password-1!", "member", true); err != nil {
		t.Fatal(err)
	}
	_, token, err := store.login("owner", "A-Strong-Password-123!")
	if err != nil {
		t.Fatal(err)
	}

	previousAuth := auth
	auth = store
	t.Cleanup(func() { auth = previousAuth })

	mux := http.NewServeMux()
	authRoutes(mux)

	request := httptest.NewRequest(http.MethodPost, "/api/v1/admin/users/member", nil)
	request.Header.Set("Authorization", "Bearer "+token)
	response := httptest.NewRecorder()
	mux.ServeHTTP(response, request)
	if response.Code != http.StatusNotFound {
		t.Fatalf("reset without /reset suffix should be 404, got %d: %s", response.Code, response.Body.String())
	}

	if _, _, err := store.login("member", "Member-Strong-Password-1!"); err != nil {
		t.Fatalf("unexpected password change through malformed route: %v", err)
	}

	request = httptest.NewRequest(http.MethodPost, "/api/v1/admin/users/member/reset", nil)
	request.Header.Set("Authorization", "Bearer "+token)
	response = httptest.NewRecorder()
	mux.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("documented reset route should succeed, got %d: %s", response.Code, response.Body.String())
	}
}
