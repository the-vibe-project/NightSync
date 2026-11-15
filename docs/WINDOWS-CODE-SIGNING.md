# Windows code signing

NightSync currently publishes unsigned Windows artifacts. This workflow adds an optional, manual Authenticode signing step; it does not manufacture a public-trust certificate.

## What this changes for users

A valid Authenticode signature identifies the publisher and detects changes to a signed binary. It does **not** guarantee that SmartScreen will stop warning on the first download. SmartScreen also evaluates file and publisher reputation, which builds over time. A new, correctly signed release may still be shown as unrecognized.

For public distribution, obtain an Organization Validation (OV) code-signing certificate from a trusted certificate authority. The CA must validate the legal publisher identity. The certificate's displayed publisher name is determined by the certificate, not by the NightSync application metadata. Do not buy an Extended Validation (EV) certificate solely to bypass SmartScreen; EV no longer provides an automatic SmartScreen reputation bypass.

Microsoft Artifact Signing (formerly Trusted Signing) is another option where the service is available. Microsoft's current availability restrictions exclude individual developers outside the United States and Canada and organizations outside the United States, Canada, European Union, and United Kingdom. For a publisher based in India, a conventional OV code-signing certificate from a trusted CA is the practical route. Check current provider requirements and pricing directly before purchasing.

## Protected GitHub Actions setup

The manual workflow is `.github/workflows/sign-windows-release.yml`. It downloads an artifact from a prior successful main-branch build, signs supported Windows binaries, verifies every signature, and uploads a separate `*-signed` artifact. It never replaces the original unsigned artifact.

Before using the workflow, a repository administrator must configure the GitHub **`windows-signing` environment**:

1. Restrict allowed deployment branches to `main`.
2. Require at least one trusted reviewer approval before a job can access the environment.
3. Add these as **environment secrets**, not repository-wide secrets:
   - `WINDOWS_SIGNING_PFX_BASE64`: Base64-encoded PFX bytes.
   - `WINDOWS_SIGNING_PFX_PASSWORD`: PFX password.
   - `WINDOWS_SIGNING_CERT_THUMBPRINT`: optional certificate thumbprint if the PFX contains more than one valid code-signing certificate.
4. Obtain a CA-issued code-signing certificate in PFX format. Convert it to Base64 locally; never commit the PFX or its password.
5. Open **Actions → Sign Windows release artifact → Run workflow** from the `main` branch.
6. Supply a successful run ID from `.github/workflows/build.yml` and choose exactly `NightSync-Windows` or `NightSync-Server`. The workflow rejects other branches, workflows, events, and artifact names.
7. Review the publisher identity and signature of the resulting `*-signed` artifact before distribution.

Example Base64 conversion in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("publisher-code-signing.pfx")) |
    Set-Clipboard
```

Keep the private key under the CA's required hardware-token/HSM protections. Do not send the PFX or password in chat, issues, pull requests, or source control. Keep signing secrets scoped to the protected environment and limit its reviewers to trusted maintainers. Rotate/revoke credentials if exposed.

## Local signing script

`scripts/sign-windows-artifacts.ps1` uses the Windows SDK SignTool. It requires a valid, currently trusted code-signing certificate with a private key, signs `.exe`, `.dll`, `.msi`, `.msix`, `.appx`, and `.cab` files with SHA-256, adds an RFC 3161 SHA-256 timestamp, and verifies the Authenticode signature and expected certificate thumbprint. It fails closed if signing or verification fails.

The timestamp allows a signature to remain verifiable after the signing certificate expires, provided the certificate was valid when timestamped and the chain remains trusted.

## Current limitations

- No certificate is included in the repository and no signature is produced until the publisher configures the protected environment and its secrets.
- The environment's required reviewers and branch restrictions are GitHub repository settings; they must be configured manually before any signing run.
- The manual workflow signs one selected artifact per run. Sign the Server and Windows client artifacts separately if both are being distributed.
- The current build remains unsigned by default; this avoids silently shipping an unsigned artifact under a misleading “signed” label.
- Signing does not address Smart App Control, WDAC/AppLocker policy blocks, or Defender malware/PUA detections. Those require investigating the exact Windows security message and detection name.
