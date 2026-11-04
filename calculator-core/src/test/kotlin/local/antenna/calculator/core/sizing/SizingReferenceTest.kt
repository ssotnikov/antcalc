package local.antenna.calculator.core.sizing

import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.ShorteningFactor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SizingReferenceTest(
    private val coefficient: Int,
    private val expectedTotal: Double,
    private val expectedArm: Double,
) {
    @Test
    fun `145 MHz dimensions match independent decimal reference values`() {
        val result = calculateSizing(SizingInput(Frequency.ofHertz(145_000_000), ShorteningFactor.ofTenThousandths(coefficient)))
        assertEquals(expectedTotal, result.dipoleTotalMeters, 1e-12)
        assertEquals(expectedArm, result.dipoleArmMeters, 1e-12)
        assertEquals(expectedArm, result.monopoleMeters, 1e-12)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "factor={0}/10000")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(10000, 1.033767096551724, 0.516883548275862),
            arrayOf(9700, 1.002754083655172, 0.501377041827586),
            arrayOf(9500, 0.982078741724138, 0.491039370862069),
            arrayOf(9300, 0.961403399793103, 0.480701699896552),
            arrayOf(9539, 0.986110433400690, 0.493055216700345),
            arrayOf(1, 0.000103376709655172, 0.000051688354827586),
        )
    }
}
