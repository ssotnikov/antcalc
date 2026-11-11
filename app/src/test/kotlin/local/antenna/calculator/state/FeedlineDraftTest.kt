package local.antenna.calculator.state

import local.antenna.calculator.core.feedline.AttenuationUnit
import local.antenna.calculator.core.units.FrequencyUnit
import local.antenna.calculator.data.FeedlinePreferences
import local.antenna.calculator.data.UnsupportedSchemaException
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class FeedlineDraftTest {
    @Test fun unitChangesPreserveTheEnteredValueAndItsFrequency() {
        val original = FeedlineDraft(profileId = null).editAttenuation("12,8")
        val converted = original.changeFrequencyUnit(FrequencyUnit.GIGAHERTZ)
            .copy(attenuationUnit = AttenuationUnit.PER_100_FEET)
        assertEquals("0,9", converted.frequency)
        assertEquals("3,90144", converted.attenuationText())
        assertEquals(original.manualAttenuation(), converted.manualAttenuation())
        assertEquals(converted.hertz(), converted.attenuationHertz)
        assertNotEquals(converted.chooseFrequency(915_000_000).hertz(), converted.attenuationHertz)
    }

    @Test fun restoresBoundedDraftAndRejectsUnknownPreferencesVersion() {
        val draft = FeedlineDraft(length = "9,", power = "50")
        val preferences = mutablePreferencesOf()
        assertEquals(FeedlineDraft(), FeedlinePreferences.read(preferences))
        FeedlinePreferences.write(preferences, draft)
        assertEquals(draft, FeedlinePreferences.read(preferences))
        assertEquals(draft, FeedlineDraftCodec.decode(FeedlineDraftCodec.encode(draft)))
        preferences[intPreferencesKey("feedline.schema")] = 2
        assertThrows(UnsupportedSchemaException::class.java) { FeedlinePreferences.read(preferences) }
    }
}
