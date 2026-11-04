package local.antenna.calculator.core.sizing

import local.antenna.calculator.core.units.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test

class IdealSizingTest {
    @Test
    fun `145 MHz reference uses the exact speed of light`() {
        val result = calculateIdealSizing(Frequency.ofHertz(145_000_000))
        assertEquals(2.067534193103448, result.wavelengthMeters, 1e-9)
        assertEquals(0.516883548275862, result.monopoleMeters, 1e-9)
        assertEquals(1.033767096551724, result.dipoleTotalMeters, 1e-9)
        assertEquals(0.516883548275862, result.dipoleArmMeters, 1e-9)
        assertEquals(1.292208870689655, result.fiveEighthsMeters, 1e-9)
    }

    @Test
    fun `lower frequency boundary uses full precision`() {
        val result = calculateIdealSizing(Frequency.ofHertz(100_000))
        assertEquals(2997.92458, result.wavelengthMeters, 2997.92458 * 1e-10)
        assertEquals(749.481145, result.monopoleMeters, 1e-9)
        assertEquals(1498.96229, result.dipoleTotalMeters, 1e-9)
        assertEquals(1873.7028625, result.fiveEighthsMeters, 1e-9)
    }

    @Test
    fun `upper frequency boundary uses full precision`() {
        val result = calculateIdealSizing(Frequency.ofHertz(6_000_000_000))
        assertEquals(0.049965409666667, result.wavelengthMeters, 0.049965409666667 * 1e-10)
        assertEquals(0.012491352416667, result.dipoleArmMeters, 1e-12)
        assertEquals(0.031228381041667, result.fiveEighthsMeters, 1e-12)
    }

    @Test
    fun `doubling frequency halves every ideal dimension`() {
        val original = calculateIdealSizing(Frequency.ofHertz(145_000_000))
        val doubled = calculateIdealSizing(Frequency.ofHertz(290_000_000))
        assertEquals(original.wavelengthMeters / 2, doubled.wavelengthMeters, 1e-12)
        assertEquals(original.monopoleMeters / 2, doubled.monopoleMeters, 1e-12)
        assertEquals(original.dipoleTotalMeters / 2, doubled.dipoleTotalMeters, 1e-12)
        assertEquals(original.dipoleArmMeters / 2, doubled.dipoleArmMeters, 1e-12)
        assertEquals(original.fiveEighthsMeters / 2, doubled.fiveEighthsMeters, 1e-12)
    }
}
