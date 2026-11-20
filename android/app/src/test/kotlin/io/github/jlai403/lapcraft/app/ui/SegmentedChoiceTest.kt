package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.events.EventKind
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedChoiceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `segments share the row width evenly`() {
        compose.setContent {
            LapCraftTheme {
                Box(Modifier.requiredWidth(360.dp)) {
                    SegmentedChoice(
                        options = listOf("All") + EventKind.entries.map { it.label },
                        selectedIndex = 0,
                        onSelect = {},
                    )
                }
            }
        }

        val widths = listOf("All", "Daily", "Time Trial", "World Series").map { label ->
            compose.onAllNodesWithText(label).fetchSemanticsNodes().first().boundsInRoot.width
        }
        widths.forEach { width ->
            assertTrue("widths=$widths", abs(width - widths.first()) < 0.5f)
        }
    }
}
