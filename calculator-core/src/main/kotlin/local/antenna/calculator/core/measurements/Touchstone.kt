package local.antenna.calculator.core.measurements

import java.io.InputStream
import kotlin.math.*

data class S11Point(val hertz: Double, val real: Double, val imaginary: Double) {
    val magnitude: Double get() = hypot(real, imaginary)
    val phaseDegrees: Double get() = Math.toDegrees(atan2(imaginary, real))
    val decibels: Double get() = 20 * log10(magnitude)
    val swr: Double? get() = magnitude.let { if (it <= 1) (1 + it) / (1 - it) else null }
    val isOpen: Boolean get() = real == 1.0 && imaginary == 0.0

    fun impedance(referenceOhms: Double): Pair<Double, Double>? {
        if (isOpen) return null
        val scale = maxOf(1.0, abs(real), abs(imaginary))
        val r = real / scale
        val i = imaginary / scale
        val one = 1 / scale
        val difference = one - r
        val denominatorScale = maxOf(abs(difference), abs(i))
        val dr = difference / denominatorScale
        val di = i / denominatorScale
        val divisor = dr * dr + di * di
        val resistance = referenceOhms * ((((one + r) * dr - i * di) / divisor) / denominatorScale)
        val reactance = referenceOhms * (((2 * one * di) / divisor) / denominatorScale)
        return if (resistance.isFinite() && reactance.isFinite()) resistance to reactance else null
    }
}

data class S11Sweep(val referenceOhms: Double, val points: List<S11Point>) {
    fun nearestIndex(hertz: Double): Int? {
        if (hertz < points.first().hertz || hertz > points.last().hertz) return null
        val index = points.binarySearchBy(hertz) { it.hertz }
        if (index >= 0) return index
        val upper = -index - 1
        return if (hertz - points[upper - 1].hertz <= points[upper].hertz - hertz) upper - 1 else upper
    }
}

enum class TouchstoneProblem { FORMAT, LIMIT, FREQUENCY }
class TouchstoneException(val problem: TouchstoneProblem, val line: Int) : Exception()

object Touchstone {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_POINTS = 20_000
    private const val MAX_LINE = 4096
    private val decimal = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    private val spacing = Regex("[ \\t]+")
    private val units = mapOf("HZ" to 1.0, "KHZ" to 1e3, "MHZ" to 1e6, "GHZ" to 1e9)
    private enum class Format { RI, MA, DB }
    private data class Options(val unit: Double = 1e9, val format: Format = Format.MA, val reference: Double = 50.0)

    fun read(input: InputStream, checkCancellation: () -> Unit = {}): S11Sweep {
        val stream = input.buffered()
        val buffer = ByteArray(MAX_LINE + 1)
        val points = ArrayList<S11Point>()
        var options = Options()
        var hasOptions = false
        var bytes = 0
        var line = 0
        fun fail(problem: TouchstoneProblem = TouchstoneProblem.FORMAT): Nothing =
            throw TouchstoneException(problem, line)
        fun number(token: String): Double = token.takeIf(decimal::matches)
            ?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: fail()

        while (true) {
            checkCancellation()
            line++
            var length = 0
            var eof = false
            while (true) {
                val next = stream.read()
                if (next < 0) { eof = true; break }
                if (++bytes > MAX_BYTES) fail(TouchstoneProblem.LIMIT)
                if (next == 10) break
                if (length == buffer.size) fail(TouchstoneProblem.LIMIT)
                buffer[length++] = next.toByte()
            }
            if (eof && length == 0) break
            if (length > 0 && buffer[length - 1] == 13.toByte()) length--
            if (length > MAX_LINE) fail(TouchstoneProblem.LIMIT)
            val raw = try {
                Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(buffer, 0, length)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                fail()
            }
            if (raw.any { it == '\uFEFF' || (it.isISOControl() && it != '\t') }) fail()
            val record = raw.substringBefore('!').trim(' ', '\t')
            if (record.startsWith('#')) {
                if (hasOptions || points.isNotEmpty()) fail()
                hasOptions = true
                val tokens = record.drop(1).trim(' ', '\t').uppercase().split(spacing)
                val seen = mutableSetOf<String>()
                var index = 0
                while (index < tokens.size) {
                    val token = tokens[index++]
                    val category = when {
                        token in units -> { options = options.copy(unit = units.getValue(token)); "unit" }
                        token == "S" -> "parameter"
                        token in Format.entries.map { it.name } -> {
                            options = options.copy(format = Format.valueOf(token)); "format"
                        }
                        token == "R" -> {
                            val value = tokens.getOrNull(index++)?.let(::number) ?: fail()
                            if (value <= 0) fail()
                            options = options.copy(reference = value)
                            "reference"
                        }
                        token.isEmpty() && tokens.size == 1 -> break
                        else -> fail()
                    }
                    if (!seen.add(category)) fail()
                }
            } else if (record.isNotEmpty()) {
                val tokens = record.split(spacing)
                if (tokens.size != 3) fail()
                if (points.size == MAX_POINTS) fail(TouchstoneProblem.LIMIT)
                val frequency = number(tokens[0]) * options.unit
                if (frequency !in 1e5..6e9 || frequency <= (points.lastOrNull()?.hertz ?: 0.0)) {
                    fail(TouchstoneProblem.FREQUENCY)
                }
                val first = number(tokens[1])
                val second = number(tokens[2])
                val point = if (options.format == Format.RI) S11Point(frequency, first, second) else {
                    val magnitude = if (options.format == Format.MA) first else 10.0.pow(first / 20)
                    if (magnitude < 0 || !magnitude.isFinite()) fail()
                    val phase = Math.toRadians(second % 360)
                    S11Point(frequency, magnitude * cos(phase), magnitude * sin(phase))
                }
                points += point
            }
            if (eof) break
        }
        if (points.isEmpty()) fail()
        return S11Sweep(options.reference, points)
    }
}
