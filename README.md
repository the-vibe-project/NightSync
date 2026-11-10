# NightSync

Free and open-source self-hosted NAS and personal cloud.

**Status: fresh development scaffold — not a production NAS and not yet security-reviewed.**

## Product direction

- NightSync Server: Windows background service (initial target).
- NightSync Desktop: graphical Windows client and server administration.
- NightSync Mobile: Android first; iOS later.
- NightSync Web: browser-based administration and file access.
- Storage: independent drives first; Windows Storage Spaces integration later.
- SMB: Windows-native SMB integration, LAN use only; never expose SMB directly to the public internet.
- Media: personal photo, audio and video library.
- Backup: local/external drives, another NightSync server, network destinations; optional integrations later.
- No subscriptions, ads, paid tiers or mandatory hosted cloud.

## Repository layout

```text
.github/workflows/       CI builds
server/                  Go server foundation
desktop/windows/         WPF Windows application
mobile/android/          Android application
docs/                    Architecture and development notes
```

## Current capabilities

- Android starter screen.
- Windows desktop starter screen.
- Go HTTP health endpoint bound to localhost.
- Automated GitHub Actions build definitions for Android APK, Windows app and Go server executable.

## Not implemented yet

Authentication, encryption, file transfer, synchronization, camera backup, NAS disk/pool management, SMB integration, media streaming, backup/restore, Windows Service installation, remote access, and production installers.

Do not expose the server to the internet or store important data in this scaffold.

## Build outputs

GitHub Actions uploads development artifacts:
- `NightSync-Android-debug.apk`
- `NightSync-Windows-x64` (self-contained publish folder)
- `NightSync-Server-windows-x64.exe`

These are unsigned development builds, not production releases.

## Local requirements

- Android: JDK 17 and Android SDK 35.
- Windows desktop: .NET 8 SDK on Windows.
- Server: Go 1.23+.

## Licence

GNU AGPL-3.0-or-later. Keep the repository's existing full AGPL-3.0 licence file when replacing the project contents. Do not replace it with a shortened notice.
