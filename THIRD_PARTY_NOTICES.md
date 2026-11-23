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

## OpenNEC

OpenNEC 2.4.3 is compiled into the application from [the v.2.4.3 source](https://github.com/maurymarkowitz/OpenNEC/tree/v.2.4.3). The complete upstream LICENSE follows.

**Copyright © 2026 Maury Markowitz**

[![MIT license](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)

This software is released under the MIT license:

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

https://opensource.org/license/mit

Original NEC-2 source licence:

Numerical Electromagnetics Code, Version 2 (NEC-2)
Written in FORTRAN by G. J. Burke and A. J. Poggio
Lawrence Livermore National Laboratory, 1981.

NEC-2 was released publicly without formal copyright restrictions
and is understood to be in the public domain. To the extent any
copyright may apply, this code is provided here with all rights
waived and may be used, modified, and redistributed without
restriction.
