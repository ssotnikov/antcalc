package local.antenna.calculator.ui

import org.junit.Assert.*
import org.junit.Test

class GuideSearchTest {
    private fun document(prose: String) = "<header>not searched</header><main id=\"guide\">$prose</main>"

    @Test fun acceptsOnlyBoundedLocalGuideRequests() {
        val request = GuideSearchRequest("ru.html", "КСВ и α", 2)
        assertEquals(request, guideSearchRequest(guideSearchAddress(request) + "#guide-match-2"))
        for (url in listOf("http://appassets.androidplatform.net/userguide/ru.html?q=x",
            "https://appassets.androidplatform.net.evil/userguide/ru.html?q=x",
            "https://x@appassets.androidplatform.net/userguide/ru.html?q=x",
            "https://appassets.androidplatform.net:443/userguide/ru.html?q=x",
            "$GUIDE_ORIGIN/userguide/../ru.html?q=x", "$GUIDE_ORIGIN/userguide/ru.html?unknown=x",
            "$GUIDE_ORIGIN/userguide/ru.html?q=a&q=b", "$GUIDE_ORIGIN/userguide/ru.html?q=%00",
            "$GUIDE_ORIGIN/userguide/ru.html?q=${"a".repeat(33)}", "$GUIDE_ORIGIN/userguide/ru.html?q=%ZZ")) {
            assertNull(url, guideSearchRequest(url))
            assertNull(url, guideAssetPath(url))
        }
    }

    @Test fun matchesAcrossInlineMarkupAndDecodesEntities() {
        val html = document("<p>ДИАГРАММА <strong>Смита</strong></p><p>R &amp; X</p>")
        val phrase = highlightGuide(html, "диаграмма Смита", 0)
        assertEquals(1, phrase.count)
        assertTrue(phrase.html.contains("id=\"guide-match-0\""))
        assertTrue(phrase.html.contains("<strong><mark class=\"guide-current\">Смита</mark></strong>"))
        val entity = highlightGuide(html, "R & X", 0)
        assertEquals(1, entity.count)
        assertTrue(entity.html.contains("R &amp; X</mark>"))
    }

    @Test fun ignoresNavigationAndDoesNotJoinParagraphs() {
        val html = document("<div class=\"app-links\"><a>КСВ</a></div><span class=\"web-label\">КСВ</span>" +
            "<p>КСВ</p><p>антенны</p>")
        assertEquals(1, highlightGuide(html, "КСВ", 0).count)
        assertEquals(0, highlightGuide(html, "КСВ антенны", 0).count)
    }

    @Test fun selectsClampedMatchAndTreatsMarkupAsLiteralText() {
        val html = document("<p>α α &lt;script&gt;</p>")
        val found = highlightGuide(html, "α", Int.MAX_VALUE)
        assertEquals(2, found.count)
        assertEquals(1, found.active)
        assertTrue(found.html.contains("id=\"guide-match-1\" class=\"guide-current\""))
        assertEquals(1, highlightGuide(html, "<script>", 0).count)
        assertFalse(highlightGuide(html, "<script>", 0).html.contains("<script>"))
        assertEquals(html, highlightGuide(html, "", 0).html)
    }

    @Test fun rendersSafeQueryAndLocalNavigation() {
        val html = document("<p>α α</p>") + "<!-- GUIDE_SEARCH_VALUE --><!-- GUIDE_SEARCH_STATUS --><!-- GUIDE_SEARCH_STEPS -->"
        val found = searchableGuideHtml(html, GuideSearchRequest("ru.html", "α"))
        assertTrue(found.contains("1 из 2"))
        assertTrue(found.contains("match=1#guide-match-1"))
        assertTrue(found.contains("Вперед"))
        assertFalse(found.contains("<!-- GUIDE_SEARCH_"))
        assertTrue(searchableGuideHtml(html, GuideSearchRequest("ru.html", "\"<test>"))
            .contains("&quot;&lt;test&gt;"))
        assertTrue(searchableGuideHtml(html, GuideSearchRequest("en.html", "missing")).contains("No matches"))
    }
}
