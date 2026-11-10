package main

import (
	"net/http/httptest"
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
