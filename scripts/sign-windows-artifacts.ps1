[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Path,
    [Parameter(Mandatory = $true)]
    [string] $PfxPath
)
$ErrorActionPreference = "Stop"
$timestampUrl = "http://timestamp.digicert.com"
$codeSigningEku = "1.3.6.1.5.5.7.3.3"
$certificateStore = "Cert:\CurrentUser\My"
$importedThumbprints = @()
try {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) { throw "Artifact directory does not exist: $Path" }
    if (-not (Test-Path -LiteralPath $PfxPath -PathType Leaf)) { throw "Signing PFX file does not exist." }
    if ([string]::IsNullOrWhiteSpace($env:WINDOWS_SIGNING_PFX_PASSWORD)) { throw "WINDOWS_SIGNING_PFX_PASSWORD is not configured." }

    $password = ConvertTo-SecureString -String $env:WINDOWS_SIGNING_PFX_PASSWORD -AsPlainText -Force
    $certificates = @(Import-PfxCertificate -FilePath $PfxPath -CertStoreLocation $certificateStore -Password $password)
    $validCertificates = @($certificates | Where-Object {
        $_.HasPrivateKey -and $_.NotBefore -le (Get-Date) -and $_.NotAfter -gt (Get-Date) -and
        @($_.EnhancedKeyUsageList | ForEach-Object { $_.ObjectId.Value }) -contains $codeSigningEku
    })
    if ($env:WINDOWS_SIGNING_CERT_THUMBPRINT) {
        $requestedThumbprint = ($env:WINDOWS_SIGNING_CERT_THUMBPRINT -replace '\s', '').ToUpperInvariant()
        $validCertificates = @($validCertificates | Where-Object { $_.Thumbprint.ToUpperInvariant() -eq $requestedThumbprint })
    }
    if ($validCertificates.Count -ne 1) { throw "Expected exactly one valid code-signing certificate with a private key. Configure WINDOWS_SIGNING_CERT_THUMBPRINT if the PFX contains multiple certificates." }

    $certificate = $validCertificates[0]
    $importedThumbprints += $certificate.Thumbprint
    Write-Host "Signing publisher certificate: $($certificate.Subject)"
    Write-Host "Certificate expires: $($certificate.NotAfter.ToUniversalTime().ToString('u'))"

    $programFilesX86 = [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
    $sdkBin = Join-Path $programFilesX86 "Windows Kits\10\bin"
    $signTool = Get-ChildItem -LiteralPath $sdkBin -Filter "signtool.exe" -Recurse -ErrorAction Stop |
        Where-Object { $_.FullName -match '\\x64\\signtool\.exe$' } |
        Sort-Object -Property FullName -Descending | Select-Object -First 1
    if (-not $signTool) { throw "Windows SDK SignTool (x64) was not found on this runner." }

    $extensions = @(".exe", ".dll", ".msi", ".msix", ".appx", ".cab")
    $files = @(Get-ChildItem -LiteralPath $Path -File -Recurse |
        Where-Object { $extensions -contains $_.Extension.ToLowerInvariant() } |
        Sort-Object -Property FullName)
    if ($files.Count -eq 0) { throw "No signable Windows binaries were found under: $Path" }

    foreach ($file in $files) {
        Write-Host "Signing $($file.FullName)"
        & $signTool.FullName sign /sha1 $certificate.Thumbprint /fd SHA256 /tr $timestampUrl /td SHA256 /v $file.FullName
        if ($LASTEXITCODE -ne 0) { throw "SignTool failed to sign $($file.Name) with exit code $LASTEXITCODE." }

        & $signTool.FullName verify /pa /all /v $file.FullName
        if ($LASTEXITCODE -ne 0) { throw "Authenticode verification failed for $($file.Name) with exit code $LASTEXITCODE." }

        $signature = Get-AuthenticodeSignature -FilePath $file.FullName
        if ($signature.Status -ne "Valid" -or -not $signature.SignerCertificate -or
            $signature.SignerCertificate.Thumbprint -ne $certificate.Thumbprint) {
            throw "PowerShell signature verification did not confirm the expected publisher for $($file.Name)."
        }
    }
    Write-Host "Successfully signed and verified $($files.Count) Windows binary file(s)."
}
finally {
    foreach ($thumbprint in $importedThumbprints) { Remove-Item -LiteralPath "$certificateStore\$thumbprint" -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $PfxPath -PathType Leaf) { Remove-Item -LiteralPath $PfxPath -Force -ErrorAction SilentlyContinue }
}