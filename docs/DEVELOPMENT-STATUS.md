# NightSync development status — v0.8.3

## Implemented
- Go server with local account setup, sign-in, password change, health/status endpoints, and basic local file list/upload/download/delete operations.
- Windows Server UI with a premium dark interface, storage path selection, status polling, and activity log.
- Windows Service Control Manager support. The Server UI can register the server for Automatic (Delayed Start) under LocalSystem, before a user signs in.
- Windows service restart-on-failure recovery settings.
- Windows desktop client and Android authentication foundation.
- Android sync schedule screen with one, two, or three daily times and editable time pickers. Preferences are saved on the device.

## Not yet implemented
- Remote/LAN access and TLS.
- Phone-to-server file transfers.
- A real sync worker, internet reconnect handling, offline queue, and conflict resolution.
- Scheduled backup and restore.
- Android background work that executes the selected sync times.
- Camera upload, SMB, media streaming, recycle bin, and production security review.

## Important connection limitation
The server intentionally listens on `127.0.0.1:8787`. It is not reachable from another device. Keep it local; do not expose it to a LAN or the internet. Secure remote access must be completed before the Android app can synchronize with the host.

## Quality status
GitHub Actions builds the Android APK, Windows desktop, Windows Server UI, and Windows server executable. The Windows service lifecycle passes a Windows-runner CI smoke test (register, start, health check, stop). A green CI test does not replace an actual install/reboot test on the target PC.
