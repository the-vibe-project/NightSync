# NightSync v0.7 — Windows host setup and controls

## Configure this PC
1. Open NightSync Desktop and sign in with the administrator account.
2. Open **Server control & setup**.
3. Browse to the downloaded `NightSync-Server-windows-x64.exe`.
4. Choose three separate folders:
   - Main cloud storage — active file storage for this test build.
   - Backup destination — saved for the future backup engine.
   - Sync destination — saved for the future folder synchronization engine.
5. Choose whether to start the server automatically when this Windows user signs in.
6. Select **Save setup**.

The app creates the selected folders and stores the setup at `%AppData%\\NightSync\\host-config.json`.

## Start / stop / restart
- **Start server** launches the selected server executable in the background.
- **Stop server** sends an authenticated administrator-only shutdown request.
- **Restart server** stops it, waits for it to go offline, then starts it again.
- **Auto-start** adds/removes a Windows current-user Run registry entry. It starts at user sign-in, not before login.

Server logs are written to `%AppData%\\NightSync\\server.log`; the Server logs button opens this file.

The server remains a normal Windows process in this milestone. It is not yet installed as a Windows Service.

## Destination behavior
- Main cloud storage is active for file uploads.
- Backup destination is saved and passed to the server, but no backup-copy or scheduled backup job runs yet.
- Sync destination is saved and passed to the server, but no two-way synchronization job runs yet.

Do not assume the backup or sync folder contains copies until those engines are implemented. The dashboard reports these destinations as configured, not active.

## Safety and existing data
This build listens only on `127.0.0.1:8787`. Do not expose it to LAN or the internet.
Keep the existing `NightSyncData` folder if you want to retain existing test users and uploaded files. Choosing a different main storage directory creates a separate user database and storage area; it does not migrate existing data.
