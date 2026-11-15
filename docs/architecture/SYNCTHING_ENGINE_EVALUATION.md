# Architecture Decision: Evaluate Syncthing as the NightSync sync engine

- Status: Proposed — evaluation required before bundling or release
- Date: 2026-11-16
- Owner: The Vibe Project / NightSync

## Context

NightSync needs reliable continuous, bidirectional folder synchronization between a Windows host and user devices. Implementing a custom file synchronization protocol, block transfer, change detection, conflict handling, peer identity, discovery, and relay support would create a large security and reliability burden.

Syncthing is a mature open-source peer-to-peer synchronization engine. Its project goals explicitly prioritize avoiding data loss, security, ease of use, and automation. It uses per-device cryptographic identity and encrypted transport, and exposes a local REST API for management.

Reference project: https://github.com/syncthing/syncthing  
REST API documentation: https://docs.syncthing.net/dev/rest.html  
Configuration documentation: https://docs.syncthing.net/users/config.html

## Proposed direction

Evaluate Syncthing as a separately managed synchronization engine behind NightSync's own product experience.

NightSync remains responsible for:
- First-run setup and storage selection
- Owner/member accounts, permissions, and device approval/revocation
- QR-based onboarding and clear device/folder sharing UX
- A unified Windows and mobile dashboard
- Photo/video backup workflows, backup history, restore, and retention
- Notifications, health checks, diagnostics, and supportable updates

Syncthing would be responsible, if the evaluation succeeds, for:
- Folder indexing and change detection
- Bidirectional transfer and resumption
- Peer-to-peer encrypted transport and device identity
- Conflict handling and synchronization state
- Discovery/relay behavior supported by the selected Syncthing configuration

Do not reimplement or expose Syncthing's protocol in NightSync.

## Integration boundary

1. Run a pinned, verified Syncthing executable as a child/background process on Windows, with a dedicated NightSync-owned config and database directory.
2. Bind its management GUI/REST API to loopback only. Never expose port 8384 or its API key to the LAN or internet.
3. Have the NightSync Windows service act as the sole local controller. Keep the API key in a protected secret store and pass it only to the local controller.
4. NightSync translates its own authenticated user/device/folder permissions into explicit Syncthing device and folder configuration. Never enable automatic folder acceptance by default.
5. Treat the sync engine's folder roots as an authorization boundary: validate all paths, prevent overlap with application secrets, and never share account databases, credentials, logs, or TLS private keys.
6. Keep the existing NightSync HTTPS API for account management, photo backup, restore orchestration, and app-specific features. Do not confuse a Syncthing peer connection with a NightSync user session.
7. The Android/iOS engine strategy is an open spike. A desktop daemon cannot simply be assumed to work reliably as a mobile background service. Evaluate Android process lifecycle, foreground-service requirements, battery restrictions, storage permissions, and iOS platform limitations before promising mobile two-way sync.
8. Photo/video backup should be designed as a distinct, user-visible backup workflow with backup status, restore, retention, and deletion semantics. Do not claim that ordinary folder synchronization is a backup.

## Security requirements

- Never expose Syncthing's REST/GUI listener publicly.
- Use unique, private engine configuration and data directories per NightSync installation.
- Verify downloaded/built binaries against upstream release signatures/checksums and pin exact versions.
- Do not disable TLS verification or weaken peer identity checks.
- Do not auto-approve unknown devices or folders.
- Do not put engine API keys, device private keys, or account secrets in QR codes, logs, command-line arguments, or repository files.
- Define and test safe behavior for account removal, device revocation, folder removal, conflicts, disk-full conditions, and engine crashes.
- Keep backups separate from synchronized working copies; provide a recoverable version/history strategy before calling the product a backup solution.

## Licensing and distribution gate

The Syncthing repository is licensed under Mozilla Public License 2.0 (MPL-2.0); NightSync currently identifies itself as GNU AGPL v3. Do not copy Syncthing source into NightSync or statically link its libraries as part of this evaluation.

Before distributing a bundled Syncthing executable:
- Pin the exact upstream version and source commit.
- Preserve upstream copyright and license notices and include the MPL-2.0 text.
- Provide the corresponding source for the exact distributed version and any modifications to MPL-covered files, in the manner required by MPL-2.0.
- Add a third-party notices page and document how users obtain the corresponding source.
- Review the interaction between MPL-2.0 distribution obligations and NightSync's AGPLv3 distribution model with qualified counsel. This note is an engineering checklist, not legal advice.

Upstream license: https://github.com/syncthing/syncthing/blob/main/LICENSE

## Evaluation plan and acceptance criteria

### Spike A — Windows engine lifecycle
- Build or obtain a pinned Windows binary and verify its authenticity.
- Start/stop it from the NightSync service without an interactive user session.
- Use a dedicated config directory and random API key.
- Confirm its REST API is reachable only on loopback and requires the API key.
- Confirm clean restart/recovery and no secret leakage in logs or process arguments.

### Spike B — Real two-device synchronization
- Pair two test devices using an explicit approval flow.
- Share a test folder and verify create, edit, rename, and delete propagation in both directions.
- Test simultaneous edits, offline/online transitions, large files, interrupted transfers, and disk-full behavior.
- Verify that revoking a device stops future synchronization.

### Spike C — Mobile feasibility
- Prove a supported Android engine lifecycle on current Android versions, including Doze, reboot, background restrictions, and scoped storage.
- Assess iOS feasibility separately; do not infer parity from Android.
- If native engine integration is not viable, document a supported alternative rather than presenting a web/API upload as continuous two-way sync.

### Release gate
No NightSync release may describe continuous sync, mobile two-way sync, remote access, or photo backup as complete until the corresponding end-to-end acceptance tests pass on real devices.

## Decision

Proceed with a time-boxed technical evaluation of Syncthing as the Windows synchronization engine. Do not yet bundle it in production installers, promise Android/iOS engine parity, or replace NightSync's account, setup, backup, and management layers.
