package local.antenna.calculator.core.feedline

import local.antenna.calculator.core.input.*
import local.antenna.calculator.core.units.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CableLengthTest {
    private val f = Frequency.ofHertz(145_000_000)
    private val vf = VelocityFactor.ofTenThousandths(6600)
    @Test fun quarterWaveReferenceAndScaling() {
        val r = cableFromAngle(f, vf, 90_000)
        assertEquals(.341143141862069, r.lengthMeters.toDouble(), 1e-12)
        assertEquals(1e9 / (4 * 145e6), r.delayNanoseconds.toDouble(), 1e-12)
        assertEquals(90.0, r.fullDegrees.toDouble(), 0.0)
        assertEquals(90.0, r.wrappedDegrees.toDouble(), 0.0)
        assertEquals(r.lengthMeters.toDouble() / 2, cableFromAngle(Frequency.ofHertz(290_000_000), vf, 90_000).lengthMeters.toDouble(), 1e-12)
        assertEquals(r.lengthMeters.toDouble() / 2, cableFromAngle(f, VelocityFactor.ofTenThousandths(3300), 90_000).lengthMeters.toDouble(), 1e-12)
    }
    @Test fun zeroAndMultipleTurns() {
        val r = cableFromLength(f, vf, CableLength.ofNanometers(0))
        assertEquals(0, r.lengthMeters.signum()); assertEquals(0, r.fullDegrees.signum()); assertEquals(0, r.delayNanoseconds.signum())
        val turns = cableFromAngle(f, vf, 450_000)
        assertEquals(450.0, turns.fullDegrees.toDouble(), 0.0)
        assertEquals(90.0, turns.wrappedDegrees.toDouble(), 0.0)
        assertEquals(5 * cableFromAngle(f, vf, 90_000).lengthMeters.toDouble(), turns.lengthMeters.toDouble(), 1e-12)
    }
    @Test fun lengthAndAngleAgreeAtExactWavelength() {
        val freq = Frequency.ofHertz(299_792_458)
        val factor = VelocityFactor.ofTenThousandths(10_000)
        val length = cableFromLength(freq, factor, CableLength.ofNanometers(1_000_000_000))
        val angle = cableFromAngle(freq, factor, 360_000)
        assertEquals(360.0, length.fullDegrees.toDouble(), 1e-12)
        assertEquals(0, length.wrappedDegrees.signum())
        assertEquals(1.0, angle.lengthMeters.toDouble(), 1e-12)
        assertEquals(length.delayNanoseconds.toDouble(), angle.delayNanoseconds.toDouble(), 1e-12)
    }
    @Test fun extremesRemainFiniteWithBoundedModulo() {
        val r = cableFromLength(Frequency.ofHertz(Frequency.MAX_HERTZ), VelocityFactor.ofTenThousandths(1), CableLength.ofNanometers(10_000_000_000_000))
        assertTrue(r.fullDegrees > BigDecimal("360000000000"))
        assertTrue(r.wrappedDegrees >= BigDecimal.ZERO && r.wrappedDegrees < BigDecimal(360))
        val max = cableFromAngle(Frequency.ofHertz(Frequency.MIN_HERTZ), VelocityFactor.ofTenThousandths(10_000), 360_000_000)
        assertEquals(2_997_924.58, max.lengthMeters.toDouble(), 1e-9)
    }
    @Test fun exactInputBoundsStepsAndSyntax() {
        fun parse(text: String, field: CableField) = parseCableNumber(text, field, FrequencyUnit.MEGAHERTZ, LengthUnit.MILLIMETERS)
        assertEquals(FieldState.Valid(0L), parse("0", CableField.LENGTH))
        assertEquals(FieldState.Valid(145_000_001L), parse("145,000001", CableField.FREQUENCY))
        assertEquals(FieldState.Valid(6600L), parse("0.6600", CableField.VELOCITY))
        assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), parse("145.0000001", CableField.FREQUENCY))
        assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), parse("0.66001", CableField.VELOCITY))
        assertEquals(FieldState.Invalid(InputError.TOO_PRECISE), parse("0.0001", CableField.ANGLE))
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), parse("0", CableField.VELOCITY))
        assertEquals(FieldState.Invalid(InputError.OUT_OF_RANGE), parse("360000.001", CableField.ANGLE))
        assertEquals(FieldState.Incomplete, parse("90,", CableField.ANGLE))
        for (text in listOf("-1", "NaN", "Infinity", "1e3", "1,2.3", "1 000"))
            assertEquals(FieldState.Invalid(InputError.INVALID_NUMBER), parse(text, CableField.LENGTH))
        assertEquals(FieldState.Invalid(InputError.TOO_LONG), parse("1".repeat(33), CableField.LENGTH))
    }
    @Test fun invalidDirectValuesAreRejected() {
        for (call in listOf<() -> Any>({ VelocityFactor.ofTenThousandths(0) }, { CableLength.ofNanometers(-1) }, { cableFromAngle(f, vf, -1) })) {
            try { call(); fail("Expected range error") } catch (_: IllegalArgumentException) { }
        }
    }
}
