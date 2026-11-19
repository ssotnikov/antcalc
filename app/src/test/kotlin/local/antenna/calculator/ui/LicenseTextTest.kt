package local.antenna.calculator.ui

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LicenseTextTest {
    @Test fun bundledNoticePreservesLegalTextAndResolvesBothMitLinks() {
        val file = sequenceOf(File("src/main/res/raw/opennec_license.txt"), File("app/src/main/res/raw/opennec_license.txt"))
            .first { it.isFile }
        val source = file.readText()
        val parts = licenseTextParts(source)
        val visible = parts.joinToString("") { it.text }
        assertEquals(listOf("https://opensource.org/licenses/MIT", "https://opensource.org/license/mit"),
            parts.mapNotNull { it.address })
        assertFalse(visible.contains("img.shields.io"))
        assertFalse(visible.contains("**"))
        assertTrue(parts.first().bold)
        val legalStart = source.indexOf("This software is released")
        val legalEnd = source.indexOf("https://opensource.org/license/mit")
        assertTrue(visible.contains(source.substring(legalStart, legalEnd)))
        assertTrue(visible.endsWith(source.substring(source.indexOf("Original NEC-2 source licence:"))))
    }

    @Test fun unknownHostsAndLookalikesRemainNonInteractive() {
        val parts = licenseTextParts("[Unknown](https://example.org/license) https://opensource.org.evil.test/license/mit https://opensource.org/other")
        assertTrue(parts.none { it.address != null })
        assertTrue(parts.joinToString("") { it.text }.startsWith("Unknown "))
        assertNull(validatedSourceAddress("http://opensource.org/license/mit"))
        assertNull(validatedSourceAddress("https://opensource.org@evil.test/license/mit"))
    }

    @Test fun linksDoNotSwallowSurroundingPunctuation() {
        val parts = licenseTextParts("MIT (https://opensource.org/license/mit).")
        assertEquals("MIT (https://opensource.org/license/mit).", parts.joinToString("") { it.text })
        assertEquals(listOf("https://opensource.org/license/mit"), parts.mapNotNull { it.address })
    }
}
