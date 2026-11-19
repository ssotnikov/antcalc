package local.antenna.calculator.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import local.antenna.calculator.state.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class UserGuideLanguageTest : CalculatorUiFixture() {
    @Test fun returningFromSettingsLoadsTheGuideInTheNewInterfaceLanguage() {
        start()
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-user-guide").performClick()
        assertDocument("Приложение · Руководство пользователя")

        switchLanguage(AppLanguage.ENGLISH)
        assertDocument("The app · User Guide")

        switchLanguage(AppLanguage.RUSSIAN)
        assertDocument("Приложение · Руководство пользователя")
    }

    private fun switchLanguage(language: AppLanguage) {
        settings()
        compose.onNodeWithTag("language-${language.name}").performScrollTo().performClick()
        back()
    }

    private fun assertDocument(expected: String) {
        var title: String? = null
        compose.waitUntil(10_000) {
            compose.runOnUiThread { title = compose.activity.window.decorView.findWebView()?.title }
            title == expected
        }
        assertEquals(expected, title)
    }

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
        else -> null
    }
}
