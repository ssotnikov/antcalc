# Third-party notices

The application source, original antenna artwork, launcher/splash vectors and
store descriptions are Copyright (c) 2026 Sergey Sotnikov, under the root MIT
license. The application does not bundle or execute a separate reference binary.

## Runtime dependencies

[Resolved component inventory](distribution/licenses/dependencies.json) records
the exact releaseRuntimeClasspath coordinates, upstream POM URLs and declared
licenses. AndroidX, Kotlin and kotlinx components use Apache-2.0, except the
repackaged protobuf component in AndroidX DataStore, which declares BSD-3-Clause.

- [Apache License 2.0](distribution/licenses/Apache-2.0.txt)
- [DataStore's protobuf notice](distribution/licenses/Protobuf-BSD-3-Clause.txt),
  copied from the exact 1.2.1 source artifact

Dependencies retain their own copyrights and licenses. This inventory includes
resolved metadata/platform components as well as runtime implementations; it
does not claim that every resolved component contributes a separate binary.

## Formula illustrations

The seven bundled formula PNGs are rendered from the expressions in
`tools/render_formulas.py` using Matplotlib MathText's Computer Modern font set.
The fonts are not bundled as font binaries. Their upstream
[BaKoMa Fonts Licence](distribution/licenses/LICENSE_BAKOMA.txt) permits this use;
the original notice is retained voluntarily. Matplotlib is a development-time
renderer, not an Android runtime dependency. No internet images or downloaded
formula-rendering service are used by the app.

## Build tools

Gradle and its wrapper are Apache-2.0. Android SDK build tools and the JDK are
build-time dependencies installed separately under their respective terms.
They are not relicensed by this application or distributed inside its APK.
