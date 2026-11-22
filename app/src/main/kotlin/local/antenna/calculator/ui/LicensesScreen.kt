package local.antenna.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R
import local.antenna.calculator.core.TrapDipole.Profile
import local.antenna.calculator.nec.OpenNec

private data class CreditedPublication(val title: Int, val url: String)
private data class PublicationTerms(val description: Int, val url: String)

private data class PublicationCredit(
    val author: Int,
    val publications: List<CreditedPublication>,
    val terms: PublicationTerms? = null,
)

private val publicationCredits = listOf(
    PublicationCredit(R.string.slim_jim_ok1ike_author, listOf(
        CreditedPublication(R.string.slim_jim_ok1ike_source, SLIM_JIM_OK1IKE_URL),
    )),
    PublicationCredit(R.string.jpole_ok1ike_hb9acc_author, listOf(
        CreditedPublication(R.string.jpole_ok1ike_hb9acc, OK1IKE_HB9ACC_URL),
    )),
    PublicationCredit(R.string.slim_jim_sv1jrf_title, listOf(
        CreditedPublication(R.string.slim_jim_sv1jrf_source, SLIM_JIM_SV1JRF_URL),
    )),
    PublicationCredit(R.string.licenses_author_1, listOf(
        CreditedPublication(R.string.licenses_work_1_1, LIZAALERT_YAGI_URL),
    ), PublicationTerms(R.string.licenses_r3udk_terms, LIZAALERT_YAGI_URL)),
    PublicationCredit(R.string.licenses_author_2, listOf(
        CreditedPublication(R.string.licenses_work_2_1, "https://www.dg7ybn.de/144MHz/144_5elem.htm"),
        CreditedPublication(R.string.ys_article, YAGI_SCALING_URL),
        CreditedPublication(R.string.licenses_ybn_dubus, YBN_DUBUS_URL),
        CreditedPublication(R.string.licenses_gtv_bending, DG7YBN_BENT_DIPOLE_URL),
        CreditedPublication(R.string.licenses_work_2_2, "https://www.dg7ybn.de/144MHz/144_GTV2_6m.htm"),
    ), PublicationTerms(R.string.yagi_dg_notice, "https://www.dg7ybn.de/")),
    PublicationCredit(R.string.ys_book_authors, listOf(CreditedPublication(R.string.ys_book, YAGI_SCALING_BOOK)),
        PublicationTerms(R.string.ys_book_rights, YAGI_SCALING_BOOK)),
    PublicationCredit(R.string.licenses_author_3, listOf(
        CreditedPublication(R.string.seg_source, DK7ZB_SEGMENTATION_SOURCE),
        CreditedPublication(R.string.licenses_work_3_1, "https://dk7zb.darc.de/PVC-Yagis/3-Ele-2m.htm"),
        CreditedPublication(R.string.licenses_work_3_2, "https://dk7zb.darc.de/70cm/5.htm"),
        CreditedPublication(R.string.licenses_work_3_3, "https://dk7zb.darc.de/Duoband/duoband_2-70_2-3.htm"),
        CreditedPublication(R.string.licenses_dk7zb_duoband_article, DK7ZB_DUOBAND_ARTICLE_URL),
        CreditedPublication(R.string.licenses_work_3_4, "https://dk7zb.darc.de/PVC-Yagis/PVC-details.htm"),
        CreditedPublication(R.string.licenses_work_3_5, "https://dk7zb.darc.de/70cm/70cm-kurz.htm"),
        CreditedPublication(R.string.licenses_work_3_6, "https://www.qsl.net/dk7zb/J_Pole/wiremanjpole.htm"),
    ), PublicationTerms(R.string.yagi_dk_rights, "https://dk7zb.darc.de/")),
    PublicationCredit(R.string.licenses_nuxcom_author, listOf(
        CreditedPublication(R.string.licenses_nuxcom_dk7zb, NUXCOM_DK7ZB_URL),
    ), PublicationTerms(R.string.licenses_nuxcom_terms, NUXCOM_DK7ZB_URL)),
    PublicationCredit(R.string.licenses_author_4, listOf(
        CreditedPublication(R.string.licenses_work_4_1, SLIM_JIM_DU1ANV_URL),
    )),
    PublicationCredit(R.string.licenses_author_5, listOf(
        CreditedPublication(R.string.licenses_work_5_1, SLIM_JIM_M0UKD_URL),
        CreditedPublication(R.string.licenses_work_5_2, "https://m0ukd.com/calculators/quarter-wave-ground-plane-antenna-calculator/"),
    )),
    PublicationCredit(R.string.licenses_author_6, listOf(
        CreditedPublication(R.string.licenses_work_6_1, "https://www.infotechcomms.co.uk/downloads/10m_Slim_Jim.pdf"),
        CreditedPublication(R.string.licenses_work_6_2, "https://g0kya.blogspot.com/2011/08/10m-slim-jim-antenna.html"),
    )),
    PublicationCredit(R.string.licenses_author_7, listOf(
        CreditedPublication(R.string.licenses_work_7_1, "https://www.iarc.org/wp-content/uploads/2025/04/2007-06.pdf#page=59"),
    )),
    PublicationCredit(R.string.licenses_author_8, listOf(
        CreditedPublication(R.string.licenses_work_8_1, "https://antenna2.github.io/cebik/content/vhf/jp1.html"),
        CreditedPublication(R.string.licenses_work_8_2, SLIM_JIM_CEBIK_URL),
        CreditedPublication(R.string.licenses_work_8_3, "https://antenna2.github.io/cebik/content/vhf/jp3.html"),
        CreditedPublication(R.string.licenses_work_8_4, "https://antenna2.github.io/cebik/content/vhf/jp4.html"),
    )),
    PublicationCredit(R.string.licenses_author_9, listOf(
        CreditedPublication(R.string.licenses_work_9_1, "https://www.iw5edi.com/ham-radio/6250/j-vertical-antenna-for-10-meters-band"),
        CreditedPublication(R.string.licenses_work_9_2, "https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-j-%D0%B2%D0%B5%D1%80%D1%82%D0%B8%D0%BA%D0%B0%D0%BB-%D0%BD%D0%B0-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/"),
    )),
    PublicationCredit(R.string.licenses_author_10, listOf(
        CreditedPublication(R.string.licenses_work_10_1, "https://practicalantennas.com/theory/loop/full-wave/"),
    )),
    PublicationCredit(R.string.licenses_author_11, listOf(
        CreditedPublication(R.string.licenses_work_11_1, "https://radioskot.com/calc/interaktivnaya-diagramma-smita"),
    )),
    PublicationCredit(R.string.licenses_author_12, listOf(
        CreditedPublication(R.string.licenses_work_12_1, "https://www.mdpi.com/1996-1073/14/21/7042"),
    ), PublicationTerms(R.string.licenses_cc_by_terms, "https://www.mdpi.com/1996-1073/14/21/7042")),
    PublicationCredit(R.string.licenses_author_13, listOf(
        CreditedPublication(R.string.licenses_work_13_1, "https://nt1k.com/open-stub-j-pole-project-completed-many-times/"),
        CreditedPublication(R.string.yagi_nt1k_article, NT1K_TAPE_YAGI_URL),
        CreditedPublication(R.string.yagi_nt1k_drawing, NT1K_TAPE_YAGI_DRAWING_URL),
        CreditedPublication(R.string.yagi_nt1k_open_measurement, NT1K_TAPE_YAGI_MEASUREMENT_URL),
    )),
    PublicationCredit(R.string.licenses_author_14, listOf(
        CreditedPublication(R.string.licenses_work_14_1, INVERTED_V_SOURCE_RU),
    )),
    PublicationCredit(R.string.licenses_author_15, listOf(
        CreditedPublication(R.string.licenses_work_15_1, "https://www.arrl.org/files/file/Technology/tis/info/pdf/9106023.pdf"),
    )),
    PublicationCredit(R.string.licenses_author_16, listOf(
        CreditedPublication(R.string.licenses_work_16_1, "https://ntrs.nasa.gov/api/citations/20090029239/downloads/20090029239.pdf"),
    )),
)

private val haloPublicationCredits = listOf(
    PublicationCredit(R.string.licenses_halo_cushcraft, listOf(
        CreditedPublication(R.string.licenses_halo_csq11,
            "https://www.worldradiohistory.com/Archive-DX/73-magazine/73-magazine-1964/73-magazine-06-june-1964.pdf"),
        CreditedPublication(R.string.licenses_halo_asq10, "https://moonrakeronline.com/us/amfile/file/download/file/68/product/7978/"),
    ), PublicationTerms(R.string.licenses_halo_asq10_terms, "https://moonrakeronline.com/us/amfile/file/download/file/68/product/7978/")),
    PublicationCredit(R.string.licenses_halo_buckwalter, listOf(
        CreditedPublication(R.string.licenses_halo_s9, "https://www.worldradiohistory.com/Archive-CB-Radio/S9-Magazine/S9-1964-12.pdf"),
    )),
    PublicationCredit(R.string.licenses_halo_milazzo, listOf(
        CreditedPublication(R.string.licenses_halo_kp4md, "https://www.qsl.net/kp4md/144_mhz_halo.htm"),
    )),
    PublicationCredit(R.string.licenses_author_8, listOf(
        CreditedPublication(R.string.licenses_halo_intloop, "https://antenna2.github.io/cebik/content/wire/intloop.pdf"),
    )),
    PublicationCredit(R.string.licenses_halo_roberts, listOf(
        CreditedPublication(R.string.licenses_halo_es, "https://www.kk5jy.net/Es-halo/"),
    ), PublicationTerms(R.string.licenses_halo_es_terms, "https://www.kk5jy.net/Es-halo/")),
    PublicationCredit(R.string.licenses_halo_aerial, listOf(
        CreditedPublication(R.string.licenses_halo_aerial_models, "https://3g-aerial.biz/vsenapravlennye-antenny-gorizontalnoj-polyarizatsii"),
        CreditedPublication(R.string.licenses_halo_aerial_gamma,
            "https://3g-aerial.biz/onlajn-raschety/dopolnitelnye-raschety/raschet-gamma-soglasuyushchego-ustrojstva"),
    ), PublicationTerms(R.string.hb9cv_aerial_terms, Hb9cvSource.AERIAL_TERMS.url)),
    PublicationCredit(R.string.licenses_halo_ra3poy, listOf(
        CreditedPublication(R.string.licenses_halo_w2eey,
            "https://forum.qrz.ru/40-antenny-kv/32796-chudnaya-antenna-w2eey-1-a.html"),
    )),
)

internal val haloSourceAddresses = haloPublicationCredits.flatMap { credit ->
    credit.publications.map { it.url } + listOfNotNull(credit.terms?.url)
}.toSet()

@Composable
private fun PublicationCreditCard(credit: PublicationCredit) {
    val context = LocalContext.current
    Column {
        SelectableText(
            stringResource(credit.author),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        credit.publications.forEach { publication ->
            WebLinkButton(
                onClick = { openSourceWithFeedback(context, publication.url) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(publication.title), modifier = Modifier.fillMaxWidth())
            }
        }
        credit.terms?.let { terms ->
            LicenseHelpText(terms.description)
            if (terms.description == R.string.yagi_dg_notice &&
                LocalConfiguration.current.locales[0].language == "ru") {
                LicenseHelpText(R.string.yagi_dg_rights)
            }
            WebLinkButton(onClick = { openSourceWithFeedback(context, terms.url) }) {
                Text(stringResource(R.string.licenses_author_terms))
            }
        }
    }
}

@Composable
internal fun TrapPublicationLinks(profile: Profile? = null) {
    val titles = listOf(R.string.trap_source_r2auk, R.string.trap_source_vk2evb, R.string.trap_source_aa4lr)
    val context = LocalContext.current
    val profiles = profile?.let(::listOf) ?: Profile.entries
    profiles.forEach {
        PublicationCreditCard(PublicationCredit(trapAuthors[it.ordinal],
            listOf(CreditedPublication(titles[it.ordinal], trapPublications[it.ordinal]))))
    }
    if (profile == null || profile == Profile.VK2EVB) {
        WebLinkButton(onClick = { openSourceWithFeedback(context, TRAP_ZL1BJQ_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.trap_zl1bjq_source), modifier = Modifier.fillMaxWidth())
        }
    }
    listOf(R.string.trap_arrl_source to TRAP_ARRL_URL, R.string.trap_wheeler_source to TRAP_WHEELER_URL).forEach { (title, url) ->
        WebLinkButton(onClick = { openSourceWithFeedback(context, url) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(title), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun HaloPublicationLinks() {
    haloPublicationCredits.forEach { PublicationCreditCard(it) }
}

@Composable
internal fun LicensesScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()
            .verticalScroll(rememberPageScrollState()).padding(20.dp).testTag("licenses-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LicenseHelpText(R.string.licenses_mit)
        OpenNecLicense()
        LicenseHelpText(R.string.licenses_intro)
        publicationCredits.forEach { PublicationCreditCard(it) }
        MoxonSource.entries.groupBy { it.author }.forEach { (author, sources) ->
            Column {
                SelectableText(author, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                sources.forEach { source ->
                    WebLinkButton(onClick = { openSourceWithFeedback(context, source.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(source.title, modifier = Modifier.fillMaxWidth())
                    }
                }
                sources.mapNotNull { it.terms }.distinct().forEach { LicenseHelpText(it) }
            }
        }
        Text(
            stringResource(R.string.hb9cv_sources_heading),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        Hb9cvSource.entries.groupBy { it.author }.forEach { (author, sources) ->
            Column {
                Text(
                    stringResource(author), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary,
                )
                sources.forEach { source ->
                    WebLinkButton(onClick = { openSourceWithFeedback(context, source.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(source.title), modifier = Modifier.fillMaxWidth())
                    }
                }
                sources.mapNotNull { it.terms }.distinct().forEach { LicenseHelpText(it) }
            }
        }
        CantennaSourceLinks()
        Text(
            stringResource(R.string.licenses_halo_sources),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        HaloPublicationLinks()
        Text(stringResource(R.string.trap_sources), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        LicenseHelpText(R.string.trap_rights)
        TrapPublicationLinks()
        LicenseHelpText(R.string.licenses_rights)
    }
}

@Composable
private fun OpenNecLicense() {
    val context = LocalContext.current
    val license = remember(context) {
        context.resources.openRawResource(R.raw.opennec_license).bufferedReader().use { it.readText() }
    }
    Column(Modifier.testTag("opennec-license"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.opennec_component, OpenNec.VERSION),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        WebLinkButton(onClick = { openSourceWithFeedback(context, OpenNec.SOURCE) }) {
            Text(stringResource(R.string.opennec_source))
        }
        LicenseLinkedText(license, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LicenseHelpText(resource: Int) {
    LicenseLinkedText(stringResource(resource), style = MaterialTheme.typography.bodyMedium,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant)
}
