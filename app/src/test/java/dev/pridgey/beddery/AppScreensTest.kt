package dev.pridgey.beddery

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.hasScrollToIndexAction

import androidx.compose.ui.test.performScrollToIndex
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

        // A refined screensaver links back to its original for comparison.
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToKey("breathe_refined")
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithText("REFINED").onFirst().assertExists()
        // Its title is the last "Breathe" (the original sits just before it).
        compose.onAllNodesWithText("Breathe").onLast().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Compare with the original").assertExists()
        snapshot("detail_refined")
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_500)

        compose.onAllNodesWithText("Settings").onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("How Beddery starts").assertExists()
        snapshot("settings")
        compose.onNodeWithText("Which should I use?").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Screensaver or Standby?").assertExists()
        snapshot("settings_compare")
        // The sheet lives in its own window, so capture that root directly.
        runCatching {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val sheet = (field.get(global) as List<android.view.View>).last()
            val bitmap = Bitmap.createBitmap(sheet.width, sheet.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            compose.runOnUiThread { sheet.draw(canvas) }
            File(outDir, "app_compare_sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }.onFailure { println("Couldn't capture sheet: $it") }
        // This test phone has no Android screensaver, so the sheet recommends Standby and can turn it on.
        compose.onAllNodesWithText("Use Standby").onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        waitFor { compose.onAllNodesWithText("Start when plugged in").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000)
        snapshot("settings_standby")
        // Settings items: 0 how it starts, 1 what's shown, 2 look, 3 while showing, 4 notifications, 5 night.
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(4)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Notification access").assertExists()
        snapshot("settings_notifications")
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(1)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Shuffle screensavers").performClick()
        waitFor { compose.onAllNodesWithText("Each time it starts").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Each time it starts").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        snapshot("settings_shuffle")
    }
}
