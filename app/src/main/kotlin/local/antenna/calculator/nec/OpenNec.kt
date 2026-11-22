package local.antenna.calculator.nec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.matching.MatchingMeasure
import local.antenna.calculator.core.matching.matchingFromImpedance
import kotlin.math.ceil

internal object OpenNec {
    const val VERSION = "2.4.2"
    const val VERSION_CODE = 20402
    const val SOURCE = "https://github.com/maurymarkowitz/OpenNEC/tree/v.2.4.2"
    const val AZIMUTH_STEP = 5
    private const val MAX_SEGMENTS = 241
    private const val MAX_POINTS = 41
    private const val ANGLES = 360 / AZIMUTH_STEP + 1

    val available: Boolean by lazy {
        try {
            System.loadLibrary("antcalc_nec")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    data class Point(
        val frequencyHz: Long,
        val resistance: Double,
        val reactance: Double,
        val swr: Double,
        val azimuthDbi: List<Double>,
    ) {
        val forwardDbi get() = azimuthDbi.first()
        val frontToBackDb get() = forwardDbi - azimuthDbi[180 / AZIMUTH_STEP]
    }

    enum class SegmentationIssue { INPUT, POSITIONS, ELEMENT, STEP, LIMIT }

    data class SegmentPlan(
        val wavelength: Double,
        val counts: List<Int> = emptyList(),
        val issue: SegmentationIssue? = null,
        val elementIndex: Int? = null,
    )

    fun segmentation(elements: List<Element>, highestHz: Long): SegmentPlan {
        val wavelength = 299_792_458.0 / highestHz
        if (highestHz !in 100_000L..6_000_000_000L || elements.size !in 1..24) {
            return SegmentPlan(wavelength, issue = SegmentationIssue.INPUT)
        }
        val counts = elements.mapIndexed { index, element ->
            if (element.position !in 0.0..1000.0 || element.length !in Double.MIN_VALUE..1000.0 ||
                element.diameter !in Double.MIN_VALUE..element.length) {
                return SegmentPlan(wavelength, issue = SegmentationIssue.ELEMENT, elementIndex = index)
            }
            val count = ceil(element.length / (0.05 * wavelength)).toInt().coerceAtLeast(11) or 1
            if (count > MAX_SEGMENTS) return SegmentPlan(wavelength, issue = SegmentationIssue.LIMIT)
            if (element.length / count < 2 * element.diameter) {
                return SegmentPlan(wavelength, issue = SegmentationIssue.STEP, elementIndex = index)
            }
            count
        }
        if (elements.first().position != 0.0 ||
            elements.zipWithNext().any { (a, b) -> b.position - a.position <= (a.diameter + b.diameter) / 2 }) {
            return SegmentPlan(wavelength, issue = SegmentationIssue.POSITIONS)
        }
        if (counts.sum() > MAX_SEGMENTS) return SegmentPlan(wavelength, issue = SegmentationIssue.LIMIT)
        return SegmentPlan(wavelength, counts)
    }

    fun segmentCounts(elements: List<Element>, highestHz: Long): List<Int>? =
        segmentation(elements, highestHz).takeIf { it.issue == null }?.counts

    fun finerSegments(elements: List<Element>, counts: List<Int>): List<Int>? {
        if (elements.size != counts.size) return null
        val doubled = counts.map { it * 2 - 1 }
        if (doubled.sum() <= MAX_SEGMENTS && elements.zip(doubled).all { (element, count) ->
                element.length / count >= 2 * element.diameter
            }) return doubled
        var remaining = MAX_SEGMENTS - counts.sum()
        val refined = counts.mapIndexed { index, count ->
            if (remaining >= 2 && elements[index].length / (count + 2) >= 2 * elements[index].diameter) {
                remaining -= 2
                count + 2
            } else count
        }
        return refined.takeIf { it != counts }
    }

    suspend fun sweep(
        elements: List<Element>,
        segments: List<Int>,
        feedElement: Int,
        frequenciesHz: List<Long>,
        referenceOhms: Double = 50.0,
    ): List<Point>? = withContext(Dispatchers.Default) {
        if (!available || elements.size !in 1..24 || elements.size != segments.size || feedElement !in elements.indices ||
            frequenciesHz.size !in 1..MAX_POINTS || frequenciesHz.zipWithNext().any { (a, b) -> a >= b } ||
            frequenciesHz.any { it !in 100_000L..6_000_000_000L } || referenceOhms !in 1.0..1000.0 ||
            segments.any { it !in 3..MAX_SEGMENTS || it % 2 == 0 } || segments.sum() > MAX_SEGMENTS) return@withContext null
        val geometry = elements.flatMap { listOf(it.position, it.length, it.diameter) }.toDoubleArray()
        val counts = segments.toIntArray()
        val coroutine = currentCoroutineContext()
        frequenciesHz.map { frequency ->
            coroutine.ensureActive()
            val data = synchronized(OpenNec) {
                coroutine.ensureActive()
                solvePoint(geometry, counts, feedElement, frequency / 1e6)
            } ?: return@withContext null
            coroutine.ensureActive()
            if (data.size != 2 + ANGLES || data.any { !it.isFinite() } ||
                data[0] !in Double.MIN_VALUE..1e6 || data[1] !in -1e6..1e6) return@withContext null
            val swr = (matchingFromImpedance(data[0], data[1], referenceOhms).swr as? MatchingMeasure.Finite)
                ?.value ?: return@withContext null
            Point(frequency, data[0], data[1], swr, data.drop(2))
        }
    }

    private external fun solvePoint(geometry: DoubleArray, segments: IntArray, feed: Int, frequencyMHz: Double): DoubleArray?
}

internal fun necVersion(code: Int): String = "${code / 10000}.${code / 100 % 100}.${code % 100}"
