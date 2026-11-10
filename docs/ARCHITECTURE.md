# NightSync Architecture

## Applications

1. **Server** — independent background process; Windows first, Linux later.
2. **Desktop** — WPF graphical client and administration console.
3. **Mobile** — Android first, iOS later.
4. **Web** — browser interface, added after the API and security model stabilise.

## Server modules (planned)

- API gateway and authentication
- Authorization and role management
- Storage abstraction
- File/object engine
- Sync and conflict engine
- Backup and restore engine
- Media indexing and streaming
- Notifications and diagnostics
- Windows platform integration (Storage Spaces, service lifecycle, SMB)

## Storage design

- Phase 1: independent NTFS volumes; no destructive disk operations.
- Phase 2: read-only discovery and health reporting.
- Phase 3: Storage Spaces integration after dedicated testing.
- Phase 4: pools, volumes, quotas and recovery workflows.

NightSync must never silently initialize, format, pool, repartition or erase a disk. All destructive operations require explicit confirmation and clear warnings.

RAID/Storage Spaces redundancy is not a backup.

## Security principles

- Bind development API to loopback only.
- Do not expose the scaffold to LAN or internet.
- TLS, authentication, authorization, rate limiting, audit logs and recovery must be implemented and reviewed before remote access.
- Do not store plaintext passwords.
- Do not claim end-to-end encryption if the server owner can recover and read user content.

## Current milestone

This repository is a clean starter scaffold. The health endpoint is the only server API. No real file storage or account functionality exists yet.
