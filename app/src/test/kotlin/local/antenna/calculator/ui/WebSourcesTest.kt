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
            "https://m0ukd.com/calculators/quarter-wave-ground-plane-antenna-calculator/",
            "https://www.rlocman.ru/shem/schematics.html?di=4982",
            "https://lib.qrz.ru/book/export/html/2182",
            "https://www.antenna-theory.com/tutorial/smith/chart.php",
            "https://rfic.eecs.berkeley.edu/courses/ee217sp05/lect5.pdf",
            "https://rutube.ru/video/fabc676744b9b80c73ed1f2c2eccad4e/",
            "https://www.youtube.com/watch?v=GDkbrUMCgKc",
            "https://radioprog.ru/post/1387",
            "https://ra1ohx.ru/load/biblioteka_radioljubitelja/radio_knigi/antenny_nastrojka_i_soglasovanie/8-1-0-44",
            "https://tz-lab.ru/calculators/smith-chart",
            "https://radioskot.com/calc/interaktivnaya-diagramma-smita",
            INVERTED_V_SOURCE_RU,
            RFI_DIELECTRICS_URL, RADIOLAB_CABLES_URL,
            "https://timesmicrowave.com/wp-content/uploads/2022/06/lmr-400-datasheet.pdf",
            "https://catalog.belden.com/techdata/EN/8240_techdata.pdf",
            "https://www.radiolab.ru/scalar/8dfbccapvc.htm",
            "https://www.arrl.org/files/file/Technology/tis/info/pdf/9106023.pdf",
            "https://ssotnikov.pro",
            "https://lizaalert.org/",
            "https://www.dg7ybn.de/144MHz/144_5elem.htm",
            LIZAALERT_YAGI_URL,
            "https://github.com/ssotnikov/antcalc/issues/new",
            "https://github.com/ssotnikov/antcalc/releases",
            "https://github.com/ssotnikov/antcalc/releases/tag/github-direct-v1.7.0",
            "https://t.me/ssotnikov",
        )) assertEquals(url, validatedSourceAddress(url))
    }

    @Test fun yagiProfilesUseApprovedSourcesAndUniqueStableIds() {
        val profiles = YagiProfile.entries
        assertEquals(7, profiles.map { it.id }.distinct().size)
        assertEquals(7, profiles.map { it.image }.distinct().size)
        for (profile in profiles) {
            assertEquals(profile.original, validatedSourceAddress(profile.original))
            profile.terms?.let { assertEquals(it, validatedSourceAddress(it)) }
            assertTrue(profile.hz.zipWithNext().all { (first, second) -> first < second })
        }
    }

    @Test fun unsafeOrAmbiguousUrisFailClosed() {
        for (url in listOf("", "http://www.arrl.org/", "javascript:alert(1)", "file:///sdcard/test",
            "content://www.arrl.org/test", "intent://www.arrl.org/", "https://www.arrl.org.evil.example/",
            "https://www.arrl.org@evil.example/", "https://evil.example@www.arrl.org/",
            "https://www.arrl.org:8443/", "https://www.arrl.org\\@evil.example/", "https://www.arrl.org /",
            "http://lizaalert.org/", "https://lizaalert.org.evil.example/", LIZAALERT_URL + "?next=unexpected",
            "https://www.dg7ybn.de/144MHz/144_5elem.htm?next=unexpected", "https://www.dg7ybn.de/other.htm",
            LIZAALERT_YAGI_URL + "?next=unexpected", "https://wiki.app.lizaalert.ru/s/unapproved",
            "https://ssotnikov.pro.evil.example/", "https://ssotnikov.pro@evil.example/", "http://ssotnikov.pro",
            "https://www.youtube.com/watch?v=unapproved", "https://www.antenna-theory.com/other",
            "https://www.antenna-theory.com/tutorial/smith/chart.php?utm_source=unexpected",
            "https://ruqrz.com/other", INVERTED_V_SOURCE_RU + "?q=unexpected",
            "https://products.rfi.com.au/", "https://radiolab.ru/", RFI_DIELECTRICS_URL + "&q=unexpected",
            RADIOLAB_CABLES_URL + "?q=unexpected",
            "https://catalog.belden.com/techdata/EN/8240_techdata.pdf?redirect=unexpected",
            "https://www.radiolab.ru/scalar/../other.htm",
            "https://t.me/other", "https://t.me/ssotnikov?text=unexpected",
            "https://github.com/ssotnikov/other/releases/tag/github-direct-v1.7.0",
            "https://github.com/ssotnikov/antcalc/releases/download/github-direct-v1.7.0/other.apk",
            "https://github.com/ssotnikov/antcalc/releases/tag/github-direct-v1.7.0?next=unsafe",
            "tg://resolve?domain=ssotnikov", "https://github.com/ssotnikov/other/issues/new", "https://github.com/ssotnikov/antcalc/issues/new?body=data",
            "https://www.arrl.org%2eevil.example/", "https://www.arrl.org/\n", "https://www.arrl.org/" + "a".repeat(3000))) {
            assertNull(url.take(80), validatedSourceAddress(url))
        }
    }
}
