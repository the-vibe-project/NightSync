# NightSync

**A product by The Vibe Project**  
Free-software personal cloud project, licensed under **GNU AGPL v3** (`LICENSE`).

The software may be used, modified, and redistributed under AGPL v3. If you modify the server and make that modified version available for network use, AGPL v3 requires offering the corresponding source to those users. This license does not itself promise support, hosted infrastructure, or a no-charge commercial service.

> **Development status: not ready for public production release.** The current build is a local-host prototype. Secure remote connectivity and real scheduled synchronization are still being developed.

## Three applications

1. **NightSync Server** — one Windows application containing the dashboard, first-run wizard, and bundled background server engine.
2. **NightSync for Windows** — desktop client with sign-in, server connection profile, overview, and private file manager.
3. **NightSync for Android** — mobile client with sign-in, private file manager, and preferred sync-time settings.

## Current capabilities

- First-run wizard selects the host storage location, installs the Windows background service, and creates the first owner account.
- The Windows service is configured for delayed automatic startup before Windows sign-in and restart-on-failure recovery.
- The Server dashboard includes an administrator-only User Management window to list accounts, create member/admin accounts with generated one-time temporary passwords, and reset passwords.
- Owner/admin and member accounts have individual private storage roots.
- File manager supports folder listing/navigation, create folder, upload, download, file deletion, and empty-folder deletion.
- Uploads are capped at 100 MB in this prototype. Existing files are not silently overwritten.
- Path traversal, symbolic links, Windows reserved filenames, and unauthenticated file operations are rejected.
- Android lets users save one, two, or three preferred sync times per day. These times are preferences only for now.

## Build artifacts

GitHub Actions produces three artifacts:
- `NightSync-Server`
- `NightSync-Windows`
- `NightSync-Android`

Use artifacts from the **same successful workflow run**. Do not mix packages from different commits.

## Current release blockers

- The supported Windows dashboard still runs the server in loopback-only mode; Android cannot yet connect remotely.
- The Go engine has an opt-in, TLS-only concrete-interface listener foundation behind explicit CLI flags, but it is not wired into the Windows dashboard and is not a supported customer connection flow.
- Android certificate pinning, secure device pairing, remote authentication UX, and relay/tunnel connectivity are not implemented.
- There is no real sync engine, offline queue, conflict resolution, or internet-reconnect synchronization.
- Android schedule choices are saved locally but do not start transfers.
- Backup/restore jobs, camera upload, SMB, media streaming, recycle bin, encryption at rest, and a full security review are not implemented.
- A CI Windows VM test does not replace an install/reboot test on a customer's actual PC.

## Quality checks

The workflow runs:
- Go API unit and customer-scenario tests.
- Windows server-service register/start/health/stop smoke test.
- Windows Server dashboard launch smoke test.
- Windows client publish and launch smoke test.
- Android emulator launch and login validation tests.
- APK and Windows artifact publishing.

See:
- `docs/DEVELOPMENT-STATUS.md`
- `docs/SERVER-UI.md`
- `docs/WINDOWS-SERVICE.md`
- `docs/DESIGN-SYSTEM.md`
- `docs/ACCOUNT-SETUP.md`
