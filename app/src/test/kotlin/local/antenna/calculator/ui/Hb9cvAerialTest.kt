package local.antenna.calculator.ui

import local.antenna.calculator.core.Hb9cvAerial
import local.antenna.calculator.core.Hb9cvAerial.Dimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class Hb9cvAerialTest {
    @Test fun reproducesAllPublishedControlReadings() {
        for (line in readings.lineSequence()) {
            val values = line.split(' ').map(String::toDouble)
            val mhz = values.first()
            val result = Hb9cvAerial.Result(mhz)
            val lengths = listOf(HB9CV_AERIAL_WAVE to result.wavelengthMeters) +
                Dimension.entries.map { it.key to result.millimeters(it) / 1000 }
            val actual = lengths.map { (key, length) ->
                val (number, unit) = hb9cvAerialLength(Locale.US, key, length, mhz, "mm", "m").split(' ')
                if (unit == "m") number.toDouble() * 1000 else number.toDouble()
            } + hb9cvAerialNumber(Locale.US, result.capacitancePf, 1).toDouble()
            values.drop(1).zip(actual).forEachIndexed { i, (expected, reading) ->
                assertEquals("$mhz MHz, field $i", expected, reading, 1e-9)
            }
        }
    }

    @Test fun reproducesCapacitanceRoundingTransitionsInBothLocales() {
        val transitions = listOf(
            Triple(145.362, 145.363, "15.9" to "15.8"),
            Triple(903.529, 903.530, "2.6" to "2.5"),
            Triple(1181.538, 1181.539, "2" to "1.9"),
            Triple(1486.451, 1486.452, "1.6" to "1.5"),
        )
        for ((lower, higher, expected) in transitions) {
            for ((frequency, value) in listOf(lower to expected.first, higher to expected.second)) {
                val capacitance = Hb9cvAerial.Result(frequency).capacitancePf
                assertEquals(value, hb9cvAerialNumber(Locale.US, capacitance, 1))
                assertEquals(value.replace('.', ','), hb9cvAerialNumber(Locale.forLanguageTag("ru"), capacitance, 1))
            }
        }
    }

    @Test fun rejectsInvalidInputsAndUsesUnroundedSpacingForTheBoom() {
        for (invalid in listOf(99.999, 1500.001, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Hb9cvAerial.Result(invalid) }
        }
        val result = Hb9cvAerial.Result(435.0)
        val boom = result.boom(15.0, 20.0)
        assertEquals(15.0, boom.rearPositionMm, 1e-12)
        assertEquals(15.0 + 29_952.0 / 435, boom.frontPositionMm, 1e-12)
        assertEquals(35.0 + 29_952.0 / 435, boom.lengthMm, 1e-12)
        assertThrows(IllegalArgumentException::class.java) { result.boom(-1.0, 20.0) }
    }

    // f (MHz), λ, LR, LF, MR, MF, A, D, d, h (mm), C (pF).
    // Readings from https://3g-aerial.biz/onlajn-raschety/raschety-antenn/raschet-antenny-hb9cv
    private val readings = """
        100 2998 1477 1315 271 256 300 18.3 9.14 9.14 23
        101 2968 1463 1302 268 254 297 18.1 9.05 9.05 22.8
        123.456 2428 1197 1065 219 208 243 14.8 7.41 7.41 18.7
        137.29 2184 1076 958 197 187 218 13.3 6.66 6.66 16.8
        144 2082 1026 913 188 178 208 12.7 6.35 6.35 16
        145 2068 1019 907 187 177 207 12.6 6.31 6.31 15.9
        145.5 2060 1015 904 186 176 206 12.6 6.28 6.28 15.8
        146.52 2046 1008 897 185 175 204 12.5 6.24 6.24 15.7
        199.99 1499 739 657 135 128 150 9.14 4.57 4.57 11.5
        260 1153 568 506 104 98.6 115 7.03 3.52 3.52 8.9
        299.79 1000 493 439 90.3 85.5 99.9 6.1 3.05 3.05 7.7
        299.8 1000 493 439 90.3 85.5 99.9 6.1 3.05 3.05 7.7
        300 999 492 438 90.2 85.4 99.8 6.1 3.05 3.05 7.7
        301 996 491 437 89.9 85.2 99.5 6.08 3.04 3.04 7.7
        400 749 369 329 67.7 64.1 74.9 4.57 2.29 2.29 5.8
        420 713.8 351.8 313 64.5 61 71.3 4.35 2.18 2.18 5.5
        433.92 690.9 340.5 303 62.4 59.1 69 4.21 2.11 2.11 5.3
        435 689.2 339.6 302.2 62.2 58.9 68.9 4.2 2.1 2.1 5.3
        500 599.6 295.5 262.9 54.1 51.3 59.9 3.66 1.83 1.83 4.6
        578.9 517.9 255.2 227.1 46.8 44.3 51.7 3.16 1.58 1.58 4
        600 499.7 246.2 219.1 45.1 42.7 49.9 3.05 1.52 1.52 3.8
        712.38 420.8 207.4 184.6 38 36 42 2.57 1.28 1.28 3.2
        900 333.1 164.2 146.1 30.1 28.5 33.3 2.03 1.02 1.02 2.6
        987.65 303.5 149.6 133.1 27.4 26 30.3 1.85 0.926 0.926 2.3
        1200 249.8 123.1 109.6 22.6 21.4 25 1.52 0.762 0.762 1.9
        1250.25 239.8 118.2 105.2 21.7 20.5 24 1.46 0.731 0.731 1.8
        1296 231.3 114 101.4 20.9 19.8 23.1 1.41 0.706 0.706 1.8
        1387.42 216.1 106.5 94.8 19.5 18.5 21.6 1.32 0.659 0.659 1.7
        1499 200 98.6 87.7 18.1 17.1 20 1.22 0.61 0.61 1.5
        1500 199.9 98.5 87.6 18 17.1 20 1.22 0.61 0.61 1.5
    """.trimIndent()
}
