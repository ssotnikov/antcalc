[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackupFile,
    [Parameter(Mandatory)][string]$NewKeyDirectory,
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release_key_helpers.ps1')
if (Test-Path -LiteralPath $NewKeyDirectory) { throw 'Restore destination already exists; original keys are never overwritten.' }
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) { throw 'Backup file not found.' }
if (-not $JdkHome) { throw 'Set JAVA_HOME to JDK 21.' }
$policyPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'distribution/signing.json'
$policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
$password = Read-Host 'Enter the portable backup password' -AsSecureString
try {
    $env:ANTCALC_RECOVERY_PASSWORD = [Net.NetworkCredential]::new('', $password).Password
    $certificate = & (Join-Path $JdkHome 'bin/keytool.exe') -exportcert -rfc -keystore $BackupFile -alias antcalc -storepass:env ANTCALC_RECOVERY_PASSWORD
    if ($LASTEXITCODE) { throw 'Cannot read backup with this password.' }
    $encoded = ($certificate | Where-Object { $_ -notmatch '^-----' }) -join ''
    $der = [Convert]::FromBase64String($encoded)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $fingerprint = ([BitConverter]::ToString($hasher.ComputeHash($der))).Replace('-', '').ToLowerInvariant() } finally { $hasher.Dispose() }
    if ($fingerprint -ne $policy.certificate_sha256) { throw 'Backup is not the approved production identity.' }
    $directory = New-AntcalcPrivateDirectory -Path $NewKeyDirectory
    Copy-Item -LiteralPath $BackupFile -Destination (Join-Path $directory.FullName 'production.p12')
    $password | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $directory.FullName 'password.dpapi') -Encoding ascii
    [IO.File]::WriteAllBytes((Join-Path $directory.FullName 'certificate.der'), $der)
    @{ alias = 'antcalc'; certificate_sha256 = $fingerprint; password_storage = 'Windows DPAPI CurrentUser'; recovered_from_portable_backup = $true } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory.FullName 'identity.json') -Encoding utf8
    Read-AntcalcReleasePassword -KeyDirectory $directory.FullName | Out-Null
    Write-Output "Release identity restored. Certificate SHA-256: $fingerprint"
} finally {
    Remove-Item Env:ANTCALC_RECOVERY_PASSWORD -ErrorAction SilentlyContinue
}
