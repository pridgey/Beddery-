package dev.pridgey.beddery

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.savers.SaverHost
import dev.pridgey.beddery.savers.Savers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screensaver for a few seconds of animation to catch runtime crashes.
 * Screenshots land in app/build/screenshots for a quick visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    @Test
    fun everySaverRendersInPortrait() = renderAll("portrait")

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun everySaverRendersInLandscape() = renderAll("landscape")

    private fun snapshot(name: String?) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        if (name == null) return
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun renderAll(tag: String) {
        var id by mutableStateOf(Savers.all.first().id)
        val settings = AppSettings(nightModeEnabled = false, burnInProtection = false)
        compose.mainClock.autoAdvance = false
        compose.setContent { SaverHost(settings, Modifier.fillMaxSize(), saverOverride = id) }
        for (saver in Savers.all) {
            id = saver.id
            compose.mainClock.advanceTimeBy(1_500)
            snapshot(null) // first draw lets canvas-sized savers lay themselves out
            compose.mainClock.advanceTimeBy(3_000)
            compose.onRoot().assertExists()
            snapshot("${tag}_${saver.id}")
        }
    }
}
