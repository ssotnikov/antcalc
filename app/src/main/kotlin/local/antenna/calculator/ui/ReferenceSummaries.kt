package local.antenna.calculator.ui

import androidx.annotation.StringRes
import local.antenna.calculator.R

internal data class ReferenceSummary(
    @field:StringRes val description: Int,
    @field:StringRes val language: Int? = null,
)

internal val additionalReferenceSummaries = mapOf(
    CantennaSource.CHANGPUAK.url to ReferenceSummary(R.string.ref_card_cantenna_changpuak, R.string.ref_card_language_en),
    CantennaSource.CERN.url to ReferenceSummary(R.string.ref_card_cantenna_cern, R.string.ref_card_language_en),
    CantennaSource.MIT.url to ReferenceSummary(R.string.ref_card_cantenna_mit, R.string.ref_card_language_en),
    CantennaSource.SETI.url to ReferenceSummary(R.string.ref_card_cantenna_seti, R.string.ref_card_language_en),
    CantennaSource.HORN.url to ReferenceSummary(R.string.ref_card_cantenna_horn, R.string.ref_card_language_en),
    CantennaSource.GOUGH.url to ReferenceSummary(R.string.ref_card_cantenna_gough, R.string.ref_card_language_ru),
    CantennaSource.AERIAL.url to ReferenceSummary(R.string.ref_card_cantenna_aerial, R.string.ref_card_language_ru),
    CantennaSource.ELEPAL.url to ReferenceSummary(R.string.ref_card_cantenna_elepal, R.string.ref_card_language_en),
    Hb9cvSource.TAX.url to ReferenceSummary(R.string.ref_card_hb9cv_tax, R.string.ref_card_language_nl),
    Hb9cvSource.ASQ.url to ReferenceSummary(R.string.ref_card_hb9cv_asq, R.string.ref_card_language_ja),
    Hb9cvSource.LVT.url to ReferenceSummary(R.string.ref_card_hb9cv_lvt, R.string.ref_card_language_fr),
    Hb9cvSource.LVT_PCB.url to ReferenceSummary(R.string.ref_card_hb9cv_lvt_pcb, R.string.ref_card_language_fr),
    Hb9cvSource.SNES.url to ReferenceSummary(R.string.ref_card_hb9cv_snes, R.string.ref_card_language_ru),
    Hb9cvSource.BUTORIN.url to ReferenceSummary(R.string.ref_card_hb9cv_butorin, R.string.ref_card_language_ru),
    MoxonSource.METHOD.url to ReferenceSummary(R.string.ref_card_moxon_method, R.string.ref_card_language_en),
    MoxonSource.CEBIK2M.url to ReferenceSummary(R.string.ref_card_moxon_cebik2m, R.string.ref_card_language_en),
    MoxonSource.QST2M.url to ReferenceSummary(R.string.ref_card_moxon_qst2m, R.string.ref_card_language_en),
    MoxonSource.W6YE.url to ReferenceSummary(R.string.ref_card_moxon_w6ye, R.string.ref_card_language_en),
    MoxonSource.W1CDN.url to ReferenceSummary(R.string.ref_card_moxon_w1cdn, R.string.ref_card_language_en),
    MoxonSource.BAKER.url to ReferenceSummary(R.string.ref_card_moxon_baker, R.string.ref_card_language_en),
    MoxonSource.G6GVI.url to ReferenceSummary(R.string.ref_card_moxon_g6gvi, R.string.ref_card_language_en),
    MoxonSource.CBWIRE.url to ReferenceSummary(R.string.ref_card_moxon_cbwire, R.string.ref_card_language_en),
    MoxonSource.CBFIELD.url to ReferenceSummary(R.string.ref_card_moxon_cbfield, R.string.ref_card_language_de),
    MoxonSource.EA2BD.url to ReferenceSummary(R.string.ref_card_moxon_ea2bd, R.string.ref_card_language_es),
    MoxonSource.PORTABLE.url to ReferenceSummary(R.string.ref_card_moxon_portable, R.string.ref_card_language_en),
    MoxonSource.WIRE.url to ReferenceSummary(R.string.ref_card_moxon_wire, R.string.ref_card_language_en),
    MoxonSource.FDIM.url to ReferenceSummary(R.string.ref_card_moxon_fdim, R.string.ref_card_language_en),
    MoxonSource.SATELLITE.url to ReferenceSummary(R.string.ref_card_moxon_satellite, R.string.ref_card_language_en),
    MoxonSource.LORA.url to ReferenceSummary(R.string.ref_card_moxon_lora),
    MoxonSource.PRINT10M.url to ReferenceSummary(R.string.ref_card_moxon_print10m),
    MoxonSource.YAMA.url to ReferenceSummary(R.string.ref_card_moxon_yama, R.string.ref_card_language_en),
    SLIM_JIM_OK1IKE_URL to ReferenceSummary(R.string.ref_card_slim_jim_ok1ike, R.string.ref_card_language_cs),
    OK1IKE_HB9ACC_URL to ReferenceSummary(R.string.ref_card_jpole_hb9acc, R.string.ref_card_language_de),
    SLIM_JIM_SV1JRF_URL to ReferenceSummary(R.string.ref_card_slim_jim_sv1jrf, R.string.ref_card_language_en),
)

internal val hb9cvReferenceSources = listOf(
    Hb9cvSource.LVT, Hb9cvSource.LVT_PCB, Hb9cvSource.ASQ,
    Hb9cvSource.SNES, Hb9cvSource.BUTORIN, Hb9cvSource.TAX,
)
