package local.antenna.calculator.measurements

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import local.antenna.calculator.ui.AppTextField
import local.antenna.calculator.R
import local.antenna.calculator.core.measurements.swrBandwidth
import local.antenna.calculator.ui.CantennaField
import java.text.DecimalFormatSymbols

@Composable
internal fun BeforeAfterBandwidth(before: MeasurementTrace?, after: MeasurementTrace?, projectName: String?, onCopied: () -> Unit) {
    Text(stringResource(R.string.cantenna_before_after), style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.secondary)
    if (before == null || after == null) {
        Text(stringResource(R.string.cantenna_second_needed))
        return
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = resources.configuration.locales[0]
    val presentation = remember(resources, locale) {
        BandwidthPresentation(resources, MeasurementNumberFormat(locale), DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }
    val mismatch = remember(before, after) {
        buildList {
            if (before.sweep.referenceOhms != after.sweep.referenceOhms) add(R.string.sweep_reference_mismatch)
            if (before.plane != after.plane || before.plane == SweepPlane.UNKNOWN) add(R.string.sweep_plane_mismatch)
        }
    }
    mismatch.forEach { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    if (mismatch.isNotEmpty()) return
    val differentGrid = remember(before.sweep, after.sweep) {
        val a = before.sweep.points
        val b = after.sweep.points
        a.size != b.size || a.indices.any { a[it].hertz != b[it].hertz }
    }
    if (differentGrid) Text(stringResource(R.string.cantenna_measurement_grid))
    var rawThreshold by rememberSaveable { mutableStateOf("2") }
    var project by rememberSaveable(projectName) { mutableStateOf(projectName.orEmpty()) }
    var tuning by rememberSaveable { mutableStateOf("") }
    val threshold = parseBandwidthThreshold(rawThreshold)
    CantennaField(rawThreshold, R.string.bandwidth_threshold, threshold == null, showUnit = false) { rawThreshold = it }
    if (threshold == null) {
        Text(stringResource(R.string.bandwidth_threshold_range))
        return
    }
    AppTextField(project, { project = it.take(100) }, Modifier.fillMaxWidth(), singleLine = true,
        label = stringResource(R.string.cantenna_project))
    AppTextField(tuning, { tuning = it.take(500) }, Modifier.fillMaxWidth(),
        label = stringResource(R.string.cantenna_tuning))
    val traces = listOf(before, after)
    val reports = remember(before.sweep, after.sweep, threshold) {
        traces.map { checkNotNull(it.sweep.swrBandwidth(threshold.toDouble())) }
    }
    val headings = listOf(R.string.cantenna_before, R.string.cantenna_after)
    traces.forEachIndexed { index, trace ->
        val report = reports[index]
        var shown by rememberSaveable(index, trace.name) { mutableIntStateOf(12) }
        Text(stringResource(headings[index]) + " · " + trace.name, style = MaterialTheme.typography.titleMedium)
        Text(presentation.traceDetails(trace))
        report.minimum?.let { Text(presentation.minimum(R.string.bandwidth_sweep_minimum, it)) }
        if (report.bands.isEmpty()) Text(stringResource(R.string.bandwidth_no_bands))
        report.bands.take(shown).forEachIndexed { bandIndex, band ->
            Text(stringResource(R.string.bandwidth_interval, bandIndex + 1), style = MaterialTheme.typography.titleSmall)
            presentation.bandLines(band).forEach { Text(it) }
        }
        if (report.bands.size > shown) {
            OutlinedButton(onClick = { shown += 12 }) { Text(stringResource(R.string.cantenna_show_intervals)) }
        }
    }
    val title = stringResource(R.string.cantenna_before_after)
    val body = stringResource(R.string.cantenna_report)
    val projectLabel = stringResource(R.string.cantenna_project)
    val tuningLabel = stringResource(R.string.cantenna_tuning)
    val gridNote = stringResource(R.string.cantenna_measurement_grid)
    OutlinedButton(onClick = {
        val text = buildString {
            appendLine(title)
            if (project.isNotBlank()) appendLine("$projectLabel · $project")
            if (tuning.isNotBlank()) appendLine("$tuningLabel · $tuning")
            if (differentGrid) appendLine(gridNote)
            traces.forEachIndexed { i, trace ->
                appendLine(resources.getString(headings[i]))
                appendLine(presentation.report(trace, threshold, reports[i]))
            }
        }
        copyMeasurementReport(context, title, text)
        onCopied()
    }, modifier = Modifier.testTag("cantenna-copy-before-after")) { Text(body) }
}

private fun copyMeasurementReport(context: Context, title: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(title, text))
}
