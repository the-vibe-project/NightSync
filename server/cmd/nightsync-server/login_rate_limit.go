package main

import (
	"net"
	"net/http"
	"sync"
	"time"
)

const (
	loginFailureLimit = 10
	loginWindow       = 15 * time.Minute
)

type loginAttempt struct {
	failures  int
	windowEnd time.Time
}

type loginRateLimiter struct {
	mu       sync.Mutex
	attempts map[string]loginAttempt
	now      func() time.Time
}

func newLoginRateLimiter() *loginRateLimiter {
	return &loginRateLimiter{
		attempts: make(map[string]loginAttempt),
		now:      time.Now,
	}
}

func clientAddress(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil {
		return host
	}
	// RemoteAddr is supplied by the HTTP server. Forwarded headers are
	// intentionally ignored because this server has no trusted proxy layer.
	return r.RemoteAddr
}

func (l *loginRateLimiter) allowed(key string) (bool, time.Duration) {
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()

	for candidate, attempt := range l.attempts {
		if !now.Before(attempt.windowEnd) {
			delete(l.attempts, candidate)
		}
	}

	attempt, exists := l.attempts[key]
	if !exists || !now.Before(attempt.windowEnd) {
		return true, 0
	}
	if attempt.failures < loginFailureLimit {
		return true, 0
	}
	return false, attempt.windowEnd.Sub(now)
}

func (l *loginRateLimiter) recordFailure(key string) {
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()

	attempt, exists := l.attempts[key]
	if !exists || !now.Before(attempt.windowEnd) {
		attempt = loginAttempt{windowEnd: now.Add(loginWindow)}
	}
	attempt.failures++
	l.attempts[key] = attempt
}

func (l *loginRateLimiter) clear(key string) {
	l.mu.Lock()
	delete(l.attempts, key)
	l.mu.Unlock()
}
