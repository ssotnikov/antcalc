package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import local.antenna.calculator.R
import local.antenna.calculator.nec.NecDirectionRange
import local.antenna.calculator.nec.NecReferenceExamples
import local.antenna.calculator.nec.NecWorstSwrChange
import local.antenna.calculator.nec.OpenNec
import kotlin.math.abs

internal const val NGSOLVE_PML_SOURCE = "https://docu.ngsolve.org/latest/how_to/pml.html"
internal const val ARCHIVED_OPENNEC_SOURCE = "https://github.com/maurymarkowitz/OpenNEC/tree/v.2.4.2"

@Composable
internal fun NecDirectionDetails(result: NecDirectionRange) {
    Column(Modifier.testTag("opennec-direction-peaks"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NecSwrTable(R.string.opennec_diag_mesh, listOf(
            R.string.opennec_diag_coarse to result.coarse,
            R.string.opennec_diag_fine to result.fine,
        ), 4)
    }
    ScalingDetails(R.string.opennec_diag_impedance, Modifier.testTag("opennec-direction-details")) {
        val context = LocalContext.current
        val error = result.largestResidual
        val common = result.largestCommonShift
        HelpText(R.string.opennec_diag_residual_frequency, necFrequency(context, error.frequencyHz))
        ScalingTableRow(listOf(stringResource(R.string.opennec_diag_mesh), "ΔR (Ω)", "ΔX (Ω)"))
        ScalingTableRow(listOf(stringResource(R.string.opennec_diag_coarse),
            necSigned(context, error.coarse.resistance, 4), necSigned(context, error.coarse.reactance, 4)))
        ScalingTableRow(listOf(stringResource(R.string.opennec_diag_fine),
            necSigned(context, error.fine.resistance, 4), necSigned(context, error.fine.reactance, 4)))
        HelpText(R.string.opennec_diag_residual, matchingNumber(context, error.residual.ohms, 4))
        HelpText(R.string.opennec_diag_common_shift,
            matchingNumber(context, common.commonShift.ohms, 4), necFrequency(context, common.frequencyHz))
    }
}

@Composable
internal fun NecMethodExamples() {
    ScalingDetails(R.string.opennec_pml_example, Modifier.testTag("opennec-pml-example")) {
        val context = LocalContext.current
        HelpText(R.string.opennec_pml_setup)
        Column(Modifier.testTag("opennec-pml-table"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ScalingTableRow(listOf(
                stringResource(R.string.opennec_diag_mhz),
                stringResource(R.string.opennec_pml_8),
                stringResource(R.string.opennec_pml_12),
                "|ΔZ| (Ω)",
            ))
            NecReferenceExamples.radial.forEach { sample ->
                ScalingTableRow(listOf(
                    matchingNumber(context, sample.eightLayers.frequencyHz / 1e6, 6),
                    necImpedance(context, sample.eightLayers),
                    necImpedance(context, sample.twelveLayers),
                    matchingNumber(context, sample.deltaImpedance, 6),
                ))
            }
        }
        NecReferenceExamples.radial.forEach { sample ->
            HelpText(R.string.opennec_pml_swr_change,
                necFrequency(context, sample.eightLayers.frequencyHz), necSigned(context, sample.deltaSwr, 6))
        }
        WebLinkButton({ openSourceWithFeedback(context, NGSOLVE_PML_SOURCE) }) {
            Text(stringResource(R.string.opennec_pml_source))
        }
    }
    ScalingDetails(R.string.opennec_band_example, Modifier.testTag("opennec-band-example")) {
        val context = LocalContext.current
        val rows = listOf(
            R.string.opennec_band_wire8 to NecReferenceExamples.wire8,
            R.string.opennec_band_wire4 to NecReferenceExamples.wire4,
            R.string.opennec_band_pml8 to NecReferenceExamples.pml8,
        )
        HelpText(R.string.opennec_band_setup)
        Column(Modifier.testTag("opennec-band-table"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NecSwrTable(R.string.opennec_diag_model, rows, 6)
        }
        HelpText(R.string.opennec_band_delta_range,
            necSigned(context, rows.minOf { it.second.delta }, 6),
            necSigned(context, rows.maxOf { it.second.delta }, 6))
        Text(stringResource(R.string.opennec_direction_indeterminate), style = MaterialTheme.typography.titleSmall)
        HelpText(R.string.opennec_band_edges)
        HelpText(R.string.opennec_band_correlation)
        WebLinkButton({ openSourceWithFeedback(context, ARCHIVED_OPENNEC_SOURCE) }) {
            Text(stringResource(R.string.opennec_band_source))
        }
        HelpText(R.string.opennec_examples_scope)
    }
}

@Composable
private fun NecSwrTable(heading: Int, rows: List<Pair<Int, NecWorstSwrChange>>, places: Int) {
    val context = LocalContext.current
    ScalingTableRow(listOf(
        stringResource(heading),
        stringResource(R.string.opennec_diag_before),
        stringResource(R.string.opennec_diag_after),
        stringResource(R.string.opennec_diag_change),
    ))
    rows.forEach { (label, change) ->
        ScalingTableRow(listOf(
            stringResource(label),
            matchingNumber(context, change.before.swr, places) + "\n" + necFrequency(context, change.before.frequencyHz),
            matchingNumber(context, change.after.swr, places) + "\n" + necFrequency(context, change.after.frequencyHz),
            necSigned(context, change.delta, places),
        ))
    }
}

@Composable
private fun necFrequency(context: Context, hertz: Long) =
    stringResource(R.string.opennec_diag_frequency, matchingNumber(context, hertz / 1e6, 6))

private fun necSigned(context: Context, value: Double, places: Int) =
    (if (value < 0) "−" else "+") + matchingNumber(context, abs(value), places)

private fun necImpedance(context: Context, value: OpenNec.Point) =
    matchingNumber(context, value.resistance, 4) + " " +
        (if (value.reactance < 0) "−" else "+") + " j" + matchingNumber(context, abs(value.reactance), 4)
