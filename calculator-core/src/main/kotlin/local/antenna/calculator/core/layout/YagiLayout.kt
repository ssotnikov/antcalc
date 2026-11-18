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
}

data class YagiCutList(
    val spanMm: Double,
    val boomMm: Double?,
    val blanksMm: List<Double?>,
    val verticalBlanksMm: List<Double?> = emptyList(),
)

fun calculateYagiCutList(design: YagiDesign, allowanceMm: Double, rearMm: Double?, frontMm: Double?): YagiCutList {
    require(design.available)
    require(allowanceMm in 0.0..1000.0)
    require(listOfNotNull(rearMm, frontMm).all { it in 0.0..1000.0 })
    val span = maxOf(design.elements.last().positionMm, design.verticalElements.lastOrNull()?.positionMm ?: 0.0) -
        minOf(design.elements.first().positionMm, design.verticalElements.firstOrNull()?.positionMm ?: design.elements.first().positionMm)
    val boom = if (rearMm != null && frontMm != null) span + rearMm + frontMm else null
    fun blanks(elements: List<YagiElement>) = elements.map { element ->
        when {
            element.blankMm != null -> element.blankMm
            design.removableHalves || element.id == "de" -> null
            else -> element.overallMm + 2 * allowanceMm
        }
    }
    return YagiCutList(span, design.publishedBoomMm ?: boom, blanks(design.elements), blanks(design.verticalElements))
}

fun calculateR3udkCutList(allowanceMm: Double, rearMm: Double?, frontMm: Double?): YagiCutList =
    calculateYagiCutList(YagiDesign.R3, allowanceMm, rearMm, frontMm)
