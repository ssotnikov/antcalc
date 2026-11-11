package local.antenna.calculator.core

import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.QuantityInput
import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.core.layout.groundPlaneLayout
import local.antenna.calculator.core.sizing.SizingInput
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.core.units.ShorteningFactor
import org.junit.Assert.*
import org.junit.Test

class GroundPlaneTest {
    private val halfMeter = PhysicalLength.ofNanometers(500_000_000)

    @Test fun fourRadialsAt45DegreesHaveExpectedFootprintAndClearance() {
        val result = groundPlaneLayout(halfMeter, halfMeter, 2_000_000_000, 450) as GroundPlaneResult.Layout
        assertEquals(0.353553390593, result.radiusMeters, 1e-9)
        assertEquals(0.707106781186, result.diameterMeters, 1e-9)
        assertEquals(0.353553390593, result.dropMeters, 1e-9)
        assertEquals(1.646446609407, result.endHeightMeters, 1e-9)
        assertEquals(2.5, result.tipHeightMeters, 1e-9)
        assertEquals(2.5, result.wireMeters, 1e-9)
    }

    @Test fun contactIsDistinctFromCrossingGroundAndOverlap() {
        val contact = groundPlaneLayout(halfMeter, halfMeter, 250_000_000, 300) as GroundPlaneResult.Layout
        assertEquals(0.0, contact.endHeightMeters, 0.0)
        assertEquals(GroundPlaneResult.RadialsBelowGround, groundPlaneLayout(halfMeter, halfMeter, 249_999_999, 300))
        assertEquals(GroundPlaneResult.RadialsOverlap, groundPlaneLayout(halfMeter, halfMeter, 2_000_000_000, 900))
        val horizontal = groundPlaneLayout(halfMeter, halfMeter, 2_000_000_000, 0) as GroundPlaneResult.Layout
        assertEquals(0.5, horizontal.radiusMeters, 0.0)
        assertEquals(2.0, horizontal.endHeightMeters, 0.0)
    }

    @Test fun separateFactorsApplyToEachLength() {
        val result = groundPlaneLayout(
            SizingInput(Frequency.ofHertz(145_000_000), ShorteningFactor.DEFAULT),
            ShorteningFactor.ofTenThousandths(8_000), 2_000_000_000, 0,
        ) as GroundPlaneResult.Layout
        val quarterWave = 299_792_458.0 / 145_000_000 / 4
        assertEquals(quarterWave * 0.95, result.whipMeters, 1e-12)
        assertEquals(quarterWave * 0.8, result.radialMeters, 1e-12)
    }

    @Test fun slopeInputHonorsTheBoundsAndStep() {
        assertEquals(FieldState.Valid(0L), QuantityInput.radialAngleTenths("0"))
        assertEquals(FieldState.Valid(899L), QuantityInput.radialAngleTenths("89,9"))
        assertEquals(FieldState.Valid(900L), QuantityInput.radialAngleTenths("90"))
        assertTrue(QuantityInput.radialAngleTenths("90.1") is FieldState.Invalid)
        assertTrue(QuantityInput.radialAngleTenths("15.05") is FieldState.Invalid)
    }
}
