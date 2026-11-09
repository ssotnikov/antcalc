package local.antenna.calculator.ui

import org.junit.Assert.*
import org.junit.Test

class WebSourcesTest {
    @Test fun approvedHttpsSourcesAreAccepted() {
        for (url in listOf(
            "https://physics.nist.gov/cuu/Constants/Value/c.html",
            "https://rsgb.org/main/get-started-in-amateur-radio/antennas/your-first-antenna-the-half-wave-dipole/",
            "https://www.arrl.org/single-band-dipoles",
            "https://www.ti.com/lit/an/swra161b/swra161b.pdf",
            "https://www.n1fd.org/2022/08/18/dipole-antenna/",
            "https://ssotnikov.pro",
            "https://github.com/ssotnikov/antcalc/issues/new",
        )) assertEquals(url, validatedSourceAddress(url))
    }

    @Test fun unsafeOrAmbiguousUrisFailClosed() {
        for (url in listOf("", "http://www.arrl.org/", "javascript:alert(1)", "file:///sdcard/test",
            "content://www.arrl.org/test", "intent://www.arrl.org/", "https://www.arrl.org.evil.example/",
            "https://www.arrl.org@evil.example/", "https://evil.example@www.arrl.org/",
            "https://www.arrl.org:8443/", "https://www.arrl.org\\@evil.example/", "https://www.arrl.org /",
            "https://ssotnikov.pro.evil.example/", "https://ssotnikov.pro@evil.example/", "http://ssotnikov.pro",
            "https://github.com/ssotnikov/other/issues/new", "https://github.com/ssotnikov/antcalc/issues/new?body=data",
            "https://www.arrl.org%2eevil.example/", "https://www.arrl.org/\n", "https://www.arrl.org/" + "a".repeat(3000))) {
            assertNull(url.take(80), validatedSourceAddress(url))
        }
    }
}
