package main

import (
	"net/http/httptest"
	"testing"
	"time"
)

func TestLoginRateLimiterBlocksAfterFailureLimit(t *testing.T) {
	limiter := newLoginRateLimiter()
	now := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	limiter.now = func() time.Time { return now }

	for i := 0; i < loginFailureLimit; i++ {
		if allowed, _ := limiter.allowed("192.0.2.10"); !allowed {
			t.Fatalf("attempt %d should still be allowed before recording failure", i+1)
		}
		limiter.recordFailure("192.0.2.10")
	}
	if allowed, retry := limiter.allowed("192.0.2.10"); allowed || retry != loginWindow {
		t.Fatalf("expected block for remaining window, got allowed=%v retry=%v", allowed, retry)
	}
	if allowed, _ := limiter.allowed("192.0.2.11"); !allowed {
		t.Fatal("rate limit for one client affected another client")
	}
}

func TestLoginRateLimiterExpiresAndClears(t *testing.T) {
	limiter := newLoginRateLimiter()
	now := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	limiter.now = func() time.Time { return now }

	for i := 0; i < loginFailureLimit; i++ {
		limiter.recordFailure("192.0.2.10")
	}
	limiter.clear("192.0.2.10")
	if allowed, _ := limiter.allowed("192.0.2.10"); !allowed {
		t.Fatal("cleared client should be allowed")
	}

	for i := 0; i < loginFailureLimit; i++ {
		limiter.recordFailure("192.0.2.10")
	}
	now = now.Add(loginWindow)
	if allowed, _ := limiter.allowed("192.0.2.10"); !allowed {
		t.Fatal("expired rate-limit window should allow a new attempt")
	}
}

func TestClientAddressUsesRemoteAddrAndIgnoresForwardedHeaders(t *testing.T) {
	request := httptest.NewRequest("POST", "/api/v1/login", nil)
	request.RemoteAddr = "192.0.2.20:54321"
	request.Header.Set("X-Forwarded-For", "203.0.113.99")
	if got := clientAddress(request); got != "192.0.2.20" {
		t.Fatalf("expected trusted socket address, got %q", got)
	}
}
