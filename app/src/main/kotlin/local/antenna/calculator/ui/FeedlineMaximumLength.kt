package local.antenna.calculator.ui

import android.content.Context
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import local.antenna.calculator.R
import local.antenna.calculator.core.feedline.FeedlineLengthLimit
import local.antenna.calculator.core.feedline.FeedlineLoss
import local.antenna.calculator.core.feedline.maximumFeedlineLength
import local.antenna.calculator.core.feedline.transmissionAtFeedlineLoss
import local.antenna.calculator.data.CableProfile
import local.antenna.calculator.state.FeedlineDraft

internal data class FeedlineMaximumCalculation(
    val limit: FeedlineLengthLimit,
    val cableDecibels: BigDecimal,
    val connectionDecibels: BigDecimal,
    val transmission: FeedlineLoss?,
)

internal fun FeedlineDraft.maximumCalculation(
    profile: CableProfile?, allowProvisional: Boolean = false, passportFrequency: Long? = hertz(),
): FeedlineMaximumCalculation? {
    val frequency = hertz() ?: return null
    val allowed = allowedDecibels() ?: return null
    val connections = connectionDecibels() ?: return null
    val watts = watts()
    if (power.isNotBlank() && watts == null) return null
    val attenuation = if (profileId == null) {
        manualAttenuation()?.takeIf { allowProvisional || attenuationHertz == frequency }
    } else profile?.attenuation?.get(passportFrequency)
    attenuation ?: return null
    val unit = if (profileId == null) enteredAttenuationUnit else profile?.unit ?: return null
    return when (val maximum = maximumFeedlineLength(allowed, attenuation, unit, connections)) {
        FeedlineLengthLimit.Exceeded -> FeedlineMaximumCalculation(maximum, BigDecimal.ZERO, connections, null)
        FeedlineLengthLimit.Unbounded -> FeedlineMaximumCalculation(maximum, BigDecimal.ZERO, connections,
            transmissionAtFeedlineLoss(connections, watts))
        is FeedlineLengthLimit.Finite -> {
            // A practical, conservative cut length; use it for every subsequent loss/power value too.
            val metres = if (maximum.metres >= BigDecimal("0.01")) maximum.metres.setScale(2, RoundingMode.FLOOR)
                else maximum.metres.round(MathContext(8, RoundingMode.FLOOR))
            val loss = (attenuation * metres).divide(unit.referenceMetres, MathContext.DECIMAL128)
            FeedlineMaximumCalculation(FeedlineLengthLimit.Finite(metres), loss, connections,
                transmissionAtFeedlineLoss(loss + connections, watts))
        }
    }
}

internal fun feedlineLengthText(context: Context, metres: BigDecimal, compact: Boolean = false): String {
    val small = metres.signum() > 0 && metres < BigDecimal("0.01")
    val value = if (small) metres * BigDecimal("1000") else metres
    val locale = context.resources.configuration.locales[0]
    val number = if (value.precision() - value.scale() > 10 || compact && value.scale() > 5) {
        DecimalFormat("0.#######E0", DecimalFormatSymbols.getInstance(locale)).apply {
            roundingMode = RoundingMode.FLOOR
        }.format(value)
    } else NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 32
    }.format(value)
    return "$number ${context.getString(if (small) R.string.mm else R.string.m)}"
}
