package local.antenna.calculator.ui

import java.math.BigDecimal
import local.antenna.calculator.core.feedline.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test

class FeedlineMaximumLengthTest {
    private val draft = FeedlineDraft(profileId = null, frequency = "150", length = "invalid",
        attenuation = "6,3", attenuationHertz = 150_000_000, power = "100",
        task = FeedlineTask.MAXIMUM_LENGTH, allowedLoss = "3", connectionLoss = "0")

    @Test fun roundsDownAndUsesTheSamePhysicalLengthForPower() {
        val result = requireNotNull(draft.maximumCalculation(null))
        val length = (result.limit as FeedlineLengthLimit.Finite).metres
        assertEquals(BigDecimal("47.61"), length)
        assertEquals(calculateFeedlineLoss(length, BigDecimal("6.3"), AttenuationUnit.PER_100_METRES, BigDecimal("100")), result.transmission)
        assertTrue(result.transmission!!.decibels <= 3)
        val withConnections = requireNotNull(draft.copy(connectionLoss = "0,4").maximumCalculation(null))
        assertEquals(BigDecimal("41.26"), (withConnections.limit as FeedlineLengthLimit.Finite).metres)
        assertTrue(withConnections.transmission!!.decibels <= 3)
    }

    @Test fun provisionalFrequencyInvalidValuesAndZeroAttenuation() {
        val changed = draft.copy(frequency = "450")
        assertNull(changed.maximumCalculation(null))
        assertNotNull(changed.maximumCalculation(null, allowProvisional = true))
        assertNull(draft.copy(allowedLoss = "").maximumCalculation(null))
        assertNull(draft.copy(connectionLoss = "-1").maximumCalculation(null))
        assertNull(draft.copy(power = "no power").maximumCalculation(null))
        val exceeded = requireNotNull(draft.copy(connectionLoss = "4").maximumCalculation(null))
        assertEquals(FeedlineLengthLimit.Exceeded, exceeded.limit)
        assertNull(exceeded.transmission)
        val unbounded = requireNotNull(draft.copy(attenuation = "0").maximumCalculation(null))
        assertEquals(FeedlineLengthLimit.Unbounded, unbounded.limit)
        assertEquals(1.0, unbounded.transmission!!.transmittedFraction, 0.0)
    }

    @Test fun subCentimetreLengthsRemainPositiveAndOptionalPowerStaysOptional() {
        val result = requireNotNull(draft.copy(allowedLoss = "0.000001", power = "").maximumCalculation(null))
        val metres = (result.limit as FeedlineLengthLimit.Finite).metres
        assertTrue(metres > BigDecimal.ZERO && metres < BigDecimal("0.01"))
        assertNull(result.transmission!!.outputWatts)
        assertEquals(draft, FeedlineDraftCodec.decode(FeedlineDraftCodec.encode(draft)))
        assertEquals(FeedlineTask.LOSS, FeedlineDraftCodec.decode(FeedlineDraftCodec.encode(draft).take(9))!!.task)
        assertNull(FeedlineDraftCodec.decode(FeedlineDraftCodec.encode(draft.copy(allowedLoss = "1".repeat(34)))))
    }
}
