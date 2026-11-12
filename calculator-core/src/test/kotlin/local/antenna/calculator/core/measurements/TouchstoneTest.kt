package local.antenna.calculator.core.measurements

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class TouchstoneTest {
    private fun parse(text: String) = Touchstone.read(text.byteInputStream())

    @Test fun representationsAndUnitsAgree() {
        val expected = parse("! One port\n# MHz S RI R 75\n145 0.3 0.4 ! inline").points.single()
        val phase = Math.toDegrees(atan2(0.4, 0.3))
        for ((header, values) in listOf(
            "kHz S MA R 75" to "145000 0.5 $phase",
            "HZ S DB R 75" to "145000000 ${20 * log10(0.5)} $phase",
            "GHz S MA R 75" to "0.145 0.5 $phase",
        )) {
            val sweep = parse("# $header\r\n$values\r\n")
            val point = sweep.points.single()
            assertEquals(75.0, sweep.referenceOhms, 0.0)
            assertEquals(expected.hertz, point.hertz, 1e-7)
            assertEquals(expected.real, point.real, 1e-9)
            assertEquals(expected.imaginary, point.imaginary, 1e-9)
        }
        assertEquals(50.0, parse("#\n0.145 0.5 90").referenceOhms, 0.0)
        assertEquals(0.5, parse("1.45e-1 5e-1 90").points.single().imaginary, 1e-9)
    }

    @Test fun lineAndPointLimitsAndInvalidData() {
        val invalid = listOf(
            "# MHz S RI R 50\n145 0 0\n145 0 0" to 3,
            "# MHz S RI R 50\n145 0\n0" to 2,
            "# MHz S RI R 50\n145 NaN 0" to 2,
            "# MHz S RI R 50\n145 0x1.0p0 0" to 2,
            "# MHz S RI R 50\n0.09 0 0" to 2,
            "# MHz Z RI R 50\n145 0 0" to 1,
            "# MHz GHz S RI R 50\n145 0 0" to 1,
            "# MHz S RI R 0\n145 0 0" to 1,
            "\uFEFF# MHz S RI R 50\n145 0 0" to 1,
            "# MHz S MA\n145 -0.5 90" to 2,
            "!" + "a".repeat(4096) to 1,
        )
        invalid.forEach { (text, line) ->
            val error = assertThrows(TouchstoneException::class.java) { parse(text) }
            assertEquals(line, error.line)
        }
        val invalidEncoding = byteArrayOf(33, 0xc0.toByte(), 0xaf.toByte(), 10)
        assertEquals(1, assertThrows(TouchstoneException::class.java) {
            Touchstone.read(invalidEncoding.inputStream())
        }.line)
        val points = buildString {
            appendLine("# Hz S RI")
            repeat(Touchstone.MAX_POINTS) { appendLine("${100000 + it} 0 0") }
        }
        assertEquals(Touchstone.MAX_POINTS, parse(points).points.size)
        assertEquals(TouchstoneProblem.LIMIT, assertThrows(TouchstoneException::class.java) {
            parse(points + "120000 0 0")
        }.problem)
        assertEquals(TouchstoneProblem.LIMIT, assertThrows(TouchstoneException::class.java) {
            parse(("!" + " ".repeat(4095) + "\n").repeat(513))
        }.problem)
    }

    @Test fun exceptionalPointsAndNearestFrequency() {
        val sweep = parse("# MHz S RI\n145 0 0\n146 1 0\n147 1.02 0")
        val (match, open, nonpassive) = sweep.points
        assertEquals(1.0, match.swr!!, 0.0)
        assertEquals(50.0 to 0.0, match.impedance(50.0))
        assertEquals(Double.NEGATIVE_INFINITY, match.decibels, 0.0)
        assertTrue(open.isOpen)
        assertEquals(Double.POSITIVE_INFINITY, open.swr!!, 0.0)
        assertNull(open.impedance(50.0))
        assertNull(nonpassive.swr)
        assertEquals(1.02, nonpassive.magnitude, 0.0)
        assertNull(sweep.nearestIndex(144e6))
        assertEquals(0, sweep.nearestIndex(145.5e6))
        assertEquals(1, sweep.nearestIndex(145.6e6))
    }

    @Test fun cancellationIsPropagatedBeforeAnyResult() {
        var lines = 0
        assertThrows(InterruptedException::class.java) {
            Touchstone.read("# MHz S RI\n145 0 0\n146 0 0".byteInputStream()) {
                if (++lines == 3) throw InterruptedException()
            }
        }
    }
}
