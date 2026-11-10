package local.antenna.calculator.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.*
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    // Android FileStorage uses File.renameTo replacement semantics available on Android/Linux.
    // Windows cannot replace an existing target that way. Use DataStore's public Okio backend
    // for repeated-write tests, retaining the same official Preferences protobuf serializer.
    private fun repository(file: File, scope: CoroutineScope) = DataStoreSettingsRepository(file, scope) {
        OkioStorage(FileSystem.SYSTEM, object : OkioSerializer<Preferences> {
            override val defaultValue: Preferences = emptyPreferences()
            override suspend fun readFrom(source: BufferedSource) = PreferencesFileSerializer.readFrom(source.inputStream())
            override suspend fun writeTo(t: Preferences, sink: BufferedSink) = PreferencesFileSerializer.writeTo(t, sink.outputStream())
        }) { file.absolutePath.toPath() }
    }

    private fun populated(): PersistedCalculatorState {
        var draft = CalculatorDraft.defaults()
        val texts = mapOf(FieldKey.SIZING_FREQUENCY to "433,000001", FieldKey.FACTOR to "0,9539",
            FieldKey.ACTUAL_LENGTH to "1000,000001", FieldKey.MEASURED to "146", FieldKey.TARGET to "156")
        texts.forEach { (key, text) -> draft = draft.update(key, draft[key].edit(text)) }
        draft = draft.update(FieldKey.SIZING_FREQUENCY, draft[FieldKey.SIZING_FREQUENCY].changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.GIGAHERTZ.name))
        draft = draft.update(FieldKey.ACTUAL_LENGTH, draft[FieldKey.ACTUAL_LENGTH].changeUnit(FieldKey.ACTUAL_LENGTH, LengthUnit.METERS.name))
        draft = draft.copy(settings = CalculatorSettings(section = Section.TRIMMING, theme = AppTheme.DARK,
            antennaType = AntennaType.SYMMETRIC_DIPOLE, sizingLengthUnit = LengthUnit.CENTIMETERS,
            trimmingLengthUnit = LengthUnit.METERS))
        return PersistedCalculatorState.defaults().advance(draft)
    }

    @Test fun codecRoundTripPreservesAllInputsUnitsAndSettingsWithoutResults() {
        val expected = populated()
        val encoded = PreferencesStateCodec.encode(expected)
        assertEquals(expected, PreferencesStateCodec.decode(encoded))
        assertEquals(433_000_001L, expected.values[FieldKey.SIZING_FREQUENCY])
        assertEquals(1_000_000_001L, expected.values[FieldKey.ACTUAL_LENGTH])
        // Tool domains are additive; the original calculator still owns 26 entries.
        val domainKeys = encoded.asMap().keys.groupingBy { it.name.substringBefore('.') }.eachCount()
        assertEquals(8, domainKeys["matching"])
        assertEquals(8, domainKeys["cable"])
        assertEquals(2, domainKeys["invertedV"])
        assertEquals(26, domainKeys.filterKeys { it !in setOf("matching", "cable", "invertedV") }.values.sum())
    }

    @Test fun initiallyAbsentActualLengthRemainsAbsentNotZero() {
        val state = PreferencesStateCodec.decode(PreferencesStateCodec.encode(PersistedCalculatorState.defaults()))
        assertFalse(state.values.containsKey(FieldKey.ACTUAL_LENGTH))
        assertEquals("", state.toDraft()[FieldKey.ACTUAL_LENGTH].text)
        assertNull(CalculatorUiState(state.toDraft()).trimming.result)
    }

    @Test fun emptyPreferencesUseFirstLaunchDefaults() {
        assertEquals(PersistedCalculatorState.defaults(), PreferencesStateCodec.decode(emptyPreferences()))
    }

    @Test fun languageSelectionSurvivesSystemLanguageChangesAndColdRead() = runTest {
        val previous = java.util.Locale.getDefault()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            val repo = repository(temporary.newFolder().resolve("language.preferences_pb"), scope)
            val first = repo.read()
            assertEquals(AppLanguage.RUSSIAN, first.settings.language)
            java.util.Locale.setDefault(java.util.Locale.GERMAN)
            assertEquals(AppLanguage.RUSSIAN, repo.read().settings.language)
            repo.write(first.copy(settings = first.settings.copy(language = AppLanguage.ENGLISH)))
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            assertEquals(AppLanguage.ENGLISH, repo.read().settings.language)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun oldSettingsAndDraftWithoutLanguageKeepTheirInputs() {
        val expected = populated()
        val old = PreferencesStateCodec.encode(expected).toMutablePreferences().apply { remove(stringPreferencesKey("language")) }
        val migrated = PreferencesStateCodec.decode(old)
        assertEquals(expected.values, migrated.values)
        assertEquals(expected.units, migrated.units)
        assertEquals(AppLanguage.initial(), migrated.settings.language)
        val draft = expected.toDraft().update(FieldKey.TARGET, NumericDraft("145,", "MEGAHERTZ", checked = true))
        val saved = DraftCodec.encode(draft).apply { removeAt(lastIndex) }
        assertEquals(draft.fields, DraftCodec.decode(saved)!!.fields)
        for (language in AppLanguage.entries) {
            val selected = draft.copy(settings = draft.settings.copy(language = language))
            assertEquals(selected, DraftCodec.decode(DraftCodec.encode(selected)))
        }
        old[stringPreferencesKey("language")] = "UNKNOWN"
        try { PreferencesStateCodec.decode(old); fail("Invalid language expected") } catch (_: InvalidSettingsException) { }
    }

    @Test fun freshNonRussianInstallationPersistsEnglishBeforeAnyEdits() = runTest {
        val previous = java.util.Locale.getDefault()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMAN)
            val file = temporary.newFolder().resolve("first-start.preferences_pb")
            val repo = repository(file, scope)
            assertEquals(AppLanguage.ENGLISH, repo.read().settings.language)
            assertTrue(file.isFile)
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            assertEquals(AppLanguage.ENGLISH, repo.read().settings.language)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun invalidValuesAndPartialSchemaAreCorruption() {
        val invalid = listOf(
            mutablePreferencesOf(intPreferencesKey("schema") to 1),
            PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[longPreferencesKey("value.SIZING_FREQUENCY")] = 0 },
            PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[longPreferencesKey("value.FACTOR")] = Long.MAX_VALUE },
            PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[longPreferencesKey("value.LEGACY_CURRENT")] = 145_000_001 },
            PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[stringPreferencesKey("theme")] = "UNKNOWN" },
            PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[stringPreferencesKey("unit.FACTOR")] = "MEGAHERTZ" },
        )
        invalid.forEach { assertThrows(InvalidSettingsException::class.java) { PreferencesStateCodec.decode(it) } }
    }

    @Test fun unknownSchemaIsExplicitlyUnsupported() {
        for (version in listOf(0, 2, 999)) {
            val encoded = PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[intPreferencesKey("schema")] = version }
            assertEquals(version, assertThrows(UnsupportedSchemaException::class.java) { PreferencesStateCodec.decode(encoded) }.version)
        }
    }

    @Test fun invalidDraftPersistsPreferredUnitAndLastValidCanonicalValue() {
        var draft = populated().toDraft()
        draft = draft.update(FieldKey.SIZING_FREQUENCY, draft[FieldKey.SIZING_FREQUENCY].edit("145,"))
        draft = draft.update(FieldKey.SIZING_FREQUENCY, draft[FieldKey.SIZING_FREQUENCY].changeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.KILOHERTZ.name))
        val state = populated().advance(draft)
        val restored = PreferencesStateCodec.decode(PreferencesStateCodec.encode(state)).toDraft()
        assertEquals("433000,001", restored[FieldKey.SIZING_FREQUENCY].text)
        assertEquals("KILOHERTZ", restored[FieldKey.SIZING_FREQUENCY].unit)
    }

    @Test fun realDataStoreSurvivesRepositoryRecreation() = runTest {
        val file = temporary.newFolder().resolve("settings.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = repository(file, firstScope)
            assertEquals(PersistedCalculatorState.defaults(), first.read())
            first.write(populated())
        } finally { firstScope.coroutineContext.job.cancelAndJoin() }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertEquals(populated(), repository(file, secondScope).read())
        } finally { secondScope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun realCorruptFileIsUntouchedByReadRetryAndWriteUntilExplicitReset() = runTest {
        val file = temporary.newFile("corrupt.preferences_pb")
        val original = byteArrayOf(0x0A, 0x7F, 0x01)
        file.writeBytes(original)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = repository(file, scope)
            repeat(2) {
                try { repo.read(); fail("Corruption expected") } catch (_: CorruptionException) { }
                assertArrayEquals(original, file.readBytes())
            }
            try { repo.write(populated()); fail("Corruption expected") } catch (_: CorruptionException) { }
            assertArrayEquals(original, file.readBytes())
            assertEquals(PersistedCalculatorState.defaults(), repo.reset())
            assertEquals(PersistedCalculatorState.defaults(), repo.read())
            repo.write(populated())
            assertEquals(populated(), repo.read())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun realFutureSchemaFileIsPreservedUntilExplicitReset() = runTest {
        val file = temporary.newFolder().resolve("future.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        raw.updateData { PreferencesStateCodec.encode(populated()).toMutablePreferences().apply { this[intPreferencesKey("schema")] = 99 } }
        scope.coroutineContext.job.cancelAndJoin()
        val original = file.readBytes()
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = repository(file, repoScope)
            try { repo.read(); fail("Unsupported schema expected") } catch (_: UnsupportedSchemaException) { }
            try { repo.write(PersistedCalculatorState.defaults()); fail("Unsupported schema expected") } catch (_: UnsupportedSchemaException) { }
            assertArrayEquals(original, file.readBytes())
            repo.reset()
            assertEquals(PersistedCalculatorState.defaults(), repo.read())
        } finally { repoScope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun draftCodecRoundTripIncludesAllRawFieldsAndTransientFlags() {
        var draft = populated().toDraft()
        draft = draft.update(FieldKey.TARGET, NumericDraft("145,", "GIGAHERTZ", "MEGAHERTZ", checked = true, focused = true))
        assertEquals(draft, DraftCodec.decode(DraftCodec.encode(draft)))
    }

    @Test fun productionFileBackendWritesAndReadsOfficialPreferencesFormat() = runTest {
        val file = temporary.newFolder().resolve("android.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = DataStoreSettingsRepository(file, scope)
            repo.write(populated())
            assertEquals(populated(), repo.read())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun productionFileBackendPreservesCorruptionAndSupportsExplicitReset() = runTest {
        val file = temporary.newFile("android-corrupt.preferences_pb")
        val bytes = byteArrayOf(0x0A, 0x7F, 0x01)
        file.writeBytes(bytes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = DataStoreSettingsRepository(file, scope)
            try { repo.read(); fail("Corruption expected") } catch (_: CorruptionException) { }
            assertArrayEquals(bytes, file.readBytes())
            repo.reset()
            assertEquals(PersistedCalculatorState.defaults(), repo.read())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun oldFileRetainsOpaqueFieldsWhileCurrentInputsAreUpdated() = runTest {
        val file = temporary.newFolder().resolve("v1.preferences_pb")
        file.outputStream().use { PreferencesFileSerializer.writeTo(storageV1Preferences(), it) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = repository(file, scope)
            val restored = repo.read()
            assertEquals(433_000_001L, restored.values[FieldKey.SIZING_FREQUENCY])
            repo.write(restored.copy(settings = restored.settings.copy(theme = AppTheme.LIGHT)))
            val raw = file.inputStream().use { PreferencesFileSerializer.readFrom(it) }
            assertEquals(434_001_000L, raw[longPreferencesKey("value.LEN_FREQUENCY")])
            assertEquals(97, raw[intPreferencesKey("lenCoefficient")])
            assertEquals(93, raw[intPreferencesKey("trimCoefficient")])
            assertEquals(false, raw[booleanPreferencesKey("compatibility")])
            assertEquals(AppTheme.LIGHT, repo.read().settings.theme)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
}
