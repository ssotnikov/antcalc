[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$UnsignedApk,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$ProjectDirectory = (Split-Path $PSScriptRoot -Parent),
    [string]$KeyDirectory = (Join-Path $env:USERPROFILE '.android/antcalc-release'),
    [string]$SdkHome = (Join-Path $env:LOCALAPPDATA 'Android/Sdk')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release_key_helpers.ps1')
$password = Read-AntcalcReleasePassword -KeyDirectory $KeyDirectory
try {
    $env:ANTCALC_STORE_PASSWORD = [Net.NetworkCredential]::new('', $password).Password
    $env:ANTCALC_RELEASE_KEYSTORE = Join-Path $KeyDirectory 'production.p12'
    python (Join-Path $PSScriptRoot 'build_release.py') sign --project $ProjectDirectory --sdk $SdkHome --unsigned $UnsignedApk --output $OutputDirectory
    if ($LASTEXITCODE) { throw 'Production signing failed.' }
} finally {
    Remove-Item Env:ANTCALC_STORE_PASSWORD,Env:ANTCALC_RELEASE_KEYSTORE -ErrorAction SilentlyContinue
}
