package local.antenna.calculator.core.journal

import java.time.OffsetDateTime
import java.util.UUID
import local.antenna.calculator.core.matching.MatchingField
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength

object JournalLimits {
    const val PROJECTS = 100
    const val RECORDS = 1000
    const val STORAGE_BYTES = 20 * 1024 * 1024
    const val TRANSFER_BYTES = 2 * 1024 * 1024
    const val TITLE = 120
    const val NOTE = 4000
}

private fun validateTitle(title: String) {
    require(title.isNotBlank() && title.length <= JournalLimits.TITLE)
}

enum class MeasurementPoint { FEED_POINT, TRANSCEIVER, UNSPECIFIED }

data class JournalMeasurement(
    val length: PhysicalLength? = null,
    val resonance: Frequency? = null,
    val testFrequency: Frequency? = null,
    val swr: Double? = null,
    val resistanceOhms: Double? = null,
    val reactanceOhms: Double? = null,
    val point: MeasurementPoint = MeasurementPoint.UNSPECIFIED,
) {
    init {
        require(length != null || resonance != null || swr != null || resistanceOhms != null)
        require((resistanceOhms == null) == (reactanceOhms == null))
        validate(swr, MatchingField.SWR)
        validate(resistanceOhms, MatchingField.RESISTANCE)
        validate(reactanceOhms, MatchingField.REACTANCE)
    }

    private fun validate(value: Double?, field: MatchingField) {
        require(value == null || value in field.minimum..field.maximum)
    }
}

enum class SnapshotUnit { HERTZ, NANOMETERS, METERS, FACTOR }

data class SnapshotValue(val key: String, val value: Double, val unit: SnapshotUnit) {
    init {
        require(key.matches(Regex("[a-z][a-z0-9_]{0,47}")))
        require(value.isFinite() && value in -1e15..1e15)
    }
}

data class CalculationSnapshot(
    val model: String,
    val revision: Int,
    val inputs: List<SnapshotValue>,
    val outputs: List<SnapshotValue>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(model.matches(Regex("[a-z][a-z0-9_]{0,47}")) && revision > 0)
        require(warnings.size <= 24 && warnings.all { it.matches(Regex("[A-Z][A-Z0-9_]{0,47}")) })
        for (values in listOf(inputs, outputs)) {
            require(values.size in 1..24 && values.map { it.key }.toSet().size == values.size)
        }
    }
}

data class JournalRecord(
    val id: UUID,
    val title: String,
    val time: OffsetDateTime,
    val antennaType: AntennaType,
    val note: String = "",
    val installation: String = "",
    val feedline: String = "",
    val measurement: JournalMeasurement? = null,
    val calculation: CalculationSnapshot? = null,
) {
    init {
        validateTitle(title)
        require(listOf(note, installation, feedline).all { it.length <= JournalLimits.NOTE })
        require((measurement == null) != (calculation == null))
    }
}

data class AntennaProject(
    val id: UUID,
    val title: String,
    val antennaType: AntennaType,
    val target: Frequency? = null,
    val initialLength: PhysicalLength? = null,
    val note: String = "",
    val installation: String = "",
    val feedline: String = "",
    val records: List<JournalRecord> = emptyList(),
) {
    init {
        validateTitle(title)
        require(listOf(note, installation, feedline).all { it.length <= JournalLimits.NOTE })
        require(records.size <= JournalLimits.RECORDS && records.map { it.id }.toSet().size == records.size)
    }

    fun duplicate(newId: UUID, recordId: () -> UUID): AntennaProject =
        copy(id = newId, records = records.map { it.copy(id = recordId()) })
}

enum class MeasurementDifference { DIFFERENT_POINT, UNKNOWN_POINT, DIFFERENT_FREQUENCY, UNKNOWN_FREQUENCY }

data class JournalComparison(
    val resonanceDeltaHz: Long?,
    val lengthDeltaNm: Long?,
    val swrDelta: Double?,
    val differences: Set<MeasurementDifference>,
)

fun compareMeasurements(before: JournalMeasurement, after: JournalMeasurement): JournalComparison {
    val differences = buildSet {
        when {
            before.point == MeasurementPoint.UNSPECIFIED || after.point == MeasurementPoint.UNSPECIFIED ->
                add(MeasurementDifference.UNKNOWN_POINT)
            before.point != after.point -> add(MeasurementDifference.DIFFERENT_POINT)
        }
        when {
            before.testFrequency == null || after.testFrequency == null -> add(MeasurementDifference.UNKNOWN_FREQUENCY)
            before.testFrequency != after.testFrequency -> add(MeasurementDifference.DIFFERENT_FREQUENCY)
        }
    }
    val resonance = before.resonance?.let { first -> after.resonance?.let { it.hertz - first.hertz } }
    val length = before.length?.let { first -> after.length?.let { it.nanometers - first.nanometers } }
    val swr = if (differences.isEmpty()) before.swr?.let { first -> after.swr?.minus(first) } else null
    return JournalComparison(resonance, length, swr, differences)
}
