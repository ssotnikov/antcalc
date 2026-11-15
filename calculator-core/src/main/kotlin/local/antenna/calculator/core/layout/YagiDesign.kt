package local.antenna.calculator.core.layout

enum class YagiDesign(
    val id: String,
    val elements: List<YagiElement>,
    val removableHalves: Boolean = false,
    val revision: Int = 1,
) {
    R3("r3udk", R3udkYagi.elements, revision = R3udkYagi.REVISION),
    YBN("ybn_2_5m", listOf(
        YagiElement("r", 8.0, 0.0, 1032.9),
        YagiElement("de", 10.0, 283.0, 995.9),
        YagiElement("d1", 8.0, 403.0, 953.9),
        YagiElement("d2", 8.0, 822.0, 925.9),
        YagiElement("d3", 8.0, 1485.0, 851.5),
    )),
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
    )),
    GTV("gtv_2_6m", listOf(
        YagiElement("r", 8.0, 0.0, 1010.83),
        YagiElement("de", 10.0, 275.0, 970.83),
        YagiElement("d1", 8.0, 521.0, 962.83),
        YagiElement("d2", 8.0, 969.0, 934.83),
        YagiElement("d3", 8.0, 1660.0, 906.83),
        YagiElement("d4", 8.0, 2358.0, 846.83),
    ));

    val model = "yagi_$id"

    init {
        require(elements.size >= 2 && elements.first().positionMm == 0.0)
        require(elements.map(YagiElement::id).distinct().size == elements.size)
        require(elements.zipWithNext().all { (left, right) -> left.positionMm < right.positionMm })
    }
}

enum class YagiMatchingCable(val sectionMm: Double) {
    RG179(120.0),
    SOLID_PE(115.0),
    FOAM_PE(141.0);

    val metricKey get() = "yagi_match_" + name.lowercase()
}
