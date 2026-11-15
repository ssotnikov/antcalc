package local.antenna.calculator.ui

import androidx.annotation.StringRes
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiMatchingCable

internal const val GTV_REAR_OFFSET_MM = 40.0
internal const val GTV_BEND_FROM_CENTER_MM = 100.0
internal const val GTV_TIP_FROM_REFLECTOR_MM = 188.0
private const val GTV_MODEL_HALF_SPAN_MM = 482.0
internal const val GTV_BEND_FRACTION = GTV_BEND_FROM_CENTER_MM / GTV_MODEL_HALF_SPAN_MM
internal val YagiProfile.publishedNotes: Int?
    get() = when (this) {
        YagiProfile.R3 -> null
        YagiProfile.YBN -> R.string.yagi_ybn_published
        YagiProfile.DK2 -> R.string.yagi_dk2_published
        YagiProfile.DK50 -> R.string.yagi_dk50_published
        YagiProfile.DK28 -> R.string.yagi_dk28_published
        YagiProfile.DUO -> R.string.yagi_duo_published
        YagiProfile.GTV -> R.string.yagi_gtv_published
        else -> R.string.yagi_gtv_extension_published
    }

@StringRes
internal fun yagiElementLabelOrNull(id: String): Int? = when (id) {
    "r" -> R.string.construction_yagi_reflector
    "de" -> R.string.construction_yagi_driven
    "d1" -> R.string.construction_yagi_director_1
    "d2" -> R.string.construction_yagi_director_2
    "d3" -> R.string.yagi_director_3
    "d4" -> R.string.yagi_director_4
    "r2m" -> R.string.yagi_reflector_2m
    "r70" -> R.string.yagi_reflector_70cm
    "os" -> R.string.yagi_open_sleeve
    else -> null
}

internal fun yagiGtvVector(profile: YagiProfile): AntennaVector {
    fun p(x: Double, y: Double, z: Double = 0.0) = AntennaPoint(x.toFloat(), y.toFloat(), z.toFloat())
    val elements = profile.design.elements
    val boom = if (profile.dualPlane) {
        AntennaRod(p(0.0, 0.0), p(checkNotNull(profile.design.publishedBoomMm), 0.0), 9f, 0)
    } else AntennaRod(p(-40.0, 0.0), p(elements.last().positionMm + 40, 0.0), 9f, 0)
    fun rods(plane: List<local.antenna.calculator.core.layout.YagiElement>, vertical: Boolean) =
        plane.flatMap { element ->
            fun point(axis: Double, arm: Double) = if (vertical) p(axis, 0.0, arm) else p(axis, arm)
            val axis = element.positionMm
            val half = element.overallMm / 2
            if (element.id == "de") {
                val tip = axis - if (profile.datv) 84.0 else 87.0
                listOf(-1, 1).flatMap { side ->
                    val bend = point(axis, side * 100.0)
                    listOf(AntennaRod(point(axis, side * 8.0), bend, 3.8f, 2),
                        AntennaRod(bend, point(tip, side * half), 3.8f, 2))
                }
            } else listOf(AntennaRod(point(axis, -half), point(axis, half), 3.4f, 0))
        }
    return AntennaVector(listOf(boom) + rods(elements, false) + rods(profile.design.verticalElements, true),
        (elements + profile.design.verticalElements).map {
        AntennaJoint(p(it.positionMm, 0.0), 10f, 1)
    })
}

@StringRes
internal fun yagiElementLabel(id: String): Int = checkNotNull(yagiElementLabelOrNull(id))

@StringRes
internal fun YagiMatchingCable.label(): Int = when (this) {
    YagiMatchingCable.RG179 -> R.string.yagi_match_rg179
    YagiMatchingCable.SOLID_PE -> R.string.yagi_match_solid_pe
    YagiMatchingCable.FOAM_PE -> R.string.yagi_match_foam_pe
}
