package local.antenna.calculator.core.feedline

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln

enum class AttenuationUnit(val referenceMetres: BigDecimal) {
    PER_100_METRES(BigDecimal("100")),
    PER_100_FEET(BigDecimal("30.48")),
}

data class FeedlineLoss(
    val decibels: Double,
    val transmittedFraction: Double,
    val outputWatts: Double?,
    val lostWatts: Double?,
)

/** One-way loss in a uniform, matched line. The result excludes connectors and mismatch. */
fun calculateFeedlineLoss(
    lengthMetres: BigDecimal,
    attenuation: BigDecimal,
    unit: AttenuationUnit,
    inputWatts: BigDecimal? = null,
): FeedlineLoss {
    require(lengthMetres in BigDecimal.ZERO..BigDecimal("10000"))
    require(attenuation in BigDecimal.ZERO..BigDecimal("1000"))
    require(inputWatts == null || inputWatts in BigDecimal.ZERO..BigDecimal("100000"))
    val decibels = (attenuation * lengthMetres).divide(unit.referenceMetres, MathContext.DECIMAL128).toDouble()
    val exponent = -decibels * ln(10.0) / 10
    val transmitted = exp(exponent)
    val lost = if (decibels == 0.0) 0.0 else -expm1(exponent)
    return FeedlineLoss(decibels, transmitted, inputWatts?.toDouble()?.times(transmitted),
        inputWatts?.toDouble()?.times(lost))
}
