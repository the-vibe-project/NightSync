# NightSync Windows background service

The single **NightSync Server** app bundles the Go engine and installs it as a Windows Service.

## Installation

1. Download the `NightSync-Server` artifact from one successful GitHub Actions run.
2. Extract the entire package to a permanent folder, for example `C:\NightSync\Server`.
3. Run `NightSync.Server.exe`.
4. Complete the first-run wizard and choose the main storage folder.
5. Review the background startup step.
6. Create the owner user ID and password.
7. Approve the Windows administrator prompt.

The wizard extracts the bundled engine to `%ProgramData%\NightSync\Engine`, registers the Windows service, starts it, checks the health endpoint, and creates the first owner account.

## Service behavior

- Service name: `NightSync`
- Startup: Automatic (Delayed Start)
- Account: LocalSystem
- Failure recovery: restart after unexpected failure
- The service continues after the Server dashboard is closed or the Windows user signs out.

## Changing storage or updating the app

Changing the main storage folder does not move existing account data or files. Do not simply change the path if you need to retain existing user content. Perform a planned backup and migration first.

Keep the Server executable in its installed location. Before replacing the Server package, remove/stop the existing service, replace the package, run the Server app again, and enable background startup.

## Development limitations

The local engine listens only on `127.0.0.1:8787`; other computers and phones cannot connect. Remote TLS, secure pairing, sync, and automatic internet-reconnect behavior are not implemented. Do not expose this build to a LAN or the public internet.

The service currently runs as LocalSystem. Mapped drive letters and remote network shares are not configured. A production release requires a security review and validation on a real target Windows PC.
