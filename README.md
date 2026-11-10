# NightSync

Free and open-source self-hosted NAS and personal cloud.

**Status: early development prototype. Not production-ready or security-reviewed.**

## v0.4 local server test
- Local-only Go server with real `/health` and `/api/v1/status` endpoints.
- Reports real storage volume capacity, process memory, heap allocation, goroutines and uptime.
- Test file API: list, upload, download and delete files in the configured NightSyncData folder.
- Windows Desktop and Android monitoring UI remain a separate client preview.
- Windows Server build remains separate in GitHub Actions.

## Critical limitations
The server binds only to `127.0.0.1:8787`. No authentication, TLS, user roles, LAN access, remote access, encryption, SMB, media streaming, backup/restore or Windows Service is implemented. Do not expose it to another device or the internet. Do not use it for important data. Client demo metrics remain labelled as demo until the clients are wired to the authenticated server API.
