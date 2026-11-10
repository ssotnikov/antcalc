package local.antenna.calculator.ui

import local.antenna.calculator.R
import local.antenna.calculator.state.AppLanguage

internal data class TechnicalReference(
    val id: String,
    val title: Int,
    val description: Int,
    val address: Int,
    val publisher: String,
    val language: AppLanguage,
    val section: String? = null,
)

internal data class ReferenceGroup(
    val title: Int,
    val materials: List<TechnicalReference>,
    val interfaceLanguageOnly: Boolean = false,
    val preferRussianSources: Boolean = true,
)

internal val referenceGroups = listOf(
    ReferenceGroup(R.string.references_fundamentals, listOf(
        TechnicalReference("nist", R.string.source_nist, R.string.source_nist_description, R.string.source_nist_url, "NIST", AppLanguage.ENGLISH),
        TechnicalReference("itu", R.string.source_itu, R.string.source_itu_description, R.string.source_itu_url, "МСЭ / ITU", AppLanguage.RUSSIAN, "§ 4.3"),
        TechnicalReference("antenna-geometry", R.string.source_antenna_geometry, R.string.source_antenna_geometry_description, R.string.source_antenna_geometry_url, "ARRL", AppLanguage.ENGLISH, "Unit 4"),
    )),
    ReferenceGroup(R.string.references_construction, listOf(
        TechnicalReference("rsgb", R.string.source_rsgb, R.string.source_rsgb_description, R.string.source_rsgb_url, "RSGB", AppLanguage.ENGLISH),
        TechnicalReference("nasa", R.string.source_nasa, R.string.source_nasa_description, R.string.source_nasa_url, "NASA Radio JOVE", AppLanguage.ENGLISH, "§ 3.1"),
        TechnicalReference("verticals", R.string.source_verticals, R.string.source_verticals_description, R.string.source_verticals_url, "ARRL", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_inverted_v, listOf(
        TechnicalReference("inverted-v-ru", R.string.source_inverted_v_ru_title, R.string.source_inverted_v_ru_description, R.string.source_inverted_v_ru_url, "RUQRZ", AppLanguage.RUSSIAN),
        TechnicalReference("inverted-v-en", R.string.source_inverted_v_en_title, R.string.source_inverted_v_en_description, R.string.source_inverted_v_en_url, "ARRL", AppLanguage.ENGLISH, "Fig. 2A"),
    ), interfaceLanguageOnly = true),
    ReferenceGroup(R.string.references_five_eighths, listOf(
        TechnicalReference("five-eighths", R.string.source_five_eighths, R.string.source_five_eighths_description, R.string.source_five_eighths_url, "Dale Hunt, WB6BYU", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_tuning, listOf(
        TechnicalReference("rig-tuning", R.string.source_rig_tuning, R.string.source_rig_tuning_description, R.string.source_rig_tuning_url, "RigExpert", AppLanguage.RUSSIAN, "§ 5.1.2"),
        TechnicalReference("nashua", R.string.source_nashua, R.string.source_nashua_description, R.string.source_nashua_url, "Martin Blustine · Nashua Area Radio Society", AppLanguage.ENGLISH, "Method II"),
        TechnicalReference("analyzer", R.string.source_analyzer, R.string.source_analyzer_description, R.string.source_analyzer_url, "ARRL", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_matching, listOf(
        TechnicalReference("kadamm", R.string.source_kadamm, R.string.source_kadamm_description, R.string.source_kadamm_url, "КАДАММ", AppLanguage.RUSSIAN),
        TechnicalReference("rohde", R.string.source_rohde, R.string.source_rohde_description, R.string.source_rohde_url, "Rohde & Schwarz", AppLanguage.ENGLISH, "§ 8"),
        TechnicalReference("reflection", R.string.source_reflection, R.string.source_reflection_description, R.string.source_reflection_url, "Analog Devices", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.references_cable, listOf(
        TechnicalReference("radiolab", R.string.source_radiolab, R.string.source_radiolab_description, R.string.source_radiolab_url, "Радиолэб", AppLanguage.RUSSIAN),
        TechnicalReference("rfi-dielectrics", R.string.source_rfi_dielectrics, R.string.source_rfi_dielectrics_description, R.string.source_rfi_dielectrics_url, "RFI", AppLanguage.ENGLISH, "PDF"),
        TechnicalReference("rig-cable", R.string.source_rig_cable, R.string.source_rig_cable_description, R.string.source_rig_cable_url, "RigExpert", AppLanguage.RUSSIAN, "§ 5.2.5"),
        TechnicalReference("steer", R.string.source_steer, R.string.source_steer_description, R.string.source_steer_url, "Michael Steer · NC State University", AppLanguage.ENGLISH, "§ 2.2"),
        TechnicalReference("cable-delay", R.string.source_cable_delay, R.string.source_cable_delay_description, R.string.source_cable_delay_url, "Keysight", AppLanguage.ENGLISH),
    ), preferRussianSources = false),
)
