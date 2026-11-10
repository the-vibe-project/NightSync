# NightSync v0.7 account setup

## Start the Windows host
1. Download and extract the GitHub Actions artifact `NightSync-Server-windows-x64`.
2. Double-click `NightSync-Server-windows-x64.exe`.
3. Keep the server console open. The server creates `NightSyncData` next to the executable.
4. Open `http://127.0.0.1:8787/health` on the host PC.

## Create the first admin
Open the NightSync Windows app. On the sign-in page, enter the local server address, choose an admin user ID, and a strong password of at least 12 characters. Select **Create first administrator**. This endpoint is available only while the user database is empty. Then sign in.

## Create member accounts
Account creation endpoint is `POST /api/v1/admin/users` with a bearer token from the admin login:
`{"username":"member1","role":"member","temporary_password":"Admin-created-unique-12-char-password"}`

The member must change this temporary password after first login. Each reset generates a unique temporary password; do not reuse one generic password across users.

## Forgotten password
A member contacts the admin. The admin calls `POST /api/v1/admin/users/{username}/reset` with their bearer token. The server returns a newly generated unique temporary password once. The admin shares it privately with that member. The member signs in and must change it immediately. Do not use one generic shared password.

## Change password
A signed-in user calls `POST /api/v1/change-password` with `current_password` and `new_password`. Passwords must be at least 12 characters. Passwords are stored as bcrypt hashes.

## Important prototype limitations
- Server listens only on 127.0.0.1:8787. Android cannot reach the host using 127.0.0.1 from another device.
- The server uses HTTP locally. Do not expose it to LAN or internet.
- The Windows app includes first-admin setup and sign-in. Android includes sign-in and forced password-change screens; Android first-admin setup is done on Windows.
- Windows account-management UI is not finished; admin user creation/reset are currently API endpoints.
- Sessions are in-memory and expire after 12 hours or server restart.
- Before multi-device access, add TLS, rate limiting, logout/revocation, audit events, secure token storage, and security review.

See [HOST-SETUP.md](HOST-SETUP.md) for Windows server start/stop/restart, auto-start and storage destination configuration.
