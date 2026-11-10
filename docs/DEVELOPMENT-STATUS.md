# NightSync v0.2 development status

## Implemented in this milestone
- Windows Desktop: save multiple HTTPS server profiles locally in the current Windows user's AppData.
- Windows Desktop: request the `/health` endpoint and display basic reachability status.
- Windows Desktop: retain a visible future server-host setup option.
- Android: save an HTTPS server address in app preferences.
- Android: request `/health` and display basic reachability status.
- Files, Photos and Storage navigation placeholders retained.

## Not yet implemented
- Server authentication, pairing and account creation.
- File browsing, uploads, downloads and sync.
- Camera backup.
- Storage metrics and NAS disk management.
- Windows server service, SMB, media server and backup engine.

## Important connection limitation
The current Go development server listens on 127.0.0.1:8787 only. It is intentionally loopback-only and is not reachable from another device. The client health check expects an HTTPS endpoint at `/health`. A secure server endpoint, TLS setup and LAN/remote access will be implemented before end-to-end device connection is enabled.

Never expose the development server to the internet.
