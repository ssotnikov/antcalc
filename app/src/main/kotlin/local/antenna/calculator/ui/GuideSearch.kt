package local.antenna.calculator.ui

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal data class GuideSearchRequest(val document: String, val query: String, val match: Int = 0)

internal fun guideSearchRequest(address: String): GuideSearchRequest? = try {
    val uri = URI(address)
    val document = uri.rawPath?.removePrefix("/userguide/")
    if (uri.scheme != "https" || uri.rawAuthority != "appassets.androidplatform.net" ||
        document !in setOf("ru.html", "en.html")) null
    else {
        val pairs = uri.rawQuery?.split('&')?.map { part ->
            val key = part.substringBefore('=')
            key to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }.orEmpty()
        if (pairs.map { it.first }.distinct().size != pairs.size || pairs.any { it.first !in setOf("q", "match") }) null
        else {
            val values = pairs.toMap()
            val query = values["q"].orEmpty()
            val match = values["match"]?.toIntOrNull() ?: 0
            if (query.length > 32 || query.any { it.isISOControl() } || match < 0) null
            else GuideSearchRequest(requireNotNull(document), query.trim(), match)
        }
    }
} catch (_: IllegalArgumentException) {
    null
} catch (_: java.net.URISyntaxException) {
    null
}

internal fun guideSearchAddress(request: GuideSearchRequest): String =
    "$GUIDE_ORIGIN/userguide/${request.document}" + if (request.query.isEmpty()) "" else
        "?q=${URLEncoder.encode(request.query, "UTF-8")}&match=${request.match}"

internal fun guideEscapeHtml(value: String): String = value.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private val htmlEntity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);")
private fun decodeGuideText(text: String): String = htmlEntity.replace(text) {
    when (val name = it.groupValues[1]) {
        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> "\u00a0"
        else -> {
            val code = if (name.startsWith("#x")) name.drop(2).toIntOrNull(16) else name.drop(1).toIntOrNull()
            if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF)
                String(Character.toChars(code)) else it.value
        }
    }
}

private data class GuideTextRun(val raw: IntRange, val start: Int, val text: String)
internal data class GuideSearchResult(val html: String, val count: Int, val active: Int)

/** Search bundled guide prose without executing scripts or interpreting the query as markup. */
internal fun highlightGuide(html: String, query: String, requested: Int): GuideSearchResult {
    if (query.isBlank()) return GuideSearchResult(html, 0, 0)
    val mainStart = html.indexOf("<main ")
    val mainEnd = html.indexOf("</main>", mainStart.coerceAtLeast(0))
    if (mainStart < 0 || mainEnd < 0) return GuideSearchResult(html, 0, 0)
    val text = StringBuilder()
    val runs = mutableListOf<GuideTextRun>()
    val stack = mutableListOf<Pair<String, Boolean>>()
    val blocks = setOf("p", "h1", "h2", "h3", "h4", "li", "td", "th", "tr", "div", "section", "figcaption", "br")
    val voids = setOf("br", "img", "hr", "input", "meta", "link", "wbr", "source")
    val token = Regex("<!--.*?-->|<[^>]*>|[^<]+", RegexOption.DOT_MATCHES_ALL)
    for (part in token.findAll(html.substring(mainStart, mainEnd))) {
        val value = part.value
        if (value.startsWith('<')) {
            val name = Regex("^</?([a-zA-Z0-9]+)").find(value)?.groupValues?.get(1)?.lowercase() ?: continue
            if (name in blocks) text.append('\n')
            if (value.startsWith("</")) {
                val index = stack.indexOfLast { it.first == name }
                if (index >= 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            } else if (name !in voids && !value.endsWith("/>")) {
                val ignored = stack.lastOrNull()?.second == true || name in setOf("script", "style", "svg", "form") ||
                    Regex("class=\"[^\"]*\\b(app-links|web-label|direct-link)\\b").containsMatchIn(value)
                stack += name to ignored
            }
        } else if (stack.lastOrNull()?.second != true) {
            val decoded = decodeGuideText(value)
            runs += GuideTextRun((mainStart + part.range.first)..(mainStart + part.range.last), text.length, decoded)
            text.append(decoded)
        }
    }
    val plain = text.toString()
    val matches = mutableListOf<IntRange>()
    var cursor = 0
    while (cursor <= plain.length - query.length) {
        val found = plain.indexOf(query, cursor, ignoreCase = true)
        if (found < 0) break
        matches += found until found + query.length
        cursor = found + query.length
    }
    val active = requested.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
    val output = StringBuilder(html.length + matches.size * 70)
    cursor = 0
    for (run in runs) {
        output.append(html, cursor, run.raw.first)
        val relevant = matches.withIndex().filter { (_, range) -> range.first < run.start + run.text.length && range.last >= run.start }
        if (relevant.isEmpty()) output.append(html, run.raw.first, run.raw.last + 1)
        else {
            var local = 0
            for ((index, range) in relevant) {
                val from = (range.first - run.start).coerceAtLeast(0)
                val to = (range.last + 1 - run.start).coerceAtMost(run.text.length)
                output.append(guideEscapeHtml(run.text.substring(local, from)))
                output.append("<mark")
                if (range.first >= run.start) output.append(" id=\"guide-match-$index\"")
                if (index == active) output.append(" class=\"guide-current\"")
                output.append('>').append(guideEscapeHtml(run.text.substring(from, to))).append("</mark>")
                local = to
            }
            output.append(guideEscapeHtml(run.text.substring(local)))
        }
        cursor = run.raw.last + 1
    }
    output.append(html, cursor, html.length)
    return GuideSearchResult(output.toString(), matches.size, active)
}

internal fun searchableGuideHtml(html: String, request: GuideSearchRequest): String {
    val result = highlightGuide(html, request.query, request.match)
    val russian = request.document == "ru.html"
    val status = when {
        request.query.isEmpty() -> if (russian) "Введите текст и нажмите «Найти»." else "Enter text and press Find."
        result.count == 0 -> if (russian) "Совпадений нет" else "No matches"
        else -> if (russian) "${result.active + 1} из ${result.count}" else "${result.active + 1} of ${result.count}"
    }
    val buttons = if (result.count < 2) "" else listOf(-1, 1).joinToString("") { direction ->
        val index = (result.active + direction + result.count) % result.count
        val address = guideSearchAddress(request.copy(match = index)) + "#guide-match-$index"
        val label = if (direction < 0) { if (russian) "Назад" else "Previous" } else { if (russian) "Вперед" else "Next" }
        "<a class=\"guide-search-step\" href=\"${guideEscapeHtml(address)}\">$label</a>"
    }
    return result.html.replace("class=\"guide-search\"",
        if (result.count > 0) "class=\"guide-search has-results\"" else "class=\"guide-search\"")
        .replace("<!-- GUIDE_SEARCH_VALUE -->", guideEscapeHtml(request.query))
        .replace("<!-- GUIDE_SEARCH_STATUS -->", guideEscapeHtml(status))
        .replace("<!-- GUIDE_SEARCH_STEPS -->", buttons)
}
