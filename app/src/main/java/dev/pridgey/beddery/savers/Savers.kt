package dev.pridgey.beddery.savers

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScubaDiving
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tornado
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.impl.AgendaSaver
import dev.pridgey.beddery.savers.impl.RidgelinesSaver
import dev.pridgey.beddery.savers.impl.ContoursSaver
import dev.pridgey.beddery.savers.impl.OrbSaver
import dev.pridgey.beddery.savers.impl.CountDownSaver
import dev.pridgey.beddery.savers.impl.WindDownSaver
import dev.pridgey.beddery.savers.impl.TideSaver
import dev.pridgey.beddery.savers.impl.BoxBreathSaver
import dev.pridgey.beddery.savers.impl.SleepCoachSaver
import dev.pridgey.beddery.savers.impl.PowerCellsSaver
import dev.pridgey.beddery.savers.impl.ChargeGardenSaver
import dev.pridgey.beddery.savers.impl.PowerFlowSaver
import dev.pridgey.beddery.savers.impl.BigFillSaver
import dev.pridgey.beddery.savers.impl.ChargeVialSaver
import dev.pridgey.beddery.savers.impl.AnalogClockSaver
import dev.pridgey.beddery.savers.impl.AuroraRefinedSaver
import dev.pridgey.beddery.savers.impl.BedsideClassicSaver
import dev.pridgey.beddery.savers.impl.BigTypeSaver
import dev.pridgey.beddery.savers.impl.BinaryClockSaver
import dev.pridgey.beddery.savers.impl.BouncingClockSaver
import dev.pridgey.beddery.savers.impl.BreatheRefinedSaver
import dev.pridgey.beddery.savers.impl.BreatheSaver
import dev.pridgey.beddery.savers.impl.CampfireSaver
import dev.pridgey.beddery.savers.impl.CandleSaver
import dev.pridgey.beddery.savers.impl.ChargingSaver
import dev.pridgey.beddery.savers.impl.ColorFieldSaver
import dev.pridgey.beddery.savers.impl.ConstellationRefinedSaver
import dev.pridgey.beddery.savers.impl.ConstellationSaver
import dev.pridgey.beddery.savers.impl.DayArcSaver
import dev.pridgey.beddery.savers.impl.FirefliesSaver
import dev.pridgey.beddery.savers.impl.FlipClockSaver
import dev.pridgey.beddery.savers.impl.FlowFieldSaver
import dev.pridgey.beddery.savers.impl.GameOfLifeSaver
import dev.pridgey.beddery.savers.impl.HarmonographSaver
import dev.pridgey.beddery.savers.impl.JellyfishSaver
import dev.pridgey.beddery.savers.impl.KoiPondSaver
import dev.pridgey.beddery.savers.impl.LavaLampSaver
import dev.pridgey.beddery.savers.impl.MatrixSaver
import dev.pridgey.beddery.savers.impl.MinimalClockSaver
import dev.pridgey.beddery.savers.impl.MonthSaver
import dev.pridgey.beddery.savers.impl.MoonPhaseSaver
import dev.pridgey.beddery.savers.impl.NightlightSaver
import dev.pridgey.beddery.savers.impl.NixieSaver
import dev.pridgey.beddery.savers.impl.NotificationsSaver
import dev.pridgey.beddery.savers.impl.OceanRefinedSaver
import dev.pridgey.beddery.savers.impl.OdometerSaver
import dev.pridgey.beddery.savers.impl.OrbitsRefinedSaver
import dev.pridgey.beddery.savers.impl.PendulumWaveSaver
import dev.pridgey.beddery.savers.impl.PhotoFrameSaver
import dev.pridgey.beddery.savers.impl.ProgressSaver
import dev.pridgey.beddery.savers.impl.RainGlassSaver
import dev.pridgey.beddery.savers.impl.RingsClockSaver
import dev.pridgey.beddery.savers.impl.SleepSaver
import dev.pridgey.beddery.savers.impl.SnowfallSaver
import dev.pridgey.beddery.savers.impl.StarfieldRefinedSaver
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
    /** The id of the original this is a polished version of. */
    val variantOf: String? = null,
    /** Has room for the notification icons at the top. Scenic savers show them under their small clock instead. */
    val showsNotifications: Boolean = false,
    val content: @Composable (SaverContext) -> Unit,
) {
    val refined: Boolean get() = variantOf != null

    /** Every saver that can show notifications, either at the top or under the small clock overlay. */
    val supportsNotifications: Boolean get() = showsNotifications || !showsTime
}

object Savers {
    private val C = SaverCategory.CLOCKS
    private val I = SaverCategory.INFO
    private val R_ = SaverCategory.RELAX
    private val A = SaverCategory.AMBIENT

    val all: List<Saver> = listOf(
        // Clocks
        Saver("minimal", R.string.saver_minimal, R.string.saver_minimal_desc, C, Icons.Rounded.Schedule, true, showsNotifications = true) { MinimalClockSaver(it) },
        Saver("analog", R.string.saver_analog, R.string.saver_analog_desc, C, Icons.Rounded.Timer, true, showsNotifications = true) { AnalogClockSaver(it) },
        Saver("flip", R.string.saver_flip, R.string.saver_flip_desc, C, Icons.Rounded.Tv, true, showsNotifications = true) { FlipClockSaver(it) },
        Saver("classic", R.string.saver_classic, R.string.saver_classic_desc, C, Icons.Rounded.Alarm, true) { BedsideClassicSaver(it) },
        Saver("nixie", R.string.saver_nixie, R.string.saver_nixie_desc, C, Icons.Rounded.Lightbulb, true, showsNotifications = true) { NixieSaver(it) },
        Saver("odometer", R.string.saver_odometer, R.string.saver_odometer_desc, C, Icons.Rounded.Speed, true, showsNotifications = true) { OdometerSaver(it) },
        Saver("bigtype", R.string.saver_bigtype, R.string.saver_bigtype_desc, C, Icons.Rounded.FormatSize, true, showsNotifications = true) { BigTypeSaver(it) },
        Saver("rings", R.string.saver_rings, R.string.saver_rings_desc, C, Icons.Rounded.DonutLarge, true) { RingsClockSaver(it) },
        Saver("dayarc", R.string.saver_dayarc, R.string.saver_dayarc_desc, C, Icons.Rounded.WbSunny, true) { DayArcSaver(it) },
        Saver("word", R.string.saver_word, R.string.saver_word_desc, C, Icons.Rounded.TextFields, true) { WordClockSaver(it) },
        Saver("nightlight", R.string.saver_nightlight, R.string.saver_nightlight_desc, C, Icons.Rounded.Bedtime, true) { NightlightSaver(it) },
        Saver("binary", R.string.saver_binary, R.string.saver_binary_desc, C, Icons.Rounded.Memory, true, showsNotifications = true) { BinaryClockSaver(it) },
        Saver("bounce", R.string.saver_bounce, R.string.saver_bounce_desc, C, Icons.AutoMirrored.Rounded.ShowChart, true) { BouncingClockSaver(it) },

        // Info
        Saver("battery_vial", R.string.saver_battery_vial, R.string.saver_battery_vial_desc, I, Icons.Rounded.Science, true) { ChargeVialSaver(it) },
        Saver("battery_fill", R.string.saver_battery_fill, R.string.saver_battery_fill_desc, I, Icons.Rounded.Percent, true) { BigFillSaver(it) },
        Saver("battery_flow", R.string.saver_battery_flow, R.string.saver_battery_flow_desc, I, Icons.Rounded.ElectricBolt, true) { PowerFlowSaver(it) },
        Saver("battery_garden", R.string.saver_battery_garden, R.string.saver_battery_garden_desc, I, Icons.Rounded.LocalFlorist, true) { ChargeGardenSaver(it) },
        Saver("battery_cells", R.string.saver_battery_cells, R.string.saver_battery_cells_desc, I, Icons.Rounded.Battery5Bar, true) { PowerCellsSaver(it) },
        Saver("charging", R.string.saver_charging, R.string.saver_charging_desc, I, Icons.Rounded.BatteryChargingFull, true, showsNotifications = true) { ChargingSaver(it) },
        Saver("weather", R.string.saver_weather, R.string.saver_weather_desc, I, Icons.Rounded.WbCloudy, true, configurable = true, showsNotifications = true) { WeatherSaver(it) },
        Saver("agenda", R.string.saver_agenda, R.string.saver_agenda_desc, I, Icons.Rounded.CalendarMonth, true, configurable = true, showsNotifications = true) { AgendaSaver(it) },
        Saver("notifications", R.string.saver_notifications, R.string.saver_notifications_desc, I, Icons.Rounded.Notifications, true, configurable = true) { NotificationsSaver(it) },
        Saver("sleep", R.string.saver_sleep, R.string.saver_sleep_desc, I, Icons.Rounded.Hotel, true, showsNotifications = true) { SleepSaver(it) },
        Saver("sleepcoach", R.string.saver_sleepcoach, R.string.saver_sleepcoach_desc, I, Icons.Rounded.EmojiEmotions, true) { SleepCoachSaver(it) },
        Saver("moon", R.string.saver_moon, R.string.saver_moon_desc, I, Icons.Rounded.DarkMode, true) { MoonPhaseSaver(it) },
        Saver("month", R.string.saver_month, R.string.saver_month_desc, I, Icons.Rounded.CalendarViewMonth, true, configurable = true) { MonthSaver(it) },
        Saver("progress", R.string.saver_progress, R.string.saver_progress_desc, I, Icons.Rounded.Timelapse, true) { ProgressSaver(it) },
        Saver("world", R.string.saver_world, R.string.saver_world_desc, I, Icons.Rounded.Public, true, configurable = true, showsNotifications = true) { WorldClockSaver(it) },
        Saver("photos", R.string.saver_photos, R.string.saver_photos_desc, I, Icons.Rounded.Photo, true, configurable = true) { PhotoFrameSaver(it) },

        // Relax
        Saver("breathe", R.string.saver_breathe, R.string.saver_breathe_desc, R_, Icons.Rounded.Air, false, configurable = true) { BreatheSaver(it) },
        Saver("breathe_refined", R.string.saver_breathe, R.string.saver_breathe_refined_desc, R_, Icons.Rounded.Air, false, configurable = true, variantOf = "breathe") { BreatheRefinedSaver(it) },
        Saver("boxbreath", R.string.saver_boxbreath, R.string.saver_boxbreath_desc, R_, Icons.Rounded.CropSquare, false) { BoxBreathSaver(it) },
        Saver("tide", R.string.saver_tide, R.string.saver_tide_desc, R_, Icons.Rounded.Water, false, configurable = true) { TideSaver(it) },
        Saver("winddown", R.string.saver_winddown, R.string.saver_winddown_desc, R_, Icons.Rounded.SelfImprovement, false) { WindDownSaver(it) },
        Saver("countdown", R.string.saver_countdown, R.string.saver_countdown_desc, R_, Icons.Rounded.Numbers, false) { CountDownSaver(it) },
        Saver("ocean", R.string.saver_ocean, R.string.saver_ocean_refined_desc, R_, Icons.Rounded.Waves, false) { OceanRefinedSaver(it) },
        Saver("aurora", R.string.saver_aurora, R.string.saver_aurora_refined_desc, R_, Icons.Rounded.WbTwilight, false) { AuroraRefinedSaver(it) },
        Saver("lava", R.string.saver_lava, R.string.saver_lava_desc, R_, Icons.Rounded.BubbleChart, false) { LavaLampSaver(it) },
        Saver("fireflies", R.string.saver_fireflies, R.string.saver_fireflies_desc, R_, Icons.Rounded.AutoAwesome, false) { FirefliesSaver(it) },
        Saver("rain", R.string.saver_rain, R.string.saver_rain_desc, R_, Icons.Rounded.WaterDrop, false) { RainGlassSaver(it) },
        Saver("campfire", R.string.saver_campfire, R.string.saver_campfire_desc, R_, Icons.Rounded.LocalFireDepartment, false) { CampfireSaver(it) },
        Saver("candle", R.string.saver_candle, R.string.saver_candle_desc, R_, Icons.Rounded.Whatshot, false) { CandleSaver(it) },
        Saver("snow", R.string.saver_snow, R.string.saver_snow_desc, R_, Icons.Rounded.AcUnit, false) { SnowfallSaver(it) },
        Saver("koi", R.string.saver_koi, R.string.saver_koi_desc, R_, Icons.Rounded.SetMeal, false) { KoiPondSaver(it) },

        // Ambient
        Saver("starfield", R.string.saver_starfield, R.string.saver_starfield_refined_desc, A, Icons.Rounded.Star, false) { StarfieldRefinedSaver(it) },
        Saver("orbits", R.string.saver_orbits, R.string.saver_orbits_refined_desc, A, Icons.Rounded.Language, false) { OrbitsRefinedSaver(it) },
        Saver("constellation", R.string.saver_constellation, R.string.saver_constellation_refined_desc, A, Icons.Rounded.AutoAwesome, false) { ConstellationRefinedSaver(it) },
        Saver("plexus", R.string.saver_plexus, R.string.saver_plexus_desc, A, Icons.Rounded.Hub, false) { ConstellationSaver(it) },
        Saver("orb", R.string.saver_orb, R.string.saver_orb_desc, A, Icons.Rounded.Lens, false) { OrbSaver(it) },
        Saver("jellyfish", R.string.saver_jellyfish, R.string.saver_jellyfish_desc, A, Icons.Rounded.ScubaDiving, false) { JellyfishSaver(it) },
        Saver("colorfield", R.string.saver_colorfield, R.string.saver_colorfield_desc, A, Icons.Rounded.Gradient, false) { ColorFieldSaver(it) },
        Saver("contours", R.string.saver_contours, R.string.saver_contours_desc, A, Icons.Rounded.Terrain, false) { ContoursSaver(it) },
        Saver("ridgelines", R.string.saver_ridgelines, R.string.saver_ridgelines_desc, A, Icons.Rounded.Landscape, false) { RidgelinesSaver(it) },
        Saver("flow", R.string.saver_flow, R.string.saver_flow_desc, A, Icons.Rounded.Tornado, false) { FlowFieldSaver(it) },
        Saver("harmonograph", R.string.saver_harmonograph, R.string.saver_harmonograph_desc, A, Icons.Rounded.Gesture, false) { HarmonographSaver(it) },
        Saver("pendulum", R.string.saver_pendulum, R.string.saver_pendulum_desc, A, Icons.Rounded.Timeline, false, configurable = true) { PendulumWaveSaver(it) },
        Saver("matrix", R.string.saver_matrix, R.string.saver_matrix_desc, A, Icons.Rounded.Code, false) { MatrixSaver(it) },
        Saver("life", R.string.saver_life, R.string.saver_life_desc, A, Icons.Rounded.GridOn, false) { GameOfLifeSaver(it) },
    )

    private val byId = all.associateBy { it.id }

    /** Screensavers that were merged or renamed, so saved settings keep working. */
    private val aliases = mapOf(
        "ocean_refined" to "ocean", "aurora_refined" to "aurora", "lava_refined" to "lava",
        "fireflies_refined" to "fireflies", "starfield_refined" to "starfield", "orbits_refined" to "orbits",
        "constellation_refined" to "constellation",
    )

    fun find(id: String?): Saver = findOrNull(id) ?: byId.getValue("minimal")

    fun findOrNull(id: String?): Saver? = byId[id] ?: byId[aliases[id]]

    /** The refined version of [id], or the original if [id] is refined. */
    fun counterpart(id: String): Saver? {
        val s = byId[id] ?: return null
        return if (s.variantOf != null) byId[s.variantOf] else all.firstOrNull { it.variantOf == id }
    }
}
