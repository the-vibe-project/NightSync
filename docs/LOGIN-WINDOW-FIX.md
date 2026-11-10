# NightSync 0.8.1 — post-login window fix

The WPF application previously used the default `OnLastWindowClose` shutdown mode. When the modal login window closed successfully, WPF could shut down the application before the main workspace was shown.

The fix sets `ShutdownMode=OnExplicitShutdown` while the login dialog is active, then sets `ShutdownMode=OnMainWindowClose` after the main workspace is shown. The login window also no longer calls `Close()` after setting `DialogResult`, because setting the dialog result already closes a modal dialog.

## Apply
Replace the repository contents with this package, commit and push to `main`, then download the new `NightSync-Windows-x64` artifact from the successful GitHub Actions run. Do not reuse the previous EXE.
