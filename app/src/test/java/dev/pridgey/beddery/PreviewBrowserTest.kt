package dev.pridgey.beddery

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.pridgey.beddery.standby.StandbyActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Full-screen preview: swiping moves to the next screensaver, and the controls follow along. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class PreviewBrowserTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun swipeBrowsesToTheRefinedVersion() {
        val intent = StandbyActivity.intent(ApplicationProvider.getApplicationContext(), preview = true, saverId = "breathe")
        ActivityScenario.launch<StandbyActivity>(intent).use { scenario ->
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(2_000)
            compose.onNodeWithText("Breathe").assertExists()
            compose.onNodeWithText("Swipe to browse").assertExists()
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeBy(1_500)
            compose.onAllNodesWithText("REFINED").onFirst().assertExists()
            scenario.onActivity { activity ->
                val view = activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File("build/screenshots").mkdirs()
                File("build/screenshots/app_preview_browser.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
            }
        }
    }
}
