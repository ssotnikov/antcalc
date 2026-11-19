package local.antenna.calculator.ui

import android.os.Bundle
import android.graphics.Bitmap
import java.io.File
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.AppTheme
import local.antenna.calculator.state.CalculatorDraft
import org.junit.Assert.*
import org.junit.Test

class GuideSearchAcceptanceTest : CalculatorUiFixture() {
    @Test fun russianSearchHighlightsNavigatesAndClearsWithoutScripts() = exercise(true)
    @Test fun englishSearchInLightTheme() = exercise(false)

    @Test fun russianSearchInLightTheme() = exercise(true, AppTheme.LIGHT)
    @Test fun englishSearchInDarkTheme() = exercise(false, AppTheme.DARK)

    private fun exercise(russian: Boolean, theme: AppTheme = if (russian) AppTheme.DARK else AppTheme.LIGHT) {
        // Connect accessibility before WebView creates its virtual node tree on first load.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) { automation.rootInActiveWindow != null }
        start(CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(
            language = if (russian) AppLanguage.RUSSIAN else AppLanguage.ENGLISH,
            theme = theme,
        )) })
        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("open-user-guide").performClick()
        val input = if (russian) "Поиск по руководству" else "Search the guide"
        val find = if (russian) "Найти" else "Find"
        val next = if (russian) "Вперед" else "Next"
        val clear = if (russian) "Очистить поиск" else "Clear search"
        val query = if (russian) "КСВ" else "SWR"
        waitNode { it.className == "android.widget.EditText" }
        compose.runOnUiThread {
            val view = requireNotNull(compose.activity.window.decorView.guideWebView())
            assertFalse(view.settings.javaScriptEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertTrue(view.settings.blockNetworkLoads)
        }
        capture("${if (russian) "ru" else "en"}-${theme.name.lowercase()}-initial")
        fun enter(value: String) {
            val field = waitNode { it.className == "android.widget.EditText" }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }))
            assertTrue(waitNode { it.text?.toString() == find }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        enter(query)
        waitNode { it.text?.toString()?.matches(Regex(if (russian) "1 из [2-9][0-9]*|1 из 1[0-9]+" else "1 of [2-9][0-9]*|1 of 1[0-9]+")) == true }
        capture("${if (russian) "ru" else "en"}-${theme.name.lowercase()}-matched")
        assertTrue(waitNode { it.text?.toString() == next }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitNode { it.text?.toString()?.startsWith(if (russian) "2 из " else "2 of ") == true }
        enter("zzzz-not-found")
        waitNode { it.text?.toString() == if (russian) "Совпадений нет" else "No matches" }
        assertTrue(waitNode { it.contentDescription?.toString() == clear || it.text?.toString() == clear }
            .performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitNode { it.className == "android.widget.EditText" && (it.text.isNullOrEmpty() || it.text?.toString() == input) }
    }

    private fun capture(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "guide-search-shots").apply { mkdirs() }
        var ready = false
        compose.runOnUiThread {
            val view = requireNotNull(compose.activity.window.decorView.guideWebView())
            view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { view.postOnAnimation { ready = true } }
            })
        }
        compose.waitUntil(10_000) { ready }
        File(folder, "$name.png").outputStream().use {
            requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        compose.waitUntil(30_000) {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (predicate(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
                return null
            }
            result = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let(::find)
            result != null
        }
        return requireNotNull(result)
    }

    private fun View.guideWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).guideWebView() }
        else -> null
    }
}
