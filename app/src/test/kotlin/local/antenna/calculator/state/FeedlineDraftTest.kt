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
        preferences[intPreferencesKey("feedline.schema")] = 3
        assertThrows(UnsupportedSchemaException::class.java) { FeedlinePreferences.read(preferences) }
    }
    @Test fun readsLegacyPreferencesWithoutInventingNewInputs() {
        val draft = FeedlineDraft(profileId = null, frequency = "145", length = "11", power = "3",
            attenuation = "10", attenuationHertz = 145_000_000, task = FeedlineTask.MAXIMUM_LENGTH,
            allowedLoss = "7.5", connectionLoss = "0.8")
        val preferences = mutablePreferencesOf()
        FeedlinePreferences.write(preferences, draft)
        preferences[intPreferencesKey("feedline.schema")] = 1
        for (key in listOf("task", "allowedLoss", "connectionLoss")) {
            preferences.remove(androidx.datastore.preferences.core.stringPreferencesKey("feedline.$key"))
        }
        assertEquals(draft.copy(task = FeedlineTask.LOSS, allowedLoss = "3", connectionLoss = "0"),
            FeedlinePreferences.read(preferences))
        val future = FeedlineDraftCodec.encode(draft).apply { this[9] = "FUTURE" }
        assertNull(FeedlineDraftCodec.decode(future))
    }
}
