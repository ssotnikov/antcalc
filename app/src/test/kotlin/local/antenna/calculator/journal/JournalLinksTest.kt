package local.antenna.calculator.journal

import local.antenna.calculator.ui.LIZAALERT_YAGI_URL
import local.antenna.calculator.ui.validatedRecordLinkAddress
import org.junit.Assert.*
import org.junit.Test

class JournalLinksTest {
    @Test fun publishedAndUserLinksPreserveTheirExactTextRanges() {
        val second = "https://example.org/antenna_(VHF)?f=145#feed"
        val text = "Конструкция: $LIZAALERT_YAGI_URL\nСм. ($second)."
        val links = journalWebLinks(text)
        assertEquals(listOf(LIZAALERT_YAGI_URL, second), links.map { it.address })
        assertEquals(links.map { it.address }, links.map { text.substring(it.start, it.end) })
    }

    @Test fun sentencePunctuationDoesNotBecomePartOfTheAddress() {
        val text = "«https://example.org/a», затем https://example.org/b."
        assertEquals(listOf("https://example.org/a", "https://example.org/b"), journalWebLinks(text).map { it.address })
    }

    @Test fun unsafeOrMalformedLinksStayPlainText() {
        for (address in listOf("javascript:alert(1)", "file:///a", "intent://example.org", "http://example.org",
            "https://user:password@example.org/a", "https://example.org:8443/a", "https:///a",
            "https://example.org/\\bad", "https://example.org/" + "x".repeat(2050))) {
            assertNull(address.take(80), validatedRecordLinkAddress(address))
            assertTrue(journalWebLinks(address).isEmpty())
        }
    }

    @Test fun unicodePathIsEncodedOnlyForNavigation() {
        val text = "https://example.org/антенна"
        val link = journalWebLinks(text).single()
        assertEquals(text, text.substring(link.start, link.end))
        assertTrue(link.address.contains("%D0"))
    }
}
