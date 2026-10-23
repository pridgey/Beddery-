package dev.pridgey.beddery.savers

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.impl.AgendaSaver
import dev.pridgey.beddery.savers.impl.AnalogClockSaver
import dev.pridgey.beddery.savers.impl.AuroraSaver
import dev.pridgey.beddery.savers.impl.BinaryClockSaver
import dev.pridgey.beddery.savers.impl.BouncingClockSaver
import dev.pridgey.beddery.savers.impl.BreatheSaver
import dev.pridgey.beddery.savers.impl.ChargingSaver
import dev.pridgey.beddery.savers.impl.ConstellationSaver
import dev.pridgey.beddery.savers.impl.FirefliesSaver
import dev.pridgey.beddery.savers.impl.FlipClockSaver
import dev.pridgey.beddery.savers.impl.GameOfLifeSaver
import dev.pridgey.beddery.savers.impl.LavaLampSaver
import dev.pridgey.beddery.savers.impl.MatrixSaver
import dev.pridgey.beddery.savers.impl.MinimalClockSaver
import dev.pridgey.beddery.savers.impl.NightlightSaver
import dev.pridgey.beddery.savers.impl.OceanSaver
import dev.pridgey.beddery.savers.impl.OrbitsSaver
import dev.pridgey.beddery.savers.impl.PhotoFrameSaver
import dev.pridgey.beddery.savers.impl.StarfieldSaver
import dev.pridgey.beddery.savers.impl.WeatherSaver
import dev.pridgey.beddery.savers.impl.WordClockSaver
import dev.pridgey.beddery.savers.impl.WorldClockSaver

enum class SaverCategory(@StringRes val label: Int) {
    CLOCKS(R.string.category_clocks),
    INFO(R.string.category_info),
    RELAX(R.string.category_relax),
    AMBIENT(R.string.category_ambient),
}

@Immutable
class Saver(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val category: SaverCategory,
    val icon: ImageVector,
    /** Whether the saver shows the time itself. If not, the host can add a small clock on top. */
    val showsTime: Boolean,
    /** Has extra options on its detail page. */
    val configurable: Boolean = false,
    val content: @Composable (SaverContext) -> Unit,
)

object Savers {
    val all: List<Saver> = listOf(
        // Clocks
        Saver("minimal", R.string.saver_minimal, R.string.saver_minimal_desc, SaverCategory.CLOCKS, Icons.Rounded.Schedule, true) { MinimalClockSaver(it) },
        Saver("analog", R.string.saver_analog, R.string.saver_analog_desc, SaverCategory.CLOCKS, Icons.Rounded.Timer, true) { AnalogClockSaver(it) },
        Saver("flip", R.string.saver_flip, R.string.saver_flip_desc, SaverCategory.CLOCKS, Icons.Rounded.Tv, true) { FlipClockSaver(it) },
        Saver("word", R.string.saver_word, R.string.saver_word_desc, SaverCategory.CLOCKS, Icons.Rounded.TextFields, true) { WordClockSaver(it) },
        Saver("nightlight", R.string.saver_nightlight, R.string.saver_nightlight_desc, SaverCategory.CLOCKS, Icons.Rounded.Bedtime, true) { NightlightSaver(it) },
        Saver("binary", R.string.saver_binary, R.string.saver_binary_desc, SaverCategory.CLOCKS, Icons.Rounded.Memory, true) { BinaryClockSaver(it) },
        Saver("bounce", R.string.saver_bounce, R.string.saver_bounce_desc, SaverCategory.CLOCKS, Icons.AutoMirrored.Rounded.ShowChart, true) { BouncingClockSaver(it) },

        // Info
        Saver("charging", R.string.saver_charging, R.string.saver_charging_desc, SaverCategory.INFO, Icons.Rounded.BatteryChargingFull, true) { ChargingSaver(it) },
        Saver("weather", R.string.saver_weather, R.string.saver_weather_desc, SaverCategory.INFO, Icons.Rounded.WbCloudy, true, configurable = true) { WeatherSaver(it) },
        Saver("agenda", R.string.saver_agenda, R.string.saver_agenda_desc, SaverCategory.INFO, Icons.Rounded.CalendarMonth, true, configurable = true) { AgendaSaver(it) },
        Saver("world", R.string.saver_world, R.string.saver_world_desc, SaverCategory.INFO, Icons.Rounded.Public, true, configurable = true) { WorldClockSaver(it) },
        Saver("photos", R.string.saver_photos, R.string.saver_photos_desc, SaverCategory.INFO, Icons.Rounded.Photo, true, configurable = true) { PhotoFrameSaver(it) },

        // Relax
        Saver("breathe", R.string.saver_breathe, R.string.saver_breathe_desc, SaverCategory.RELAX, Icons.Rounded.Air, false, configurable = true) { BreatheSaver(it) },
        Saver("ocean", R.string.saver_ocean, R.string.saver_ocean_desc, SaverCategory.RELAX, Icons.Rounded.Waves, false) { OceanSaver(it) },
        Saver("aurora", R.string.saver_aurora, R.string.saver_aurora_desc, SaverCategory.RELAX, Icons.Rounded.WbTwilight, false) { AuroraSaver(it) },
        Saver("lava", R.string.saver_lava, R.string.saver_lava_desc, SaverCategory.RELAX, Icons.Rounded.BubbleChart, false) { LavaLampSaver(it) },
        Saver("fireflies", R.string.saver_fireflies, R.string.saver_fireflies_desc, SaverCategory.RELAX, Icons.Rounded.AutoAwesome, false) { FirefliesSaver(it) },

        // Ambient
        Saver("starfield", R.string.saver_starfield, R.string.saver_starfield_desc, SaverCategory.AMBIENT, Icons.Rounded.Star, false) { StarfieldSaver(it) },
        Saver("orbits", R.string.saver_orbits, R.string.saver_orbits_desc, SaverCategory.AMBIENT, Icons.Rounded.Language, false) { OrbitsSaver(it) },
        Saver("constellation", R.string.saver_constellation, R.string.saver_constellation_desc, SaverCategory.AMBIENT, Icons.Rounded.Hub, false) { ConstellationSaver(it) },
        Saver("matrix", R.string.saver_matrix, R.string.saver_matrix_desc, SaverCategory.AMBIENT, Icons.Rounded.Code, false) { MatrixSaver(it) },
        Saver("life", R.string.saver_life, R.string.saver_life_desc, SaverCategory.AMBIENT, Icons.Rounded.GridOn, false) { GameOfLifeSaver(it) },
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String?): Saver = byId[id] ?: byId.getValue("minimal")

    fun findOrNull(id: String?): Saver? = byId[id]
}
