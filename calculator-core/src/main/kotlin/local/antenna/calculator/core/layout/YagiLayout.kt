package local.antenna.calculator.core.layout

data class YagiElement(
    val id: String,
    val diameterMm: Double,
    val positionMm: Double,
    val overallMm: Double,
    val blankMm: Double? = null,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9]{0,15}")))
        require(diameterMm in 0.001..1000.0 && positionMm in 0.0..100_000.0 && overallMm in 0.001..100_000.0)
        require(blankMm == null || blankMm in 0.001..100_000.0)
    }
}

object R3udkYagi {
    const val FREQUENCY_HZ = 145_000_000L
    const val REVISION = 1
    val elements = listOf(
        YagiElement("r", 2.4, 0.0, 1016.0),
        YagiElement("de", 2.4, 211.0, 990.0, blankMm = 995.0),
        YagiElement("d1", 2.4, 389.0, 955.0),
        YagiElement("d2", 2.4, 868.0, 929.0),
    )

    init {
        require(elements.first().positionMm == 0.0)
        require(elements.map(YagiElement::id).distinct().size == elements.size)
        require(elements.zipWithNext().all { (left, right) -> left.positionMm < right.positionMm })
    }
}

data class YagiCutList(val spanMm: Double, val boomMm: Double?, val blanksMm: List<Double>)

fun calculateR3udkCutList(allowanceMm: Double, rearMm: Double?, frontMm: Double?): YagiCutList {
    require(allowanceMm in 0.0..1000.0)
    require(listOfNotNull(rearMm, frontMm).all { it in 0.0..1000.0 })
    val span = R3udkYagi.elements.last().positionMm
    val boom = if (rearMm != null && frontMm != null) span + rearMm + frontMm else null
    return YagiCutList(span, boom, R3udkYagi.elements.map { it.blankMm ?: (it.overallMm + 2 * allowanceMm) })
}
