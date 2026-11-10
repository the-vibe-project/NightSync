# NightSync

Free and open-source self-hosted NAS and personal cloud.

**Status: early development prototype. Not production-ready or security-reviewed.**

## v0.8.0 graphical server manager
- Added a separate WPF NightSync Server UI with start/stop/restart, health polling, drive capacity, storage destination setup, logs and Windows sign-in auto-start.
- Added a dedicated GitHub Actions artifact for the Server UI.
- Added a visible startup error dialog to NightSync Desktop if the main window fails after login.

See `docs/SERVER-UI.md` for installation steps.

## v0.7.1 build-fix package
- Removed duplicate server setup panel declarations that caused WPF CS0102 build errors.
- Windows server CI now runs `go mod tidy` before compiling, generating required module checksums (`go.sum`).

## v0.7 development features
- Windows desktop sign-in and first-administrator setup.
- Server control screen: choose server executable, main storage, backup destination and sync destination.
- Start, stop and restart the local NightSync Server from the Windows app.
- Optional automatic server start when the current Windows user signs in.
- Server log file at `%AppData%\\NightSync\\server.log`.
- Server status includes live storage volume readings and configured backup/sync destination paths.
- Server file test: list, upload, download and delete.
- Server listens only on `127.0.0.1:8787` in this development build.

## Important limitations
- Backup and sync destinations can be selected and saved, but automatic backup and two-way sync jobs are not implemented yet.
- Auto-start is a Windows user sign-in startup entry, not a Windows Service and not a pre-login boot service.
- The server is localhost-only. Do not expose it to LAN or the internet.
- Sessions are held in memory and are lost on server restart; sign in again after restarting.
- Android currently has sign-in and password-change foundation screens; remote access from a phone is not enabled in this localhost-only server build.
- No encryption-at-rest, recycle bin, SMB, media streaming, camera backup or production security review yet.

See `docs/HOST-SETUP.md` and `docs/ACCOUNT-SETUP.md`.
