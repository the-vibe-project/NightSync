# NightSync development status — 0.9.0

Publisher: The Vibe Project.

## Three-app structure
- **NightSync Server** — one WPF app with bundled Go server engine, guided setup, host dashboard, and background Windows Service integration.
- **NightSync for Windows** — WPF user client and private file manager.
- **NightSync for Android** — Compose mobile client, private file manager, and locally saved schedule preferences.

## Implemented in the current prototype
- Four-step first-run Server wizard: welcome, storage selection, Windows background service, and initial owner account.
- Windows Service configuration for Automatic (Delayed Start), LocalSystem, and restart-on-failure.
- Server health endpoint and host dashboard status/logging.
- First-admin creation protected against concurrent first-run setup.
- Server session logout and password-change flows.
- Per-user private storage roots.
- Folder list/navigation, create folder, upload, download, delete file, delete empty folder.
- File path validation and symlink rejection; upload collision prevention; 100 MB upload cap.
- Android sync schedule preferences for one, two, or three times per day.
- The Vibe Project publisher branding across clients and Windows metadata.

## Current release blockers
- Server binds only to `127.0.0.1:8787`. It is not remotely reachable by Android or another PC.
- No TLS listener, secure remote pairing, or remote device authentication.
- No actual sync worker, scheduled transfer execution, internet-reconnect retry, offline queue, or conflict handling.
- Android schedule settings are stored locally and do not execute transfers.
- Backup/restore, encryption at rest, recycle bin, SMB, media streaming, camera backup, iOS client, and security review are not implemented.

## Automated QA
GitHub Actions includes:
- Go unit and customer-scenario API tests.
- Windows service lifecycle smoke test.
- Windows Server and Windows client launch smoke tests.
- Android emulator launch/login tests.
- Artifact creation for exactly three applications.

A green CI run verifies the tested scenarios on hosted virtual devices. It does not verify a customer's real Windows boot configuration, storage drive permissions, router, or phone. Final release requires an install/reboot test on the target PC and an actual Android-to-server connection test once remote networking is implemented.

## Data handling notes
- Server account sessions are held in memory and are lost when the server restarts; users must sign in again.
- User files are stored below the configured host storage directory in per-user subfolders.
- The current prototype has no recycle bin; deleting a file is permanent.
- Do not expose this local-development server to a LAN or the public internet.
