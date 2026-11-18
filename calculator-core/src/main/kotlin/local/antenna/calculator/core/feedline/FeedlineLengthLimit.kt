package local.antenna.calculator.core.feedline

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface FeedlineLengthLimit {
    data class Finite(val metres: BigDecimal) : FeedlineLengthLimit
    data object Unbounded : FeedlineLengthLimit
    data object Exceeded : FeedlineLengthLimit
}

/** Physical length allowed by a matched-line loss allowance at one frequency. */
fun maximumFeedlineLength(
    allowedDecibels: BigDecimal,
    attenuation: BigDecimal,
    unit: AttenuationUnit,
    connectionDecibels: BigDecimal = BigDecimal.ZERO,
): FeedlineLengthLimit {
    require(allowedDecibels in BigDecimal.ZERO..BigDecimal("1000"))
    require(connectionDecibels in BigDecimal.ZERO..BigDecimal("1000"))
    require(attenuation in BigDecimal.ZERO..BigDecimal("1000"))
    val remaining = allowedDecibels - connectionDecibels
    if (remaining.signum() < 0) return FeedlineLengthLimit.Exceeded
    if (attenuation.signum() == 0) return FeedlineLengthLimit.Unbounded
    // Round towards shorter lengths; do not exceed the entered loss allowance.
    return FeedlineLengthLimit.Finite((remaining * unit.referenceMetres)
        .divide(attenuation, MathContext(34, RoundingMode.FLOOR)))
}
