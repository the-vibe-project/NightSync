# NightSync

Free and open-source self-hosted NAS and personal cloud platform.

## Project status

**Early prototype / source scaffold. Not production-ready.**

This repository currently contains:
- Android UI starter (Kotlin + Jetpack Compose).
- Windows WPF desktop UI starter.
- Go engine foundation with a localhost-only health endpoint and SQLite metadata schema.
- Initial architecture notes.

The current Android app does not yet connect to a server or upload files. The Windows desktop app and Go engine are foundations only. Authentication, secure remote access, actual file transfer/synchronization, encryption, Windows Service installation, SMB integration, media streaming, backup/restore, and NAS storage management are not yet implemented.

Do not expose the current engine to the internet or store important data in it.

## Product principles

- Free to build, install and use; no subscriptions, ads, paid feature tiers, or mandatory hosted cloud.
- Self-hosted first; LAN operation should not require an internet connection.
- Windows server first; Linux server support is a future goal.
- Separate server, Windows desktop, web, Android and iOS clients.
- Storage modes: independent drives and supported Windows Storage Spaces pools.
- SMB sharing through native Windows capabilities, never exposed directly to the public internet.
- Built-in personal photo/audio/video library.
- Backup destinations: local/external drives, another NightSync server, network storage, and future optional destinations.
- Privacy, recoverability and security take priority over feature count.

## Repository layout

```text
.
├── .github/workflows/       # Automated Android and Windows builds
├── desktop/windows/         # Windows WPF client/admin starter
├── mobile/android/          # Android client starter
├── server/engine/           # Go server-engine foundation
└── docs/                    # Architecture notes
```

## Automated builds

Push to `main` or open a pull request to run the build workflows.

- Android: creates a debug APK artifact.
- Windows: publishes a self-contained Windows x64 desktop executable folder as an artifact.

These are development builds, not signed public-release installers. The Windows output is a self-contained publish package; it is not yet an MSI installer or a registered Windows service.

## Local development requirements

- Android: JDK 17, Android SDK 35, Gradle 8.11.1 or compatible.
- Windows desktop: .NET 8 SDK on Windows.
- Engine: Go 1.23+.

## License

Intended project license: GNU AGPL-3.0-or-later. Add the official full-text `LICENSE` file to the GitHub repository before publishing. Third-party dependencies retain their own licences.
