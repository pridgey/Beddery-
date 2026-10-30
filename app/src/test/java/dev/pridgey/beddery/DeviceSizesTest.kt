package dev.pridgey.beddery

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the gallery and settings on the screen sizes most people have: a budget phone
 * (the Galaxy A series is 360dp wide), the same phone with large text, and a tablet.
 * The PNGs land in build/screenshots/ for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DeviceSizesTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun snapshot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(outDir, "size_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    /** The activity is already open by now, so restart it to pick up the new text size. */
    private fun largeText(scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        compose.activityRule.scenario.recreate()
    }

    private fun walk(prefix: String) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        snapshot("${prefix}_gallery")
        compose.onAllNodesWithText("Settings").onFirst().performClick()
        compose.mainClock.advanceTimeBy(1_500)
        snapshot("${prefix}_settings")
        // Settings items: 0 how it starts, 1 what's shown, 2 look, 3 while showing, 4 notifications, 5 night.
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(2)
        snapshot("${prefix}_settings_look")
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(5)
        snapshot("${prefix}_settings_night")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun budgetPhone() = walk("phone360")

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun budgetPhoneLargeText() {
        largeText(1.3f)
        walk("phone360_text130")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun budgetPhoneLargestText() {
        largeText(2.0f)
        walk("phone360_text200")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet() = walk("tablet800")
}
