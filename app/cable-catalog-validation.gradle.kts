import groovy.json.JsonGenerator
import groovy.json.JsonSlurper
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.LocalDate

val cableCatalog = layout.projectDirectory.file("src/main/res/raw/cable_catalog.json")

val validateCableCatalog = tasks.register("validateCableCatalog") {
    inputs.file(cableCatalog)

    doLast {
        fun checkCatalog(condition: Boolean, message: String) {
            if (!condition) throw GradleException("cable_catalog.json: $message")
        }

        val file = cableCatalog.asFile
        checkCatalog(file.length() in 1..65_536, "expected 1 to 65536 bytes")
        val json = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(file.readBytes())).toString()
        // Bound nesting before handing input to the parser and retain strings byte-for-byte.
        var depth = 0
        var quoted = false
        var escaped = false
        val compact = buildString {
            for (character in json) {
                if (quoted) {
                    append(character)
                    if (escaped) escaped = false
                    else if (character == '\\') escaped = true
                    else if (character == '"') quoted = false
                    continue
                }
                when (character) {
                    '"' -> quoted = true
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                    ' ', '\t', '\r', '\n' -> continue
                }
                checkCatalog(depth in 0..8, "invalid or excessive nesting")
                append(character)
            }
        }
        checkCatalog(!quoted && depth == 0, "unterminated JSON")
        val raw = try {
            JsonSlurper().parseText(json)
        } catch (error: RuntimeException) {
            throw GradleException("cable_catalog.json: malformed JSON", error)
        }

        // Only insignificant JSON whitespace may differ from strict serialization.
        // This also rejects duplicate keys, comments, and trailing input accepted by lenient parsers.
        val encoder = JsonGenerator.Options().disableUnicodeEscaping().build()
        checkCatalog(compact == encoder.toJson(raw), "expected strict JSON without duplicate keys or noncanonical tokens")

        fun record(value: Any?, required: Set<String>, optional: Set<String>, at: String): Map<*, *> {
            val fields = value as? Map<*, *> ?: throw GradleException("cable_catalog.json: $at must be an object")
            checkCatalog(fields.keys.containsAll(required) && fields.keys.all { it in required || it in optional },
                "$at has missing or unsupported fields")
            return fields
        }

        fun list(value: Any?, max: Int, at: String): List<*> {
            val items = value as? List<*> ?: throw GradleException("cable_catalog.json: $at must be an array")
            checkCatalog(items.size in 1..max, "$at must contain 1 to $max entries")
            return items
        }

        val labelFormat = Regex("""[\p{L}\p{N}][\p{L}\p{N} ()/.,+_-]{0,79}""")
        fun label(value: Any?, at: String) {
            checkCatalog(value is String && labelFormat.matches(value) && value.trim() == value,
                "$at must be a plain label of up to 80 characters")
        }

        fun decimal(value: Any?, low: String, high: String, scale: Int, at: String) {
            checkCatalog(value is Number, "$at must be numeric")
            val number = value.toString().toBigDecimalOrNull()
            checkCatalog(number != null && number in BigDecimal(low)..BigDecimal(high) && number.scale() in 0..scale,
                "$at is outside the allowed range or precision")
        }

        val sourceFormat = Regex(
            """https://(?:timesmicrowave\.com/wp-content/uploads/[0-9]{4}/[0-9]{2}/[A-Za-z0-9-]+\.pdf|catalog\.belden\.com/techdata/(?:en|EN)/[0-9]+_techdata\.pdf|www\.radiolab\.ru/(?:ru/3/[0-9]+/[0-9]+/[0-9]+\.php|scalar/[a-z0-9]+\.htm))""",
        )
        val root = record(raw, setOf("schema", "profiles", "referenceCables", "journalSuggestions"), emptySet(), "root")
        checkCatalog(root["schema"] == 1, "unsupported schema")
        val profileIds = mutableSetOf<String>()
        for ((index, value) in list(root["profiles"], 128, "profiles").withIndex()) {
            val at = "profiles[$index]"
            val fields = record(value, setOf("id", "manufacturer", "model", "source", "verified", "unit", "pointsMHz"),
                setOf("vf", "shorteningRatio", "conditions"), at)
            val id = fields["id"]
            checkCatalog(id is String && id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) &&
                id.length <= 48 && profileIds.add(id), "$at has an invalid or duplicate ID")
            label(fields["manufacturer"], "$at.manufacturer")
            label(fields["model"], "$at.model")
            val source = fields["source"]
            checkCatalog(source is String && source.length <= 240 && sourceFormat.matches(source),
                "$at has an unapproved source URL")
            val verified = fields["verified"]
            checkCatalog(verified is String && verified.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) &&
                runCatching { LocalDate.parse(verified) }.isSuccess, "$at has an invalid verification date")
            checkCatalog(!fields.containsKey("conditions") || fields["conditions"] in setOf("matched-25c", "nominal"),
                "$at has unknown conditions")
            checkCatalog(fields["unit"] in setOf("PER_100_METRES", "PER_100_FEET"), "$at has an unknown unit")
            checkCatalog(!fields.containsKey("vf") || !fields.containsKey("shorteningRatio"),
                "$at supplies both VF and its reciprocal")
            if (fields.containsKey("vf")) decimal(fields["vf"], "0.0001", "1", 4, "$at.vf")
            if (fields.containsKey("shorteningRatio")) decimal(fields["shorteningRatio"], "1", "10000", 4, "$at.shorteningRatio")
            var previousMHz = 0
            for ((pointIndex, valueAtPoint) in list(fields["pointsMHz"], 128, "$at.pointsMHz").withIndex()) {
                val point = valueAtPoint as? List<*>
                val mhz = point?.firstOrNull() as? Int
                checkCatalog(point?.size == 2 && mhz != null && mhz in (previousMHz + 1)..6000,
                    "$at.pointsMHz[$pointIndex] has an invalid, duplicate or unsorted frequency")
                previousMHz = requireNotNull(mhz)
                decimal(requireNotNull(point)[1], "0", "1000", 8, "$at.pointsMHz[$pointIndex]")
            }
        }

        val referenceNames = mutableSetOf<String>()
        for ((index, value) in list(root["referenceCables"], 128, "referenceCables").withIndex()) {
            val at = "referenceCables[$index]"
            val fields = record(value, setOf("name", "ohms", "vf"), emptySet(), at)
            label(fields["name"], "$at.name")
            checkCatalog(referenceNames.add(fields["name"] as String), "$at has a duplicate name")
            checkCatalog(fields["ohms"] is Int && (fields["ohms"] as Int) in 1..1000, "$at has invalid impedance")
            decimal(fields["vf"], "0.0001", "1", 4, "$at.vf")
        }

        val englishSuggestions = mutableSetOf<String>()
        val russianSuggestions = mutableSetOf<String>()
        for ((index, value) in list(root["journalSuggestions"], 128, "journalSuggestions").withIndex()) {
            val at = "journalSuggestions[$index]"
            val fields = record(value, setOf("en"), setOf("ru"), at)
            label(fields["en"], "$at.en")
            if (fields.containsKey("ru")) label(fields["ru"], "$at.ru")
            checkCatalog(englishSuggestions.add(fields["en"] as String) &&
                russianSuggestions.add((fields["ru"] ?: fields["en"]) as String), "$at has a duplicate suggestion")
        }
    }
}

tasks.named("preBuild") { dependsOn(validateCableCatalog) }
