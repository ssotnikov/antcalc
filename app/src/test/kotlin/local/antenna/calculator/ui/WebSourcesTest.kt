package local.antenna.calculator.ui

import local.antenna.calculator.nec.OpenNec
import org.junit.Assert.*
import org.junit.Test

class WebSourcesTest {
    @Test fun feedlineMethodUsesOnlyTheApprovedArticle() {
        assertEquals(FEEDLINE_LOSS_METHOD_URL, validatedSourceAddress(FEEDLINE_LOSS_METHOD_URL))
        assertNull(validatedSourceAddress(FEEDLINE_LOSS_METHOD_URL.replace("https:", "http:")))
        assertNull(validatedSourceAddress(FEEDLINE_LOSS_METHOD_URL + "other/"))
    }

    @Test fun necExamplesUseOnlyTheirExactReferences() {
        assertEquals(NGSOLVE_PML_SOURCE, validatedSourceAddress(NGSOLVE_PML_SOURCE))
        assertEquals(ARCHIVED_OPENNEC_SOURCE, validatedSourceAddress(ARCHIVED_OPENNEC_SOURCE))
        assertNull(validatedSourceAddress("$NGSOLVE_PML_SOURCE?other=true"))
        assertNull(validatedSourceAddress(ARCHIVED_OPENNEC_SOURCE.replace("https://", "http://")))
    }

    @Test fun necLinksPermitOnlyTheApprovedAddresses() {
        assertEquals("https://www.nec2.org/", validatedSourceAddress(NEC2_URL))
        assertEquals(OpenNec.SOURCE, validatedSourceAddress(OpenNec.SOURCE))
        assertNull(validatedSourceAddress("https://www.nec2.org/other"))
        assertNull(validatedSourceAddress("http://www.nec2.org/"))
    }

    @Test fun moxonAuthorLinkUsesOnlyTheVerifiedPage() {
        val url = "https://dx-antennas.com/wire-antennas/2el-moxon/"
        assertEquals(url, MoxonSource.CBWIRE.url)
        assertEquals(url, validatedSourceAddress(url))
        assertNull(validatedSourceAddress("https://dx-antennas.com/wire-antennas/other/"))
    }

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
            "https://radio3rt.ru/2-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2%D0%B0%D1%8F-%D0%BF%D0%BE%D0%BB%D1%83%D0%B2%D0%BE%D0%BB%D0%BD%D0%BE%D0%B2%D0%B0%D1%8F-%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0/",
            "https://radio3rt.ru/%D0%B0%D0%B2%D0%B0%D1%80%D0%B8%D0%B9%D0%BD%D0%B0%D1%8F-%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-%D0%BA%D0%B2-%D0%B8-%D1%83%D0%BA%D0%B2/",
            "https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-slim-jim-%D0%B4%D0%BB%D1%8F-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD%D0%B0-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/",
            "https://www.infotechcomms.co.uk/downloads/10m_Slim_Jim.pdf",
            "https://radio3rt.ru/%D0%BC%D0%BE%D0%B4%D0%B8%D1%84%D0%B8%D0%BA%D0%B0%D1%86%D0%B8%D1%8F-%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D1%8B-slim-jim-%D0%B4%D0%BB%D1%8F-20-%D0%BC-4x4-slimtenna/",
            "https://www.iarc.org/wp-content/uploads/2025/04/2007-06.pdf",
            "https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-j-%D0%B2%D0%B5%D1%80%D1%82%D0%B8%D0%BA%D0%B0%D0%BB-%D0%BD%D0%B0-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/",
            "https://www.iw5edi.com/ham-radio/6250/j-vertical-antenna-for-10-meters-band",
            SLIM_JIM_DU1ANV_URL, SLIM_JIM_M0UKD_URL, SLIM_JIM_CEBIK_URL, SLIM_JIM_KX4O_URL,
            "https://www.mdpi.com/1996-1073/14/21/7042",
            "https://ntrs.nasa.gov/api/citations/20090029239/downloads/20090029239.pdf",
            "https://github.com/ssotnikov/antcalc/issues/new",
            "https://github.com/ssotnikov/antcalc/releases",
            "https://github.com/ssotnikov/antcalc/releases/tag/github-direct-v1.7.0",
            "https://t.me/ssotnikov",
        )) assertEquals(url, validatedSourceAddress(url))
    }

    @Test fun antennaMeasurementReferencesPermitOnlyTheirExactHttpsAddresses() {
        for (url in listOf(
            "https://web.archive.org/web/20231114044718id_/http://theleggios.net/wb2hol/projects/rdf/tape_bm.htm",
            "https://nt1k.com/vhf-3el-tape-measure-yagi/",
            "https://nt1k.com/wp-content/uploads/2012/11/3L-Tape-Measure-Yagi-Assy.pdf",
            "https://www.printables.com/model/1169207-tape-measure-yagi-antenna-2m",
            "https://github.com/jopecl/wifi-antenna-design-nec",
            "https://www.ieice.org/cs/isap/ISAP_Archives/2010/pdf/91.pdf",
            "https://edu.tusur.ru/publications/2796",
        )) {
            assertEquals(url, validatedSourceAddress(url))
            assertNull(validatedSourceAddress(url.replaceFirst("https:", "http:")))
            assertNull(validatedSourceAddress("$url?redirect=unexpected"))
        }
    }

    @Test fun yagiProfilesUseApprovedSourcesAndUniqueStableIds() {
        val profiles = YagiProfile.entries
        assertEquals(profiles.size, profiles.map { it.id }.distinct().size)
        val restrictedIds = profiles.filter { it.design.restricted && it.design.available }.map { it.id }.toSet()
        assertTrue(restrictedIds.isEmpty() || restrictedIds == setOf("ybn_2_5m", "gtv_2_6m"))
        for (profile in profiles) {
            assertEquals(profile.original, validatedSourceAddress(profile.original))
            profile.drivenCutSource?.let { assertEquals(it, validatedSourceAddress(it)) }
            profile.terms?.let { assertEquals(it, validatedSourceAddress(it)) }
            assertTrue(profile.hz.zipWithNext().all { (first, second) -> first < second })
        }
    }

    @Test fun hb9cvPublicationsPermitOnlyTheirExactHttpsAddresses() {
        val addresses = Hb9cvSource.entries.map { it.url }
        assertEquals(addresses.size, addresses.distinct().size)
        for (url in addresses) {
            assertEquals(url, validatedSourceAddress(url))
            assertNull(validatedSourceAddress(url.replaceFirst("https:", "http:")))
            val unexpected = if ('?' in url) "&redirect=unexpected" else "?redirect=unexpected"
            assertNull(validatedSourceAddress(url.substringBefore('#') + unexpected))
        }
    }

    @Test fun cantennaPublicationsUseExactApprovedHttpsAddresses() {
        val addresses = CantennaSource.entries.flatMap { listOfNotNull(it.url, it.termsUrl) }
        for (url in addresses) {
            assertEquals(url, validatedSourceAddress(url))
            assertNull(validatedSourceAddress(url.replaceFirst("https:", "http:")))
            assertNull(validatedSourceAddress("$url?redirect=unexpected"))
        }
    }

    @Test fun haloPublicationsUseExactApprovedHttpsAddresses() {
        for (url in haloSourceAddresses) {
            assertEquals(url, validatedSourceAddress(url))
            assertNull(validatedSourceAddress(url.replaceFirst("https:", "http:")))
            assertNull(validatedSourceAddress("$url?redirect=unexpected"))
        }
    }

    @Test fun onlyTheConfiguredAuthorSupportAddressIsAllowed() {
        authorSupportPaymentUrl?.let {
            assertEquals(it, validatedSourceAddress(it))
            assertNull(validatedSourceAddress("$it?next=unexpected"))
            assertNull(validatedSourceAddress(it.replace("https:", "http:")))
        }
        assertNull(validatedSourceAddress("https://finance.ozon.ru/apps/sbp/ozonbankpay/"))
        assertNull(validatedSourceAddress("https://finance.ozon.ru/apps/sbp/ozonbankpay/unapproved"))
    }

    @Test fun privacyPolicyPermitsOnlyThePublishedAddress() {
        assertEquals(PRIVACY_POLICY_URL, validatedSourceAddress(PRIVACY_POLICY_URL))
        assertNull(validatedSourceAddress(PRIVACY_POLICY_URL + "?extra=1"))
        assertNull(validatedSourceAddress("https://antcalc.ssotnikov.pro/"))
    }

    @Test fun userGuidePermitsOnlyThePublishedExampleDownloads() {
        assertEquals(4, guideExampleUrls.size)
        for (url in guideExampleUrls) {
            assertEquals(url, validatedSourceAddress(url))
            assertNull(validatedSourceAddress("$url?redirect=unexpected"))
        }
        assertNull(validatedSourceAddress("https://github.com/ssotnikov/antcalc/raw/refs/heads/main/examples/other.s1p"))
    }

    @Test fun guideRoutesAndAssetsRequireExactBundledAddresses() {
        assertEquals(GuideDestination.YAGI_SEGMENTATION, guideDestination("$GUIDE_ORIGIN/open/yagi-segmentation"))
        assertEquals("images/yagi-segmentation.svg", guideAssetPath("$GUIDE_ORIGIN/userguide/images/yagi-segmentation.svg"))
        assertNull(guideAssetPath("$GUIDE_ORIGIN/userguide/images/other.svg"))
        assertEquals(DK7ZB_SEGMENTATION_SOURCE, validatedSourceAddress(DK7ZB_SEGMENTATION_SOURCE))
        assertEquals(GuideDestination.MOXON, guideDestination("$GUIDE_ORIGIN/open/moxon"))
        assertEquals("ru.html", guideAssetPath("$GUIDE_ORIGIN/userguide/ru.html"))
        assertEquals("images/en/08-yagi.webp", guideAssetPath("$GUIDE_ORIGIN/userguide/images/en/08-yagi.webp"))
        assertEquals("images/ru/light/08-yagi.webp", guideAssetPath("$GUIDE_ORIGIN/userguide/images/ru/light/08-yagi.webp"))
        val invalid = listOf(
            "$GUIDE_ORIGIN/open/settings?anything", "$GUIDE_ORIGIN/open/%73ettings",
            "$GUIDE_ORIGIN/open/settings#fragment", "$GUIDE_ORIGIN.evil.example/open/settings",
            "$GUIDE_ORIGIN/userguide/../ru.html", "$GUIDE_ORIGIN/userguide/images/en/%2e%2e/test.webp",
            "file:///android_asset/ru.html", "content://local.antenna.calculator/ru.html",
        )
        for (address in invalid) {
            assertNull(address, guideDestination(address))
            assertNull(address, guideAssetPath(address))
        }
    }

    @Test fun guideUsesTheSameThemeForThumbnailsAndFullscreenImages() {
        val dark = """<a href="images/ru/08-yagi.webp"><img src="images/ru/08-yagi.webp"></a>"""
        val light = """<a href="images/ru/light/08-yagi.webp"><img src="images/ru/light/08-yagi.webp"></a>"""
        assertEquals(dark, guideImageReferences(dark, dark = true))
        assertEquals(light, guideImageReferences(dark, dark = false))
    }

    @Test fun unsafeOrAmbiguousUrisFailClosed() {
        for (url in listOf("", "http://www.arrl.org/", "javascript:alert(1)", "file:///sdcard/test",
            "content://www.arrl.org/test", "intent://www.arrl.org/", "https://www.arrl.org.evil.example/",
            "https://www.arrl.org@evil.example/", "https://evil.example@www.arrl.org/",
            "https://www.arrl.org:8443/", "https://www.arrl.org\\@evil.example/", "https://www.arrl.org /",
            "http://lizaalert.org/", "https://lizaalert.org.evil.example/", LIZAALERT_URL + "?next=unexpected",
            "https://www.dg7ybn.de/144MHz/144_5elem.htm?next=unexpected", "https://www.dg7ybn.de/other.htm",
            LIZAALERT_YAGI_URL + "?next=unexpected", "https://wiki.app.lizaalert.ru/s/unapproved",
            "https://www.mdpi.com/1996-1073/14/21/7042?next=unexpected", "https://ntrs.nasa.gov/api/citations/20090029239/downloads/20090029239.pdf?next=unexpected",
            "https://www.mdpi.com/other", "https://ntrs.nasa.gov/other",
            "https://ssotnikov.pro.evil.example/", "https://ssotnikov.pro@evil.example/", "http://ssotnikov.pro",
            "https://www.youtube.com/watch?v=unapproved", "https://www.antenna-theory.com/other",
            "https://www.antenna-theory.com/tutorial/smith/chart.php?utm_source=unexpected",
            "https://ruqrz.com/other", INVERTED_V_SOURCE_RU + "?q=unexpected",
            "https://products.rfi.com.au/", "https://radiolab.ru/", RFI_DIELECTRICS_URL + "&q=unexpected",
            RADIOLAB_CABLES_URL + "?q=unexpected",
            "https://catalog.belden.com/techdata/EN/8240_techdata.pdf?redirect=unexpected",
            "https://www.radiolab.ru/scalar/../other.htm",
            SLIM_JIM_DU1ANV_URL + "?next=unexpected", "https://www.qsl.net/other.pdf",
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
