package local.antenna.calculator.core.measurements

import kotlin.math.abs

enum class BandEdgeKind { MEASURED, ESTIMATED, SWEEP_LIMIT, INVALID_NEIGHBOR, WINDOW_LIMIT }

data class BandEdge(val hertz: Double, val kind: BandEdgeKind)

data class SwrMinimum(val hertz: Double, val swr: Double)

data class SwrBand(val lower: BandEdge, val upper: BandEdge, val minimum: SwrMinimum) {
    val widthHertz get() = upper.hertz - lower.hertz
}

data class ReactanceZero(val firstHertz: Double, val lastHertz: Double = firstHertz, val estimated: Boolean = false)

data class SwrBandwidth(
    val threshold: Double,
    val bands: List<SwrBand>,
    val minimum: SwrMinimum?,
    val reactanceZeros: List<ReactanceZero>,
    val excludedPoints: Int,
)

/** A verified Touchstone sweep is in frequency order. Null denotes an invalid threshold. */
fun S11Sweep.swrBandwidth(threshold: Double): SwrBandwidth? {
    if (threshold !in 1.0..1000.0) return null
    val limit = (threshold - 1) / (threshold + 1)
    val magnitudes = points.map { it.magnitude.takeIf { value -> value <= 1.0 } }

    fun edge(index: Int, outside: Int): BandEdge {
        val point = points[index]
        if (outside !in points.indices) return BandEdge(point.hertz, BandEdgeKind.SWEEP_LIMIT)
        val neighbor = magnitudes[outside] ?: return BandEdge(point.hertz, BandEdgeKind.INVALID_NEIGHBOR)
        val inside = checkNotNull(magnitudes[index])
        if (inside == limit) return BandEdge(point.hertz, BandEdgeKind.MEASURED)
        val fraction = (limit - inside) / (neighbor - inside)
        return BandEdge(interpolate(point.hertz, points[outside].hertz, fraction), BandEdgeKind.ESTIMATED)
    }

    fun minimumAt(index: Int): SwrMinimum = SwrMinimum(points[index].hertz, checkNotNull(points[index].swr))

    val bands = buildList {
        var index = 0
        while (index < points.size) {
            if ((magnitudes[index] ?: Double.POSITIVE_INFINITY) > limit) {
                index++
                continue
            }
            val start = index
            var minimum = index
            do {
                if (checkNotNull(magnitudes[index]) < checkNotNull(magnitudes[minimum])) minimum = index
                index++
            } while (index < points.size && (magnitudes[index] ?: Double.POSITIVE_INFINITY) <= limit)
            add(SwrBand(edge(start, start - 1), edge(index - 1, index), minimumAt(minimum)))
        }
    }
    val minimum = points.indices.filter { magnitudes[it] != null }
        .minByOrNull { checkNotNull(magnitudes[it]) }?.let(::minimumAt)
    val reactance = points.mapIndexed { index, point ->
        if (magnitudes[index] == null) null else point.impedance(referenceOhms)?.second
    }
    return SwrBandwidth(threshold, bands, minimum, reactanceZeros(points, reactance), magnitudes.count { it == null })
}

/** Restricts a measured result without treating a window boundary as a measured crossing. */
fun S11Sweep.swrBandwidth(threshold: Double, window: ClosedFloatingPointRange<Double>): SwrBandwidth? {
    if (!window.start.isFinite() || !window.endInclusive.isFinite() || window.start >= window.endInclusive) return null
    val candidates = points.filter { it.hertz in window }
    val measured = candidates.mapNotNull { point -> point.swr?.let { SwrMinimum(point.hertz, it) } }
    val minimum = measured.minByOrNull { it.swr } ?: return null
    val result = swrBandwidth(threshold) ?: return null
    var index = 0
    val bands = result.bands.mapNotNull { band ->
        if (band.upper.hertz < window.start || band.lower.hertz > window.endInclusive) return@mapNotNull null
        val lower = if (band.lower.hertz < window.start) BandEdge(window.start, BandEdgeKind.WINDOW_LIMIT) else band.lower
        val upper = if (band.upper.hertz > window.endInclusive) BandEdge(window.endInclusive, BandEdgeKind.WINDOW_LIMIT) else band.upper
        while (index < measured.size && measured[index].hertz < lower.hertz) index++
        val start = index
        while (index < measured.size && measured[index].hertz <= upper.hertz) index++
        val bandMinimum = measured.subList(start, index).minByOrNull { it.swr } ?: return@mapNotNull null
        SwrBand(lower, upper, bandMinimum)
    }
    val zeros = result.reactanceZeros.filter { it.lastHertz >= window.start && it.firstHertz <= window.endInclusive }
        .map { it.copy(firstHertz = maxOf(it.firstHertz, window.start), lastHertz = minOf(it.lastHertz, window.endInclusive)) }
    return SwrBandwidth(threshold, bands, minimum, zeros, candidates.size - measured.size)
}

private fun reactanceZeros(points: List<S11Point>, reactance: List<Double?>): List<ReactanceZero> = buildList {
    var index = 0
    while (index < points.size) {
        val current = reactance[index]
        if (current == 0.0) {
            val first = index
            while (reactance.getOrNull(index + 1) == 0.0) index++
            add(ReactanceZero(points[first].hertz, points[index].hertz))
        } else if (current != null) {
            val next = reactance.getOrNull(index + 1)
            if (next != null && next != 0.0 && (current > 0) != (next > 0)) {
                val scale = maxOf(abs(current), abs(next))
                val a = abs(current) / scale
                val b = abs(next) / scale
                add(ReactanceZero(interpolate(points[index].hertz, points[index + 1].hertz, a / (a + b)), estimated = true))
            }
        }
        index++
    }
}

private fun interpolate(start: Double, end: Double, fraction: Double): Double = start + (end - start) * fraction
