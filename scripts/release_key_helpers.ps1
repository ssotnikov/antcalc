$script:AntcalcReleaseProjectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent)).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar

function New-AntcalcPrivateDirectory {
    param([Parameter(Mandatory)][string]$Path)
    $Path = [IO.Path]::GetFullPath($Path)
    if ($Path.StartsWith($script:AntcalcReleaseProjectRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Private key directories must remain outside the project.' }
    if (Test-Path -LiteralPath $Path) { throw 'Key directory already exists; nothing was overwritten.' }
    $directory = New-Item -ItemType Directory -Path $Path
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($identity.User)
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity.User, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    Set-Acl -LiteralPath $directory.FullName -AclObject $acl
    return $directory
}

function Read-AntcalcReleasePassword {
    param([Parameter(Mandatory)][string]$KeyDirectory)
    $path = Join-Path $KeyDirectory 'password.dpapi'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Production password file is missing; do not replace or regenerate an existing release key.'
    }
    # Set-Content appends a line ending; DPAPI's hex decoder does not ignore it.
    [string]$raw = Get-Content -LiteralPath $path -Raw
    $encoded = $raw.Trim()
    if (-not $encoded -or $encoded -notmatch '^[0-9a-fA-F]+$' -or $encoded.Length % 2) {
        throw 'Invalid encrypted password file. Restore it from the approved key backup.'
    }
    try { return ConvertTo-SecureString $encoded -ErrorAction Stop }
    catch { throw 'Cannot decrypt the release password. Use the Windows account that created it or restore the portable backup.' }
}
