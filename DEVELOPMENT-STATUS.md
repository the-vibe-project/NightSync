# Development notes

## Current prototype limitations

The Android starter is a UI scaffold. It saves a server address locally but does not authenticate, connect, upload, download, back up photos, or synchronize files.

The Windows desktop starter is a WPF UI scaffold. The Go engine exposes a localhost-only health endpoint and initializes SQLite metadata. It is not a production server and does not implement authentication, storage operations, file transfer, encryption, Windows Service hosting, SMB, media serving, or backups.

## Safety rules

- Keep the Go engine bound to localhost until authentication, authorization, TLS, rate limiting and security testing are complete.
- Never put credentials, signing keys, API tokens, personal files or database files into Git.
- Do not test destructive storage-pool operations on disks containing important data.
- RAID/Storage Spaces redundancy is not a backup.
- Do not claim end-to-end encryption if the server owner is designed to recover and read user content.
