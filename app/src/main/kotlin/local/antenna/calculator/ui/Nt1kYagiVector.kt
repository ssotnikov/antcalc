package local.antenna.calculator.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import local.antenna.calculator.core.layout.YagiDesign

/**
 * Illustrative assembly after Jeffrey Bail (NT1K), VHF 3EL Tape Measure Yagi.
 * The tapes and element axes follow the catalog; small mounting parts are stylized.
 */
internal fun nt1kYagiVector(): AntennaVector {
    val design = YagiDesign.NT1K
    val driver = checkNotNull(design.drivenCut)
    val directorPosition = design.elements.last().positionMm.toFloat()
    val feedPosition = design.elements[1].positionMm.toFloat()
    val rear = -152.4f
    val front = directorPosition + 152.4f
    val pvc = 4
    val metal = 0
    val copper = 2
    val coax = 1
    val yellow = 6
    fun point(x: Float, y: Float = 0f, z: Float = 0f) = AntennaPoint(x, y, z)

    val rods = buildList {
        add(AntennaRod(point(rear), point(front), 5.6f, pvc))
        add(AntennaRod(point(rear), point(rear + 24f), 7.2f, pvc))
        add(AntennaRod(point(front - 24f), point(front), 7.2f, pvc))
        for (element in design.elements) {
            val x = element.positionMm.toFloat()
            val half = element.overallMm.toFloat() / 2f
            val halfWidth = point(checkNotNull(element.tapeWidthMm).toFloat() / 2f)
            fun tape(from: Float, to: Float) {
                add(AntennaRod(point(x, from, 21f), point(x, to, 21f), 1f, yellow, halfWidth))
            }
            add(AntennaRod(point(x, -43f), point(x, 43f), 7.2f, pvc))
            if (element.id == "de") {
                val gap = driver.gapMm.toFloat() / 2f
                tape(-half, -gap)
                tape(gap, half)
            } else {
                tape(-half, half)
            }
            for (side in listOf(-1f, 1f)) {
                val y = side * 32f
                add(AntennaRod(point(x - 13f, y, 25f), point(x + 13f, y, 25f), 3.8f, metal))
            }
        }
        val terminal = driver.gapMm.toFloat() / 2f + 5f
        val hairpin = listOf(point(feedPosition, -terminal, 21f), point(feedPosition + 40f, -terminal, 23f),
            point(feedPosition + 47f, -terminal * .55f, 23f), point(feedPosition + 47f, terminal * .55f, 23f),
            point(feedPosition + 40f, terminal, 23f), point(feedPosition, terminal, 21f))
        for ((first, last) in hairpin.zipWithNext()) add(AntennaRod(first, last, 1.4f, copper))

        val coilStart = feedPosition - 85f
        val coilLength = 49f
        val coil = List(49) { index ->
            val angle = index * (2 * PI / 8)
            point(coilStart + coilLength * index / 48f, (16 * sin(angle)).toFloat(), (16 * cos(angle)).toFloat())
        }
        for ((first, last) in coil.zipWithNext()) add(AntennaRod(first, last, 1.7f, coax))
        add(AntennaRod(coil.last(), point(feedPosition - 22f, terminal, 16f), 1.8f, coax))
        add(AntennaRod(point(feedPosition - 22f, terminal, 16f), point(feedPosition, terminal, 21f), 1.8f, coax))
        add(AntennaRod(point(rear), point(rear - 39f, 13f, -4f), 1.8f, coax))
    }
    return AntennaVector(rods, emptyList())
}
