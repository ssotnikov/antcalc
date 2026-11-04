package local.antenna.calculator.core.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuantitiesTest {
    @Test
    fun `frequency accepts both working boundaries`() {
        assertEquals(100_000L, Frequency.ofHertz(100_000).hertz)
        assertEquals(6_000_000_000L, Frequency.ofHertz(6_000_000_000).hertz)
    }

    @Test
    fun `frequency rejects values outside boundaries without overflow`() {
        for (value in listOf(Long.MIN_VALUE, -1L, 0L, 99_999L, 6_000_000_001L, Long.MAX_VALUE)) {
            assertThrows("hertz=$value", IllegalArgumentException::class.java) { Frequency.ofHertz(value) }
        }
    }

    @Test
    fun `physical length accepts one millimeter and ten thousand meters`() {
        assertEquals(1_000_000L, PhysicalLength.ofNanometers(1_000_000).nanometers)
        assertEquals(10_000_000_000_000L, PhysicalLength.ofNanometers(10_000_000_000_000).nanometers)
    }

    @Test
    fun `physical length rejects values outside boundaries without overflow`() {
        for (value in listOf(Long.MIN_VALUE, 0L, 999_999L, 10_000_000_000_001L, Long.MAX_VALUE)) {
            assertThrows("nanometers=$value", IllegalArgumentException::class.java) {
                PhysicalLength.ofNanometers(value)
            }
        }
    }

    @Test
    fun `factor accepts its smallest step and unity`() {
        assertEquals(1, ShorteningFactor.ofTenThousandths(1).tenThousandths)
        assertEquals(10_000, ShorteningFactor.ofTenThousandths(10_000).tenThousandths)
    }

    @Test
    fun `factor rejects zero negative and excessive values`() {
        for (value in listOf(Int.MIN_VALUE, -1, 0, 10_001, Int.MAX_VALUE)) {
            assertThrows("factor=$value", IllegalArgumentException::class.java) {
                ShorteningFactor.ofTenThousandths(value)
            }
        }
    }

    @Test
    fun `factor presets and default match the specification`() {
        assertEquals(9_500, ShorteningFactor.DEFAULT.tenThousandths)
        assertEquals(listOf(10_000, 9_700, 9_500, 9_300), ShorteningFactor.PRESETS.map { it.tenThousandths })
    }
}
