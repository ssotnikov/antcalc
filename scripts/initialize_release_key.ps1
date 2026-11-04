[CmdletBinding()]
param(
    [string]$KeyDirectory = (Join-Path $env:USERPROFILE '.android/antcalc-release'),
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) { throw 'This initializer uses Windows user-scoped DPAPI.' }
if (-not $JdkHome) { throw 'Set JAVA_HOME to JDK 21.' }
$keytool = Join-Path $JdkHome 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'keytool not found.' }
if (Test-Path -LiteralPath $KeyDirectory) { throw 'Key directory already exists; existing release identities are never overwritten.' }
. (Join-Path $PSScriptRoot 'release_key_helpers.ps1')
$directory = New-AntcalcPrivateDirectory -Path $KeyDirectory
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
    $env:ANTCALC_KEY_PASSWORD = [Convert]::ToBase64String($bytes)
    $secure = ConvertTo-SecureString $env:ANTCALC_KEY_PASSWORD -AsPlainText -Force
    $secure | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $KeyDirectory 'password.dpapi') -Encoding ascii
    $keystore = Join-Path $KeyDirectory 'production.p12'
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias antcalc -keyalg RSA -keysize 4096 -validity 10950 -dname 'CN=Sergey Sotnikov' -storepass:env ANTCALC_KEY_PASSWORD -keypass:env ANTCALC_KEY_PASSWORD -noprompt
    if ($LASTEXITCODE) { throw 'Production key generation failed; preserve the directory for diagnosis.' }
    $certificate = Join-Path $KeyDirectory 'certificate.der'
    & $keytool -exportcert -keystore $keystore -alias antcalc -storepass:env ANTCALC_KEY_PASSWORD -file $certificate
    if ($LASTEXITCODE) { throw 'Public certificate export failed.' }
    $fingerprint = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
    @{ alias = 'antcalc'; certificate_sha256 = $fingerprint; subject = 'CN=Sergey Sotnikov'; password_storage = 'Windows DPAPI CurrentUser'; portable_backup = 'not yet confirmed' } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $KeyDirectory 'identity.json') -Encoding utf8
    Write-Output "Production key created outside Git. SHA-256 certificate: $fingerprint"
    Write-Output 'Create and verify a portable encrypted backup before publication.'
} finally {
    Remove-Item Env:ANTCALC_KEY_PASSWORD -ErrorAction SilentlyContinue
    [Array]::Clear($bytes, 0, $bytes.Length)
    $rng.Dispose()
}
