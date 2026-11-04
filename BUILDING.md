# Сборка / Building

Нужны JDK 21, Android SDK platform 37 и Build Tools 36.0.0. Gradle 9.6.0
загружается wrapper с закреплённым SHA-256. Установите `JAVA_HOME` и `ANDROID_HOME`.
На Linux исходники и SDK должны находиться в Linux-файловой системе.

JDK 21, Android SDK platform 37 and Build Tools 36.0.0 are required. Set
`JAVA_HOME` and `ANDROID_HOME`. The wrapper pins Gradle 9.6.0 and its SHA-256.

## Unsigned release

```shell
python3 scripts/build_release.py build --output artifacts/fdroid/unsigned
```

Команда выполняет численные/JVM-тесты, сборку release, lint и сохраняет
реестр разрешённых runtime-зависимостей. Unsigned APK не предназначен для установки.
The command runs core/JVM tests, assembles release, runs lint and exports the
resolved runtime dependency inventory. An unsigned APK is not installable.

Эквивалентная Gradle-сборка без приватного ключа / equivalent unsigned Gradle build:

```shell
./gradlew --no-daemon --no-build-cache :calculator-core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease :app:writeReleaseDependencyInventory
```

APK: `app/build/outputs/apk/release/antcalc.apk`. Release disables debugging,
code shrinking and embedded VCS metadata. Version literals are in `app/build.gradle.kts`.

## Production signature

Для обоих магазинов используется один ключ и один подписанный APK.
Приватный ключ и пароли не нужны сборщику F-Droid и не хранятся в Git.
`distribution/signing.json` содержит только публичный fingerprint.

Both channels use the same signed APK. F-Droid receives no private key.
On Windows, initialize the owner-controlled identity once, then make a portable
encrypted backup with a separately stored password:

```powershell
.\scripts\initialize_release_key.ps1
.\scripts\backup_release_key.ps1 -Destination D:\KeyBackup
```

The initializer refuses to replace existing keys. DPAPI protects the automated
signing password for the current Windows user. `restore_release_key.ps1` restores
the portable backup into a new directory and checks the expected certificate.

Подписывать можно только unsigned APK из чистого checkout того же commit:

```powershell
.\scripts\sign_release.ps1 -UnsignedApk artifacts\fdroid\unsigned\antcalc.apk -OutputDirectory artifacts\release
```

The signing command verifies package/version, provenance and the production
certificate. It never falls back to the debug key. Passwords are passed through
process environment variables, not command arguments or logs. Copy the same
signed `antcalc.apk` to both channel packages; do not sign separate channel builds.

## Verification

```shell
python3 scripts/build_release.py verify --apk artifacts/release/antcalc.apk
```

F-Droid reproduction additionally requires two clean Linux builds and successful
APK signature copying against the upstream signed APK. A successful Gradle build
alone does not establish reproducibility. Keep tags immutable and use one
`vMAJOR.MINOR.PATCH` tag format. Debug installations use a different certificate
and cannot be silently replaced by the production app.

The signer preserves existing ZIP alignment (`--alignment-preserved true`).
Build Tools 35+ otherwise change alignment padding, breaking signature copying
with apksigcopier. Verify every candidate with the actual independent build:

```shell
apksigcopier compare --unsigned signed/antcalc.apk independent/antcalc.apk
python3 scripts/validate_store_metadata.py --code 16
```

Use the candidate's versionCode in the metadata command. `apksigner` must be
available on PATH for apksigcopier. A failed comparison blocks the shared-signature
F-Droid route; do not switch certificates to bypass it.

## Debug

Windows: `build.ps1` keeps its existing `artifacts/antcalc.apk` output.
It is separate from production channel artifacts.
