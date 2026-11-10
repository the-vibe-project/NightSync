# NightSync Server UI 0.8.0

The graphical server manager is a separate Windows application. It launches and monitors the NightSync Server process; it is not the server itself.

## Install and start
1. Download both GitHub Actions artifacts:
   - `NightSync-Server-UI-windows-x64`
   - `NightSync-Server-windows-x64`
2. Extract both ZIP artifacts into stable folders, for example:
   - `C:\\NightSync\\ServerUI`
   - `C:\\NightSync\\Server`
3. Open `NightSync.ServerUI.exe`.
4. Browse to `NightSync-Server-windows-x64.exe`.
5. Choose the main storage folder and optionally choose backup and sync folders.
6. Save configuration, then press Start.

Do not run the server executable from inside a temporary ZIP preview or a temporary extraction directory. Keep it in a permanent folder.

## What this build does
- Start and stop the server process launched by this UI.
- Restart and poll the local health endpoint every three seconds.
- Display server version, uptime and the selected drive's used/free capacity.
- Save main, backup and sync paths.
- View the latest 250 lines of the server log.
- Optionally register the server executable to start when the current Windows user signs in.

## Important limitations
- Server is still localhost-only at `127.0.0.1:8787`.
- The UI's Stop button can stop a process it started itself. If a server was started separately in a terminal, close that terminal to stop it.
- Storage capacity shown is the whole selected drive, not NightSync folder usage.
- Backup and sync paths are configuration only; automatic jobs are not active.
- Auto-start launches the server process at Windows user sign-in. It is not a Windows Service and does not start before sign-in.
- This is a development build, not production-ready or security-reviewed.
