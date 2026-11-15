package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

func normalizeDuckDNSName(raw string) (string, error) {
	name := strings.TrimSpace(strings.ToLower(raw))
	name = strings.TrimSuffix(name, ".duckdns.org")
	if len(name) > 63 || !regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$`).MatchString(name) {
		return "", errors.New("DuckDNS domain must be a single valid subdomain, such as nightsync")
	}
	return name, nil
}

func updateDuckDNS(ctx context.Context, client *http.Client, endpoint, domain, token string) error {
	name, err := normalizeDuckDNSName(domain)
	if err != nil { return err }
	if strings.TrimSpace(token) == "" { return errors.New("DUCKDNS_TOKEN is required when DuckDNS is enabled") }
	u, err := url.Parse(endpoint)
	if err != nil { return errors.New("invalid DuckDNS endpoint") }
	q := u.Query()
	q.Set("domains", name)
	q.Set("token", token)
	q.Set("verbose", "false")
	u.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil { return errors.New("could not create DuckDNS update request") }
	resp, err := client.Do(req)
	if err != nil { return errors.New("DuckDNS update request failed; check network access") }
	defer resp.Body.Close()
	body, readErr := io.ReadAll(io.LimitReader(resp.Body, 256))
	if readErr != nil { return errors.New("could not read DuckDNS response") }
	if resp.StatusCode != http.StatusOK || strings.TrimSpace(string(body)) != "OK" {
		return fmt.Errorf("DuckDNS rejected the update (HTTP %d); check the hostname and token", resp.StatusCode)
	}
	return nil
}

func startDuckDNSUpdater(domain, token string) {
	if strings.TrimSpace(domain) == "" { return }
	client := &http.Client{Timeout: 15 * time.Second}
	update := func() {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := updateDuckDNS(ctx, client, "https://www.duckdns.org/update", domain, token); err != nil {
			log.Printf("DuckDNS update failed: %v", err)
			return
		}
		log.Printf("DuckDNS hostname updated: %s.duckdns.org", strings.TrimSuffix(domain, ".duckdns.org"))
	}
	update()
	ticker := time.NewTicker(5 * time.Minute)
	go func() {
		defer ticker.Stop()
		for range ticker.C { update() }
	}()
}
