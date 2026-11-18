package local.antenna.calculator.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import local.antenna.calculator.MainActivity
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsNavigationOrderTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun catalogsFollowProjectsAndScalingLeadsAdditionalTools() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
        compose.onNodeWithTag("nav-TOOLS").performClick()
        val tags = listOf("open-journal", "open-antenna-parameter-tools", "open-antenna-construction", "open-matching")
        val positions = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().top.value }
        assertTrue(positions.zipWithNext().all { (before, after) -> before < after })
        compose.onNodeWithTag("open-yagi-scaling").assertDoesNotExist()
        compose.onNodeWithTag("open-additional-tools").performScrollTo().performClick()
        val scaling = compose.onNodeWithTag("open-yagi-scaling")
        val segmentation = compose.onNodeWithTag("open-yagi-segmentation")
        assertTrue(scaling.getUnclippedBoundsInRoot().top < segmentation.getUnclippedBoundsInRoot().top)
        scaling.performClick()
        compose.onNodeWithTag("ys-example").assertExists()
        compose.onNodeWithTag("back").performClick()
        scaling.assertIsDisplayed()
        segmentation.assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-additional-tools").assertExists()
        } finally { scenario.close() }
    }
}
