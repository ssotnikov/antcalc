package local.antenna.calculator.ui

import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiDesign
import local.antenna.calculator.core.layout.YagiDrivenCut

internal data class YagiDriverGeometry(
    val gapMm: Double,
    val bendMm: Double? = null,
    val setbackMm: Double? = null,
)

internal data class YagiDriverPlane(
    val spanMm: Double,
    val correctionMm: Double? = null,
    val feedFromReflectorMm: Double = 0.0,
) {
    val labels get() = if (correctionMm == null) listOf(R.string.yagi_driven_gap_mm)
        else listOf(R.string.yagi_driven_gap_mm, R.string.yagi_bend_mm, R.string.yagi_setback_mm)

    fun invalid(values: List<Double?>, index: Int): Boolean {
        val value = values[index] ?: return true
        return when (index) {
            0 -> value !in 0.1..minOf(100.0, (values.getOrNull(1) ?: spanMm / 2) * 2 - 0.1)
            1 -> value !in 0.1..(spanMm / 2 - 0.1)
            else -> value !in 0.1..feedFromReflectorMm
        }
    }

    fun cut(geometry: YagiDriverGeometry): YagiDrivenCut = if (correctionMm == null) {
        YagiDrivenCut.straight(spanMm, geometry.gapMm)
    } else {
        YagiDrivenCut.bent(spanMm / 2, checkNotNull(geometry.setbackMm), correctionMm,
            checkNotNull(geometry.bendMm), geometry.gapMm)
    }
}

internal fun YagiDesign.driverPlanes(): List<YagiDriverPlane> = when (this) {
    YagiDesign.DK50, YagiDesign.DK28 -> listOf(YagiDriverPlane(elements.single { it.id == "de" }.overallMm))
    YagiDesign.GTV_XPOL_STAUFF -> driverPlanes(965.0, 965.0, 5.23)
    YagiDesign.GTV_XPOL_8 -> driverPlanes(956.0, 956.0, 6.83)
    YagiDesign.GTV_XPOL_THROUGH4 -> driverPlanes(962.0, 962.0, 8.23)
    YagiDesign.GTV_XPOL_THROUGH476 -> driverPlanes(962.0, 962.0, 9.13)
    YagiDesign.GTV_SAT -> driverPlanes(951.0, 954.0, 5.23)
    else -> emptyList()
}

private fun YagiDesign.driverPlanes(horizontal: Double, vertical: Double, correction: Double) =
    listOf(horizontal, vertical).zip(listOf(elements, verticalElements)).map { (span, plane) ->
        YagiDriverPlane(span, correction, plane.single { it.id == "de" }.positionMm - plane.first().positionMm)
    }
