package main

import (
	"errors"
	"net"
	"strconv"
	"strings"
)

// validateListenerConfig keeps the default development server loopback-only.
// A non-loopback listener is permitted only when the operator explicitly opts
// in, binds one concrete unicast IP address, and supplies a TLS certificate.
func validateListenerConfig(addr, certFile, keyFile string, allowRemote bool) (bool, error) {
	host, portText, err := net.SplitHostPort(strings.TrimSpace(addr))
	if err != nil {
		return false, errors.New("listen address must be an IP address and port")
	}
	port, err := strconv.Atoi(portText)
	if err != nil || port < 1 || port > 65535 {
		return false, errors.New("listen port must be between 1 and 65535")
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return false, errors.New("listen host must be a literal IP address; hostnames are not accepted")
	}

	hasCert, hasKey := strings.TrimSpace(certFile) != "", strings.TrimSpace(keyFile) != ""
	if hasCert != hasKey {
		return false, errors.New("both --tls-cert and --tls-key must be supplied together")
	}
	if ip.IsLoopback() {
		if allowRemote && !hasCert {
			return false, errors.New("--allow-remote requires a TLS certificate and private key")
		}
		return hasCert, nil
	}
	if !allowRemote {
		return false, errors.New("non-loopback listening requires explicit --allow-remote opt-in")
	}
	if ip.IsUnspecified() || !ip.IsGlobalUnicast() {
		return false, errors.New("remote listening must bind one concrete unicast interface IP, not a wildcard or multicast address")
	}
	if !hasCert {
		return false, errors.New("remote listening requires --tls-cert and --tls-key; plaintext remote access is disabled")
	}
	return true, nil
}
