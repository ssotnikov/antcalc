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
    )),
    PublicationCredit(R.string.licenses_author_2, listOf(
        CreditedPublication(R.string.licenses_work_2_1, "https://www.dg7ybn.de/144MHz/144_5elem.htm"),
        CreditedPublication(R.string.licenses_work_2_2, "https://www.dg7ybn.de/144MHz/144_GTV2_6m.htm"),
    ), PublicationTerms(R.string.yagi_dg_rights, "https://www.dg7ybn.de/")),
    PublicationCredit(R.string.licenses_author_3, listOf(
        CreditedPublication(R.string.licenses_work_3_1, "https://dk7zb.darc.de/PVC-Yagis/3-Ele-2m.htm"),
        CreditedPublication(R.string.licenses_work_3_2, "https://dk7zb.darc.de/70cm/5.htm"),
        CreditedPublication(R.string.licenses_work_3_3, "https://dk7zb.darc.de/Duoband/duoband_2-70_2-3.htm"),
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
    )),
    PublicationCredit(R.string.licenses_author_9, listOf(
        CreditedPublication(R.string.licenses_work_9_1, "https://www.iw5edi.com/ham-radio/6250/j-vertical-antenna-for-10-meters-band"),
    )),
    PublicationCredit(R.string.licenses_author_10, listOf(
        CreditedPublication(R.string.licenses_work_10_1, "https://practicalantennas.com/theory/loop/full-wave/"),
    )),
    PublicationCredit(R.string.licenses_author_11, listOf(
        CreditedPublication(R.string.licenses_work_11_1, "https://radioskot.com/calc/interaktivnaya-diagramma-smita"),
    )),
    PublicationCredit(R.string.licenses_author_12, listOf(
        CreditedPublication(R.string.licenses_work_12_1, "https://www.mdpi.com/1996-1073/14/21/7042"),
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
        HelpText(R.string.licenses_rights)
    }
}
