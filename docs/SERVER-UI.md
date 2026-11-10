# NightSync Server 0.9.0

NightSync Server is a **single Windows application**. It includes the graphical dashboard and the bundled Go server engine; there is no separate server executable to download or keep beside the dashboard.

## First run

1. Download the `NightSync-Server` artifact from a successful GitHub Actions run.
2. Extract the package to a permanent folder, such as `C:\NightSync\Server`.
3. Open `NightSync.Server.exe`.
4. Follow the four-step wizard:
   - Welcome.
   - Choose the main storage folder.
   - Review automatic Windows background startup.
   - Create the first owner account and password.
5. Approve the Windows administrator prompt when asked to install the service.
6. The wizard starts the background engine and checks that its local health endpoint responds.

Do not move the Server executable after installing the background service. To update it, stop/remove the service, replace the Server package, then enable background startup again.

## Background service

- Registered as Windows Service `NightSync`.
- Automatic (Delayed Start), before user sign-in.
- Runs under LocalSystem in this development version.
- Configured to restart after unexpected failures.
- The dashboard can close without stopping the service.
- Start, Stop, Restart and Remove service are available from the Server dashboard.

## Storage

The main storage location is used for per-user private folders and the server's account database. Backup and sync destination fields are configuration placeholders; automatic backup and sync jobs are not active.

Changing the main storage location does **not** migrate existing user files or the account database. Back up and migrate data manually before changing it.

## Current limits

- Local server endpoint only: `127.0.0.1:8787`.
- No phone/remote access, TLS, sync worker, or scheduled file transfers yet.
- Android scheduled times are preferences only.
- Upload limit is 100 MB; existing file names cannot be overwritten by upload.
- The service currently uses LocalSystem and has not undergone a security review.
- This is a development prototype, not a public production release.
