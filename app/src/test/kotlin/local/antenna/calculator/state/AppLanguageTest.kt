package local.antenna.calculator.state

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun russianSystemLanguageSelectsRussian() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.forSystemLanguage("ru"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.forSystemLanguage("RU"))
    }

    @Test fun allOtherSystemLanguagesSelectEnglish() {
        for (language in listOf("en", "de", "uk", "be", "zh", "ar", "", "und")) {
            assertEquals(language, AppLanguage.ENGLISH, AppLanguage.forSystemLanguage(language))
        }
    }
}
