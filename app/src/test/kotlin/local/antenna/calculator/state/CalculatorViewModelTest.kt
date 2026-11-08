package local.antenna.calculator.state

import androidx.datastore.core.CorruptionException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.*
import local.antenna.calculator.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<CalculatorViewModel>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private fun TestScope.model(repository: FakeRepository = FakeRepository(), handle: SavedStateHandle = SavedStateHandle()) =
        CalculatorViewModel(repository, handle, backgroundScope).also { models += it }
    private fun CalculatorViewModel.edit(key: FieldKey, text: String) = onEvent(CalculatorEvent.Edit(key, text))

    @Test fun matchingRestoresIncompleteSystemDraftAndLastValidColdState() = runTest {
        val repo = FakeRepository()
        val handle = SavedStateHandle()
        val vm = model(repo, handle); runCurrent()
        vm.onEvent(CalculatorEvent.Matching(vm.state.value.draft.matching.edit(
            local.antenna.calculator.core.matching.MatchingField.SWR, "3")))
        vm.onEvent(CalculatorEvent.Background); runCurrent()
        vm.onEvent(CalculatorEvent.Matching(vm.state.value.draft.matching.edit(
            local.antenna.calculator.core.matching.MatchingField.SWR, "3,")))
        vm.onEvent(CalculatorEvent.Background); runCurrent()
        val recreated = model(repo, SavedStateHandle(mapOf(
            DraftCodec.KEY to handle.get<ArrayList<String>>(DraftCodec.KEY),
            MatchingDraftCodec.KEY to handle.get<ArrayList<String>>(MatchingDraftCodec.KEY))))
        runCurrent()
        assertEquals("3,", recreated.state.value.draft.matching.fields[local.antenna.calculator.core.matching.MatchingField.SWR])
        assertNull(recreated.state.value.draft.matching.result())
        val cold = model(repo); runCurrent()
        assertEquals("3", cold.state.value.draft.matching.fields[local.antenna.calculator.core.matching.MatchingField.SWR])
        assertNotNull(cold.state.value.draft.matching.result())
    }

    @Test fun defaultsAndImmediateSizing() = runTest {
        val vm = model(); runCurrent()
        assertFalse(vm.state.value.loading)
        assertEquals("145", vm.state.value.sizing.frequency.text)
        assertEquals("0,95", vm.state.value.sizing.factor.text)
        assertEquals(0.491039370862069, vm.state.value.sizing.result!!.monopoleMeters, 1e-12)
        assertEquals("", vm.state.value.trimming.actualLength.text)
        assertNull(vm.state.value.trimming.result)
        assertEquals(5, vm.state.value.draft.fields.size)
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        assertEquals(0.164435817032333, vm.state.value.sizing.result!!.monopoleMeters, 1e-12)
    }

    @Test fun oversizedPasteKeepsAnInvalidBoundedDraftAndNoResult() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle = handle); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "9".repeat(1_000_000))
        assertEquals(33, vm.state.value.draft[FieldKey.SIZING_FREQUENCY].text.length)
        assertNull(vm.state.value.sizing.result)
        val saved = handle.get<ArrayList<String>>(DraftCodec.KEY)!!
        assertTrue(saved.sumOf { it.length } < 1024)
        assertEquals(vm.state.value.draft, DraftCodec.decode(saved))
    }

    @Test fun oversizedRestoredDraftIsBoundedEvenWhenDiskReadFails() = runTest {
        val encoded = DraftCodec.encode(CalculatorDraft.defaults()).apply { this[0] = "9".repeat(1_000_000) }
        val handle = SavedStateHandle(mapOf(DraftCodec.KEY to encoded))
        val vm = model(FakeRepository().apply { readError = IOException("disk") }, handle)
        runCurrent()
        assertEquals(33, vm.state.value.sizing.frequency.text.length)
        assertEquals(StorageIssue.READ_FAILED, vm.state.value.storageIssue)
        assertTrue(handle.get<ArrayList<String>>(DraftCodec.KEY)!!.sumOf { it.length } < 1024)
        assertNull(vm.state.value.sizing.result)
    }

    @Test fun invalidTargetHidesOldTrimAndCopyImmediately() = runTest {
        val vm = model(); runCurrent()
        vm.edit(FieldKey.ACTUAL_LENGTH, "500")
        assertEquals(0.467741935483871, vm.state.value.trimming.result!!.newLengthMeters, 1e-12)
        assertTrue(vm.state.value.trimming.canCopy)
        vm.edit(FieldKey.TARGET, "")
        assertNull(vm.state.value.trimming.result)
        assertFalse(vm.state.value.trimming.canCopy)
        assertFalse(vm.state.value.draft[FieldKey.TARGET].hasVisibleError(FieldKey.TARGET))
        vm.onEvent(CalculatorEvent.Focus(FieldKey.TARGET, false))
        assertTrue(vm.state.value.draft[FieldKey.TARGET].hasVisibleError(FieldKey.TARGET))
    }

    @Test fun invalidFactorKeepsIdealButHidesPhysicalResult() = runTest {
        val vm = model(); runCurrent()
        vm.edit(FieldKey.FACTOR, "1,01")
        assertNotNull(vm.state.value.sizing.ideal)
        assertNull(vm.state.value.sizing.result)
        assertFalse(vm.state.value.sizing.canCopy)
        assertTrue(vm.state.value.draft[FieldKey.FACTOR].hasVisibleError(FieldKey.FACTOR))
    }

    @Test fun sizingAndTrimmingHaveIndependentInputs() = runTest {
        val vm = model(); runCurrent()
        vm.edit(FieldKey.FACTOR, "0,9539")
        vm.edit(FieldKey.ACTUAL_LENGTH, "1000")
        vm.edit(FieldKey.MEASURED, "144")
        assertEquals(145_000_000L, vm.state.value.sizing.result!!.input.frequency.hertz)
        assertEquals(144_000_000L, vm.state.value.trimming.result!!.input.measuredFrequency.hertz)
        assertEquals("0,9539", vm.state.value.sizing.factor.text)
        assertEquals("1000", vm.state.value.trimming.actualLength.text)
        vm.edit(FieldKey.SIZING_FREQUENCY, "145,")
        assertNull(vm.state.value.sizing.result)
        assertNotNull(vm.state.value.trimming.result)
    }

    @Test fun dipoleAndSettingsRecalculateWithoutChangingOtherFields() = runTest {
        val vm = model(); runCurrent()
        vm.edit(FieldKey.ACTUAL_LENGTH, "1000")
        vm.onEvent(CalculatorEvent.Settings(vm.state.value.draft.settings.copy(antennaType = AntennaType.SYMMETRIC_DIPOLE)))
        assertEquals(0.467741935483871, vm.state.value.trimming.result!!.dipoleArmAdjustment!!.newLengthMeters, 1e-12)
        assertEquals("145", vm.state.value.sizing.frequency.text)
    }

    @Test fun loadingBlocksEditingAndLateDiskLoadCannotReplaceRestoredDraft() = runTest {
        val repo = FakeRepository().apply { readGate = CompletableDeferred() }
        val draft = CalculatorDraft.defaults().update(FieldKey.SIZING_FREQUENCY,
            NumericDraft("145,", FrequencyUnit.MEGAHERTZ.name, checked = true, focused = true))
        val handle = SavedStateHandle(mapOf(DraftCodec.KEY to DraftCodec.encode(draft)))
        val vm = model(repo, handle); runCurrent()
        assertFalse(vm.state.value.editingEnabled)
        vm.edit(FieldKey.SIZING_FREQUENCY, "500")
        repo.readGate!!.complete(Unit); runCurrent()
        assertEquals("145,", vm.state.value.sizing.frequency.text)
        assertNull(vm.state.value.sizing.result)
        assertTrue(vm.state.value.sizing.frequency.focused)
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        assertEquals("433", vm.state.value.sizing.frequency.text)
    }

    @Test fun savedHandleRestoresRawTextUnitsErrorsAndSettingsAfterRecreation() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle = handle); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "145,")
        vm.onEvent(CalculatorEvent.Focus(FieldKey.SIZING_FREQUENCY, false))
        vm.onEvent(CalculatorEvent.Settings(vm.state.value.draft.settings.copy(section = Section.TRIMMING, theme = AppTheme.DARK)))
        val restored = model(handle = SavedStateHandle(mapOf(DraftCodec.KEY to handle.get<ArrayList<String>>(DraftCodec.KEY))))
        runCurrent()
        assertEquals(vm.state.value.draft, restored.state.value.draft)
        assertNull(restored.state.value.sizing.result)
    }

    @Test fun invalidFieldStaysInvalidAfterUnitChangeAndSavedRestoration() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle = handle); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "10000") // Invalid MHz but valid kHz.
        vm.onEvent(CalculatorEvent.ChangeUnit(FieldKey.SIZING_FREQUENCY, FrequencyUnit.KILOHERTZ.name))
        assertNull(vm.state.value.sizing.result)
        val restored = model(handle = SavedStateHandle(mapOf(DraftCodec.KEY to handle.get<ArrayList<String>>(DraftCodec.KEY))))
        runCurrent()
        assertNull(restored.state.value.sizing.result)
        restored.edit(FieldKey.SIZING_FREQUENCY, "10000")
        assertNotNull(restored.state.value.sizing.result)
    }

    @Test fun lastValidFieldsPersistIndividuallyEvenWithInvalidNeighbor() = runTest {
        val repo = FakeRepository(); val vm = model(repo); runCurrent()
        vm.edit(FieldKey.ACTUAL_LENGTH, "500")
        vm.edit(FieldKey.TARGET, "")
        vm.edit(FieldKey.MEASURED, "433")
        advanceTimeBy(250); runCurrent()
        assertEquals(500_000_000L, repo.stored.values[FieldKey.ACTUAL_LENGTH])
        assertEquals(433_000_000L, repo.stored.values[FieldKey.MEASURED])
        assertEquals(155_000_000L, repo.stored.values[FieldKey.TARGET])
        val cold = model(repo); runCurrent()
        assertEquals("155", cold.state.value.trimming.target.text)
        assertEquals("433", cold.state.value.trimming.measured.text)
    }

    @Test fun debounceCoalescesAt250MillisecondsWithoutBlockingCalculation() = runTest {
        val repo = FakeRepository(); val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "146"); runCurrent()
        advanceTimeBy(200)
        vm.edit(FieldKey.SIZING_FREQUENCY, "433"); runCurrent()
        assertEquals(433_000_000L, vm.state.value.sizing.result!!.input.frequency.hertz)
        advanceTimeBy(249); runCurrent(); assertEquals(0, repo.writes)
        advanceTimeBy(1); runCurrent(); assertEquals(1, repo.writes)
        assertEquals(433_000_000L, repo.stored.values[FieldKey.SIZING_FREQUENCY])
    }

    @Test fun backgroundFlushBypassesDelayAndSurvivesViewModelDestruction() = runTest {
        val repo = FakeRepository().apply { writeGate = CompletableDeferred() }
        val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        vm.onEvent(CalculatorEvent.Background); runCurrent()
        assertEquals(1, repo.writes)
        vm.viewModelScope.cancel()
        repo.writeGate!!.complete(Unit); runCurrent()
        assertEquals(433_000_000L, repo.stored.values[FieldKey.SIZING_FREQUENCY])
    }

    @Test fun slowWriteCannotDelayCalculationOrOverwriteTheNewerSnapshot() = runTest {
        val repo = FakeRepository().apply { writeGate = CompletableDeferred() }
        val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "146")
        vm.onEvent(CalculatorEvent.Background); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        assertEquals(433_000_000L, vm.state.value.sizing.result!!.input.frequency.hertz)
        advanceTimeBy(250); runCurrent()
        repo.writeGate!!.complete(Unit); runCurrent()
        assertEquals(433_000_000L, repo.stored.values[FieldKey.SIZING_FREQUENCY])
        assertEquals(2, repo.writes)
    }

    @Test fun unfinishedWriteRestoresLastSuccessfullyWrittenSnapshot() = runTest {
        val repo = FakeRepository(); val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        advanceTimeBy(250); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "500")
        vm.viewModelScope.cancel()
        val cold = model(repo); runCurrent()
        assertEquals("433", cold.state.value.sizing.frequency.text)
    }

    @Test fun writeFailureKeepsCalculationsAndExplicitRetrySavesLatestSnapshot() = runTest {
        val repo = FakeRepository().apply { writeError = IOException("disk unavailable") }
        val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        advanceTimeBy(250); runCurrent()
        assertEquals(StorageIssue.WRITE_FAILED, vm.state.value.storageIssue)
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.sizing.result)
        vm.edit(FieldKey.SIZING_FREQUENCY, "500")
        advanceTimeBy(500); runCurrent(); assertEquals(1, repo.writes)
        repo.writeError = null
        vm.onEvent(CalculatorEvent.RetryStorage); runCurrent()
        assertEquals(500_000_000L, repo.stored.values[FieldKey.SIZING_FREQUENCY])
        assertNull(vm.state.value.storageIssue)
    }

    @Test fun readFailureRetryDoesNotOverwriteEditsMadeInMemory() = runTest {
        val repo = FakeRepository().apply { readError = IOException("offline disk") }
        val vm = model(repo); runCurrent()
        assertEquals(StorageIssue.READ_FAILED, vm.state.value.storageIssue)
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        repo.readError = null; repo.readGate = CompletableDeferred()
        vm.onEvent(CalculatorEvent.RetryStorage); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "500")
        repo.readGate!!.complete(Unit); runCurrent()
        assertEquals("500", vm.state.value.sizing.frequency.text)
        advanceTimeBy(250); runCurrent()
        assertEquals(500_000_000L, repo.stored.values[FieldKey.SIZING_FREQUENCY])
    }

    @Test fun corruptAndUnknownSchemasBlockAllAutomaticWritesUntilReset() = runTest {
        for (error in listOf(CorruptionException("bad bytes"), UnsupportedSchemaException(99))) {
            val repo = FakeRepository().apply { readError = error }
            val vm = model(repo); runCurrent()
            assertTrue(vm.state.value.storageIssue in setOf(StorageIssue.CORRUPT, StorageIssue.UNSUPPORTED_SCHEMA))
            vm.edit(FieldKey.SIZING_FREQUENCY, "433")
            vm.onEvent(CalculatorEvent.Background)
            advanceTimeBy(500); runCurrent(); assertEquals(0, repo.writes)
            vm.onEvent(CalculatorEvent.RetryStorage); runCurrent()
            assertNotNull(vm.state.value.storageIssue)
            assertEquals("433", vm.state.value.sizing.frequency.text)
            vm.onEvent(CalculatorEvent.ResetStorage); runCurrent()
            assertEquals(1, repo.resets)
            assertNull(vm.state.value.storageIssue)
            assertEquals("145", vm.state.value.sizing.frequency.text)
            vm.edit(FieldKey.SIZING_FREQUENCY, "500")
            advanceTimeBy(250); runCurrent(); assertEquals(1, repo.writes)
        }
    }

    @Test fun invalidSavedEnvelopeFallsBackToCanonicalDiskState() = runTest {
        val repo = FakeRepository().apply {
            stored = stored.advance(CalculatorDraft.defaults().update(FieldKey.SIZING_FREQUENCY, NumericDraft("433", "MEGAHERTZ")))
        }
        val vm = model(repo, SavedStateHandle(mapOf(DraftCodec.KEY to arrayListOf("broken")))); runCurrent()
        assertEquals("433", vm.state.value.sizing.frequency.text)
    }

    @Test fun queuedAutomaticWriteDoesNotRetryAnEarlierFailedWrite() = runTest {
        val repo = FakeRepository().apply { writeGate = CompletableDeferred(); writeError = IOException("disk") }
        val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        vm.onEvent(CalculatorEvent.Background); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "500")
        advanceTimeBy(250); runCurrent()
        repo.writeGate!!.complete(Unit); runCurrent()
        assertEquals(1, repo.writes)
        assertEquals(StorageIssue.WRITE_FAILED, vm.state.value.storageIssue)
        repo.writeError = null
        vm.onEvent(CalculatorEvent.RetryStorage); runCurrent()
        assertEquals(500_000_000L, repo.stored.values[FieldKey.SIZING_FREQUENCY])
    }

    @Test fun corruptionDetectedOnWriteAlsoSuspendsAutomaticWrites() = runTest {
        for (error in listOf(CorruptionException("corrupt"), UnsupportedSchemaException(99))) {
            val repo = FakeRepository().apply { writeError = error }
            val vm = model(repo); runCurrent()
            vm.edit(FieldKey.SIZING_FREQUENCY, "433")
            advanceTimeBy(250); runCurrent()
            assertTrue(vm.state.value.storageIssue in setOf(StorageIssue.CORRUPT, StorageIssue.UNSUPPORTED_SCHEMA))
            vm.edit(FieldKey.SIZING_FREQUENCY, "500")
            vm.onEvent(CalculatorEvent.Background)
            advanceTimeBy(500); runCurrent()
            assertEquals(1, repo.writes)
            assertEquals("500", vm.state.value.sizing.frequency.text)
        }
    }

    @Test fun failedExplicitResetKeepsCurrentDraftAndBlocksFurtherWrites() = runTest {
        val repo = FakeRepository().apply { resetError = IOException("disk") }
        val vm = model(repo); runCurrent()
        vm.edit(FieldKey.SIZING_FREQUENCY, "433")
        vm.onEvent(CalculatorEvent.ResetStorage); runCurrent()
        assertEquals("433", vm.state.value.sizing.frequency.text)
        assertNotNull(vm.state.value.storageIssue)
        advanceTimeBy(500); runCurrent(); assertEquals(0, repo.writes)
    }

    @Test fun loadingRetainsCanonicalInputsAndTheme() = runTest {
        val repo = FakeRepository()
        val draft = CalculatorDraft.defaults().update(FieldKey.FACTOR, NumericDraft("0,9539", "FACTOR"))
            .copy(settings = CalculatorSettings(theme = AppTheme.DARK))
        repo.stored = PersistedCalculatorState.defaults().advance(draft)
        val previousValues = repo.stored.values
        val vm = model(repo); runCurrent()
        assertEquals(5, vm.state.value.draft.fields.size)
        assertEquals(AppTheme.DARK, vm.state.value.draft.settings.theme)
        assertEquals("0,9539", vm.state.value.sizing.factor.text)
        advanceTimeBy(250); runCurrent()
        assertEquals(previousValues, repo.stored.values)
    }

    @Test fun restoredDraftCannotReactivateRetiredModeEvenWhenDiskReadFails() = runTest {
        val saved = local.antenna.calculator.data.savedDraftV1()
        val repo = FakeRepository().apply { readError = IOException("disk") }
        val vm = model(repo, SavedStateHandle(mapOf(DraftCodec.KEY to saved)))
        runCurrent()
        assertEquals(5, vm.state.value.draft.fields.size)
        assertEquals("145,", vm.state.value.sizing.frequency.text)
        assertTrue(vm.state.value.sizing.frequency.checked)
        assertNull(vm.state.value.sizing.result)
        assertEquals(StorageIssue.READ_FAILED, vm.state.value.storageIssue)
    }
}

private class FakeRepository : SettingsRepository {
    var stored = PersistedCalculatorState.defaults()
    var readError: Exception? = null
    var writeError: Exception? = null
    var resetError: Exception? = null
    var readGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var writes = 0
    var resets = 0
    override suspend fun read(): PersistedCalculatorState { readGate?.await(); readError?.let { throw it }; return stored }
    override suspend fun write(state: PersistedCalculatorState) { writes++; writeGate?.await(); writeError?.let { throw it }; stored = state }
    override suspend fun reset(): PersistedCalculatorState { resets++; resetError?.let { throw it }; readError = null; writeError = null; stored = PersistedCalculatorState.defaults(); return stored }
}
