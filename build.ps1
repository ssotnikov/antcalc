[CmdletBinding()]
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$SdkHome = $env:ANDROID_HOME,
    [switch]$ConnectedTests
)

$ErrorActionPreference = 'Stop'

if (-not $JdkHome) {
    $jdkParent = Join-Path $env:ProgramFiles 'Android/openjdk'
    if (Test-Path -LiteralPath $jdkParent) {
        $candidate = Get-ChildItem -LiteralPath $jdkParent -Directory -Filter 'jdk-21*' |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($candidate) { $JdkHome = $candidate.FullName }
    }
}
if (-not $SdkHome) { $SdkHome = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/java.exe'))) {
    throw 'Укажите путь к JDK 21 через -JdkHome или JAVA_HOME.'
}
if (-not (Test-Path -LiteralPath (Join-Path $SdkHome 'platforms/android-37.0'))) {
    throw 'Не найдена платформа Android SDK 37. Укажите SDK через -SdkHome или ANDROID_HOME.'
}

function Copy-SourceDirectory([string]$Source, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination | Out-Null
    foreach ($entry in Get-ChildItem -LiteralPath $Source -Force) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Сборка не копирует ссылки: $($entry.Name)"
        }
        if ($entry.PSIsContainer) {
            if ($entry.Name -notin @('build', '.gradle', '.kotlin')) {
                Copy-SourceDirectory $entry.FullName (Join-Path $Destination $entry.Name)
            }
        } else {
            Copy-Item -LiteralPath $entry.FullName -Destination $Destination
        }
    }
}

function Assert-AndroidTestResults([string]$BuildRoot) {
    $resultDirectory = Join-Path $BuildRoot 'app/build/outputs/androidTest-results/connected'
    $reports = @(Get-ChildItem -LiteralPath $resultDirectory -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    if ($reports.Count -eq 0) { throw 'Нет результатов Android-тестов: успешного завершения Gradle недостаточно.' }
    $testCount = 0
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $result = $document.DocumentElement
        if ([int]$result.GetAttribute('failures') -gt 0 -or [int]$result.GetAttribute('errors') -gt 0) {
            throw "Android-тесты не прошли: $($report.FullName)"
        }
        $testCount += [int]$result.GetAttribute('tests')
    }
    if ($testCount -lt 1) { throw 'Android-тесты не запускались. Проверьте установку тестовых пакетов на устройстве.' }
    Write-Host "Android-тесты: $testCount, без ошибок."
}

$stagingRoot = Join-Path ([IO.Path]::GetTempPath()) ('antcalc-build-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stagingRoot | Out-Null
foreach ($name in @('app', 'calculator-core', 'gradle')) {
    Copy-SourceDirectory (Join-Path $PSScriptRoot $name) (Join-Path $stagingRoot $name)
}
foreach ($name in @('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stagingRoot
}

$previousJavaHome = $env:JAVA_HOME
$previousAndroidHome = $env:ANDROID_HOME
try {
    $env:JAVA_HOME = $JdkHome
    $env:ANDROID_HOME = $SdkHome
    Push-Location -LiteralPath $stagingRoot
    try {
        $buildTasks = @(':calculator-core:test', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintDebug')
        if ($ConnectedTests) { $buildTasks += ':app:connectedDebugAndroidTest' }
        & .\gradlew.bat @buildTasks --console plain
        if ($LASTEXITCODE -ne 0) {
            throw "Сборка не завершена. Диагностика сохранена: $stagingRoot"
        }
        if ($ConnectedTests) { Assert-AndroidTestResults $stagingRoot }
    } finally {
        Pop-Location
    }

    $artifacts = Join-Path $PSScriptRoot 'artifacts'
    New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
    $apk = Join-Path $stagingRoot 'app/build/outputs/apk/debug/antcalc.apk'
    $metadataFile = Join-Path $stagingRoot 'app/build/outputs/apk/debug/output-metadata.json'
    $metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
    $release = $metadata.elements | Where-Object outputFile -eq 'antcalc.apk' | Select-Object -First 1
    if (-not $release) { throw 'В метаданных сборки отсутствует antcalc.apk.' }
    & (Join-Path $SdkHome 'build-tools/36.0.0/apksigner.bat') verify $apk
    if ($LASTEXITCODE -ne 0) { throw 'Подпись APK не прошла проверку.' }
    Copy-Item -LiteralPath $apk -Destination (Join-Path $artifacts 'antcalc.apk') -Force
    Copy-Item -LiteralPath $metadataFile -Destination (Join-Path $artifacts 'apk-metadata.json') -Force
    Copy-Item -LiteralPath (Join-Path $stagingRoot 'app/build/reports/lint-results-debug.html') -Destination $artifacts -Force
    Copy-Item -LiteralPath (Join-Path $stagingRoot 'calculator-core/build/reports/tests/test') -Destination $artifacts -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $stagingRoot 'app/build/reports/tests/testDebugUnitTest') -Destination $artifacts -Recurse -Force
    if ($ConnectedTests) {
        Copy-Item -LiteralPath (Join-Path $stagingRoot 'app/build/reports/androidTests/connected') -Destination $artifacts -Recurse -Force
    }
    $hash = Get-FileHash -LiteralPath (Join-Path $artifacts 'antcalc.apk') -Algorithm SHA256
    Write-Host "APK: $($hash.Path)"
    Write-Host "Версия: $($release.versionName) (versionCode $($release.versionCode))"
    Write-Host "SHA-256: $($hash.Hash)"
    Write-Host "Отчёт тестов: $(Join-Path $artifacts 'test/index.html')"
    Write-Host "Временная сборка: $stagingRoot"
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_HOME = $previousAndroidHome
}
