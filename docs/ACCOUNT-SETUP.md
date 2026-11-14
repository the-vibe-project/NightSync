# NightSync 0.9 account setup

## Start the Windows host
1. Download the NightSync-Server artifact from one successful GitHub Actions run. Use artifacts from the same run; do not mix builds from different commits.
2. Extract the complete package to a permanent folder such as C:\NightSync\Server.
3. Run NightSync.Server.exe and complete the first-run wizard.
4. Choose the main storage folder, review background-service setup, and create the first owner account.
5. Approve the Windows administrator prompt when installing the NightSync service.
6. On the host PC, verify http://127.0.0.1:8787/health reports the server online.

The Server UI bundles the Go engine and installs it as a Windows Service. Do not move the Server application after service installation. See SERVER-UI.md and WINDOWS-SERVICE.md for service management.

## Create member accounts
Member accounts are currently created through the authenticated admin API; a complete account-management screen is not yet available. Send POST /api/v1/admin/users with the admin bearer token and a unique temporary password:

~~~json
{
  "username": "member1",
  "role": "member",
  "temporary_password": "Use-a-unique-12-character-password"
}
~~~

Passwords must contain at least 12 characters. Members must change temporary passwords after first sign-in. Admin password resets use POST /api/v1/admin/users/{username}/reset; the server returns a newly generated temporary password once. Share it privately and never reuse it.

## Passwords and sessions
A signed-in user changes a password through POST /api/v1/change-password with current_password and new_password. Passwords are stored as bcrypt hashes.

Sessions are held in memory, expire after 12 hours, and are lost when the server restarts. Users must sign in again after a restart.

## Important prototype limitations
- The server listens only on 127.0.0.1:8787. Android on a separate phone and other PCs cannot connect to this host.
- The server uses local HTTP. Do not expose it to a LAN or the internet.
- TLS, secure remote pairing, remote authentication, and multi-device connectivity are not implemented.
- Login rate limiting is proposed in PR #1 and is not part of main until reviewed and merged.
- File deletion is permanent; there is no recycle bin.
- Automatic sync, scheduled transfers, and backup jobs are not implemented. Android schedule entries are preferences only.

This is a development prototype, not a production-ready personal-cloud service. See DEVELOPMENT-STATUS.md before customer installation.
