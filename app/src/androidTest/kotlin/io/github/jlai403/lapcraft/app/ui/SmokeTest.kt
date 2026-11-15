package io.github.jlai403.lapcraft.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jlai403.lapcraft.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only black-box smoke test. Device-gated: requires an emulator or physical device.
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 *
 * Seeded flows (tag picker, real replay seeks) belong here once a device is available.
 * Seed files via ApplicationProvider.getApplicationContext<Context>().filesDir; the Robolectric
 * SessionFixtures cannot be reused from androidTest without a shared test module.
 */
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
