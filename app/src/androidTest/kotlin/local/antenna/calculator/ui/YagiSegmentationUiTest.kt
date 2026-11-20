package local.antenna.calculator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.nec.OpenNec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YagiSegmentationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun exampleAndManualInput() {
        compose.setContent { MaterialTheme { YagiSegmentationScreen(null) } }
        click("seg-example")
        compose.onNodeWithTag("seg-result").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.seg_feed_caption, 6, 1))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("seg-diameter-0").performScrollTo().performTextReplacement("60")
        compose.onNodeWithText(compose.activity.getString(R.string.seg_invalid_step, 1))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun legendAndGroundConditionsExplainTheDiagrams() {
        compose.setContent { MaterialTheme { YagiSegmentationScreen(null) } }
        click("seg-example")
        fun text(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
        text(R.string.seg_key_feed).performScrollTo().assertIsDisplayed()
        text(R.string.seg_key_axes).performScrollTo().assertIsDisplayed()
        text(R.string.seg_table_key).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(compose.activity.getString(R.string.seg_help), substring = true)
            .performScrollTo().performClick()
        text(R.string.seg_ground_free_hint).performScrollTo().assertIsDisplayed()
        text(R.string.seg_ground_perfect_hint).performScrollTo().assertIsDisplayed()
        text(R.string.seg_ground_real_hint).performScrollTo().assertIsDisplayed()
        text(R.string.seg_ground_height_hint).performScrollTo().assertIsDisplayed()
        text(R.string.seg_ground_table_key).assertDoesNotExist()
        click("seg-ground-example")
        text(R.string.seg_ground_table_key).performScrollTo().assertIsDisplayed()
        click("seg-ground-example")
        text(R.string.seg_ground_table_key).assertDoesNotExist()
    }

    @Test fun conflictingMeshDirectionsAreReported() {
        val base = listOf(Element(0.0, 1.010, .010), Element(.210, .920, .010))
        val candidate = base.toMutableList().apply { this[1] = this[1].copy(length = .925) }
        val coarse = requireNotNull(OpenNec.segmentCounts(candidate, 146_450_000))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberPageScrollState())) {
                    YagiNecPanel(base, candidate, 145_000_000, null, null,
                        sweep = { geometry, counts, grid ->
                            val peak = when {
                                geometry == base -> 1.5
                                counts == coarse -> 1.3
                                else -> 1.6
                            }
                            grid.mapIndexed { index, hz ->
                                val sampledPeak = if (geometry == base) grid.lastIndex else 0
                                OpenNec.Point(hz, 30.0, 0.0, if (index == sampledPeak) peak else 1.1, List(73) { 3.0 })
                            }
                        }, onApply = {})
                }
            }
        }
        click("opennec-expand")
        click("opennec-run")
        click("opennec-direction")
        compose.onNodeWithText(compose.activity.getString(R.string.opennec_direction_indeterminate))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("opennec-low").performScrollTo().performTextReplacement("143")
        compose.onNodeWithText(compose.activity.getString(R.string.opennec_direction_indeterminate)).assertDoesNotExist()
    }

    @Test fun meshComparisonUsesTheSameBandAndIsInvalidatedOnEdit() {
        val geometry = listOf(Element(0.0, 1.010, .010), Element(.210, .920, .010))
        val calls = mutableListOf<Pair<List<Int>, List<Long>>>()
        var transferred: ArrayList<String>? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberPageScrollState())) {
                    YagiNecPanel(null, geometry, 145_000_000, null, null, sweep = { _, segments, grid ->
                        calls.add(segments to grid)
                        grid.map { OpenNec.Point(it, 30.0, segments.first().toDouble(), 1.3, List(73) { 3.0 }) }
                    }, onSegmentation = { transferred = it }, onApply = {})
                }
            }
        }
        click("opennec-expand")
        click("opennec-mesh-expand")
        click("opennec-open-segmentation")
        compose.runOnIdle { assertEquals(segmentationSeed(geometry, 143_550_000, 146_450_000, 1), transferred) }
        click("opennec-mesh-compare")
        compose.onNodeWithTag("opennec-mesh-result").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(11, 11), calls[0].first)
            assertEquals(listOf(21, 21), calls[1].first)
            assertEquals(calls[0].second, calls[1].second)
        }
        compose.onNodeWithTag("opennec-high").performScrollTo().performTextReplacement("146")
        compose.onNodeWithTag("opennec-mesh-result").assertDoesNotExist()
    }

    @Test fun physicalGapKeepsNecActionsUnavailable() {
        compose.setContent {
            MaterialTheme {
                YagiScalingScreen(initial = null, onSave = null, necAvailable = { true }, onCopied = {})
            }
        }
        click("ys-example")
        compose.onNodeWithTag("opennec-expand").assertExists()
        compose.onNodeWithTag("ys-gap").performScrollTo().performTextReplacement("40")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText(compose.activity.getString(R.string.opennec_split)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("opennec-expand").assertDoesNotExist()
        compose.onNodeWithTag("ys-copy").performScrollTo().assertIsEnabled()
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
}
