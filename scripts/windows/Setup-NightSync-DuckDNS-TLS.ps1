# NightSync DuckDNS + Let's Encrypt certificate provisioning
# Run from an elevated PowerShell window. The DuckDNS token is not stored in this file.

[CmdletBinding()]
param(
    [string]$Domain = "nightsync.duckdns.org",
    [string]$LegoPath = "$PSScriptRoot\lego.exe",
    [string]$InstallRoot = "$env:ProgramData\NightSync",
    [string]$Email
)

$ErrorActionPreference = "Stop"

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Open PowerShell with Run as administrator, then run this script again."
    }
}

function Protect-NightSyncDirectory([string]$Path) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    & icacls.exe $Path /inheritance:r | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not disable inherited permissions on $Path" }

    $rules = @(
        "*S-1-5-18:(OI)(CI)F",
        "*S-1-5-32-544:(OI)(CI)F",
        "$($env:USERDOMAIN)\$($env:USERNAME):(OI)(CI)F"
    )
    & icacls.exe $Path /grant:r $rules | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not apply restrictive permissions to $Path" }
}

Assert-Administrator

if ($Domain -notmatch '^(?=.{1,253}$)[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.duckdns\.org$') {
    throw "Domain must be a valid DuckDNS hostname, for example nightsync.duckdns.org."
}

if (-not (Test-Path -LiteralPath $LegoPath -PathType Leaf)) {
    throw "lego.exe was not found at '$LegoPath'. Download the matching Windows release from the official go-acme/lego GitHub Releases page, then place lego.exe beside this script or pass -LegoPath."
}

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = Read-Host "Email address for Let's Encrypt certificate expiry notices"
}
if ($Email -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
    throw "Enter a valid email address."
}

$InstallRoot = [IO.Path]::GetFullPath($InstallRoot)
$AcmePath = Join-Path $InstallRoot "acme"
$CertDir = Join-Path $AcmePath "certificates"

Write-Host "Preparing protected NightSync certificate storage..."
Protect-NightSyncDirectory -Path $InstallRoot
Protect-NightSyncDirectory -Path $AcmePath

$tokenSecure = Read-Host "Paste your DuckDNS token (input is hidden)" -AsSecureString
$tokenPtr = [IntPtr]::Zero
$token = $null

try {
    $tokenPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($tokenSecure)
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPtr)
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "DuckDNS token cannot be empty."
    }

    # Lego's DuckDNS provider reads this process-only environment variable.
    $env:DUCKDNS_TOKEN = $token
    Write-Host "Requesting a Let's Encrypt certificate for $Domain using DuckDNS DNS-01..."
    & $LegoPath --email $Email --domains $Domain --dns duckdns --path $AcmePath run
    if ($LASTEXITCODE -ne 0) {
        throw "Lego failed with exit code $LASTEXITCODE. Check the hostname, DuckDNS token, DNS propagation, and system clock."
    }
}
finally {
    Remove-Item Env:DUCKDNS_TOKEN -ErrorAction SilentlyContinue
    $token = $null
    if ($tokenPtr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPtr)
    }
    if ($tokenSecure) { $tokenSecure.Dispose() }
}

$CertFile = Join-Path $CertDir "$Domain.crt"
$KeyFile = Join-Path $CertDir "$Domain.key"
if (-not (Test-Path -LiteralPath $CertFile -PathType Leaf) -or
    -not (Test-Path -LiteralPath $KeyFile -PathType Leaf)) {
    throw "Lego completed but the expected certificate/key files were not found. Inspect $CertDir before proceeding."
}

# Re-apply restrictive ACLs to ACME account data and the issued private key.
Protect-NightSyncDirectory -Path $AcmePath
Protect-NightSyncDirectory -Path $CertDir

Write-Host ""
Write-Host "Certificate provisioning completed."
Write-Host "Hostname:     $Domain"
Write-Host "Certificate:  $CertFile"
Write-Host "Private key:  $KeyFile"
Write-Host ""
Write-Warning "This provisions the certificate only. It does NOT configure the NightSync Windows service, Android certificate pinning/pairing, Windows Firewall, router forwarding, automatic renewal, or CGNAT/relay access."
Write-Host "Do not expose the NightSync listener to the internet until those pieces are implemented and tested."
