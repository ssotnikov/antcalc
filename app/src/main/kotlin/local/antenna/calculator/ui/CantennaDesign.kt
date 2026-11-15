package local.antenna.calculator.ui

import androidx.annotation.StringRes
import local.antenna.calculator.R
import local.antenna.calculator.core.Cantenna.Can
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class CantennaSource(
    val author: String, @field:StringRes val title: Int, val url: String,
    @field:StringRes val terms: Int? = null, val termsUrl: String? = null,
) {
    CHANGPUAK("Changpuak", R.string.cantenna_chang, "https://www.changpuak.ch/electronics/cantenna.php",
        R.string.cantenna_chang_terms, "https://www.changpuak.ch/terms/index.php"),
    CERN("Piotr Kowina, Christine Völlinger, Manfred Wendt · CERN Accelerator School", R.string.cantenna_use_calc,
        "https://indico.cern.ch/event/1380440/contributions/6176014/attachments/2964377/5214841/RFMeasurementTechniques_Theory_Spa.pdf"),
    MIT("Alan J. Fenn · MIT OpenCourseWare", R.string.cantenna_mit_source,
        "https://ocw.mit.edu/courses/res-ll-003-build-a-small-radar-system-capable-of-sensing-range-doppler-and-synthetic-aperture-radar-imaging-january-iap-2011/fa61f02356be7efc88142b258d031110_MITRES_LL_003IAP11_lec02.pdf",
        R.string.cantenna_mit_terms, "https://ocw.mit.edu/pages/privacy-and-terms-of-use/"),
    SETI("H. Paul Shuch (N6TX) · The SETI League", R.string.cantenna_ve_source,
        "https://www.setileague.org/hardware/feedchok.htm",
        R.string.cantenna_ve_terms, "https://www.setileague.org/admin/copyrigh.htm"),
    HORN("Yurii Pylypenko", R.string.cantenna_horn_source, "https://ypylypenko.livejournal.com/43582.html"),
    GOUGH("Dave Gough", R.string.cantenna_gough_source, "https://vtanaev.narod.ru/2.4Ggz/cylindrical_waves.htm"),
    AERIAL("3G-Aerial", R.string.cantenna_aerial_source,
        "https://3g-aerial.biz/konstruktsii-antenn/odnonapravlennye-antenny/banochnaya-antenna",
        R.string.hb9cv_aerial_terms, Hb9cvSource.AERIAL_TERMS.url),
    ELEPAL("Elepal", R.string.cantenna_elepal_source, "https://www.elepal.fi/antennit/antenna2.html"),
}

internal enum class CantennaDesign(
    val model: String, @field:StringRes val title: Int, val source: CantennaSource, val mhz: Double,
    val can: Can, val offset: Double, val probe: Double,
) {
    MIT("cantenna_mit", R.string.cantenna_mit, CantennaSource.MIT, 2400.0, Can(.099, .133), .046, .030),
    VE4MA("cantenna_ve4ma", R.string.cantenna_ve, CantennaSource.SETI, 1420.405752, Can(.156, .278), .088, .046),
    HORN("cantenna_flare", R.string.cantenna_horn, CantennaSource.HORN, 2440.0, Can(.085, .172, probeDiameter = .001),
        .0574, .0297);

    companion object {
        const val RING_DIAMETER = .360
        const val RING_DEPTH = .106
        const val FLARE_MOUTH = .145
        val flareDepth = sqrt(.060 * .060 - .030 * .030)
        const val TUNING_PIN_HEIGHT = .0192
        const val TUNING_PIN_SPACING = .0303
    }
}

internal fun cantennaVector(
    diameter: Double = .085, depth: Double = .174, probe: Double = .031,
    offset: Double = .058, throat: Double? = null, mouth: Double? = null, flareLength: Double? = null,
    ringPosition: Double? = null, tuningPin: Boolean = false,
): AntennaVector {
    val radius = diameter * 500
    val front = depth * 1000
    val rods = mutableListOf<AntennaRod>()
    val joints = mutableListOf<AntennaJoint>()
    fun point(x: Double, y: Double, z: Double) = AntennaPoint(x.toFloat(), y.toFloat(), z.toFloat())
    fun rod(a: AntennaPoint, b: AntennaPoint, width: Float, material: Int) {
        rods += AntennaRod(a, b, width, material)
    }
    fun around(x: Double, r: Double, index: Int): AntennaPoint {
        val angle = 2 * PI * index / 36
        return point(x, r * cos(angle), r * sin(angle))
    }
    fun wall(start: Double, end: Double, r1: Double, r2: Double) {
        for (index in 0 until 36) {
            val a = around(start, r1, index)
            val b = around(end, r2, index)
            rod(a, around(start, r1, index + 1), 3.6f, 0)
            rod(b, around(end, r2, index + 1), 4.4f, 0)
            rod(a, b, if (index % 3 == 0) 2.6f else 1.4f, 0)
        }
    }
    wall(0.0, front, radius, radius)
    for (index in 0 until 36 step 3) rod(point(0.0, 0.0, 0.0), around(0.0, radius, index), 2f, 0)
    if (throat != null && mouth != null && flareLength != null) {
        wall(front, front + flareLength * 1000, throat * 500, mouth * 500)
    }
    ringPosition?.let {
        val rear = front - it * 1000
        wall(rear, rear + CantennaDesign.RING_DEPTH * 1000, CantennaDesign.RING_DIAMETER * 500, CantennaDesign.RING_DIAMETER * 500)
        for (index in 0 until 36 step 3) {
            rod(around(rear, radius, index), around(rear, CantennaDesign.RING_DIAMETER * 500, index), 2f, 0)
        }
    }
    fun pin(position: Double, height: Double, connector: Boolean) {
        val x = position * 1000
        if (x >= front) return
        rod(point(x, radius + 2, 0.0), point(x, radius - height * 1000, 0.0), 4f, 2)
        if (connector) {
            rod(point(x, radius + 2, 0.0), point(x, radius + 13, 0.0), 9f, 0)
            joints += AntennaJoint(point(x, radius + 3, 0.0), 15f, 0)
        }
    }
    pin(offset, probe, true)
    if (tuningPin) pin(offset + CantennaDesign.TUNING_PIN_SPACING, CantennaDesign.TUNING_PIN_HEIGHT, false)
    return AntennaVector(rods, joints)
}
