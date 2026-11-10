# NightSync Architecture

## Applications

1. **Server** — Go server engine, currently Windows-first and capable of running as a Windows Service.
2. **Server UI** — WPF dashboard for server status, storage configuration, service install/removal, and logs.
3. **Desktop** — WPF sign-in and administration client.
4. **Mobile** — Android Compose client with sign-in and local sync-schedule preferences.
5. **Web** — not started; planned after API security and remote connectivity are ready.

## Implemented server foundation

- Local account setup, sign-in, password change, and role information.
- Local file listing, upload, download, and delete endpoints.
- Health and authenticated status endpoints.
- Main storage directory selection.
- Windows Service Control Manager integration for boot-time startup.
- Windows service restart-on-failure policy configured by the Server UI.

## Planned server modules

- Secure remote API and device pairing.
- File sync engine, conflict handling, and offline queue.
- Scheduled backup and restore.
- Per-user file browsing and shared folders.
- Media indexing and streaming.
- Notifications and diagnostics.
- SMB and optional storage-pool integration.

## Storage design

- Phase 1: independent NTFS volumes; no destructive disk operations.
- Phase 2: read-only discovery and health reporting.
- Phase 3: Storage Spaces integration after dedicated testing.
- Phase 4: pools, volumes, quotas and recovery workflows.

NightSync must never silently initialize, format, pool, repartition or erase a disk. All destructive operations require explicit confirmation and clear warnings.

RAID/Storage Spaces redundancy is not a backup.

## Security principles

- Keep the development API bound to loopback.
- Do not expose the scaffold to LAN or internet.
- TLS, remote authentication, authorization, rate limiting, audit logs and recovery must be implemented and reviewed before remote access.
- Do not store plaintext passwords.
- Do not claim end-to-end encryption if the server owner can recover and read user content.

## Current limits

The server currently listens on `127.0.0.1:8787`, has in-memory sessions, and is not production-ready or security-reviewed. The Android daily schedule stores one, two, or three preferred times locally; it does not run transfers. Backup and two-way sync jobs are not implemented.
