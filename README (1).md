# NightSync Android — v0.2.0

Early Kotlin + Jetpack Compose Android source project.

## Included
- Overview, Files, Activity and Settings tabs.
- HTTPS-only server address entry.
- Server address saved locally using Android DataStore.
- Honest empty states: this build does not yet connect to a server or upload files.

## Not implemented yet
- Backend API, live connection test, login and device pairing.
- File browsing, uploads, downloads and resumable transfers.
- Camera backup and background scheduling.
- Recycle bin, restore, encryption, key recovery and multi-user permissions.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. Sync Gradle and run the `app` configuration. This source has not been compiled or device-tested here and is not an APK.

Never commit passwords, tokens, private keys or personal files to source control. Do not expose the Debian server publicly until authentication, TLS, firewall rules, updates and backups are configured.
