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
import local.antenna.calculator.R
import local.antenna.calculator.nec.NecDirectionRange
import local.antenna.calculator.nec.NecWorstSwrChange
import kotlin.math.abs

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
