package local.antenna.calculator.ui

import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.journal.SnapshotUnit.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.journal.*
import local.antenna.calculator.state.*
import org.junit.Assert.*
import org.junit.Test

class RecordCalculatorRequestTest {
    private fun record(type: AntennaType, snapshot: CalculationSnapshot) = JournalRecord(
        UUID.randomUUID(), "Saved", OffsetDateTime.now(), type, calculation = snapshot)
    private fun snapshot(model: String, vararg inputs: SnapshotValue, revision: Int = 1) =
        CalculationSnapshot(model, revision, inputs.toList().ifEmpty { listOf(SnapshotValue("frequency", 145e6, HERTZ)) },
            listOf(SnapshotValue("test", 1.0, FACTOR)))

    @Test fun sizingAndTrimmingRestoreExactlyWithoutChangingLanguageOrTheme() {
        val draft = CalculatorDraft.defaults().edit(FieldKey.ACTUAL_LENGTH, "989")
            .edit(FieldKey.MEASURED, "144.6").edit(FieldKey.TARGET, "145.5")
        for (section in listOf(Section.SIZING, Section.TRIMMING)) {
            for (type in AntennaType.straightConductors) {
                val source = draft.copy(settings = draft.settings.copy(antennaType = type, section = section))
                val saved = requireNotNull(journalSnapshot(CalculatorUiState(source, loading = false), type, section))
                val restored = requireNotNull(RecordCalculatorRequest.from(record(type, saved))).restoreDraft(CalculatorDraft.defaults())
                assertEquals(saved, journalSnapshot(CalculatorUiState(restored, loading = false), type, section))
                assertEquals(draft.settings.language, restored.settings.language)
                assertEquals(draft.settings.theme, restored.settings.theme)
            }
        }
    }

    @Test fun layoutCalculatorsRoundTripAllSavedInputsAndResults() {
        val current = CalculatorDraft.defaults()
        for (saved in listOf(requireNotNull(current.invertedV.invertedVSnapshot()),
            requireNotNull(current.groundPlane.groundPlaneSnapshot()), requireNotNull(current.fullWaveLoop.fullWaveLoopSnapshot()))) {
            val type = if (saved.model == "ground_plane") AntennaType.MONOPOLE else if (saved.model == "inverted_v")
                AntennaType.SYMMETRIC_DIPOLE else AntennaType.FULL_WAVE_LOOP
            val restored = requireNotNull(RecordCalculatorRequest.from(record(type, saved))).restoreDraft(current)
            val result = when (saved.model) {
                "ground_plane" -> restored.groundPlane.groundPlaneSnapshot()
                "inverted_v" -> restored.invertedV.invertedVSnapshot()
                else -> restored.fullWaveLoop.fullWaveLoopSnapshot()
            }
            assertEquals(saved.model, saved, result)
        }
    }

    @Test fun everyAvailablePublishedProfileHasACalculatorDestination() {
        for (profile in YagiProfile.entries.filter { it.design.available }) {
            val saved = snapshot(profile.design.model, revision = profile.design.revision)
            assertEquals(Page.YAGI_CALCULATOR, RecordCalculatorRequest.from(record(AntennaType.YAGI, saved))?.page)
        }
        for (profile in moxonCalculatorProfiles) {
            assertEquals(Page.MOXON, RecordCalculatorRequest.from(record(AntennaType.MOXON,
                snapshot(profile.id, revision = profile.revision)))?.page)
        }
        for (profile in SlimJimProfile.entries) {
            assertEquals(Page.SLIM_JIM, RecordCalculatorRequest.from(record(AntennaType.SLIM_JIM,
                snapshot(profile.model, revision = profile.revision)))?.page)
        }
        for (profile in CantennaDesign.entries) {
            assertEquals(Page.CANTENNA, RecordCalculatorRequest.from(record(AntennaType.CANTENNA, snapshot(profile.model)))?.page)
        }
        for (index in 0 until 22) {
            assertEquals(Page.HB9CV, RecordCalculatorRequest.from(record(AntennaType.HB9CV, snapshot("hb9cv_$index")))?.page)
        }
    }

    @Test fun measurementRecordsTransferOnlyKnownData() {
        for (type in AntennaType.entries) {
            val measured = JournalRecord(UUID.randomUUID(), "Manual", OffsetDateTime.now(), type,
                measurement = JournalMeasurement(resonance = Frequency.ofHertz(145_500_000), length = PhysicalLength.ofNanometers(989_000_000)))
            val request = requireNotNull(RecordCalculatorRequest.from(measured))
            assertEquals("145.5", request.text("frequency", HERTZ, 1e-6))
            assertEquals("", request.text("factor", FACTOR))
            assertNull(request.snapshot)
            if (type in AntennaType.straightConductors) {
                assertEquals(local.antenna.calculator.R.string.trimming, request.title)
                val restored = request.restoreDraft(CalculatorDraft.defaults())
                assertEquals(Section.TRIMMING, restored.settings.section)
                assertEquals("989", restored[FieldKey.ACTUAL_LENGTH].text)
                assertEquals("", restored[FieldKey.TARGET].text)
            }
        }
    }

    @Test fun cantennaRestoresCanonicalUnitsAndOptionalGeometry() {
        val saved = snapshot("cantenna_calculated", SnapshotValue("frequency", 2_437_000_000.0, HERTZ),
            SnapshotValue("cantenna_low", 2_412_000_000.0, HERTZ), SnapshotValue("cantenna_high", 2_462_000_000.0, HERTZ),
            SnapshotValue("cantenna_diameter", .085, METERS), SnapshotValue("cantenna_depth", .174, METERS),
            SnapshotValue("cantenna_tolerance", .001, METERS), SnapshotValue("cantenna_probe_d", .002, METERS))
        val draft = requireNotNull(RecordCalculatorRequest.from(record(AntennaType.CANTENNA, saved))).cantennaDraft()
        assertEquals("2437", draft.target)
        assertTrue(draft.withBand)
        assertEquals("85", draft.cans.single().diameter)
        assertEquals("174", draft.cans.single().depth)
        assertEquals("1", draft.cans.single().tolerance)
        assertEquals("2", draft.cans.single().probeDiameter)
    }

    @Test fun unknownRevisionsAndModelsAreNotOpenedAsAnotherDesign() {
        assertNull(RecordCalculatorRequest.from(record(AntennaType.YAGI, snapshot("future_design"))))
        assertNull(RecordCalculatorRequest.from(record(AntennaType.J_POLE, snapshot("j_pole", revision = 99))))
        assertNull(RecordCalculatorRequest.from(record(AntennaType.CANTENNA, snapshot("cantenna_future"))))
        assertNull(RecordCalculatorRequest.from(record(AntennaType.HB9CV, snapshot("hb9cv_99"))))
        assertNull(RecordCalculatorRequest.from(record(AntennaType.YAGI, snapshot("sizing"))))
        for (profile in YagiProfile.entries.filterNot { it.design.available }) {
            assertNull(RecordCalculatorRequest.from(record(AntennaType.YAGI, snapshot(profile.design.model))))
        }
        for (profile in moxonProfiles.filter { it.dimensions == null }) {
            assertNull(RecordCalculatorRequest.from(record(AntennaType.MOXON, snapshot(profile.id))))
        }
    }

    @Test fun wrongUnitsAreNotSilentlyReinterpreted() {
        val saved = snapshot("j_pole", SnapshotValue("frequency", 145.0, METERS))
        val request = requireNotNull(RecordCalculatorRequest.from(record(AntennaType.J_POLE, saved)))
        assertEquals("", request.text("frequency", HERTZ, 1e-6))
    }
}
