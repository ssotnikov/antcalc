$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'release_key_helpers.ps1')
$folder = Join-Path ([IO.Path]::GetTempPath()) ('antcalc-password-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $folder | Out-Null
$file = Join-Path $folder 'password.dpapi'
$fixture = [Guid]::NewGuid().ToString('N')
$encoded = ConvertTo-SecureString $fixture -AsPlainText -Force | ConvertFrom-SecureString
try {
    foreach ($ending in @('', "`n", "`r`n")) {
        [IO.File]::WriteAllText($file, $encoded + $ending)
        $actual = Read-AntcalcReleasePassword -KeyDirectory $folder
        if ([Net.NetworkCredential]::new('', $actual).Password -cne $fixture) { throw 'Password round-trip failed.' }
    }
    foreach ($invalid in @('', 'not-an-encrypted-password', '123')) {
        [IO.File]::WriteAllText($file, $invalid)
        $rejected = $false
        try { Read-AntcalcReleasePassword -KeyDirectory $folder | Out-Null } catch { $rejected = $true }
        if (-not $rejected) { throw 'Malformed password file was accepted.' }
    }
    $insideProject = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) ('artifacts/private-key-test-' + [Guid]::NewGuid().ToString('N'))
    $rejected = $false
    try { New-AntcalcPrivateDirectory -Path $insideProject | Out-Null } catch { $rejected = $true }
    if (-not $rejected -or (Test-Path -LiteralPath $insideProject)) { throw 'Private key directory inside project was accepted.' }
    Write-Output 'PASS: DPAPI no newline/LF/CRLF; malformed data and private keys inside project rejected'
} finally {
    if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    Remove-Item -LiteralPath $folder
}
