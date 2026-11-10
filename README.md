# NightSync

Free and open-source self-hosted NAS and personal cloud.

**Status: early development prototype. Not production-ready or security-reviewed.**

## v0.5
- Windows desktop client connects to the local NightSync Server.
- Live status: server version, uptime, process memory, runtime, goroutines and real storage volume capacity.
- Server file browser: list, upload, download and delete test files.
- Local HTTP is allowed only for loopback testing; remote server profiles require HTTPS.
- Android app remains a UI/connection-test preview; live remote connection awaits secure LAN/TLS and authentication.

## Safety and limitations
Server has no authentication or TLS and listens only on 127.0.0.1:8787. Do not expose it to LAN or internet or store important/private files. Member/Admin role selector is only a UI preview and is not server-enforced. No encryption, backup, recycle bin, SMB, media streaming, sync or camera backup yet.
