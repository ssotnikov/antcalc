package local.antenna.calculator.journal

import local.antenna.calculator.core.journal.JournalRecord
import local.antenna.calculator.core.layout.YagiDesign

internal val JournalRecord.restrictedYagiMaterials: Boolean
    get() = calculation?.model?.let { model ->
        YagiDesign.entries.any { it.model == model && it.restricted }
    } == true
