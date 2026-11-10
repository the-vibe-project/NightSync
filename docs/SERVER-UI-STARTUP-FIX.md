# NightSync Server UI 0.8.2 — startup fix

## Root cause
The WPF Server UI's `App.xaml` did not declare a `StartupUri`. WPF therefore had no instruction to instantiate `MainWindow.xaml` on application startup. The EXE could launch and immediately exit or remain without a visible window.

## Fix
- Added `StartupUri="MainWindow.xaml"` to the WPF Application declaration.
- Added a dispatcher-level fatal exception dialog so a future startup exception is visible.
- Updated the Server UI assembly version to 0.8.2.

## Build
GitHub Actions builds this project in the `server-ui` job. Download the newly generated `NightSync-Server-UI-windows-x64` artifact from the successful run. Do not reuse the old artifact.

## Verification status
The project XML and WPF XAML are checked for well-formed XML in this packaging step. A real Windows GUI launch still needs to be confirmed on a Windows runner or the user's PC.
