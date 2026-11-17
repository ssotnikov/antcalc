package local.antenna.calculator.nec

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import local.antenna.calculator.core.layout.YagiScaling.Element
import kotlin.time.TimeSource

internal data class NecTrial(val elements: List<Element>, val points: List<OpenNec.Point>) {
    val worstSwr = points.maxOf { it.swr }
}

internal data class NecOptimization(val initial: NecTrial, val best: NecTrial, val goal: Double) {
    val reachedGoal get() = best.worstSwr <= goal
}

internal suspend fun optimizeNecLengths(
    original: List<Element>,
    goal: Double,
    evaluate: suspend (List<Element>) -> List<OpenNec.Point>?,
): NecOptimization? {
    if (original.size < 2 || goal !in 1.0..99.0) return null
    val started = TimeSource.Monotonic.markNow()
    val timeBudgetMs = 30_000L
    val initialPoints = withTimeoutOrNull(timeBudgetMs) { evaluate(original) }?.takeIf { it.isNotEmpty() } ?: return null
    val initial = NecTrial(original, initialPoints)
    var best = initial
    var remaining = 24
    val indices = listOf(1, 0) + original.indices.drop(2)
    for (step in listOf(.02, .01, .005)) {
        for (index in indices) {
            if (remaining < 2 || best.worstSwr <= goal) return NecOptimization(initial, best, goal)
            val base = best.elements
            val bounds = (original[index].length * .95)..(original[index].length * 1.05)
            for (direction in listOf(-1, 1)) {
                currentCoroutineContext().ensureActive()
                val length = (base[index].length + direction * original[index].length * step).coerceIn(bounds)
                if (length == base[index].length) continue
                val timeLeft = timeBudgetMs - started.elapsedNow().inWholeMilliseconds
                if (timeLeft <= 0) return NecOptimization(initial, best, goal)
                val candidate = base.toMutableList().also { it[index] = it[index].copy(length = length) }
                remaining--
                val points = withTimeoutOrNull(timeLeft) { evaluate(candidate) }?.takeIf { it.isNotEmpty() } ?: continue
                val trial = NecTrial(candidate, points)
                if (trial.worstSwr < best.worstSwr) best = trial
            }
        }
    }
    return NecOptimization(initial, best, goal)
}
