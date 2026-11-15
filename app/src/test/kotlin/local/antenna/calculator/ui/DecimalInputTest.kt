package local.antenna.calculator.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecimalInputTest {
    @Test fun acceptsDecimalEditsInBothLocales() {
        listOf("", "0", "145,500", "145.500", "0,", ".5").forEach { assertTrue(it.acceptsDecimalEdit()) }
    }

    @Test fun rejectsPastedTextAndDuplicateSeparators() {
        listOf("145 MHz", "1e3", "1,2.3", "1 450", "9".repeat(33)).forEach { assertFalse(it.acceptsDecimalEdit()) }
        assertFalse("1234".acceptsDecimalEdit(limit = 3))
    }

    @Test fun permitsSignsOnlyInSignedFields() {
        listOf("-", "+", "-12,5", "+12.5").forEach {
            assertTrue(it.acceptsDecimalEdit(signed = true))
            assertFalse(it.acceptsDecimalEdit())
        }
        assertFalse("1-2".acceptsDecimalEdit(signed = true))
    }
}
