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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import local.antenna.calculator.R

private data class CreditedPublication(val title: Int, val url: String)
private data class PublicationTerms(val description: Int, val url: String)

private data class PublicationCredit(
    val author: Int,
    val publications: List<CreditedPublication>,
    val terms: PublicationTerms? = null,
)

private val publicationCredits = listOf(
    PublicationCredit(R.string.licenses_author_1, listOf(
        CreditedPublication(R.string.licenses_work_1_1, LIZAALERT_YAGI_URL),
    ), PublicationTerms(R.string.licenses_r3udk_terms, LIZAALERT_YAGI_URL)),
    PublicationCredit(R.string.licenses_author_2, listOf(
        CreditedPublication(R.string.licenses_work_2_1, "https://www.dg7ybn.de/144MHz/144_5elem.htm"),
        CreditedPublication(R.string.licenses_work_2_2, "https://www.dg7ybn.de/144MHz/144_GTV2_6m.htm"),
    ), PublicationTerms(R.string.yagi_dg_rights, "https://www.dg7ybn.de/")),
    PublicationCredit(R.string.licenses_author_3, listOf(
        CreditedPublication(R.string.licenses_work_3_1, "https://dk7zb.darc.de/PVC-Yagis/3-Ele-2m.htm"),
        CreditedPublication(R.string.licenses_work_3_2, "https://dk7zb.darc.de/70cm/5.htm"),
        CreditedPublication(R.string.licenses_work_3_3, "https://dk7zb.darc.de/Duoband/duoband_2-70_2-3.htm"),
        CreditedPublication(R.string.licenses_work_3_4, "https://dk7zb.darc.de/PVC-Yagis/PVC-details.htm"),
        CreditedPublication(R.string.licenses_work_3_5, "https://dk7zb.darc.de/70cm/70cm-kurz.htm"),
        CreditedPublication(R.string.licenses_work_3_6, "https://www.qsl.net/dk7zb/J_Pole/wiremanjpole.htm"),
    ), PublicationTerms(R.string.yagi_dk_rights, "https://dk7zb.darc.de/")),
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

@Composable
internal fun LicensesScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()
            .verticalScroll(rememberPageScrollState()).padding(20.dp).testTag("licenses-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HelpText(R.string.licenses_mit)
        HelpText(R.string.licenses_intro)
        publicationCredits.forEach { credit ->
            Column {
                Text(
                    stringResource(credit.author),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                credit.publications.forEach { publication ->
                    TextButton(
                        onClick = { openSourceWithFeedback(context, publication.url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(publication.title), modifier = Modifier.fillMaxWidth())
                    }
                }
                credit.terms?.let { terms ->
                    HelpText(terms.description)
                    TextButton(onClick = { openSourceWithFeedback(context, terms.url) }) {
                        Text(stringResource(R.string.licenses_author_terms))
                    }
                }
            }
        }
        MoxonSource.entries.groupBy { it.author }.forEach { (author, sources) ->
            Column {
                Text(author, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                sources.forEach { source ->
                    TextButton(onClick = { openSourceWithFeedback(context, source.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(source.title, modifier = Modifier.fillMaxWidth())
                    }
                }
                sources.mapNotNull { it.terms }.distinct().forEach { HelpText(it) }
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
                    TextButton(onClick = { openSourceWithFeedback(context, source.url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(source.title), modifier = Modifier.fillMaxWidth())
                    }
                }
                sources.mapNotNull { it.terms }.distinct().forEach { HelpText(it) }
            }
        }
        HelpText(R.string.licenses_rights)
    }
}
