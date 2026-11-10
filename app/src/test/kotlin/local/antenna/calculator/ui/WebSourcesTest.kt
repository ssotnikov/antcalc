package local.antenna.calculator.ui

import org.junit.Assert.*
import org.junit.Test

class WebSourcesTest {
    @Test fun approvedHttpsSourcesAreAccepted() {
        for (url in listOf(
            "https://physics.nist.gov/cuu/Constants/Value/c.html",
            "https://rsgb.org/main/get-started-in-amateur-radio/antennas/your-first-antenna-the-half-wave-dipole/",
            "https://www.arrl.org/files/file/LabHandbook/RLH%20Unit%204.pdf#page=12",
            "https://www.itu.int/dms_pub/itu-d/opb/hdb/D-HDB-WET-2007-PDF-R.pdf#page=115",
            "https://radiojove.gsfc.nasa.gov/radio_telescope/manuals/RJ_ant_manual_2.0.pdf#page=8",
            "https://practicalantennas.com/designs/verticals/5eights/",
            "https://www.rigexpert.com/files/product_documentation/aa170/aa-170-manual_ru.pdf#page=19",
            "https://www.kadamm.com/ru/blog/ksvn-poteri-i-razvyazka-kak-chitat-kharakteristiki/",
            "https://scdn.rohde-schwarz.com/ur/pws/dl_downloads/dl_application/application_notes/1gp77/1GP77_8e_dB_Calculator.pdf#page=22",
            "https://eng.libretexts.org/Bookshelves/Electrical_Engineering/Electronics/Microwave_and_RF_Design_II_-_Transmission_Lines_%28Steer%29/02%253A_Transmission_Lines/2.02%253A_Transmission_Line_Theory",
            "https://www.analog.com/en/resources/analog-dialogue/raqs/2021/12/16/16/41/raq-issue-197.html",
            "https://helpfiles.keysight.com/csg/e5061b/measurement_with_options/option_005_impedance_analysis/setting_up_measurement/eliminating_error_factor_of_fixture/open_short_compensation_and_port_extension.htm",
            "https://www.n1fd.org/2022/08/18/dipole-antenna/",
            INVERTED_V_SOURCE_RU,
            "https://www.arrl.org/files/file/Technology/tis/info/pdf/9106023.pdf",
            "https://ssotnikov.pro",
            "https://github.com/ssotnikov/antcalc/issues/new",
            "https://t.me/ssotnikov",
        )) assertEquals(url, validatedSourceAddress(url))
    }

    @Test fun unsafeOrAmbiguousUrisFailClosed() {
        for (url in listOf("", "http://www.arrl.org/", "javascript:alert(1)", "file:///sdcard/test",
            "content://www.arrl.org/test", "intent://www.arrl.org/", "https://www.arrl.org.evil.example/",
            "https://www.arrl.org@evil.example/", "https://evil.example@www.arrl.org/",
            "https://www.arrl.org:8443/", "https://www.arrl.org\\@evil.example/", "https://www.arrl.org /",
            "https://ssotnikov.pro.evil.example/", "https://ssotnikov.pro@evil.example/", "http://ssotnikov.pro",
            "https://ruqrz.com/other", INVERTED_V_SOURCE_RU + "?q=unexpected",
            "https://t.me/other", "https://t.me/ssotnikov?text=unexpected",
            "tg://resolve?domain=ssotnikov", "https://github.com/ssotnikov/other/issues/new", "https://github.com/ssotnikov/antcalc/issues/new?body=data",
            "https://www.arrl.org%2eevil.example/", "https://www.arrl.org/\n", "https://www.arrl.org/" + "a".repeat(3000))) {
            assertNull(url.take(80), validatedSourceAddress(url))
        }
    }
}
