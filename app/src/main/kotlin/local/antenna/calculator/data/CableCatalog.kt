package local.antenna.calculator.data

import android.content.Context
import local.antenna.calculator.R
import local.antenna.calculator.core.feedline.AttenuationUnit
import local.antenna.calculator.core.feedline.VelocityFactor
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal data class CableProfile(
    val id: String,
    val name: String,
    val source: String,
    val verified: String,
    val conditions: Int?,
    val velocity: VelocityFactor?,
    val reciprocalVelocity: Boolean,
    val unit: AttenuationUnit,
    val attenuation: Map<Long, BigDecimal>,
)

internal data class CableReference(val name: String, val impedance: Int, val velocityFactor: Double)
internal data class CableSuggestion(val english: String, val russian: String)

internal class CableCatalog private constructor(
    val profiles: List<CableProfile>,
    val references: List<CableReference>,
    val suggestions: List<CableSuggestion>,
) {
    private val profilesById = profiles.associateBy(CableProfile::id)
    fun profile(id: String?) = profilesById[id]

    companion object {
        @Volatile private var cached: CableCatalog? = null

        fun read(context: Context): CableCatalog = cached ?: synchronized(this) {
            cached ?: context.resources.openRawResource(R.raw.cable_catalog).bufferedReader().use {
                parse(JSONObject(it.readText()))
            }.also { cached = it }
        }

        private fun parse(root: JSONObject): CableCatalog {
            check(root.getInt("schema") == 1)
            return CableCatalog(
                root.getJSONArray("profiles").objects().map { entry ->
                    val reciprocal = entry.has("shorteningRatio")
                    val vf = when {
                        reciprocal -> BigDecimal.ONE.divide(BigDecimal(entry.getString("shorteningRatio")), 4, RoundingMode.HALF_UP)
                        entry.has("vf") -> BigDecimal(entry.getString("vf"))
                        else -> null
                    }
                    val points = entry.getJSONArray("pointsMHz")
                    CableProfile(
                        entry.getString("id"), entry.getString("manufacturer") + " " + entry.getString("model"),
                        entry.getString("source"), entry.getString("verified"),
                        when (entry.optString("conditions")) {
                            "matched-25c" -> R.string.feedline_matched_temperature
                            "nominal" -> R.string.feedline_nominal
                            else -> null
                        },
                        vf?.movePointRight(4)?.intValueExact()?.let(VelocityFactor::ofTenThousandths), reciprocal,
                        AttenuationUnit.valueOf(entry.getString("unit")),
                        (0 until points.length()).associate { index ->
                            val point = points.getJSONArray(index)
                            point.getLong(0) * 1_000_000L to BigDecimal(point.getString(1))
                        },
                    )
                },
                root.getJSONArray("referenceCables").objects().map {
                    CableReference(it.getString("name"), it.getInt("ohms"), it.getDouble("vf"))
                },
                root.getJSONArray("journalSuggestions").objects().map {
                    CableSuggestion(it.getString("en"), it.optString("ru", it.getString("en")))
                },
            )
        }

        private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    }
}
