package local.antenna.calculator.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.CalculatorApplication
import local.antenna.calculator.MainActivity
import local.antenna.calculator.R
import org.junit.Assert.assertEquals
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
        val extraPoints = (147..168).joinToString(separator = "") { "\n$it 0.2 0.1" }
        val uris = ArrayDeque(listOf(
            sample(before, "# MHz S RI R 50\n144 0.2 0.1\n145 0.1 0\n146 0.2 0.1" + extraPoints),
            sample(after, "# MHz S RI R 50\n144 0.2 0.1\n145 0.05 0\n146 0.2 0.1" + extraPoints),
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

            val chart = compose.onNodeWithTag("sweep-chart").performScrollTo()
            fun assertChartFrequency(mhz: String) = chart.assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, compose.activity.getString(R.string.sweep_mhz, mhz),
            ))
            assertChartFrequency("145")
            chart.performTouchInput { swipe(center, Offset(right, centerY)) }
            assertChartFrequency("168")
            chart.performScrollTo().performTouchInput { swipe(center, Offset(centerX, bottom)) }
            assertChartFrequency("168")
            chart.performScrollTo().performTouchInput {
                val start = 52.dp.toPx()
                val plotWidth = width - start - 12.dp.toPx()
                click(Offset(start + plotWidth / 24f, centerY))
            }
            assertChartFrequency("145")

            compose.onNodeWithTag("sweep-table-toggle").performScrollTo().performClick()
            val middleRow = compose.onNodeWithTag("sweep-table-row-1")
            middleRow.assertIsSelected()
            val decimal = java.text.NumberFormat.getNumberInstance(compose.activity.resources.configuration.locales[0])
            fun swr(value: Double) = compose.onNode(
                hasText(decimal.format(value)) and hasAnyAncestor(hasTestTag("sweep-table-row-1")),
                useUnmergedTree = true,
            )
            swr(1.22).assertExists()
            fun assertPage(number: Int) =
                compose.onNodeWithText(compose.activity.getString(R.string.sweep_table_page, number, 3)).assertExists()
            fun turnPage(label: Int) = compose.onNodeWithText(compose.activity.getString(label)).performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            fun switchTrace(slot: Int) = compose.onNodeWithTag("sweep-table-trace-$slot").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick).assertIsSelected()

            assertPage(1)
            repeat(2) { turnPage(R.string.sweep_next) }
            assertPage(3)
            switchTrace(1)
            assertPage(1)
            turnPage(R.string.sweep_next)
            assertPage(2)
            switchTrace(0)
            assertPage(3)
            switchTrace(1)
            assertPage(2)
            turnPage(R.string.sweep_previous)
            assertPage(1)
            swr(1.11).assertExists()
            compose.onNodeWithTag("sweep-copy-frequency").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(15_000) { compose.activity.hasWindowFocus() }
            compose.runOnIdle {
                val activity = compose.activity
                val clipboard = checkNotNull(activity.getSystemService(ClipboardManager::class.java))
                val blocks = checkNotNull(clipboard.primaryClip).getItemAt(0).text.toString().split("\n\n")
                assertEquals(2, blocks.size)
                listOf(before to 0.1, after to 0.05).forEachIndexed { index, (fileName, magnitude) ->
                    val lines = blocks[index].lines()
                    assertEquals(8, lines.size)
                    assertEquals(activity.getString(if (index == 0) R.string.sweep_trace_a else R.string.sweep_trace_b) + " · " + fileName, lines[0])
                    assertEquals(activity.getString(R.string.sweep_mhz, "145"), lines[1])
                    assertEquals("|S11| · ${decimal.format(magnitude)}", lines[3])
                }
            }
            compose.onNodeWithTag("sweep-table").performScrollTo()
            compose.onNodeWithTag("sweep-table-row-0").assertIsDisplayed().performClick().assertIsSelected()

            val projectTitle = "S1P $suffix"
            compose.onNodeWithTag("sweep-save-project").performScrollTo().performClick()
            waitFor("project-transfer-project-picker")
            compose.activityRule.scenario.recreate()
            waitFor("project-transfer-project-picker")
            compose.onNodeWithTag("project-transfer-create").performClick()
            compose.onNodeWithTag("journal-name").performTextInput(projectTitle)
            compose.onNodeWithTag("journal-save").performScrollTo().performClick()
            waitFor("measurement-screen")
            compose.onNodeWithTag("sweep-save-project").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("project-transfer-project-picker")
            compose.onNodeWithTag("project-transfer-project-search").performTextReplacement(projectTitle)
            val projectRow = hasTestTag("project-transfer-project") and hasText(projectTitle)
            compose.onNodeWithTag("project-transfer-project-list").performScrollToNode(projectRow)
            compose.onNode(projectRow).performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithTag("project-transfer-confirm-project").assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick)
            waitFor("measurement-screen")
            val application = compose.activity.application as CalculatorApplication
            val project = runBlocking { application.projectsRepository.read().single { it.title == projectTitle } }
            assertEquals(listOf(before, after), project.sweeps.single().traces.map { it.name })
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
