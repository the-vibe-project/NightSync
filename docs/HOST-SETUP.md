# NightSync host setup

The old **Server control & setup** panel inside NightSync Desktop is retained for reference from the v0.7 milestone. For current Windows server installation and boot-time startup, use the separate **NightSync Server UI**.

Follow [SERVER-UI.md](SERVER-UI.md) and [WINDOWS-SERVICE.md](WINDOWS-SERVICE.md) to:
- choose the server executable and main storage folder;
- save the host configuration;
- install the Windows boot service so the server starts before sign-in;
- start, stop, restart, and monitor the service.

## Existing data

Keep the existing `NightSyncData` folder if you want to retain test users and uploaded files. Choosing a different main storage directory creates a separate user database and storage area; it does not migrate existing data.

## Current limits

The server listens only on `127.0.0.1:8787`. Do not expose it to LAN or the internet. Backup paths and sync paths can be selected, but automatic backup and two-way synchronization are not implemented. The Android schedule screen currently saves preferred times locally but does not initiate transfers.
