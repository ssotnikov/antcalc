package local.antenna.calculator.core.layout

data class YagiDrivenCut(val gapMm: Double, val halfBlankMm: Double) {
    companion object {
        fun straight(overallMm: Double, gapMm: Double) = YagiDrivenCut(gapMm, (overallMm - gapMm) / 2)

        fun bent(
            halfSpanMm: Double,
            setbackMm: Double,
            correctionMm: Double,
            bendFromCenterMm: Double = 100.0,
            gapMm: Double = 10.0,
        ): YagiDrivenCut {
            val slopingLength = kotlin.math.hypot(halfSpanMm - bendFromCenterMm, setbackMm)
            val halfBlankMm = bendFromCenterMm + slopingLength + (correctionMm - gapMm) / 2
            return YagiDrivenCut(gapMm, kotlin.math.round(halfBlankMm * 10) / 10)
        }
    }
}

enum class YagiDesign(
    val id: String,
    val elements: List<YagiElement> = emptyList(),
    val removableHalves: Boolean = false,
    val revision: Int = 1,
    val verticalElements: List<YagiElement> = emptyList(),
    val publishedBoomMm: Double? = null,
    val drivenCut: YagiDrivenCut? = null,
) {
    R3("r3udk", R3udkYagi.elements, revision = R3udkYagi.REVISION),
    YBN("ybn_2_5m", Dg7ybnGeometry.ybn, revision = 2, drivenCut = Dg7ybnGeometry.ybnCut),
    DK2("dk7zb_2m", listOf(
        YagiElement("r", 4.0, 0.0, 1039.0),
        YagiElement("de", 4.0, 300.0, 1020.0),
        YagiElement("d1", 4.0, 470.0, 926.0),
    ), removableHalves = true),
    DK50("dk7zb_70cm_50", listOf(
        YagiElement("r", 6.0, 0.0, 332.0),
        YagiElement("de", 6.0, 155.0, 323.0),
        YagiElement("d1", 6.0, 238.0, 298.0),
        YagiElement("d2", 6.0, 420.0, 296.0),
        YagiElement("d3", 6.0, 590.0, 286.0),
    )),
    DK28("dk7zb_70cm_28", listOf(
        YagiElement("r", 6.0, 0.0, 336.0),
        YagiElement("de", 10.0, 135.0, 307.0),
        YagiElement("d1", 6.0, 260.0, 301.0),
        YagiElement("d2", 6.0, 460.0, 296.0),
        YagiElement("d3", 6.0, 630.0, 288.0),
    )),
    DUO("dk7zb_duoband_22", listOf(
        YagiElement("r2m", 6.0, 0.0, 1022.0),
        YagiElement("r70", 6.0, 230.0, 322.0),
        YagiElement("de", 6.0, 370.0, 938.0),
        YagiElement("os", 6.0, 392.0, 318.0),
    ), revision = 2, drivenCut = YagiDrivenCut.straight(938.0, 10.0)),
    GTV("gtv_2_6m", Dg7ybnGeometry.gtv, revision = 2, drivenCut = Dg7ybnGeometry.gtvCut),
    GTV15("gtv_15", revision = 1),
    GTV_SP6("gtv_sp6gwn", revision = 1),
    GTV_STAUFF("gtv_stauff", revision = 1),
    GTV_THROUGH4("gtv_through_4", revision = 1),
    GTV_THROUGH476("gtv_through_476", revision = 1),
    GTV_DATV("gtv_datv", revision = 2),
    GTV_DATV_THROUGH("gtv_datv_through", revision = 2),
    GTV_XPOL_STAUFF("gtv_xpol_stauff", revision = 1),
    GTV_XPOL_8("gtv_xpol_8", revision = 1),
    GTV_SAT("gtv_sat", revision = 1),
    GTV_XPOL_THROUGH4("gtv_xpol_through_4", revision = 1),
    GTV_XPOL_THROUGH476("gtv_xpol_through_476", revision = 1),
    ;

    val model = "yagi_$id"
    val restricted get() = id == "ybn_2_5m" || id.startsWith("gtv")
    val dualPlane get() = id.startsWith("gtv_xpol") || this == GTV_SAT
    val available get() = elements.isNotEmpty()

    init {
        require(elements.isEmpty() || (elements.size >= 2 && elements.first().positionMm == 0.0))
        require(elements.map(YagiElement::id).distinct().size == elements.size)
        require(elements.zipWithNext().all { (left, right) -> left.positionMm < right.positionMm })
        if (verticalElements.isNotEmpty()) {
            require(verticalElements.map(YagiElement::id) == elements.map(YagiElement::id))
            require(verticalElements.zipWithNext().all { (left, right) -> left.positionMm < right.positionMm })
            require(checkNotNull(publishedBoomMm) > maxOf(elements.last().positionMm, verticalElements.last().positionMm))
        }
    }
}

enum class YagiMatchingCable(val sectionMm: Double) {
    RG179(120.0),
    SOLID_PE(115.0),
    FOAM_PE(141.0);

    val metricKey get() = "yagi_match_" + name.lowercase()
}
