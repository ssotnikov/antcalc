package local.antenna.calculator.ui

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.roundToLong
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.AntennaProject
import local.antenna.calculator.core.journal.CalculationSnapshot
import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import local.antenna.calculator.journal.JournalJson
import local.antenna.calculator.journal.journalSnapshot
import local.antenna.calculator.journal.outputsForAntenna
import local.antenna.calculator.journal.sizingSnapshot
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.state.Section

private enum class TransferSource(
    val model: String,
    val page: Page,
    val title: Int,
    val frequencyKey: String = "frequency",
) {
    SIZING("sizing", Page.CALCULATOR, R.string.sizes),
    TRIMMING("trimming", Page.CALCULATOR, R.string.trimming, "target"),
    INVERTED_V("inverted_v", Page.INVERTED_V, R.string.inverted_v_title),
    GROUND_PLANE("ground_plane", Page.GROUND_PLANE, R.string.ground_plane_title),
    SLIM_JIM("slim_jim", Page.SLIM_JIM, R.string.slim_jim_title),
    MOXON("moxon_calculated", Page.MOXON, R.string.moxon_title),
    HB9CV("hb9cv", Page.HB9CV, R.string.hb9cv_title),
    J_POLE("j_pole", Page.J_POLE, R.string.jpole_title),
    YAGI("yagi_r3udk", Page.YAGI_CALCULATOR, R.string.yagi_title),
    FULL_WAVE_LOOP("full_wave_loop", Page.FULL_WAVE_LOOP, R.string.loop_title, "loop_frequency");

    companion object {
        fun fromModel(model: String): TransferSource = when {
            model.startsWith("hb9cv_") -> HB9CV
            SlimJimProfile.entries.any { it.model == model } -> SLIM_JIM
            moxonProfiles.any { it.id == model } -> MOXON
            YagiProfile.entries.any { it.design.model == model } -> YAGI
            else -> entries.first { it.model == model }
        }
    }
}

internal class PendingProjectCalculation(
    val snapshot: CalculationSnapshot,
    private val projectId: UUID = UUID.randomUUID(),
    val recordId: UUID = UUID.randomUUID(),
    private val time: OffsetDateTime = OffsetDateTime.now(),
    private val note: String = "",
) {
    private val source = TransferSource.fromModel(snapshot.model)
    val needsAntennaChoice get() = source == TransferSource.SIZING
    val returnPage get() = source.page
    val title get() = source.title
    val target = targetFrequency()
    val recommendedLengthNanometers get() = if (source == TransferSource.TRIMMING)
        outputLengthNanometers("new_length") else null

    val hasInitialLength get() = when (source) {
        TransferSource.YAGI, TransferSource.SLIM_JIM, TransferSource.MOXON, TransferSource.HB9CV -> false
        else -> true
    }

    fun initialLength(type: AntennaType): PhysicalLength? {
        val nanometers = when (source) {
            TransferSource.YAGI, TransferSource.SLIM_JIM, TransferSource.MOXON, TransferSource.HB9CV -> return null
            TransferSource.J_POLE -> outputLengthNanometers("jpole_long")
            TransferSource.GROUND_PLANE -> outputLengthNanometers("gp_whip")
            TransferSource.FULL_WAVE_LOOP -> outputLengthNanometers("loop_perimeter")
            TransferSource.INVERTED_V -> outputLengthNanometers("dipole_total")
            TransferSource.SIZING -> outputLengthNanometers(if (type == AntennaType.MONOPOLE) "monopole_length" else "dipole_total")
            TransferSource.TRIMMING -> snapshot.inputs.first { it.key == "length" }.value.toLong()
        }
        return nanometers.takeIf { it in PhysicalLength.MIN_NANOMETERS..PhysicalLength.MAX_NANOMETERS }
            ?.let(PhysicalLength::ofNanometers)
    }

    private fun outputLengthNanometers(key: String) =
        (snapshot.outputs.first { it.key == key }.value * 1_000_000_000).roundToLong()

    private fun targetFrequency(): Frequency? {
        if (snapshot.model == YagiProfile.DUO.design.model ||
            snapshot.inputs.any { it.key == "moxon_second_frequency" }) return null

        val fromOutput = source == TransferSource.FULL_WAVE_LOOP
        val values = if (fromOutput) snapshot.outputs else snapshot.inputs
        val frequency = values.firstOrNull { it.key == source.frequencyKey }?.value ?: return null
        val hertz = if (fromOutput) frequency.roundToLong() else frequency.toLong()
        return hertz.takeIf { it in Frequency.MIN_HERTZ..Frequency.MAX_HERTZ }?.let(Frequency::ofHertz)
    }

    fun newProject(type: AntennaType, name: String) =
        AntennaProject(projectId, name, type, target, initialLength(type))

    fun record(type: AntennaType, title: String) =
        JournalRecord(recordId, title, time, type, note = note, calculation = snapshot.copy(outputs = snapshot.outputsForAntenna(type)))

    companion object {
        fun capture(state: CalculatorUiState, section: Section, type: AntennaType): PendingProjectCalculation? {
            val snapshot = if (section == Section.SIZING) sizingSnapshot(state) else journalSnapshot(state, type, section)
            return snapshot?.let { PendingProjectCalculation(it) }
        }

        val saver = Saver<PendingProjectCalculation?, Bundle>(
            save = { pending ->
                pending?.let {
                    Bundle().apply {
                        putByteArray("snapshot", JournalJson.encodePendingSnapshot(it.snapshot))
                        putString("note", it.note)
                        putString("project", it.projectId.toString())
                        putString("record", it.recordId.toString())
                        putString("time", it.time.toString())
                    }
                }
            },
            restore = { bundle ->
                runCatching {
                    PendingProjectCalculation(
                        JournalJson.decodePendingSnapshot(requireNotNull(bundle.getByteArray("snapshot"))),
                        UUID.fromString(bundle.getString("project")),
                        UUID.fromString(bundle.getString("record")),
                        OffsetDateTime.parse(bundle.getString("time")),
                        bundle.getString("note").orEmpty(),
                    )
                }.getOrNull()
            },
        )
    }
}
