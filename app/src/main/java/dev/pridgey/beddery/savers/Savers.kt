package dev.pridgey.beddery.savers

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.material.icons.rounded.MusicNote
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
import dev.pridgey.beddery.savers.impl.SunriseSaver
import dev.pridgey.beddery.savers.impl.WeatherWindowSaver
import dev.pridgey.beddery.savers.impl.SkyTonightSaver
import dev.pridgey.beddery.savers.impl.SceneLighthouseSaver
import dev.pridgey.beddery.savers.impl.SceneTrainSaver
import dev.pridgey.beddery.savers.impl.SleepingCatSaver
import dev.pridgey.beddery.savers.impl.SceneAquariumSaver
import dev.pridgey.beddery.savers.impl.SceneHourglassSaver
import dev.pridgey.beddery.savers.impl.SceneThreeHourglassesSaver
import dev.pridgey.beddery.savers.impl.TinyRoomSaver
import dev.pridgey.beddery.savers.impl.BlockClockSaver
import dev.pridgey.beddery.savers.impl.RidgelinesSaver
import dev.pridgey.beddery.savers.impl.ContoursSaver
import dev.pridgey.beddery.savers.impl.SceneOrbSaver
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
import dev.pridgey.beddery.savers.impl.SceneDayArcSaver
import dev.pridgey.beddery.savers.impl.FirefliesSaver
import dev.pridgey.beddery.savers.impl.FlipClockSaver
import dev.pridgey.beddery.savers.impl.FlowFieldSaver
import dev.pridgey.beddery.savers.impl.GameOfLifeSaver
import dev.pridgey.beddery.savers.impl.HarmonographSaver
import dev.pridgey.beddery.savers.impl.SceneJellyfishSaver
import dev.pridgey.beddery.savers.impl.KoiPondSaver
import dev.pridgey.beddery.savers.impl.LavaLampSaver
import dev.pridgey.beddery.savers.impl.MatrixSaver
import dev.pridgey.beddery.savers.impl.MinimalClockSaver
import dev.pridgey.beddery.savers.impl.MonthSaver
import dev.pridgey.beddery.savers.impl.SceneMoonSaver
import dev.pridgey.beddery.savers.impl.NightlightSaver
import dev.pridgey.beddery.savers.impl.NixieSaver
import dev.pridgey.beddery.savers.impl.NotificationsSaver
import dev.pridgey.beddery.savers.impl.NowPlayingSaver
import dev.pridgey.beddery.savers.impl.OceanRefinedSaver
import dev.pridgey.beddery.savers.impl.OdometerSaver
import dev.pridgey.beddery.savers.impl.OrbitsRefinedSaver
import dev.pridgey.beddery.savers.impl.PendulumWaveSaver
import dev.pridgey.beddery.savers.impl.PhotoFrameSaver
import dev.pridgey.beddery.savers.impl.ProgressSaver
import dev.pridgey.beddery.savers.impl.SceneRainSaver
import dev.pridgey.beddery.savers.impl.RingsClockSaver
import dev.pridgey.beddery.savers.impl.SleepSaver
import dev.pridgey.beddery.savers.impl.SnowfallSaver
import dev.pridgey.beddery.savers.impl.StarfieldRefinedSaver
import dev.pridgey.beddery.savers.impl.WeatherSaver
import dev.pridgey.beddery.savers.impl.WordClockSaver
import dev.pridgey.beddery.savers.impl.WorldClockSaver

/** Gallery groups, in the order their filter chips appear. */
enum class SaverCategory(@StringRes val label: Int) {
    CLOCKS(R.string.category_clocks),
    SLEEP(R.string.category_sleep),
    INFO(R.string.category_info),
    SCENES(R.string.category_scenes),
    ABSTRACT(R.string.category_abstract),
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
    /** Has its own buttons, so the screensaver must accept touches while it shows. */
    val interactive: Boolean = false,
    val content: @Composable (SaverContext) -> Unit,
) {
    val refined: Boolean get() = variantOf != null

    /** Every saver that can show notifications, either at the top or under the small clock overlay. */
    val supportsNotifications: Boolean get() = showsNotifications || !showsTime
}

object Savers {
    private val C = SaverCategory.CLOCKS
    private val S = SaverCategory.SLEEP
    private val I = SaverCategory.INFO
    private val SC = SaverCategory.SCENES
    private val AB = SaverCategory.ABSTRACT

    val all: List<Saver> = listOf(
        // Clocks: easy to read first, then retro and playful, then scenes
        Saver("minimal", R.string.saver_minimal, R.string.saver_minimal_desc, C, Icons.Rounded.Schedule, true, showsNotifications = true) { MinimalClockSaver(it) },
        Saver("analog", R.string.saver_analog, R.string.saver_analog_desc, C, Icons.Rounded.Timer, true, showsNotifications = true) { AnalogClockSaver(it) },
        Saver("nightlight", R.string.saver_nightlight, R.string.saver_nightlight_desc, C, Icons.Rounded.Bedtime, true) { NightlightSaver(it) },
        Saver("classic", R.string.saver_classic, R.string.saver_classic_desc, C, Icons.Rounded.Alarm, true) { BedsideClassicSaver(it) },
        Saver("bigtype", R.string.saver_bigtype, R.string.saver_bigtype_desc, C, Icons.Rounded.FormatSize, true, showsNotifications = true) { BigTypeSaver(it) },
        Saver("flip", R.string.saver_flip, R.string.saver_flip_desc, C, Icons.Rounded.Tv, true, showsNotifications = true) { FlipClockSaver(it) },
        Saver("rings", R.string.saver_rings, R.string.saver_rings_desc, C, Icons.Rounded.DonutLarge, true) { RingsClockSaver(it) },
        Saver("word", R.string.saver_word, R.string.saver_word_desc, C, Icons.Rounded.TextFields, true) { WordClockSaver(it) },
        Saver("world", R.string.saver_world, R.string.saver_world_desc, C, Icons.Rounded.Public, true, configurable = true, showsNotifications = true) { WorldClockSaver(it) },
        Saver("nixie", R.string.saver_nixie, R.string.saver_nixie_desc, C, Icons.Rounded.Lightbulb, true, showsNotifications = true) { NixieSaver(it) },
        Saver("odometer", R.string.saver_odometer, R.string.saver_odometer_desc, C, Icons.Rounded.Speed, true, configurable = true, showsNotifications = true) { OdometerSaver(it) },
        Saver("binary", R.string.saver_binary, R.string.saver_binary_desc, C, Icons.Rounded.Memory, true, showsNotifications = true) { BinaryClockSaver(it) },
        Saver("blocks", R.string.saver_blocks, R.string.saver_blocks_desc, C, Icons.Rounded.Widgets, true) { BlockClockSaver(it) },
        Saver("bounce", R.string.saver_bounce, R.string.saver_bounce_desc, C, Icons.AutoMirrored.Rounded.ShowChart, true) { BouncingClockSaver(it) },
        Saver("dayarc", R.string.saver_dayarc, R.string.saver_dayarc_desc, C, Icons.Rounded.WbSunny, false) { SceneDayArcSaver(it) },
        Saver("tinyroom", R.string.saver_tinyroom, R.string.saver_tinyroom_desc, C, Icons.Rounded.Bed, true) { TinyRoomSaver(it) },
        Saver("hourglass", R.string.saver_hourglass, R.string.saver_hourglass_desc, C, Icons.Rounded.HourglassBottom, false) { SceneHourglassSaver(it) },
        Saver("hourglasses", R.string.saver_hourglasses, R.string.saver_hourglasses_desc, C, Icons.Rounded.HourglassBottom, false) { SceneThreeHourglassesSaver(it) },

        // Sleep: falling asleep and waking up
        Saver("sleep", R.string.saver_sleep, R.string.saver_sleep_desc, S, Icons.Rounded.Hotel, true, showsNotifications = true) { SleepSaver(it) },
        Saver("sunrise", R.string.saver_sunrise, R.string.saver_sunrise_desc, S, Icons.Rounded.LightMode, true) { SunriseSaver(it) },
        Saver("sleepcoach", R.string.saver_sleepcoach, R.string.saver_sleepcoach_desc, S, Icons.Rounded.EmojiEmotions, true) { SleepCoachSaver(it) },
        Saver("breathe", R.string.saver_breathe, R.string.saver_breathe_desc, S, Icons.Rounded.Air, false, configurable = true) { BreatheSaver(it) },
        Saver("breathe_refined", R.string.saver_breathe, R.string.saver_breathe_refined_desc, S, Icons.Rounded.Air, false, configurable = true, variantOf = "breathe") { BreatheRefinedSaver(it) },
        Saver("boxbreath", R.string.saver_boxbreath, R.string.saver_boxbreath_desc, S, Icons.Rounded.CropSquare, false) { BoxBreathSaver(it) },
        Saver("tide", R.string.saver_tide, R.string.saver_tide_desc, S, Icons.Rounded.Water, false, configurable = true) { TideSaver(it) },
        Saver("winddown", R.string.saver_winddown, R.string.saver_winddown_desc, S, Icons.Rounded.SelfImprovement, false) { WindDownSaver(it) },
        Saver("countdown", R.string.saver_countdown, R.string.saver_countdown_desc, S, Icons.Rounded.Numbers, false) { CountDownSaver(it) },

        // Info: what's coming up first, then the sky, then charging
        Saver("weather", R.string.saver_weather, R.string.saver_weather_desc, I, Icons.Rounded.WbCloudy, true, configurable = true, showsNotifications = true) { WeatherSaver(it) },
        Saver("window", R.string.saver_window, R.string.saver_window_desc, I, Icons.Rounded.Window, true, configurable = true) { WeatherWindowSaver(it) },
        Saver("agenda", R.string.saver_agenda, R.string.saver_agenda_desc, I, Icons.Rounded.CalendarMonth, true, configurable = true, showsNotifications = true) { AgendaSaver(it) },
        Saver("month", R.string.saver_month, R.string.saver_month_desc, I, Icons.Rounded.CalendarViewMonth, true, configurable = true) { MonthSaver(it) },
        Saver("notifications", R.string.saver_notifications, R.string.saver_notifications_desc, I, Icons.Rounded.Notifications, true, configurable = true) { NotificationsSaver(it) },
        Saver("nowplaying", R.string.saver_nowplaying, R.string.saver_nowplaying_desc, I, Icons.Rounded.MusicNote, true, configurable = true, interactive = true) { NowPlayingSaver(it) },
        Saver("moon", R.string.saver_moon, R.string.saver_moon_desc, I, Icons.Rounded.DarkMode, false) { SceneMoonSaver(it) },
        Saver("skytonight", R.string.saver_skytonight, R.string.saver_skytonight_desc, I, Icons.Rounded.Stars, true) { SkyTonightSaver(it) },
        Saver("progress", R.string.saver_progress, R.string.saver_progress_desc, I, Icons.Rounded.Timelapse, true) { ProgressSaver(it) },
        Saver("photos", R.string.saver_photos, R.string.saver_photos_desc, I, Icons.Rounded.Photo, true, configurable = true) { PhotoFrameSaver(it) },
        Saver("charging", R.string.saver_charging, R.string.saver_charging_desc, I, Icons.Rounded.BatteryChargingFull, true, showsNotifications = true) { ChargingSaver(it) },
        Saver("battery_vial", R.string.saver_battery_vial, R.string.saver_battery_vial_desc, I, Icons.Rounded.Science, true) { ChargeVialSaver(it) },
        Saver("battery_fill", R.string.saver_battery_fill, R.string.saver_battery_fill_desc, I, Icons.Rounded.Percent, true) { BigFillSaver(it) },
        Saver("battery_cells", R.string.saver_battery_cells, R.string.saver_battery_cells_desc, I, Icons.Rounded.Battery5Bar, true) { PowerCellsSaver(it) },
        Saver("battery_flow", R.string.saver_battery_flow, R.string.saver_battery_flow_desc, I, Icons.Rounded.ElectricBolt, true) { PowerFlowSaver(it) },
        Saver("battery_garden", R.string.saver_battery_garden, R.string.saver_battery_garden_desc, I, Icons.Rounded.LocalFlorist, true) { ChargeGardenSaver(it) },

        // Scenes: illustrated places
        Saver("ocean", R.string.saver_ocean, R.string.saver_ocean_refined_desc, SC, Icons.Rounded.Waves, false) { OceanRefinedSaver(it) },
        Saver("aurora", R.string.saver_aurora, R.string.saver_aurora_refined_desc, SC, Icons.Rounded.WbTwilight, false) { AuroraRefinedSaver(it) },
        Saver("campfire", R.string.saver_campfire, R.string.saver_campfire_desc, SC, Icons.Rounded.LocalFireDepartment, false) { CampfireSaver(it) },
        Saver("rain", R.string.saver_rain, R.string.saver_rain_desc, SC, Icons.Rounded.WaterDrop, false) { SceneRainSaver(it) },
        Saver("snow", R.string.saver_snow, R.string.saver_snow_desc, SC, Icons.Rounded.AcUnit, false) { SnowfallSaver(it) },
        Saver("cat", R.string.saver_cat, R.string.saver_cat_desc, SC, Icons.Rounded.Pets, false, configurable = true) { SleepingCatSaver(it) },
        Saver("koi", R.string.saver_koi, R.string.saver_koi_desc, SC, Icons.Rounded.SetMeal, false) { KoiPondSaver(it) },
        Saver("lighthouse", R.string.saver_lighthouse, R.string.saver_lighthouse_desc, SC, Icons.Rounded.Highlight, false) { SceneLighthouseSaver(it) },
        Saver("train", R.string.saver_train, R.string.saver_train_desc, SC, Icons.Rounded.Train, false) { SceneTrainSaver(it) },
        Saver("aquarium", R.string.saver_aquarium, R.string.saver_aquarium_desc, SC, Icons.Rounded.Pool, false) { SceneAquariumSaver(it) },
        Saver("candle", R.string.saver_candle, R.string.saver_candle_desc, SC, Icons.Rounded.Whatshot, false) { CandleSaver(it) },
        Saver("fireflies", R.string.saver_fireflies, R.string.saver_fireflies_desc, SC, Icons.Rounded.AutoAwesome, false) { FirefliesSaver(it) },
        Saver("jellyfish", R.string.saver_jellyfish, R.string.saver_jellyfish_desc, SC, Icons.Rounded.ScubaDiving, false) { SceneJellyfishSaver(it) },
        Saver("orb", R.string.saver_orb, R.string.saver_orb_desc, SC, Icons.Rounded.Lens, false, configurable = true) { SceneOrbSaver(it) },

        // Abstract: patterns and motion
        Saver("starfield", R.string.saver_starfield, R.string.saver_starfield_refined_desc, AB, Icons.Rounded.Star, false) { StarfieldRefinedSaver(it) },
        Saver("orbits", R.string.saver_orbits, R.string.saver_orbits_refined_desc, AB, Icons.Rounded.Language, false) { OrbitsRefinedSaver(it) },
        Saver("constellation", R.string.saver_constellation, R.string.saver_constellation_refined_desc, AB, Icons.Rounded.AutoAwesome, false) { ConstellationRefinedSaver(it) },
        Saver("plexus", R.string.saver_plexus, R.string.saver_plexus_desc, AB, Icons.Rounded.Hub, false) { ConstellationSaver(it) },
        Saver("flow", R.string.saver_flow, R.string.saver_flow_desc, AB, Icons.Rounded.Tornado, false) { FlowFieldSaver(it) },
        Saver("colorfield", R.string.saver_colorfield, R.string.saver_colorfield_desc, AB, Icons.Rounded.Gradient, false) { ColorFieldSaver(it) },
        Saver("contours", R.string.saver_contours, R.string.saver_contours_desc, AB, Icons.Rounded.Terrain, false) { ContoursSaver(it) },
        Saver("ridgelines", R.string.saver_ridgelines, R.string.saver_ridgelines_desc, AB, Icons.Rounded.Landscape, false) { RidgelinesSaver(it) },
        Saver("harmonograph", R.string.saver_harmonograph, R.string.saver_harmonograph_desc, AB, Icons.Rounded.Gesture, false) { HarmonographSaver(it) },
        Saver("pendulum", R.string.saver_pendulum, R.string.saver_pendulum_desc, AB, Icons.Rounded.Timeline, false, configurable = true) { PendulumWaveSaver(it) },
        Saver("lava", R.string.saver_lava, R.string.saver_lava_desc, AB, Icons.Rounded.BubbleChart, false) { LavaLampSaver(it) },
        Saver("matrix", R.string.saver_matrix, R.string.saver_matrix_desc, AB, Icons.Rounded.Code, false) { MatrixSaver(it) },
        Saver("life", R.string.saver_life, R.string.saver_life_desc, AB, Icons.Rounded.GridOn, false) { GameOfLifeSaver(it) },
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
