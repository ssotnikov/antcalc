package local.antenna.calculator.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import local.antenna.calculator.R
import local.antenna.calculator.nec.OpenNec
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class SourceOpenResult { OPENED, NO_BROWSER, BLOCKED }

internal const val NGSOLVE_PML_SOURCE = "https://docu.ngsolve.org/latest/how_to/pml.html"
internal const val ARCHIVED_OPENNEC_SOURCE = "https://github.com/maurymarkowitz/OpenNEC/tree/v.2.4.2"
internal const val INVERTED_V_SOURCE_RU = "https://ruqrz.com/petlevoj-vibrator-v-antenne-inverted-v-2/"
internal const val RFI_DIELECTRICS_URL = "https://products.rfi.com.au/hubfs/Wireless%20download%20library/Coaxial%20Cable%20Dielectrics%20%20Velocity%20of%20Propagation%20and%20Attenuation.pdf?hsLang=en"
internal const val FEEDLINE_LOSS_METHOD_URL = "https://timesmicrowave.com/interpretation-of-electrical-test-data-with-regards-to-microwave-cable-assemblies/"
internal const val RADIOLAB_CABLES_URL = "https://radiolab.ru/ru/5/1188382333/"
private val directReleaseAddress = Regex(
    """https://github\.com/ssotnikov/antcalc/releases/tag/github-direct-v[0-9]{1,9}\.[0-9]{1,9}\.[0-9]{1,9}""",
)
private val cableDatasheetAddress = Regex(
    """https://(?:timesmicrowave\.com/wp-content/uploads/[0-9]{4}/[0-9]{2}/[A-Za-z0-9-]+\.pdf|catalog\.belden\.com/techdata/(?:en|EN)/[0-9]+_techdata\.pdf|www\.radiolab\.ru/(?:ru/3/[0-9]+/[0-9]+/[0-9]+\.php|scalar/[a-z0-9]+\.htm))""",
)
internal const val SLIM_JIM_DU1ANV_URL = "https://www.qsl.net/n1ea/slimjim_du1anv.pdf"
internal const val SLIM_JIM_OK1IKE_URL = "https://ok1ike.nagano.cz/soubory/nouz_ant2.htm"
internal const val OK1IKE_HB9ACC_URL = "https://www.e-periodica.ch/digbib/view?pid=hbr-002%3A2011%3A79%3A%3A248"
internal const val SLIM_JIM_SV1JRF_URL = "https://sv1jrf.blogspot.com/2016/02/slim-jim-uhf-antenna-desi.html?m=1"
internal const val SLIM_JIM_M0UKD_URL = "https://m0ukd.com/calculators/slim-jim-and-j-pole-calculator/"
internal const val SLIM_JIM_KX4O_URL = "https://www.hamradio.me/antennas/slimjim-vs-traditional-j-pole-antenna.html"
internal const val SLIM_JIM_CEBIK_URL = "https://antenna2.github.io/cebik/content/vhf/sj.html"

private val moxonSourceAddresses = MoxonSource.entries.map { it.url }.toSet()
private val hb9cvSourceAddresses = Hb9cvSource.entries.map { it.url }.toSet()

internal const val NT1K_TAPE_YAGI_URL = "https://nt1k.com/vhf-3el-tape-measure-yagi/"
internal const val NT1K_TAPE_YAGI_DRAWING_URL = "https://nt1k.com/wp-content/uploads/2012/11/3L-Tape-Measure-Yagi-Assy.pdf"
internal const val NT1K_TAPE_YAGI_MEASUREMENT_URL = "https://nt1k.com/wp-content/uploads/2012/11/3ElyagiVNA.jpg"
internal const val HACKERMAGNET_TAPE_YAGI_URL = "https://www.printables.com/model/1169207-tape-measure-yagi-antenna-2m"
internal const val YBN_DUBUS_URL = "https://www.ok2kkw.com/next/dubus412w.pdf"
internal const val DG7YBN_BENT_DIPOLE_URL = "https://dg7ybn.de/Bent_DE/Bent_DE.htm#Online_Calc"
internal const val DK7ZB_DUOBAND_ARTICLE_URL = "https://www.worldradiohistory.com/INTERNATIONAL/Funkschau/FunkAmateur-2008-06.pdf"

internal const val NUXCOM_DK7ZB_URL = "https://web.archive.org/web/20241110203403id_/http://www.nuxcom.de/pdf/nuxcom_bauanweisungen_144_430_50hm.pdf"

private val additionalSourceAddresses = setOf(
    FEEDLINE_LOSS_METHOD_URL,
    NUXCOM_DK7ZB_URL,
    "https://github.com/jopecl/wifi-antenna-design-nec",
    "https://www.ieice.org/cs/isap/ISAP_Archives/2010/pdf/91.pdf",
    "https://edu.tusur.ru/publications/2796",
    YBN_DUBUS_URL,
    DG7YBN_BENT_DIPOLE_URL,
    DK7ZB_DUOBAND_ARTICLE_URL,
    "https://radio3rt.ru/2-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2%D0%B0%D1%8F-%D0%BF%D0%BE%D0%BB%D1%83%D0%B2%D0%BE%D0%BB%D0%BD%D0%BE%D0%B2%D0%B0%D1%8F-%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0/",
    "https://radio3rt.ru/%D0%B0%D0%B2%D0%B0%D1%80%D0%B8%D0%B9%D0%BD%D0%B0%D1%8F-%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-%D0%BA%D0%B2-%D0%B8-%D1%83%D0%BA%D0%B2/",
    "https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-slim-jim-%D0%B4%D0%BB%D1%8F-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD%D0%B0-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/",
    "https://www.infotechcomms.co.uk/downloads/10m_Slim_Jim.pdf",
    "https://g0kya.blogspot.com/2011/08/10m-slim-jim-antenna.html",
    "https://radio3rt.ru/%D0%BC%D0%BE%D0%B4%D0%B8%D1%84%D0%B8%D0%BA%D0%B0%D1%86%D0%B8%D1%8F-%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D1%8B-slim-jim-%D0%B4%D0%BB%D1%8F-20-%D0%BC-4x4-slimtenna/",
    "https://www.iarc.org/wp-content/uploads/2025/04/2007-06.pdf",
    "https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-j-%D0%B2%D0%B5%D1%80%D1%82%D0%B8%D0%BA%D0%B0%D0%BB-%D0%BD%D0%B0-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/",
    "https://www.iw5edi.com/ham-radio/6250/j-vertical-antenna-for-10-meters-band",
    "https://www.iarc.org/wp-content/uploads/2025/04/2007-06.pdf#page=59",
    SLIM_JIM_DU1ANV_URL,
    SLIM_JIM_OK1IKE_URL,
    OK1IKE_HB9ACC_URL,
    SLIM_JIM_SV1JRF_URL,
    SLIM_JIM_M0UKD_URL,
    SLIM_JIM_KX4O_URL,
    "https://www.mdpi.com/1996-1073/14/21/7042",
    "https://ntrs.nasa.gov/api/citations/20090029239/downloads/20090029239.pdf",
    "https://www.dg7ybn.de/144MHz/144_5elem.htm",
    LIZAALERT_YAGI_URL,
    "https://www.dg7ybn.de/",
    "https://www.dg7ybn.de/144MHz/144_GTV2_6m.htm",
    "https://dk7zb.darc.de/",
    DK7ZB_SEGMENTATION_SOURCE,
    "https://dk7zb.darc.de/PVC-Yagis/3-Ele-2m.htm",
    "https://dk7zb.darc.de/PVC-Yagis/PVC-details.htm",
    "https://dk7zb.darc.de/70cm/70cm-kurz.htm",
    "https://dk7zb.darc.de/70cm/5.htm",
    "https://dk7zb.darc.de/Duoband/duoband_2-70_2-3.htm",
    "https://antenna2.github.io/cebik/content/vhf/jp1.html",
    "https://antenna2.github.io/cebik/content/vhf/jp3.html",
    "https://eax.me/2020/2020-01-20-j-pole-antenna.html",
    "https://www.cqham.ru/ant86_30.htm",
    "https://www.qsl.net/s/sv1ui/2mjpole/index.html",
    "https://www.qsl.net/dk7zb/J_Pole/wiremanjpole.htm",
    SLIM_JIM_CEBIK_URL,
    "https://antenna2.github.io/cebik/content/vhf/jp4.html",
    "https://web.archive.org/web/20231114044718id_/http://theleggios.net/wb2hol/projects/rdf/tape_bm.htm",
    NT1K_TAPE_YAGI_URL,
    NT1K_TAPE_YAGI_DRAWING_URL,
    NT1K_TAPE_YAGI_MEASUREMENT_URL,
    HACKERMAGNET_TAPE_YAGI_URL,
    "https://nt1k.com/open-stub-j-pole-project-completed-many-times/",
    "https://www.radioman.ru/shem/anteny/an-kvad.php",
    "https://gef79.narod.ru/Shem/Antens/119.htm",
    "https://ra1ohx.ru/publ/antenny_kv/antenna_delta_loop_diapazona_20_metrov/1-1-0-549",
    "https://courses.ece.vt.edu/ece3105/chp2_lssn6.html",
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
)

internal const val GITHUB_RELEASES_URL = "https://github.com/ssotnikov/antcalc/releases"
internal const val LIZAALERT_URL = "https://lizaalert.org/"
internal const val LIZAALERT_YAGI_URL = "https://wiki.app.lizaalert.ru/s/8a0ac7fb-4e86-4453-8778-5a45f9f0ca3e"
internal const val BUG_REPORT_URL = "https://github.com/ssotnikov/antcalc/issues/new"
internal const val PRIVACY_POLICY_URL = "https://antcalc.ssotnikov.pro/privacy/"
internal const val NEC2_URL = "https://www.nec2.org/"
private const val AUTHOR_TELEGRAM = "tg://resolve?domain=ssotnikov"
internal const val AUTHOR_TELEGRAM_WEB = "https://t.me/ssotnikov"

internal fun openAuthorContact(context: Context) {
    if (dispatchExternal(context, AUTHOR_TELEGRAM) != SourceOpenResult.OPENED) {
        openSourceWithFeedback(context, AUTHOR_TELEGRAM_WEB)
    }
}

internal fun openSourceWithFeedback(context: Context, url: String) {
    showSourceOpenResult(context, openWebSource(context, url))
}

/** User-authored journal links have arbitrary hosts; keep them separate from curated sources. */
internal fun validatedRecordLinkAddress(address: String): String? {
    if (address.length > 2048) return null
    val uri = try { URI(address) } catch (_: URISyntaxException) { return null }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return null
    return uri.toASCIIString()
}

internal fun openRecordLinkWithFeedback(context: Context, address: String) {
    val validated = validatedRecordLinkAddress(address)
    showSourceOpenResult(context, if (validated == null) SourceOpenResult.BLOCKED else dispatchExternal(context, validated))
}

private fun showSourceOpenResult(context: Context, result: SourceOpenResult) {
    val message = when (result) {
        SourceOpenResult.OPENED -> null
        SourceOpenResult.NO_BROWSER -> R.string.browser_unavailable
        SourceOpenResult.BLOCKED -> R.string.link_open_failed
    }
    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** Strict parsing avoids discrepancies between permissive URI parsers and browser routing. */
internal fun validatedSourceAddress(address: String): String? {
    if (address.length > 2048) return null
    val uri = try { URI(address) } catch (_: URISyntaxException) { return null }
    val allowedHosts = setOf(
        "physics.nist.gov", "rsgb.org", "www.arrl.org", "www.n1fd.org", "ssotnikov.pro",
        "www.itu.int", "www.rigexpert.com", "www.kadamm.com", "scdn.rohde-schwarz.com",
        "eng.libretexts.org", "www.analog.com", "helpfiles.keysight.com",
        "radiojove.gsfc.nasa.gov", "practicalantennas.com",
    )
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || uri.rawUserInfo != null ||
        uri.port !in setOf(-1, 443)) return null
    val url = uri.toASCIIString()
    if (url == authorSupportPaymentUrl) return url
    if (url == PRIVACY_POLICY_URL) return url
    if (url == NEC2_URL || url == OpenNec.SOURCE || url == ARCHIVED_OPENNEC_SOURCE ||
        url == NGSOLVE_PML_SOURCE || url in licenseSourceAddresses) return url
    if (url in guideExampleUrls) return url
    return when (url) {
        YAGI_SCALING_URL, YAGI_SCALING_BOOK, BUG_REPORT_URL, LIZAALERT_URL, GITHUB_RELEASES_URL, AUTHOR_TELEGRAM_WEB, INVERTED_V_SOURCE_RU, RFI_DIELECTRICS_URL, RADIOLAB_CABLES_URL -> url
        else -> url.takeIf { it in additionalSourceAddresses || it in moxonSourceAddresses || it in hb9cvSourceAddresses ||
            it in haloSourceAddresses || it in trapPublications ||
            it == TRAP_WHEELER_URL || it == TRAP_ARRL_URL || it == TRAP_ZL1BJQ_URL ||
            CantennaSource.entries.any { source -> source.url == it || source.termsUrl == it } ||
            directReleaseAddress.matches(it) ||
            cableDatasheetAddress.matches(it) || uri.host?.lowercase(Locale.ROOT) in allowedHosts }
    }
}

internal fun openWebSource(context: Context, address: String): SourceOpenResult {
    val validated = validatedSourceAddress(address) ?: return SourceOpenResult.BLOCKED
    return dispatchExternal(context, validated)
}

private fun dispatchExternal(context: Context, address: String): SourceOpenResult {
    val intent = Intent(Intent.ACTION_VIEW, address.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        SourceOpenResult.OPENED
    } catch (_: ActivityNotFoundException) {
        SourceOpenResult.NO_BROWSER
    } catch (_: SecurityException) {
        SourceOpenResult.BLOCKED
    }
}
