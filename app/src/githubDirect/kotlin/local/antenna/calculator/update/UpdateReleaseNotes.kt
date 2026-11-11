package local.antenna.calculator.update

import org.json.JSONObject

private const val MAX_NOTES_LENGTH = 4_000
private val RU_HEADING = Regex("""^#{1,6}\s+что изменилось.*$""", RegexOption.IGNORE_CASE)
private val EN_HEADING = Regex("""^#{1,6}\s+what['’]s changed.*$""", RegexOption.IGNORE_CASE)
private val BULLET = Regex("""(?m)^[ \t]*[-*][ \t]+""")

internal data class UpdateReleaseNotes(val ru: String?, val en: String?) {
    fun text(language: String): String? =
        (if (language == "ru") ru ?: en else en ?: ru)?.replace(BULLET, "• ")

    fun json() = JSONObject().put("ru", ru).put("en", en)

    companion object {
        fun parse(json: JSONObject?): UpdateReleaseNotes? =
            create(json?.opt("ru") as? String, json?.opt("en") as? String)

        fun fromGithubBody(body: String?): UpdateReleaseNotes? {
            if (body.isNullOrBlank() || body.length > MAX_NOTES_LENGTH * 2) return null
            val lines = body.lines()
            val ruStart = lines.indexOfFirst { RU_HEADING.matches(it.trim()) }
            val enStart = lines.indexOfFirst { EN_HEADING.matches(it.trim()) }
            if (ruStart < 0 && enStart < 0) return create(null, body)
            fun section(start: Int, other: Int) = if (start < 0) null else {
                lines.subList(start + 1, if (other > start) other else lines.size).joinToString("\n")
            }
            return create(section(ruStart, enStart), section(enStart, ruStart))
        }

        private fun create(ru: String?, en: String?): UpdateReleaseNotes? {
            fun bounded(value: String?) = value?.trim()?.takeIf { it.length in 1..MAX_NOTES_LENGTH }
            val notes = UpdateReleaseNotes(bounded(ru), bounded(en))
            return notes.takeIf { it.ru != null || it.en != null }
        }
    }
}
