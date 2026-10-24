package dev.pridgey.beddery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Single source of truth for user settings, shared by the app, the dream and standby mode. */
class SettingsRepository private constructor(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private object Keys {
        val selected = stringPreferencesKey("selected_saver")
        val shuffle = booleanPreferencesKey("shuffle")
        val shuffleIds = stringSetPreferencesKey("shuffle_ids")
        val shuffleMinutes = intPreferencesKey("shuffle_minutes")
        val shuffleMode = stringPreferencesKey("shuffle_mode")
        val shuffleDeck = stringPreferencesKey("shuffle_deck")
        val lastShuffle = stringPreferencesKey("last_shuffle")
        val notifications = stringPreferencesKey("notifications_mode")
        val notificationsNight = booleanPreferencesKey("notifications_night")
        val previewHint = booleanPreferencesKey("preview_hint")
        val clockFormat = stringPreferencesKey("clock_format")
        val accent = stringPreferencesKey("accent")
        val brightness = floatPreferencesKey("brightness")
        val clockOverlay = booleanPreferencesKey("clock_overlay")
        val burnIn = booleanPreferencesKey("burn_in")
        val night = booleanPreferencesKey("night")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
        val nightRed = booleanPreferencesKey("night_red")
        val nightDim = floatPreferencesKey("night_dim")
        val standby = booleanPreferencesKey("standby")
        val standbyPlug = booleanPreferencesKey("standby_plug")
        val standbyScreenOff = booleanPreferencesKey("standby_screen_off")
        val doubleTap = booleanPreferencesKey("double_tap_exit")
        val orientation = stringPreferencesKey("standby_orientation")
        val tempUnit = stringPreferencesKey("temp_unit")
        val weatherName = stringPreferencesKey("weather_name")
        val weatherLat = doublePreferencesKey("weather_lat")
        val weatherLon = doublePreferencesKey("weather_lon")
        val photos = stringPreferencesKey("photos")
        val worldZones = stringPreferencesKey("world_zones")
        val breath = stringPreferencesKey("breath")
        val pendulum = floatPreferencesKey("pendulum_speed")
        val odometerSeconds = booleanPreferencesKey("odometer_seconds")
        val orbCat = stringPreferencesKey("orb_cat")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val lat = p[Keys.weatherLat]
        val lon = p[Keys.weatherLon]
        val name = p[Keys.weatherName]
        return AppSettings(
            selectedSaverId = p[Keys.selected] ?: d.selectedSaverId,
            shuffleEnabled = p[Keys.shuffle] ?: d.shuffleEnabled,
            shuffleIds = p[Keys.shuffleIds] ?: d.shuffleIds,
            shuffleMinutes = p[Keys.shuffleMinutes] ?: d.shuffleMinutes,
            shuffleMode = p[Keys.shuffleMode].toEnum(d.shuffleMode),
            shuffleDeck = p[Keys.shuffleDeck]?.split(',')?.filter { it.isNotBlank() } ?: d.shuffleDeck,
            lastShuffleId = p[Keys.lastShuffle]?.takeIf { it.isNotBlank() },
            notificationsMode = p[Keys.notifications].toEnum(d.notificationsMode),
            notificationsAtNight = p[Keys.notificationsNight] ?: d.notificationsAtNight,
            previewHintSeen = p[Keys.previewHint] ?: d.previewHintSeen,
            clockFormat = p[Keys.clockFormat].toEnum(d.clockFormat),
            accent = p[Keys.accent].toEnum(d.accent),
            brightness = p[Keys.brightness]?.takeIf { it >= 0f },
            showClockOverlay = p[Keys.clockOverlay] ?: d.showClockOverlay,
            burnInProtection = p[Keys.burnIn] ?: d.burnInProtection,
            nightModeEnabled = p[Keys.night] ?: d.nightModeEnabled,
            nightStartMinutes = p[Keys.nightStart] ?: d.nightStartMinutes,
            nightEndMinutes = p[Keys.nightEnd] ?: d.nightEndMinutes,
            nightRedTint = p[Keys.nightRed] ?: d.nightRedTint,
            nightDim = p[Keys.nightDim] ?: d.nightDim,
            standbyEnabled = p[Keys.standby] ?: d.standbyEnabled,
            standbyOnPlugIn = p[Keys.standbyPlug] ?: d.standbyOnPlugIn,
            standbyOnScreenOff = p[Keys.standbyScreenOff] ?: d.standbyOnScreenOff,
            doubleTapToExit = p[Keys.doubleTap] ?: d.doubleTapToExit,
            standbyOrientation = p[Keys.orientation].toEnum(d.standbyOrientation),
            tempUnit = p[Keys.tempUnit].toEnum(d.tempUnit),
            weatherLocation = if (lat != null && lon != null && name != null) WeatherLocation(name, lat, lon) else null,
            photoUris = p[Keys.photos]?.split('\n')?.filter { it.isNotBlank() } ?: d.photoUris,
            worldZones = p[Keys.worldZones]?.split(',')?.filter { it.isNotBlank() } ?: d.worldZones,
            breathPattern = p[Keys.breath].toEnum(d.breathPattern),
            pendulumSpeed = p[Keys.pendulum] ?: d.pendulumSpeed,
            odometerSeconds = p[Keys.odometerSeconds] ?: d.odometerSeconds,
            orbCat = p[Keys.orbCat] ?: d.orbCat,
            onboardingDone = p[Keys.onboarded] ?: d.onboardingDone,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[Keys.selected] = s.selectedSaverId
        p[Keys.shuffle] = s.shuffleEnabled
        p[Keys.shuffleIds] = s.shuffleIds
        p[Keys.shuffleMinutes] = s.shuffleMinutes
        p[Keys.shuffleMode] = s.shuffleMode.name
        p[Keys.shuffleDeck] = s.shuffleDeck.joinToString(",")
        p[Keys.lastShuffle] = s.lastShuffleId.orEmpty()
        p[Keys.notifications] = s.notificationsMode.name
        p[Keys.notificationsNight] = s.notificationsAtNight
        p[Keys.previewHint] = s.previewHintSeen
        p[Keys.clockFormat] = s.clockFormat.name
        p[Keys.accent] = s.accent.name
        p[Keys.brightness] = s.brightness ?: -1f
        p[Keys.clockOverlay] = s.showClockOverlay
        p[Keys.burnIn] = s.burnInProtection
        p[Keys.night] = s.nightModeEnabled
        p[Keys.nightStart] = s.nightStartMinutes
        p[Keys.nightEnd] = s.nightEndMinutes
        p[Keys.nightRed] = s.nightRedTint
        p[Keys.nightDim] = s.nightDim
        p[Keys.standby] = s.standbyEnabled
        p[Keys.standbyPlug] = s.standbyOnPlugIn
        p[Keys.standbyScreenOff] = s.standbyOnScreenOff
        p[Keys.doubleTap] = s.doubleTapToExit
        p[Keys.orientation] = s.standbyOrientation.name
        p[Keys.tempUnit] = s.tempUnit.name
        val loc = s.weatherLocation
        if (loc != null) {
            p[Keys.weatherName] = loc.name
            p[Keys.weatherLat] = loc.latitude
            p[Keys.weatherLon] = loc.longitude
        } else {
            p.remove(Keys.weatherName)
            p.remove(Keys.weatherLat)
            p.remove(Keys.weatherLon)
        }
        p[Keys.photos] = s.photoUris.joinToString("\n")
        p[Keys.worldZones] = s.worldZones.joinToString(",")
        p[Keys.breath] = s.breathPattern.name
        p[Keys.pendulum] = s.pendulumSpeed
        p[Keys.odometerSeconds] = s.odometerSeconds
        p[Keys.orbCat] = s.orbCat
        p[Keys.onboarded] = s.onboardingDone
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
