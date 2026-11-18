package local.antenna.calculator.ui

import android.os.Bundle
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.Saver
import java.math.BigDecimal
import local.antenna.calculator.R
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.journal.JournalJson

/** A journal launch is separate from normal calculator drafts and never rewrites the record. */
internal data class RecordCalculatorRequest(
    val id: String,
    val type: AntennaType,
    val snapshot: CalculationSnapshot?,
    val resonanceHz: Long? = null,
    val testHz: Long? = null,
    val lengthNm: Long? = null,
) {
    val source: TransferSource? get() = snapshot?.let { runCatching { TransferSource.fromModel(it.model) }.getOrNull() }
    val page: Page get() = source?.page ?: when (type) {
        AntennaType.MONOPOLE, AntennaType.SYMMETRIC_DIPOLE -> Page.CALCULATOR
        AntennaType.FULL_WAVE_LOOP -> Page.FULL_WAVE_LOOP
        AntennaType.J_POLE -> Page.J_POLE
        AntennaType.YAGI -> Page.YAGI_CALCULATOR
        AntennaType.SLIM_JIM -> Page.SLIM_JIM
        AntennaType.MOXON -> Page.MOXON
        AntennaType.HB9CV -> Page.HB9CV
        AntennaType.CANTENNA -> Page.CANTENNA
        AntennaType.HALO_CB -> Page.HALO_CB
        AntennaType.TRAP_DIPOLE -> Page.TRAP_DIPOLE
    }
    val title: Int get() = source?.title ?: when (page) {
        Page.CALCULATOR -> if (lengthNm != null && resonanceHz != null) R.string.trimming else R.string.sizes
        Page.FULL_WAVE_LOOP -> R.string.loop_title
        Page.J_POLE -> R.string.jpole_title
        Page.YAGI_CALCULATOR -> R.string.yagi_title
        Page.SLIM_JIM -> R.string.slim_jim_title
        Page.MOXON -> R.string.moxon_title
        Page.HB9CV -> R.string.hb9cv_title
        Page.CANTENNA -> R.string.cantenna_title
        Page.HALO_CB -> R.string.halo_title
        Page.TRAP_DIPOLE -> R.string.trap_title
        else -> error("Unsupported record calculator")
    }

    fun value(key: String, unit: SnapshotUnit): Double? = snapshot?.inputs
        ?.firstOrNull { it.key == key && it.unit == unit }?.value?.takeIf(Double::isFinite)
        ?: if (snapshot == null && key == "frequency" && unit == SnapshotUnit.HERTZ) (resonanceHz ?: testHz)?.toDouble() else null

    fun text(key: String, unit: SnapshotUnit = SnapshotUnit.METERS, scale: Double = 1.0): String =
        value(key, unit)?.let { BigDecimal.valueOf(it).multiply(BigDecimal.valueOf(scale)).stripTrailingZeros().toPlainString() }.orEmpty()

    fun knownValues(context: Context): String = buildString {
        val presentation = local.antenna.calculator.journal.JournalPresentation(context)
        (resonanceHz ?: testHz)?.let { append("\n${context.getString(R.string.frequency)}: ${presentation.frequency(it)}") }
        lengthNm?.let { append("\n${context.getString(R.string.journal_length)}: ${presentation.length(it)}") }
    }

    fun cantennaDraft(): CantennaDraft = CantennaDraft(
        target = text("frequency", SnapshotUnit.HERTZ, 1e-6),
        withBand = value("cantenna_low", SnapshotUnit.HERTZ) != value("cantenna_high", SnapshotUnit.HERTZ),
        lower = text("cantenna_low", SnapshotUnit.HERTZ, 1e-6), upper = text("cantenna_high", SnapshotUnit.HERTZ, 1e-6),
        cans = listOf(CantennaCandidate(diameter = text("cantenna_diameter", scale = 1000.0),
            depth = text("cantenna_depth", scale = 1000.0), tolerance = text("cantenna_tolerance", scale = 1000.0),
            contact = text("cantenna_contact", scale = 1000.0), probeDiameter = text("cantenna_probe_d", scale = 1000.0))),
        throat = text("cantenna_throat", scale = 1000.0), mouth = text("cantenna_mouth", scale = 1000.0),
        flareLength = text("cantenna_flare_l", scale = 1000.0), seam = text("cantenna_seam", scale = 1000.0),
    )

    companion object {
        fun from(record: JournalRecord): RecordCalculatorRequest? {
            val request = RecordCalculatorRequest(record.id.toString(), record.antennaType, record.calculation,
                record.measurement?.resonance?.hertz, record.measurement?.testFrequency?.hertz,
                record.measurement?.length?.nanometers)
            val snapshot = request.snapshot ?: return request
            val source = request.source ?: return null
            val maxRevision = when (source) {
                TransferSource.YAGI -> YagiProfile.entries.firstOrNull { it.design.available && it.design.model == snapshot.model }
                    ?.let { maxOf(2, it.design.revision) } ?: return null
                TransferSource.SLIM_JIM -> SlimJimProfile.entries.firstOrNull { it.model == snapshot.model }?.revision ?: return null
                TransferSource.MOXON -> moxonProfiles.firstOrNull { it.id == snapshot.model }?.revision ?: 1
                TransferSource.YAGI_SCALING -> 2
                else -> 1
            }
            if (snapshot.revision !in 1..maxRevision) return null
            if (source == TransferSource.MOXON && snapshot.model != local.antenna.calculator.core.layout.MoxonEstimate.MODEL &&
                moxonCalculatorProfiles.none { it.id == snapshot.model }) return null
            if (source == TransferSource.HALO && HaloDraft.fromSnapshot(snapshot) == null) return null
            if (source == TransferSource.TRAP && TrapDraft.restore(snapshot) == null) return null
            if (source == TransferSource.YAGI_SCALING && YagiScalingDraft.restore(snapshot) == null) return null
            if (source in listOf(TransferSource.SIZING, TransferSource.TRIMMING) && record.antennaType !in AntennaType.straightConductors) return null
            if (source == TransferSource.CANTENNA && snapshot.model != "cantenna_calculated" && CantennaDesign.entries.none { it.model == snapshot.model }) return null
            if (source == TransferSource.HB9CV && snapshot.model != local.antenna.calculator.core.Hb9cvAerial.MODEL &&
                snapshot.model !in (0 until 22).map { "hb9cv_$it" }) return null
            return request
        }
        val saver = Saver<RecordCalculatorRequest?, Bundle>(
            save = { request -> request?.let {
                Bundle().apply {
                    putString("id", it.id); putString("type", it.type.name)
                    it.snapshot?.let { snapshot -> putByteArray("snapshot", JournalJson.encodePendingSnapshot(snapshot)) }
                    it.resonanceHz?.let { putLong("resonance", it) }
                    it.testHz?.let { putLong("test", it) }
                    it.lengthNm?.let { putLong("length", it) }
                }
            } },
            restore = { bundle -> runCatching {
                RecordCalculatorRequest(requireNotNull(bundle.getString("id")), AntennaType.valueOf(requireNotNull(bundle.getString("type"))),
                    bundle.getByteArray("snapshot")?.let(JournalJson::decodePendingSnapshot),
                    bundle.takeIf { it.containsKey("resonance") }?.getLong("resonance"),
                    bundle.takeIf { it.containsKey("test") }?.getLong("test"),
                    bundle.takeIf { it.containsKey("length") }?.getLong("length"))
            }.getOrNull() },
        )
    }
}

internal fun recordNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
internal val LocalCalculatorRecord = staticCompositionLocalOf<RecordCalculatorRequest?> { null }
