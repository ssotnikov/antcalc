package local.antenna.calculator.core

import local.antenna.calculator.core.layout.calculateJPole
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.ShorteningFactor
import org.junit.Assert.assertEquals
import org.junit.Test

class JPoleTest {
    @Test
    fun calculatesStartingDimensionsAndReactsToBothInputs() {
        val initial = calculateJPole(Frequency.ofHertz(146_000_000), ShorteningFactor.DEFAULT)
        assertEquals(1.4630650685, initial.longElement, 1e-9)
        assertEquals(0.0513356164, initial.spacing, 1e-9)
        assertEquals(0.0451753425, initial.feedHeight, 1e-9)

        val higherFrequency = Frequency.ofHertz(435_000_000)
        val shortened = calculateJPole(higherFrequency, ShorteningFactor.DEFAULT)
        val unshortened = calculateJPole(higherFrequency, ShorteningFactor.ofTenThousandths(10_000))
        assertEquals(0.4910517241, shortened.longElement, 1e-9)
        assertEquals(unshortened.longElement * 0.95, shortened.longElement, 1e-9)
        assertEquals(unshortened.wavelength, shortened.wavelength, 0.0)
        assertEquals(unshortened.spacing, shortened.spacing, 0.0)
        assertEquals(unshortened.feedHeight, shortened.feedHeight, 0.0)
    }
}
