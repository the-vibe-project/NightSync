# NightSync Windows architecture

## Operating modes
- Host only: this PC runs and manages a private cloud.
- Client only: this PC connects to a remote NightSync host.
- Host + Client: both capabilities run independently on the same PC.

Closing the desktop UI must not stop the host engine.

## Components
1. NightSync Desktop: C#/.NET 8 WPF user interface, setup wizard, dashboards and client connection profiles.
2. NightSync Engine: Go background process, later installed as a Windows Service. Owns local API, SQLite metadata DB, storage and sync jobs.
3. NightSync Client: initially a module in Desktop; handles remote sign-in, browsing, upload/download and selective sync.

## Data layout proposal
`%ProgramData%\NightSync\config`, `database`, `logs`.
Selected storage drive: `NightSyncData\objects`, `staging`, `recycle`, `backups`.

## Stack
- Windows UI: .NET 8 WPF
- Engine: Go
- Metadata: SQLite
- File objects: selected local drive
- API: versioned REST; localhost HTTP only during development
- Android: Kotlin; iOS/macOS: Swift; Web: React

## Security
- No public listener until authentication, authorization, TLS, rate limits and firewall guidance exist.
- Passwords hashed with Argon2id.
- Short-lived access tokens and revocable refresh sessions.
- Windows secrets protected with DPAPI.
- Random opaque storage keys; never trust client filesystem paths.
- Server-side per-user authorization on every file operation.
- Encryption key recovery must be designed and tested.
- Owner recovery access and strict end-to-end encryption have trade-offs; do not promise both.
- Admins cannot access member files through normal admin functions; privileged owner access must be controlled and audited.

## Roadmap
1. Desktop shell and engine health check.
2. Host setup, storage validation and engine lifecycle.
3. Owner account, invitations, authentication and roles.
4. File upload/download and quotas.
5. Remote client connections.
6. Resumable transfers and integrity checks.
7. Android photo backup and selective sync.
8. Recycle bin with server-confirmed 30-day retention.
9. Encryption, recovery and audit logging.
10. Secure remote access, service installer, backups and diagnostics.
