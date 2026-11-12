package local.antenna.calculator.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import local.antenna.calculator.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun toolNavigationReturnsThroughAdditionalTools() {
        openMeasurements()
        compose.onNodeWithTag("nav-TOOLS").assertIsSelected()
        compose.activityRule.scenario.recreate()
        waitFor("measurement-screen")
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-measurements").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("open-additional-tools").assertExists()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test fun importsTwoTracesAndFailedReplacementKeepsPreviouslyVerifiedTrace() {
        val resolver = compose.activity.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val documents = mutableListOf<Uri>()
        fun sample(name: String, data: String): Uri {
            val uri = checkNotNull(resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            }))
            documents += uri
            checkNotNull(resolver.openOutputStream(uri)).use { it.write(data.toByteArray()) }
            return uri
        }
        val suffix = UUID.randomUUID().toString().take(8)
        val before = "before-$suffix.s1p"
        val after = "after-$suffix.s1p"
        val uris = ArrayDeque(listOf(
            sample(before, "# MHz S RI R 50\n144 0.2 0.1\n145 0.1 0\n146 0.2 0.1"),
            sample(after, "# MHz S RI R 50\n144 0.2 0.1\n145 0.05 0\n146 0.2 0.1"),
            sample("invalid-$suffix.s1p", "# MHz S RI R 50\n144 0.2 0.1\n145 NaN 0"),
        ))
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uris.removeFirst()))
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.addMonitor(monitor)
        try {
            openMeasurements()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("open-sweep-0").fetchSemanticsNodes().isNotEmpty() &&
                compose.onNodeWithTag("open-sweep-0").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
            compose.onNodeWithTag("open-sweep-0").performScrollTo().assertIsEnabled().performClick()
            waitForText(before)
            compose.onNodeWithTag("open-sweep-1").performScrollTo().assertIsEnabled().performClick()
            waitForText(after)
            compose.activityRule.scenario.recreate()
            waitForText(before)
            waitForText(after)
            compose.onNodeWithTag("open-sweep-0").performScrollTo().assertIsEnabled().performClick()
            waitFor("sweep-error")
            compose.onNodeWithText(before).assertExists()
            compose.onNodeWithText(after).assertExists()
        } finally {
            instrumentation.removeMonitor(monitor)
            documents.forEach { resolver.delete(it, null, null) }
        }
    }

    private fun openMeasurements() {
        waitFor("nav-TOOLS")
        compose.onNodeWithTag("nav-TOOLS").performClick()
        compose.onNodeWithTag("open-additional-tools").performScrollTo().performClick()
        compose.onNodeWithTag("open-measurements").performClick()
        waitFor("measurement-screen")
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
