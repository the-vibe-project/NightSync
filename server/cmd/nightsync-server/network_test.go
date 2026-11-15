package main

import "testing"

func TestValidateListenerConfig(t *testing.T) {
	tests := []struct {
		name       string
		addr       string
		cert       string
		key        string
		allow      bool
		wantTLS    bool
		wantErr    bool
	}{
		{name: "default loopback remains HTTP", addr: "127.0.0.1:8787"},
		{name: "localhost TLS is allowed", addr: "127.0.0.1:9443", cert: "server.crt", key: "server.key", wantTLS: true},
		{name: "remote requires opt in", addr: "192.168.1.20:9443", cert: "server.crt", key: "server.key", wantErr: true},
		{name: "remote requires TLS", addr: "192.168.1.20:9443", allow: true, wantErr: true},
		{name: "remote TLS allowed on concrete interface", addr: "192.168.1.20:9443", cert: "server.crt", key: "server.key", allow: true, wantTLS: true},
		{name: "wildcard rejected", addr: "0.0.0.0:9443", cert: "server.crt", key: "server.key", allow: true, wantErr: true},
		{name: "IPv6 wildcard rejected", addr: "[::]:9443", cert: "server.crt", key: "server.key", allow: true, wantErr: true},
		{name: "hostname rejected", addr: "nightsync.example.com:9443", cert: "server.crt", key: "server.key", allow: true, wantErr: true},
		{name: "partial certificate rejected", addr: "127.0.0.1:9443", cert: "server.crt", wantErr: true},
		{name: "invalid port rejected", addr: "192.168.1.20:99999", cert: "server.crt", key: "server.key", allow: true, wantErr: true},
		{name: "multicast rejected", addr: "224.0.0.1:9443", cert: "server.crt", key: "server.key", allow: true, wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			gotTLS, err := validateListenerConfig(tt.addr, tt.cert, tt.key, tt.allow)
			if (err != nil) != tt.wantErr {
				t.Fatalf("validateListenerConfig() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err == nil && gotTLS != tt.wantTLS {
				t.Fatalf("validateListenerConfig() TLS = %v, want %v", gotTLS, tt.wantTLS)
			}
		})
	}
}
