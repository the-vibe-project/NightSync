# Windows boot service

NightSync can be registered as a Windows Service so the server engine starts during Windows startup, before anyone signs in.

## Set it up

1. Download the **NightSync-Server-UI-windows-x64** and **NightSync-Server-windows-x64** artifacts from the same successful GitHub Actions build.
2. Extract both artifacts. Keep the server executable in a permanent folder; do not leave it in Downloads if you might move or delete that folder.
3. Open `NightSync.ServerUI.exe`.
4. Under **Storage locations**, choose the server executable and the main storage folder. Save settings.
5. Select **Enable before sign-in** and approve the Windows administrator prompt.
6. NightSync is registered as **Automatic (Delayed Start)** under the built-in **LocalSystem** account. Windows is configured to restart the service after unexpected failures.

The Server dashboard is a control panel. Closing it does not stop an installed Windows service. Use Start, Stop, and Restart to manage the service.

## Storage and account notes

The service runs as LocalSystem. Setup grants the service account Modify access to the selected main storage folder. Local disks are supported. Mapped drive letters and remote network shares are not configured in this development build.

When changing the server executable or main storage path, save the settings and select **Enable before sign-in** again to update the service configuration.

To remove the service, select **Remove boot service** and approve the administrator prompt. This removes only the Windows service registration. It does not delete NightSync files, accounts, or storage data.

## Current limits

- The service starts the local server before sign-in. This does not yet mean files synchronize when internet becomes available.
- The server still listens only on `127.0.0.1:8787`. It is not reachable from other computers or phones in this build. Do not expose this development server to a LAN or the internet.
- The Android app currently saves the choice of one, two, or three daily sync times on the phone. The times do not start file transfers yet.
- Internet-aware background sync requires secure remote connectivity and a sync worker. Those parts are not implemented yet.
