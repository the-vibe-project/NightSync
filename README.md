# NightSync

Free and open-source self-hosted NAS and personal cloud.

**Status: early development prototype. Not production-ready or security-reviewed.**

## v0.8.3 premium UI and Windows boot service
- Refreshed the Windows Server dashboard with a minimal dark interface and champagne-gold accents, simpler labels and clearer server status.
- Added Windows Service Control Manager support to the server executable.
- The Server dashboard can register NightSync as a delayed automatic Windows service, before a user signs in. It runs as LocalSystem and configures restart-on-failure recovery.
- The Android app has a saved daily schedule preference for one, two or three sync times, with editable time pickers.
- The Android schedule is currently a saved preference only. It does not trigger file transfers in this prototype.

## v0.8.2 Server UI startup fix
- Fixed the Server UI WPF application startup: `App.xaml` now declares `StartupUri="MainWindow.xaml"`. Without this, the application process could start successfully but never create or display its main window.
- Added a visible fatal startup exception dialog to help diagnose future UI initialization failures.

## v0.8.1 desktop post-login lifecycle fix
- Fixed WPF shutdown behavior: closing the successful modal login no longer shuts down the application before the main workspace is displayed.
- The application now stays alive during login and switches to closing with the main workspace after sign-in.
- Removed redundant explicit close after setting the login dialog result.

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
- Optional Windows boot service is configured from the Server dashboard; it starts before sign-in.
- Server log file at `%ProgramData%\\NightSync\\server.log`.
- Server status includes live storage volume readings and configured backup/sync destination paths.
- Server file test: list, upload, download and delete.
- Server listens only on `127.0.0.1:8787` in this development build.

## Important limitations
- Backup and sync destinations can be selected and saved, but automatic backup and two-way sync jobs are not implemented yet.
- The optional boot service starts the server before sign-in, but this build still has no internet-aware sync worker.
- The server is localhost-only. Do not expose it to LAN or the internet.
- Windows service mode uses LocalSystem. Local folders are supported; mapped drive letters and network shares are not configured for service access.
- Android's one-to-three daily sync times are stored locally as preferences only until file transfer is implemented.
- Sessions are held in memory and are lost on server restart; sign in again after restarting.
- Android currently has sign-in and password-change foundation screens; remote access from a phone is not enabled in this localhost-only server build.
- No encryption-at-rest, recycle bin, SMB, media streaming, camera backup or production security review yet.

See `docs/HOST-SETUP.md` and `docs/ACCOUNT-SETUP.md`.
