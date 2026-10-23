package dev.pridgey.beddery.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.pridgey.beddery.MainViewModel
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.BreathPattern
import dev.pridgey.beddery.data.CalendarRepository
import dev.pridgey.beddery.data.TempUnit
import dev.pridgey.beddery.data.WeatherLocation
import dev.pridgey.beddery.data.WeatherRepository
import dev.pridgey.beddery.savers.impl.cityName
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun SaverOptions(saverId: String, settings: AppSettings, viewModel: MainViewModel) {
    Text(stringResource(R.string.options), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
    Column(Modifier.fillMaxWidth()) {
        when (saverId) {
            "weather", "window" -> WeatherOptions(settings, viewModel)
            "agenda", "month" -> CalendarOptions()
            "notifications" -> NotificationSettings(settings, viewModel)
            "pendulum" -> PendulumOptions(settings, viewModel)
            "photos" -> PhotoOptions(settings, viewModel)
            "world" -> WorldClockOptions(settings, viewModel)
            "breathe", "breathe_refined", "tide" -> BreatheOptions(settings, viewModel)
        }
    }
}

private val clear @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

// ---------------------------------------------------------------- Weather

@Composable
private fun WeatherOptions(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locateFailed by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }

    fun locate() {
        locating = true
        locateFailed = false
        scope.launch {
            val loc = WeatherRepository.currentLocation(context)
            locating = false
            if (loc != null) {
                val named = loc.copy(name = loc.name.ifBlank { context.getString(R.string.weather_current_location) })
                viewModel.update { it.copy(weatherLocation = named) }
            } else locateFailed = true
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else locateFailed = true
    }

    ListItem(
        colors = clear,
        headlineContent = { Text(stringResource(R.string.weather_location)) },
        supportingContent = {
            Text(
                when {
                    locating -> stringResource(R.string.weather_locating)
                    locateFailed -> stringResource(R.string.weather_locate_failed)
                    else -> settings.weatherLocation?.name ?: stringResource(R.string.weather_not_set)
                },
            )
        },
        trailingContent = if (locating) { { CircularProgressIndicator(Modifier.size(24.dp)) } } else null,
    )
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }) {
            Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
            Text(stringResource(R.string.weather_use_location), Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = { searching = true }) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
            Text(stringResource(R.string.weather_search), Modifier.padding(start = 8.dp))
        }
    }
    ListItem(
        colors = clear,
        headlineContent = { Text(stringResource(R.string.weather_units)) },
        supportingContent = {
            Choice(
                TempUnit.entries, settings.tempUnit,
                label = { if (it == TempUnit.CELSIUS) "°C" else "°F" },
                modifier = Modifier.padding(top = 8.dp),
            ) { v -> viewModel.update { it.copy(tempUnit = v) } }
        },
    )
    Text(
        stringResource(R.string.about_weather_credit), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
    )

    if (searching) {
        CitySearchDialog(
            onDismiss = { searching = false },
            onPick = { loc ->
                viewModel.update { it.copy(weatherLocation = loc) }
                searching = false
            },
        )
    }
}

@Composable
private fun CitySearchDialog(onDismiss: () -> Unit, onPick: (WeatherLocation) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherLocation>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        loading = true
        error = false
        scope.launch {
            val r = WeatherRepository.searchCity(query)
            loading = false
            results = r.getOrNull()
            error = r.isFailure
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weather_search)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.weather_search_hint)) },
                    trailingIcon = {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp))
                        else IconButton(onClick = ::search) { Icon(Icons.Rounded.Search, stringResource(R.string.weather_search)) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    error -> Text(stringResource(R.string.weather_error), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
                    results?.isEmpty() == true -> Text(stringResource(R.string.weather_no_results), Modifier.padding(top = 12.dp))
                }
                results?.takeIf { it.isNotEmpty() }?.let { list ->
                    LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                        items(list) { loc ->
                            ListItem(
                                colors = clear,
                                headlineContent = { Text(loc.name) },
                                modifier = Modifier.clickable { onPick(loc) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Calendar

@Composable
private fun CalendarOptions() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        granted = CalendarRepository.hasPermission(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    ListItem(
        colors = clear,
        headlineContent = { Text(stringResource(R.string.calendar_access)) },
        supportingContent = {
            Text(stringResource(if (granted) R.string.calendar_granted else R.string.calendar_needed))
        },
        trailingContent = if (granted) null else {
            {
                Button(onClick = {
                    if (denied) {
                        context.tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    } else launcher.launch(Manifest.permission.READ_CALENDAR)
                }) { Text(stringResource(if (denied) R.string.action_open_settings else R.string.action_allow)) }
            }
        },
    )
}

// ---------------------------------------------------------------- Photos

@Composable
private fun PhotoOptions(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // Keep access after restarts so the screensaver can read them later.
        val kept = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri.toString()
        }
        viewModel.update { it.copy(photoUris = (it.photoUris + kept).distinct()) }
    }
    val count = settings.photoUris.size
    ListItem(
        colors = clear,
        headlineContent = { Text(stringResource(R.string.photos_title)) },
        supportingContent = {
            Text(if (count == 0) stringResource(R.string.photos_none_selected) else pluralStringResource(R.plurals.photos_count, count, count))
        },
    )
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.photos_add), Modifier.padding(start = 8.dp))
        }
        if (count > 0) {
            OutlinedButton(onClick = {
                settings.photoUris.forEach { u ->
                    runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                viewModel.update { it.copy(photoUris = emptyList()) }
            }) { Text(stringResource(R.string.photos_clear)) }
        }
    }
}

// ---------------------------------------------------------------- World clock

private val PresetZones = listOf(
    "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver", "America/Chicago",
    "America/New_York", "America/Toronto", "America/Mexico_City", "America/Bogota", "America/Sao_Paulo",
    "America/Argentina/Buenos_Aires", "Atlantic/Reykjavik", "Europe/London", "Europe/Dublin", "Europe/Lisbon",
    "Europe/Paris", "Europe/Madrid", "Europe/Berlin", "Europe/Amsterdam", "Europe/Rome", "Europe/Stockholm",
    "Europe/Warsaw", "Europe/Athens", "Europe/Istanbul", "Europe/Moscow", "Africa/Lagos", "Africa/Cairo",
    "Africa/Johannesburg", "Africa/Nairobi", "Asia/Jerusalem", "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata",
    "Asia/Bangkok", "Asia/Jakarta", "Asia/Singapore", "Asia/Hong_Kong", "Asia/Shanghai", "Asia/Manila",
    "Asia/Seoul", "Asia/Tokyo", "Australia/Perth", "Australia/Brisbane", "Australia/Sydney", "Pacific/Auckland",
)

private const val MAX_CITIES = 9

@Composable
private fun WorldClockOptions(settings: AppSettings, viewModel: MainViewModel) {
    var adding by remember { mutableStateOf(false) }
    settings.worldZones.forEach { zone ->
        ListItem(
            colors = clear,
            headlineContent = { Text(cityName(zone)) },
            supportingContent = { Text(utcOffsetLabel(zone)) },
            trailingContent = {
                IconButton(onClick = { viewModel.update { it.copy(worldZones = it.worldZones - zone) } }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.world_remove, cityName(zone)))
                }
            },
        )
    }
    if (settings.worldZones.size < MAX_CITIES) {
        FilledTonalButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.world_add), Modifier.padding(start = 8.dp))
        }
    }
    if (adding) {
        val available = PresetZones.filter { it !in settings.worldZones }
            .sortedBy { ZonedDateTime.now(ZoneId.of(it)).offset.totalSeconds }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.world_add)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(available) { zone ->
                        ListItem(
                            colors = clear,
                            headlineContent = { Text(cityName(zone)) },
                            supportingContent = { Text(utcOffsetLabel(zone)) },
                            modifier = Modifier.clickable {
                                viewModel.update { it.copy(worldZones = it.worldZones + zone) }
                                adding = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

private fun utcOffsetLabel(zone: String): String =
    runCatching { "UTC" + ZonedDateTime.now(ZoneId.of(zone)).offset.id.replace("Z", "") }.getOrDefault(zone)

// ---------------------------------------------------------------- Breathe

@Composable
private fun BreatheOptions(settings: AppSettings, viewModel: MainViewModel) {
    ListItem(
        colors = clear,
        headlineContent = { Text(stringResource(R.string.breathe_pattern)) },
        supportingContent = {
            Column {
                Choice(
                    BreathPattern.entries, settings.breathPattern,
                    label = {
                        stringResource(
                            when (it) {
                                BreathPattern.BOX -> R.string.breathe_box
                                BreathPattern.RELAX -> R.string.breathe_relax
                                BreathPattern.CALM -> R.string.breathe_calm
                            },
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { v -> viewModel.update { it.copy(breathPattern = v) } }
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        when (settings.breathPattern) {
                            BreathPattern.BOX -> R.string.breathe_box_desc
                            BreathPattern.RELAX -> R.string.breathe_relax_desc
                            BreathPattern.CALM -> R.string.breathe_calm_desc
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

// ---------------------------------------------------------------- Pendulum wave

private val PendulumSpeeds = listOf(0.5f, 1f, 1.6f)

@Composable
private fun PendulumOptions(settings: AppSettings, viewModel: MainViewModel) {
    val selected = PendulumSpeeds.minByOrNull { kotlin.math.abs(it - settings.pendulumSpeed) } ?: 1f
    ListItem(
        colors = clear,
        headlineContent = { Text(stringResource(R.string.pendulum_speed)) },
        supportingContent = {
            Column {
                Choice(
                    PendulumSpeeds, selected,
                    label = {
                        stringResource(
                            when (it) {
                                0.5f -> R.string.speed_slow
                                1f -> R.string.speed_normal
                                else -> R.string.speed_fast
                            },
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { v -> viewModel.update { it.copy(pendulumSpeed = v) } }
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.pendulum_speed_desc), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
