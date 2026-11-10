# NightSync v0.3 — Monitoring dashboard

## Visible in this prototype
- Windows and Android overview dashboards.
- Clearly labelled illustrative demo metrics for server status, CPU, memory, storage, connected devices and uptime.
- Member and Admin preview roles. Member view is read-only; Admin sees a demo diagnostics action.
- Connection profiles and HTTPS `/health` test remain available on both clients.
- Storage, Files, Photos and Server Logs navigation previews.

## Critical distinction
Dashboard metrics marked DEMO are sample values, not live measurements. The role selector is a UI preview only, not an authentication or security boundary. Real permissions must be enforced by the server API. The demo diagnostics button changes no server configuration.

## Server
- `/health` returns real process uptime, goroutine count and Go runtime version.
- The server listens on 127.0.0.1:8787 only.
- Server logs are written to the console.
- It is not yet a Windows Service and cannot be monitored from another device with this loopback-only listener.

## Next implementation
1. Implement secure server identity and authentication.
2. Add an authenticated read-only status endpoint with actual CPU, memory, disk and service metrics.
3. Enforce Member/Admin permissions on every server endpoint.
4. Add TLS and explicit LAN binding before cross-device monitoring.
5. Implement admin actions (restart service, maintenance mode, backup jobs) with audit logs and confirmation.
