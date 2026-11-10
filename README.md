# NightSync

Free and open-source self-hosted NAS and personal cloud.

**Status: early development prototype. Not production-ready or security-reviewed.**

## Current v0.3 preview

- Windows Desktop and Android monitoring dashboard screens.
- Demo-only sample server/storage metrics, clearly labelled.
- Member/Admin role preview; member view is read-only and admin demo diagnostics are illustrative only.
- Save HTTPS server address and test `/health`.
- Server scaffold reports real process uptime and runtime details on its local-only health endpoint.
- Separate Windows server build remains in the repository and CI.

## Product direction

NightSync Server will be a separate Windows host application/service. Desktop and mobile are clients. Planned capabilities include NAS storage, SMB integration, media library, backups, sync, recycle bin and multi-user access.

## Important limitations

No actual storage metrics, CPU/memory monitoring, authentication, role enforcement, file transfer, photo backup, Windows Service, SMB, media streaming, or backup/restore is implemented yet. Demo dashboard values are not live. The role selector is not a security control.

The server listens only on 127.0.0.1:8787. Do not expose it to LAN or internet or store important data in this prototype.

## Build

GitHub Actions builds Android debug APK, Windows desktop package and Windows server executable. These are unsigned development builds, not production installers.
