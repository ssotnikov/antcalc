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
    val subsection: Int? = null,
    val interfaceLanguageOnly: Boolean = false,
)

internal data class ReferenceGroup(
    val title: Int,
    val materials: List<TechnicalReference>,
    val interfaceLanguageOnly: Boolean = false,
    val preferRussianSources: Boolean = true,
)

internal val referenceGroups = listOf(
    ReferenceGroup(R.string.references_yagi, listOf(
        TechnicalReference("yagi-dg7ybn", R.string.source_yagi_dg7ybn, R.string.source_yagi_dg7ybn_description,
            R.string.source_yagi_dg7ybn_url, "DG7YBN", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.jpole_references, listOf(
        TechnicalReference("jpole-cebik", R.string.jpole_link_cebik, R.string.jpole_ref_part1, R.string.jpole_source_cebik, "L. B. Cebik · W4RNL", AppLanguage.ENGLISH),
        TechnicalReference("jpole-part3", R.string.jpole_link_part3, R.string.jpole_ref_part3, R.string.jpole_source_part3, "L. B. Cebik · W4RNL", AppLanguage.ENGLISH),
        TechnicalReference("jpole-eax", R.string.jpole_link_eax, R.string.jpole_ref_eax, R.string.jpole_source_eax, "eax.me", AppLanguage.RUSSIAN),
        TechnicalReference("jpole-cqham", R.string.jpole_link_cqham, R.string.jpole_ref_cqham, R.string.jpole_source_cqham, "RU3ARJ · CQHAM", AppLanguage.RUSSIAN),
        TechnicalReference("jpole-sv", R.string.jpole_link_sv, R.string.jpole_ref_sv, R.string.jpole_source_sv, "SV1UI", AppLanguage.ENGLISH),
        TechnicalReference("jpole-wireman", R.string.jpole_link_wireman, R.string.jpole_ref_wireman, R.string.jpole_source_wireman, "DK7ZB", AppLanguage.ENGLISH),
    )),
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
    ReferenceGroup(R.string.references_smith, listOf(
        TechnicalReference("smith-antenna-theory", R.string.source_smith_antenna_theory, R.string.source_smith_antenna_theory_description, R.string.source_smith_antenna_theory_url, "Antenna-Theory", AppLanguage.ENGLISH),
        TechnicalReference("smith-berkeley", R.string.source_smith_berkeley, R.string.source_smith_berkeley_description, R.string.source_smith_berkeley_url, "UC Berkeley", AppLanguage.ENGLISH, "PDF"),
        TechnicalReference("smith-rutube", R.string.source_smith_rutube, R.string.source_smith_rutube_description, R.string.source_smith_rutube_url, "RUTUBE", AppLanguage.RUSSIAN, subsection = R.string.references_smith_video),
        TechnicalReference("smith-nanovna", R.string.source_smith_nanovna, R.string.source_smith_nanovna_description, R.string.source_smith_nanovna_url, "YouTube", AppLanguage.RUSSIAN, subsection = R.string.references_smith_video),
        TechnicalReference("smith-radioprog", R.string.source_smith_radioprog, R.string.source_smith_radioprog_description, R.string.source_smith_radioprog_url, "RadioProg", AppLanguage.RUSSIAN, subsection = R.string.references_smith_practical),
        TechnicalReference("smith-grigorov", R.string.source_smith_grigorov, R.string.source_smith_grigorov_description, R.string.source_smith_grigorov_url, "RA1OHX", AppLanguage.RUSSIAN, subsection = R.string.references_smith_practical),
        TechnicalReference("smith-tz-lab", R.string.source_smith_tz_lab, R.string.source_smith_tz_lab_description, R.string.source_smith_tz_lab_url, "ТЗ Лаб", AppLanguage.RUSSIAN, subsection = R.string.references_smith_interactive),
        TechnicalReference("smith-radioskot", R.string.source_smith_radioskot, R.string.source_smith_radioskot_description, R.string.source_smith_radioskot_url, "RadioSkot", AppLanguage.RUSSIAN, subsection = R.string.references_smith_interactive),
    )),
    ReferenceGroup(R.string.references_ground_plane, listOf(
        TechnicalReference("ground-plane-rlocman", R.string.source_ground_plane_rlocman, R.string.source_ground_plane_rlocman_description, R.string.source_ground_plane_rlocman_url, "РадиоЛоцман", AppLanguage.RUSSIAN),
        TechnicalReference("ground-plane-qrz", R.string.source_ground_plane_qrz, R.string.source_ground_plane_qrz_description, R.string.source_ground_plane_qrz_url, "QRZ.RU", AppLanguage.RUSSIAN),
        TechnicalReference("ground-plane-m0ukd", R.string.source_ground_plane_m0ukd, R.string.source_ground_plane_m0ukd_description, R.string.source_ground_plane_m0ukd_url, "M0UKD", AppLanguage.ENGLISH),
    )),
    ReferenceGroup(R.string.loop_references, listOf(
        TechnicalReference("loop-quad-ru", R.string.loop_source_ru_quad, R.string.loop_source_ru_quad_description, R.string.loop_source_ru_quad_url, "Радио · 1976", AppLanguage.RUSSIAN),
        TechnicalReference("loop-delta-7-ru", R.string.loop_source_ru_7, R.string.loop_source_ru_7_description, R.string.loop_source_ru_7_url, "Игорь Захаров, UR5EFX", AppLanguage.RUSSIAN),
        TechnicalReference("loop-delta-20-ru", R.string.loop_source_ru_20, R.string.loop_source_ru_20_description, R.string.loop_source_ru_20_url, "UA1CBM · RA1OHX", AppLanguage.RUSSIAN),
        TechnicalReference("loop-theory-en", R.string.loop_source_en_theory, R.string.loop_source_en_theory_description, R.string.loop_source_en_theory_url, "Practical Antennas", AppLanguage.ENGLISH),
        TechnicalReference("loop-sizes-en", R.string.loop_source_en_sizes, R.string.loop_source_en_sizes_description, R.string.loop_source_en_sizes_url, "Practical Antennas", AppLanguage.ENGLISH),
        TechnicalReference("loop-build-en", R.string.loop_source_en_build, R.string.loop_source_en_build_description, R.string.loop_source_en_build_url, "Practical Antennas", AppLanguage.ENGLISH),
        TechnicalReference("loop-arrl-en", R.string.loop_source_en_arrl, R.string.loop_source_en_arrl_description, R.string.loop_source_en_arrl_url, "ARRL", AppLanguage.ENGLISH),
    ), interfaceLanguageOnly = true),
    ReferenceGroup(R.string.references_cable, listOf(
        TechnicalReference("quarter-wave-transformer", R.string.loop_source_en_match, R.string.loop_source_en_match_description, R.string.loop_source_en_match_url, "Virginia Tech", AppLanguage.ENGLISH, interfaceLanguageOnly = true),
        TechnicalReference("radiolab", R.string.source_radiolab, R.string.source_radiolab_description, R.string.source_radiolab_url, "Радиолэб", AppLanguage.RUSSIAN),
        TechnicalReference("rfi-dielectrics", R.string.source_rfi_dielectrics, R.string.source_rfi_dielectrics_description, R.string.source_rfi_dielectrics_url, "RFI", AppLanguage.ENGLISH, "PDF"),
        TechnicalReference("rig-cable", R.string.source_rig_cable, R.string.source_rig_cable_description, R.string.source_rig_cable_url, "RigExpert", AppLanguage.RUSSIAN, "§ 5.2.5"),
        TechnicalReference("steer", R.string.source_steer, R.string.source_steer_description, R.string.source_steer_url, "Michael Steer · NC State University", AppLanguage.ENGLISH, "§ 2.2"),
        TechnicalReference("cable-delay", R.string.source_cable_delay, R.string.source_cable_delay_description, R.string.source_cable_delay_url, "Keysight", AppLanguage.ENGLISH),
    ), preferRussianSources = false),
)
