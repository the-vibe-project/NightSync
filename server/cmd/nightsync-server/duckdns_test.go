package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestNormalizeDuckDNSName(t *testing.T) {
	for _, input := range []string{"nightsync", "NightSync.duckdns.org"} {
		if got, err := normalizeDuckDNSName(input); err != nil || got != "nightsync" { t.Fatalf("%q: got %q, err %v", input, got, err) }
	}
	for _, input := range []string{"", "https://nightsync.duckdns.org", "a.b", "-bad", "bad-", "name/path"} {
		if _, err := normalizeDuckDNSName(input); err == nil { t.Fatalf("expected %q to be rejected", input) }
	}
}

func TestUpdateDuckDNS(t *testing.T) {
	var gotDomain, gotToken string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotDomain = r.URL.Query().Get("domains")
		gotToken = r.URL.Query().Get("token")
		io.WriteString(w, "OK")
	}))
	defer srv.Close()
	err := updateDuckDNS(context.Background(), srv.Client(), srv.URL, "nightsync.duckdns.org", "secret-token")
	if err != nil { t.Fatal(err) }
	if gotDomain != "nightsync" || gotToken != "secret-token" { t.Fatalf("unexpected query: domain=%q token=%q", gotDomain, gotToken) }
}

func TestUpdateDuckDNSRejectsBadResponse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, "KO") }))
	defer srv.Close()
	err := updateDuckDNS(context.Background(), srv.Client(), srv.URL, "nightsync", "secret-token")
	if err == nil || !strings.Contains(err.Error(), "rejected") { t.Fatalf("expected rejection, got %v", err) }
}

func TestUpdateDuckDNSRequiresToken(t *testing.T) {
	err := updateDuckDNS(context.Background(), http.DefaultClient, "https://example.invalid/update", "nightsync", "")
	if err == nil || !strings.Contains(err.Error(), "DUCKDNS_TOKEN") { t.Fatalf("expected missing-token error, got %v", err) }
}
