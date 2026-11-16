package io.github.jlai403.lapcraft.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jlai403.lapcraft.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsTheLibrary() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Library", substring = true))
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(androidx.compose.ui.test.hasTestTag("track-map"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
