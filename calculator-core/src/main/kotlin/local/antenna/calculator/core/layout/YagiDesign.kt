package local.antenna.calculator.core.layout

private fun gtvElements(passive: Double, active: Double, positions: List<Double>, sizes: List<Double>) =
    listOf("r", "de", "d1", "d2", "d3", "d4").mapIndexed { index, id ->
        YagiElement(id, if (index == 1) active else passive, positions[index], sizes[index])
    }

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
    val elements: List<YagiElement>,
    val removableHalves: Boolean = false,
    val revision: Int = 1,
    val verticalElements: List<YagiElement> = emptyList(),
    val publishedBoomMm: Double? = null,
    val drivenCut: YagiDrivenCut? = null,
) {
    R3("r3udk", R3udkYagi.elements, revision = R3udkYagi.REVISION),
    YBN("ybn_2_5m", listOf(
        YagiElement("r", 8.0, 0.0, 1032.9),
        YagiElement("de", 10.0, 283.0, 995.9),
        YagiElement("d1", 8.0, 403.0, 953.9),
        YagiElement("d2", 8.0, 822.0, 925.9),
        YagiElement("d3", 8.0, 1485.0, 851.5),
    ), revision = 2, drivenCut = YagiDrivenCut.straight(995.9, 10.0)),
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
    GTV("gtv_2_6m", listOf(
        YagiElement("r", 8.0, 0.0, 1010.83),
        YagiElement("de", 10.0, 275.0, 970.83),
        YagiElement("d1", 8.0, 521.0, 962.83),
        YagiElement("d2", 8.0, 969.0, 934.83),
        YagiElement("d3", 8.0, 1660.0, 906.83),
        YagiElement("d4", 8.0, 2358.0, 846.83),
    ), revision = 2, drivenCut = YagiDrivenCut.bent(482.0, 87.0, 6.83)),
    GTV15("gtv_15", gtvElements(8.0, 10.0,
        listOf(0.0, 275.0, 521.0, 969.0, 1660.0, 2358.0),
        listOf(1009.63, 969.63, 961.63, 933.63, 905.63, 845.63))),
    GTV_SP6("gtv_sp6gwn", gtvElements(8.0, 10.0,
        listOf(0.0, 275.0, 521.0, 969.0, 1660.0, 2358.0),
        listOf(1012.29, 972.29, 964.29, 936.29, 908.29, 848.29))),
    GTV_STAUFF("gtv_stauff", gtvElements(9.53, 9.53,
        listOf(0.0, 275.0, 521.0, 969.0, 1660.0, 2358.0),
        listOf(1007.23, 970.23, 956.73, 927.73, 898.73, 840.23))),
    GTV_THROUGH4("gtv_through_4", gtvElements(4.0, 10.0,
        listOf(0.0, 275.0, 521.0, 969.0, 1660.0, 2358.0),
        listOf(1018.93, 970.13, 977.43, 952.43, 927.23, 866.33))),
    GTV_THROUGH476("gtv_through_476", gtvElements(4.76, 10.0,
        listOf(0.0, 275.0, 521.0, 969.0, 1660.0, 2358.0),
        listOf(1017.93, 970.93, 976.43, 949.33, 923.33, 861.83))),
    GTV_DATV("gtv_datv", gtvElements(8.0, 10.0,
        listOf(0.0, 270.0, 513.0, 953.0, 1633.0, 2320.0),
        listOf(992.83, 954.83, 946.83, 919.83, 891.83, 832.83)),
        revision = 2, drivenCut = YagiDrivenCut.bent(474.0, 84.0, 6.83)),
    GTV_DATV_THROUGH("gtv_datv_through", gtvElements(8.0, 10.0,
        listOf(0.0, 270.0, 513.0, 953.0, 1633.0, 2320.0),
        listOf(993.74, 956.84, 948.64, 921.54, 893.34, 833.04)),
        revision = 2, drivenCut = YagiDrivenCut.bent(474.0, 84.0, 8.84)),
    GTV_XPOL_STAUFF("gtv_xpol_stauff",
        gtvElements(9.53, 9.53, listOf(750.0, 1025.0, 1271.0, 1719.0, 2410.0, 3108.0),
            listOf(1007.23, 970.23, 956.73, 927.73, 898.73, 840.23)),
        verticalElements = gtvElements(9.53, 9.53, listOf(860.0, 1135.0, 1381.0, 1829.0, 2520.0, 3218.0),
            listOf(1007.23, 970.23, 956.73, 927.73, 898.73, 840.23)), publishedBoomMm = 3258.0),
    GTV_XPOL_8("gtv_xpol_8",
        gtvElements(8.0, 10.0, listOf(750.0, 1022.0, 1266.0, 1710.0, 2395.0, 3087.0),
            listOf(1001.83, 962.83, 954.83, 926.83, 898.23, 838.83)),
        verticalElements = gtvElements(8.0, 10.0, listOf(860.0, 1132.0, 1376.0, 1820.0, 2505.0, 3197.0),
            listOf(1001.83, 962.83, 954.83, 926.83, 898.23, 838.83)), publishedBoomMm = 3237.0),
    GTV_SAT("gtv_sat",
        gtvElements(9.53, 9.53, listOf(40.0, 312.0, 555.0, 997.5, 1680.5, 2370.5),
            listOf(995.23, 956.23, 945.23, 917.23, 887.23, 830.23)),
        verticalElements = gtvElements(9.53, 9.53, listOf(530.0, 826.0, 1069.0, 1511.5, 2194.5, 2884.5),
            listOf(995.23, 959.23, 945.23, 917.23, 887.23, 830.23)), publishedBoomMm = 2924.5),
    GTV_XPOL_THROUGH4("gtv_xpol_through_4",
        gtvElements(4.0, 10.0, listOf(750.0, 1025.0, 1271.0, 1719.0, 2410.0, 3108.0),
            listOf(1019.63, 970.23, 977.53, 952.43, 927.33, 866.83)),
        verticalElements = gtvElements(4.0, 10.0, listOf(860.0, 1135.0, 1381.0, 1829.0, 2520.0, 3218.0),
            listOf(1019.63, 970.23, 977.53, 952.43, 927.23, 866.33)), publishedBoomMm = 3258.0),
    GTV_XPOL_THROUGH476("gtv_xpol_through_476",
        gtvElements(4.76, 10.0, listOf(750.0, 1025.0, 1271.0, 1719.0, 2410.0, 3108.0),
            listOf(1019.23, 971.13, 976.53, 949.43, 923.43, 862.63)),
        verticalElements = gtvElements(4.76, 10.0, listOf(860.0, 1135.0, 1381.0, 1829.0, 2520.0, 3218.0),
            listOf(1019.23, 971.13, 976.53, 949.43, 923.43, 861.83)), publishedBoomMm = 3258.0),
    ;

    val model = "yagi_$id"

    init {
        require(elements.size >= 2 && (elements.first().positionMm == 0.0 || publishedBoomMm != null))
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
