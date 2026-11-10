# NightSync Server UI 0.8.3

The graphical Server UI is a separate dashboard. It can launch the server as a normal process or register it as a Windows Service.

## Install and start
1. Download both GitHub Actions artifacts from the same successful build:
   - `NightSync-Server-UI-windows-x64`
   - `NightSync-Server-windows-x64`
2. Extract both ZIP artifacts into permanent folders, for example `C:\NightSync\ServerUI` and `C:\NightSync\Server`.
3. Open `NightSync.ServerUI.exe`.
4. Under **Storage locations**, browse to `NightSync-Server-windows-x64.exe` and choose the main storage folder.
5. Select **Save settings**.
6. For pre-login startup, select **Enable before sign-in** and approve the Windows administrator prompt.

Keep the server executable in a permanent location. Do not run it from a temporary ZIP preview or Downloads folder that you may later move or delete.

## Boot service
- Registers the server with Windows Service Control Manager as Automatic (Delayed Start).
- Runs the service as LocalSystem, so it starts before a user signs in.
- Configures restart-on-failure recovery.
- Closing the dashboard does not stop the service.
- The service has access to the selected local main storage folder. Mapped network drives and remote shares are not configured.
- Use **Remove boot service** to unregister it. This does not delete server files or user data.

See [WINDOWS-SERVICE.md](WINDOWS-SERVICE.md) for the detailed setup.

## Dashboard information
- Start, stop, and restart the server.
- Display server version, uptime, and selected drive capacity.
- Save main, backup, and sync locations.
- View recent server activity.

## Important limitations
- The server still listens only on `127.0.0.1:8787`. It cannot be reached from other computers or phones in this build.
- Backup and sync paths are saved as configuration only; automatic jobs are not active.
- The server boot service starts the local server at Windows startup, but no internet-aware sync worker exists yet.
- The Android app saves one, two, or three daily preferred sync times locally. Those times do not start file transfers yet.
- This is a development prototype, not production-ready or security-reviewed.
