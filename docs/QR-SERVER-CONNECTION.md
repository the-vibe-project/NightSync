# QR and manual server connection

## Goal

Let an Android user connect to a NightSync host by either scanning a QR code shown by the Windows Server dashboard or entering the server address manually. Both paths converge on the same sign-in form; the user must still enter their User ID and password.

## Current architecture constraint

The current Go server is deliberately restricted to `127.0.0.1:8787`. Its `--addr` flag rejects non-loopback addresses, and the HTTP server binds to loopback regardless. The Android release manifest also disables cleartext traffic. This is an intentional security boundary, not a UI limitation. Do not remove it or show a LAN address as connectable until authenticated HTTPS is implemented and tested.

## User experience

### Windows Server dashboard

Add a **Connect a device** panel that displays:

- Server state (online/offline).
- A copyable **Server address**.
- A QR code for the same address.
- A short instruction: connect the phone to the same trusted Wi-Fi network, scan the QR code, then sign in with the account User ID and password.
- A clear unavailable state when the server is stopped, no usable LAN interface exists, or secure remote access is not configured.

The address must be derived from the actual listening interface and port, not guessed from the machine name. If multiple interfaces exist, let the user choose one. Do not display `127.0.0.1` as a phone-connectable address.

### Android sign-in

Offer two connection methods:

1. **Scan QR code** — request camera permission only after the user taps the action, scan a NightSync connection payload, validate its scheme/host/port, and populate the server address.
2. **Enter address manually** — retain the existing server-address field.

After either method, show the same User ID and Password fields and existing Connect action. Never place a password, session token, or long-lived credential in the QR payload.

### QR payload

Use a versioned, strictly parsed payload, for example:

`nightsync://connect?v=1&url=https%3A%2F%2F192.168.1.20%3A8787&fingerprint=<sha256>`

The fingerprint field is mandatory for the initial self-signed-certificate LAN design. Reject unknown versions, malformed URLs, user-info, fragments, unexpected query parameters, unsupported schemes, and invalid ports. QR parsing must not silently downgrade HTTPS to HTTP.

## Required secure transport work

Before LAN pairing can be enabled:

1. Generate or provision a per-installation TLS certificate and private key on Windows; protect the private key using Windows ACLs and never export it.
2. Bind the Go server to the selected LAN interface only after explicit opt-in. Keep loopback-only mode as the default.
3. Serve the API over HTTPS. Do not accept credentials over LAN HTTP.
4. Expose a minimal pairing/metadata endpoint that returns the public certificate fingerprint and server identity only.
5. Pin/verify the expected certificate identity in the Android client for this server connection. Do not install a trust-all certificate manager or disable hostname verification globally.
6. Make certificate rotation/re-pairing explicit. A changed fingerprint must fail closed and require scanning the newly displayed QR code.
7. Explain that same-Wi-Fi access is not internet access; router/firewall rules and Windows Firewall must be configured deliberately.

## Validation and tests

- Unit-test QR payload parsing, version handling, malformed URLs, unsupported schemes, user-info, invalid ports, and fingerprint mismatch.
- Test LAN address discovery with multiple adapters, no adapter, VPN/virtual adapters, IPv4 and IPv6.
- Test that default server startup remains loopback-only.
- Test that LAN mode refuses to start without a valid TLS certificate and explicit opt-in.
- Test that HTTP login is unavailable on LAN and HTTPS login works with the pinned certificate.
- Android UI tests: manual address path, camera permission denied/granted, valid QR, invalid QR, fingerprint mismatch, and common credential sign-in after either path.
- Windows UI tests: server stopped, no LAN address, selected adapter, copy address, QR content, and certificate rotation.
- End-to-end test on a Windows host and a physical Android device on the same Wi-Fi network, including firewall denial and recovery.

## Acceptance criteria

- Both scan and manual entry remain available.
- Both methods lead to the same User ID/password authentication flow.
- Windows shows the exact reachable server address and a QR encoding that address.
- No secrets are embedded in QR codes.
- No LAN credentials travel over plaintext HTTP.
- The default local-only development mode remains safe and unchanged.
- CI builds and relevant unit/UI tests pass; physical-device LAN pairing is separately recorded as tested before release.

## Implementation note

This feature must be delivered as a coordinated Windows UI + Go server + Android client change. A QR image or a displayed private IP alone is not a working connection feature. Keep the dashboard's remote-connect panel disabled until the secure transport and Android certificate pinning path are complete.


## Implementation progress — transport foundation

The Go server now has a fail-closed listener configuration foundation:

- Default startup remains `127.0.0.1:8787` over HTTP for local development.
- A non-loopback listener requires the explicit `--allow-remote` opt-in.
- Remote mode must bind one concrete unicast IP address; wildcard binds such as `0.0.0.0` and `::` are rejected.
- Remote mode requires both `--tls-cert` and `--tls-key`; plaintext non-loopback listeners are rejected.
- The server validates the certificate/key pair before starting and enforces TLS 1.2 or newer.

- In TLS mode, `GET /api/v1/server/identity` returns the protocol version, server version, TLS minimum, and SHA-256 fingerprint of the presented leaf certificate. The endpoint returns 404 over plaintext HTTP and contains no credentials or private key material.
- The fingerprint is intended for out-of-band verification and the future Android QR pinning flow; Android does not consume or enforce it yet.
- Hostnames are not accepted as bind addresses. Bind to the selected interface IP.

Example for a host with a certificate whose SAN matches the selected interface IP:

```powershell
NightSync.Server.Engine.exe --addr 192.168.1.20:9443 --allow-remote --tls-cert C:\NightSync\certs\server.crt --tls-key C:\NightSync\certs\server.key
```

**This is not yet a supported customer connection flow.** The Windows dashboard does not provision/protect certificates or configure this listener, Android does not yet pin a NightSync certificate identity, and no relay/tunnel service exists for networks where direct inbound connectivity is unavailable. Do not port-forward this prototype or use it as a worldwide-access release. The CLI guard is a transport foundation only; device pairing and end-to-end validation remain release blockers.
