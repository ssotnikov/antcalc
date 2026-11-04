[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Destination,
    [string]$KeyDirectory = (Join-Path $env:USERPROFILE '.android/antcalc-release'),
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) { throw 'Run under the Windows account that created the release key.' }
if (-not $JdkHome) { throw 'Set JAVA_HOME to JDK 21.' }
. (Join-Path $PSScriptRoot 'release_key_helpers.ps1')
$source = Read-AntcalcReleasePassword -KeyDirectory $KeyDirectory
$keytool = Join-Path $JdkHome 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) { throw 'keytool not found.' }
$Destination = [IO.Path]::GetFullPath($Destination)
if ((Test-Path -LiteralPath $Destination -PathType Container) -or [IO.Path]::GetExtension($Destination) -eq '') {
    $Destination = Join-Path $Destination 'antcalc-production.p12'
}
if (Test-Path -LiteralPath $Destination) { throw 'Backup file already exists; nothing was overwritten.' }
$projectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent)).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if ($Destination.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Store the private backup outside the project.' }
$parent = Split-Path $Destination -Parent
if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
$password = Read-Host 'Choose a strong backup password; store it separately from the backup' -AsSecureString
$confirmation = Read-Host 'Confirm backup password' -AsSecureString
$plain = [Net.NetworkCredential]::new('', $password).Password
if ($plain.Length -lt 16 -or $plain -cne [Net.NetworkCredential]::new('', $confirmation).Password) { throw 'Passwords differ or contain fewer than 16 characters.' }
try {
    $env:ANTCALC_SOURCE_PASSWORD = [Net.NetworkCredential]::new('', $source).Password
    $env:ANTCALC_BACKUP_PASSWORD = $plain
    & $keytool -importkeystore -srckeystore (Join-Path $KeyDirectory 'production.p12') -srcstoretype PKCS12 -srcalias antcalc -srcstorepass:env ANTCALC_SOURCE_PASSWORD -destkeystore $Destination -deststoretype PKCS12 -destalias antcalc -deststorepass:env ANTCALC_BACKUP_PASSWORD -destkeypass:env ANTCALC_BACKUP_PASSWORD -noprompt
    if ($LASTEXITCODE) { throw 'Backup export failed.' }
    # Read the backup back with the owner-supplied password; never print the password.
    $certificate = & $keytool -exportcert -rfc -keystore $Destination -alias antcalc -storepass:env ANTCALC_BACKUP_PASSWORD
    if ($LASTEXITCODE) { throw 'Backup verification failed.' }
    $base64 = ($certificate | Where-Object { $_ -notmatch '^-----' }) -join ''
    $hash = [Security.Cryptography.SHA256]::Create()
    try { $actual = ([BitConverter]::ToString($hash.ComputeHash([Convert]::FromBase64String($base64)))).Replace('-', '').ToLowerInvariant() } finally { $hash.Dispose() }
    $expected = (Get-Content -LiteralPath (Join-Path $KeyDirectory 'identity.json') -Raw | ConvertFrom-Json).certificate_sha256
    if ($actual -ne $expected) { throw 'Backup certificate differs from the production identity.' }
    @{ certificate_sha256 = $actual; backup_sha256 = (Get-FileHash -LiteralPath $Destination).Hash.ToLowerInvariant(); destination = $Destination; verified_utc = [DateTime]::UtcNow.ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $KeyDirectory 'backup-receipt.json') -Encoding utf8
    Write-Output "Encrypted portable backup verified. Certificate SHA-256: $actual"
    Write-Output "Backup file: $Destination"
} finally {
    Remove-Item Env:ANTCALC_SOURCE_PASSWORD,Env:ANTCALC_BACKUP_PASSWORD -ErrorAction SilentlyContinue
    $plain = $null
}
