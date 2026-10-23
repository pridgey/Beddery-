package dev.pridgey.beddery

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.hasScrollToIndexAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Walks through the main screens to make sure they render and navigate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xhdpi")
class AppScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun snapshot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(outDir, "app_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(50) {
            if (condition()) return
            Thread.sleep(100)
            compose.mainClock.advanceTimeBy(100)
        }
        error("Condition not met")
    }

    @Test
    fun galleryDetailAndSettings() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Put Beddery on your nightstand").assertExists()
        snapshot("gallery")

        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToKey("flip")
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithText("Flip Clock").onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Use this screensaver").assertExists()
        snapshot("detail_flip")
        compose.onNodeWithText("Use this screensaver").performClick()
        // Settings are saved on a background thread; give it real time to land.
        waitFor { compose.onAllNodesWithText("Current").fetchSemanticsNodes().isNotEmpty() }
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_500)

        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToKey("weather")
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithText("Weather").onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        snapshot("detail_weather")
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_500)

        compose.onAllNodesWithText("Settings").onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("How Beddery starts").assertExists()
        snapshot("settings")
        compose.onNodeWithText("Standby mode").performClick()
        waitFor { compose.onAllNodesWithText("Start when plugged in").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000)
        snapshot("settings_standby")
    }
}
