package local.antenna.calculator.ui

import android.content.Context
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import local.antenna.calculator.R
import local.antenna.calculator.core.units.FrequencyUnit

/** Three decimals in MHz, with extra digits up to the app's 1 Hz resolution. */
internal class FrequencyNumberFormat(locale: Locale) {
    private val khz = decimal(locale, 0, 3)
    private val mhz = decimal(locale, 3, 6)
    private val ghz = decimal(locale, 0, 9)
    private val estimatedMhz = decimal(locale, 3, 3)

    fun inUnit(value: Number, unit: FrequencyUnit): String = when (unit) {
        FrequencyUnit.KILOHERTZ -> khz.format(value)
        FrequencyUnit.MEGAHERTZ -> mhz.format(value)
        FrequencyUnit.GIGAHERTZ -> ghz.format(value)
    }

    fun megahertz(hertz: Number): String = inUnit(toMegahertz(hertz), FrequencyUnit.MEGAHERTZ)
    fun estimatedMegahertz(hertz: Double): String = estimatedMhz.format(toMegahertz(hertz))

    private fun toMegahertz(hertz: Number): BigDecimal = BigDecimal(hertz.toString()).movePointLeft(6)

    private fun decimal(locale: Locale, minimum: Int, maximum: Int) = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = maximum
        minimumFractionDigits = minimum
    }
}

internal fun Context.formatFrequency(hertz: Long, unit: FrequencyUnit): String {
    val value = BigDecimal.valueOf(hertz).divide(BigDecimal.valueOf(unit.hertzPerUnit))
    return FrequencyNumberFormat(resources.configuration.locales[0]).inUnit(value, unit)
}

internal fun Context.frequencyWithUnit(hertz: Long, unit: FrequencyUnit): String =
    getString(R.string.value_unit, formatFrequency(hertz, unit), getString(unit.labelResource()))

// Keep draft text and its cursor positions intact while editing.
private val completeInput = Regex("[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+")

internal fun displayMegahertzInput(raw: String, separator: Char): String {
    if (!completeInput.matches(raw)) return raw
    val separatorIndex = raw.indexOfFirst { it == '.' || it == ',' }
    if (separatorIndex == -1) return raw + separator + "000"
    val decimalCount = raw.length - separatorIndex - 1
    return raw.substring(0, separatorIndex) + separator + raw.substring(separatorIndex + 1) +
        "0".repeat((3 - decimalCount).coerceAtLeast(0))
}

@Composable
internal fun megahertzFieldPresentation(modifier: Modifier, enabled: Boolean): Pair<Modifier, VisualTransformation> {
    if (!enabled) return modifier to VisualTransformation.None
    var focused by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val separator = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val transformation = remember(separator) {
        VisualTransformation { text ->
            val display = displayMegahertzInput(text.text, separator)
            TransformedText(AnnotatedString(display), object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset
                override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(text.length)
            })
        }
    }
    return modifier.onFocusChanged { focused = it.isFocused } to
        if (focused) VisualTransformation.None else transformation
}

@Composable
internal fun megahertzOutputTransformation(enabled: Boolean): OutputTransformation? {
    if (!enabled) return null
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        OutputTransformation {
            val raw = asCharSequence().toString()
            val display = displayMegahertzInput(raw, separator)
            val index = raw.indexOfFirst { it == '.' || it == ',' }
            if (index >= 0 && raw[index] != display[index]) replace(index, index + 1, separator.toString())
            if (display.length > raw.length) append(display.substring(raw.length))
        }
    }
}
