package local.antenna.calculator.core.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SlimJimTest {
    @Test
    fun derivesReturnProjectionAndConvertsSourceFeedHeight() {
        assertEquals(0.973, SlimJimDu1anv.returnProjectionMeters, 1e-9)
        assertEquals(0.0762, SlimJimDu1anv.tapMeters, 1e-9)
        assertEquals(145_000_000L, SlimJimDu1anv.frequency.hertz)
    }

    @Test
    fun estimatesM0ukdDimensionsAtTheSelectedFrequency() {
        val uhf = estimateSlimJimM0ukd(SlimJimBand.SEVENTY_CENTIMETERS.frequency, 0.96)
        assertEquals(0.5030999869885058, uhf.heightMeters, 1e-9)
        assertEquals(0.016540273544827587, uhf.tapMeters, 1e-9)
        val fourMeters = estimateSlimJimM0ukd(SlimJimBand.FOUR_METERS.frequency, 0.90)
        assertEquals(2.933683339, fourMeters.heightMeters, 1e-9)
        assertEquals(fourMeters.wavelengthMeters / 100, fourMeters.gapMeters, 1e-9)
    }

    @Test
    fun scalesG0kyaStartingDimensionsWithoutChangingThePublishedProfile() {
        val published = SlimJimG0kya(SlimJimBand.TEN_METERS.frequency)
        assertEquals(7.48, published.stockMeters, 1e-9)
        assertEquals(2.37, published.gapEndMeters, 1e-9)
        val scaled = SlimJimG0kya(SlimJimBand.SIX_METERS.frequency)
        assertEquals(4.18, scaled.stockMeters, 1e-9)
        assertEquals(2.31 * 28.5 / 51, scaled.gapStartMeters, 1e-9)
        assertEquals(0.13 * 28.5 / 51, scaled.tapMeters, 1e-9)
    }

    @Test
    fun calculatesTheSeparateSlimtennaStubUsingItsOwnVelocityFactor() {
        assertEquals(3.853, Slimtenna4X4lh.stubMeters(SlimJimBand.TWENTY_METERS.frequency), 0.001)
        assertEquals(10.5, Slimtenna4X4lh.HEIGHT_METERS, 1e-9)
    }
}
