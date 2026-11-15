package local.antenna.calculator.ui

import local.antenna.calculator.core.Cantenna
import local.antenna.calculator.core.input.FieldState
import local.antenna.calculator.core.input.PositiveDecimalParser
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val MAX_CANS = 12
private const val MAX_MM = 100_000.0

internal fun cantennaNumber(raw: String, minimum: Double, maximum: Double) =
    (PositiveDecimalParser.parse(raw, allowZero = minimum == 0.0) as? FieldState.Valid)
        ?.value?.toDouble()?.takeIf { it in minimum..maximum }

internal fun cantennaLength(raw: String, allowZero: Boolean = false) =
    cantennaNumber(raw, if (allowZero) 0.0 else .001, MAX_MM)?.div(1000)

internal data class CantennaCandidate(
    val name: String = "",
    val diameter: String = "85",
    val depth: String = "",
    val tolerance: String = "",
    val contact: String = "",
    val probeDiameter: String = "",
) {
    fun geometry(): Cantenna.Can? {
        val d = cantennaLength(diameter) ?: return null
        val l = if (depth.isBlank()) null else cantennaLength(depth) ?: return null
        val delta = if (tolerance.isBlank()) 0.0 else cantennaLength(tolerance, true) ?: return null
        val pin = if (contact.isBlank()) null else cantennaLength(contact, true) ?: return null
        val probe = if (probeDiameter.isBlank()) null else cantennaLength(probeDiameter) ?: return null
        return if (d - delta >= MIN_CANTENNA_LENGTH) Cantenna.Can(d, l, delta, pin, probe) else null
    }

    fun json() = JSONArray(listOf(name, diameter, depth, tolerance, contact, probeDiameter))
}

internal data class CantennaDraft(
    val target: String = "2437",
    val withBand: Boolean = false,
    val lower: String = "2412",
    val upper: String = "2462",
    val cans: List<CantennaCandidate> = listOf(CantennaCandidate()),
    val throat: String = "85",
    val mouth: String = "145",
    val flareLength: String = "51.961524227",
    val seam: String = "",
) {
    val hertz get() = cantennaNumber(target, 100.0, 100_000.0)?.times(1e6)
    fun band(): Cantenna.Band? {
        val target = hertz ?: return null
        return diameterBand()?.takeIf { target in it.lowHz..it.highHz }
    }

    fun diameterBand(): Cantenna.Band? {
        if (!withBand) return hertz?.let { Cantenna.Band(it) }
        val low = cantennaNumber(lower, 100.0, 100_000.0)?.times(1e6) ?: return null
        val high = cantennaNumber(upper, 100.0, 100_000.0)?.times(1e6) ?: return null
        return if (low <= high) Cantenna.Band(low, high) else null
    }

    fun json(): String = JSONObject().put("target", target).put("withBand", withBand)
        .put("lower", lower).put("upper", upper).put("throat", throat).put("mouth", mouth)
        .put("flareLength", flareLength).put("seam", seam)
        .put("cans", JSONArray().apply { cans.forEach { put(it.json()) } }).toString()

    companion object {
        fun restore(raw: String?): CantennaDraft {
            val defaults = CantennaDraft()
            if (raw == null) return defaults
            return try {
                val data = JSONObject(raw)
                val rows = data.getJSONArray("cans")
                val cans = List(rows.length().coerceAtMost(MAX_CANS)) { index ->
                    val can = rows.getJSONArray(index)
                    CantennaCandidate(can.getString(0).take(64), can.getString(1), can.getString(2),
                        can.getString(3), can.getString(4), can.getString(5))
                }
                defaults.copy(target = data.getString("target"), withBand = data.getBoolean("withBand"),
                    lower = data.getString("lower"), upper = data.getString("upper"),
                    cans = cans.ifEmpty { defaults.cans }, throat = data.optString("throat", defaults.throat),
                    mouth = data.optString("mouth", defaults.mouth),
                    flareLength = data.optString("flareLength", defaults.flareLength), seam = data.optString("seam"))
            } catch (_: JSONException) {
                defaults
            }
        }
    }
}

internal const val MIN_CANTENNA_LENGTH = .000001
