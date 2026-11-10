# NightSync v0.4 — Local server test

## Included server endpoints
- `GET /health` — real process online status and uptime.
- `GET /api/v1/status` — real storage-volume total/free/used capacity, process memory, heap allocation, goroutine count, Go version and uptime.
- `GET /api/v1/files` — list files in the NightSync data directory.
- `PUT /api/v1/files?name=hello.txt` — upload a test file (max 50 MB).
- `GET /api/v1/files/hello.txt` — download a test file.
- `DELETE /api/v1/files/hello.txt` — delete a test file.

## Run on Windows
1. Download the `NightSync-Server-windows-x64` GitHub Actions artifact and extract it.
2. Open Command Prompt in the folder containing `NightSync-Server-windows-x64.exe`.
3. Run `NightSync-Server-windows-x64.exe`.
4. It creates a `NightSyncData` folder beside the executable. Optional: pass a different folder path as the first command-line argument.
5. Open `http://127.0.0.1:8787/health` and `http://127.0.0.1:8787/api/v1/status` in the same PC browser.

## Upload a test file from PowerShell
`Invoke-WebRequest -Method Put -Uri 'http://127.0.0.1:8787/api/v1/files?name=hello.txt' -InFile '.\hello.txt'`

List:
`Invoke-RestMethod 'http://127.0.0.1:8787/api/v1/files'`

Download:
`Invoke-WebRequest 'http://127.0.0.1:8787/api/v1/files/hello.txt' -OutFile '.\hello-downloaded.txt'`

Delete:
`Invoke-RestMethod -Method Delete 'http://127.0.0.1:8787/api/v1/files/hello.txt'`

## Safety
The server binds only to 127.0.0.1. It has no authentication or TLS and is intentionally not reachable from other devices. Do not change the bind address or expose this prototype to LAN/internet. The file API is for local test files only; do not store important or private files in it. No admin/member roles are enforced yet.
