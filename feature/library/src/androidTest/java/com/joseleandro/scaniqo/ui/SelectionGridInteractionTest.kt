package com.joseleandro.scaniqo.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.domain.model.TagColor
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.ui.components.ScaniqoColorOption
import com.joseleandro.scaniqo.ui.components.ScaniqoIconOption
import com.joseleandro.scaniqo.ui.components.ScaniqoOptionGrid
import com.joseleandro.scaniqo.ui.components.color
import com.joseleandro.scaniqo.ui.components.drawableRes
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SelectionGridInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun incompleteRowsStayCenteredAndSelectionKeepsEveryTouchTargetStable() {
        val selectedIcon = mutableStateOf(0)
        val selectedColor = mutableStateOf(0)
        val enabled = mutableStateOf(true)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 400.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ScaniqoTheme(dynamicColor = false) {
                        Surface {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                ScaniqoOptionGrid(Modifier.testTag("icons")) {
                                    TagIcon.entries.take(7).forEachIndexed { index, icon ->
                                        ScaniqoIconOption(
                                            painter = painterResource(icon.drawableRes),
                                            label = icon.name,
                                            selected = selectedIcon.value == index,
                                            onClick = { selectedIcon.value = index },
                                            modifier = Modifier.testTag("icons_$index"),
                                            enabled = enabled.value,
                                        )
                                    }
                                }
                                ScaniqoOptionGrid(Modifier.testTag("colors")) {
                                    TagColor.entries.take(7).forEachIndexed { index, color ->
                                        ScaniqoColorOption(
                                            color = color.color,
                                            label = color.name,
                                            selected = selectedColor.value == index,
                                            onClick = { selectedColor.value = index },
                                            modifier = Modifier.testTag("colors_$index"),
                                            enabled = enabled.value,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (grid in listOf("icons", "colors")) {
            val boundsBefore = optionBounds(grid)
            val rows = boundsBefore.groupBy { it.top }.values
            assertTrue("The narrow grid must wrap", rows.size > 1)
            assertTrue("The final row must be incomplete", rows.last().size < rows.first().size)
            val gridCenter = compose.onNodeWithTag(grid).fetchSemanticsNode().boundsInRoot.center.x
            rows.forEach { row ->
                assertEquals(
                    "Every row, including the last, must be centered",
                    gridCenter,
                    (row.minOf { it.left } + row.maxOf { it.right }) / 2,
                    1f,
                )
                row.zipWithNext().forEach { (first, second) ->
                    assertFalse("Adjacent touch targets must not overlap", first.overlaps(second))
                }
            }

            compose.onNodeWithTag("${grid}_6").performClick().assertIsSelected()
            compose.onNodeWithTag("${grid}_0").assertIsNotSelected()
            assertEquals("Selecting must not resize or move any option", boundsBefore, optionBounds(grid))
        }

        compose.runOnIdle { enabled.value = false }
        for (grid in listOf("icons", "colors")) {
            compose.onNodeWithTag("${grid}_0").assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithTag("${grid}_6").assertIsSelected()
        }
        compose.runOnIdle {
            assertEquals(6, selectedIcon.value)
            assertEquals(6, selectedColor.value)
        }
    }

    private fun optionBounds(grid: String): List<Rect> = (0..6).map { index ->
        compose.onNodeWithTag("${grid}_$index")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .fetchSemanticsNode().boundsInRoot
    }
}
