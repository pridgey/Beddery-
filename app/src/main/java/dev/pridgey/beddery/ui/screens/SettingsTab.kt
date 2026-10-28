package dev.pridgey.beddery.ui.screens

import dev.pridgey.beddery.savers.impl.NowPlayingStyles
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.saveable.rememberSaveable
import dev.pridgey.beddery.data.NotificationsMode
import dev.pridgey.beddery.data.ShuffleMode
import dev.pridgey.beddery.notifications.NotificationStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.res.pluralStringResource
import dev.pridgey.beddery.savers.Savers
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.pridgey.beddery.BuildConfig
import dev.pridgey.beddery.MainViewModel
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Accent
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.ClockFormat
import dev.pridgey.beddery.data.StandbyOrientation
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.standby.StandbyActivity
import dev.pridgey.beddery.system.LaunchStatus
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun SettingsTab(settings: AppSettings, viewModel: MainViewModel, padding: PaddingValues, onOpenGallery: () -> Unit = {}) {
    LazyColumn(contentPadding = padding + PaddingValues(bottom = 24.dp)) {
        item { LaunchSection(settings, viewModel) }
        item { ShownSection(settings, viewModel, onOpenGallery) }
        item { LookSection(settings, viewModel) }
        item { WhileShowingSection(settings, viewModel) }
        item { NotificationsSection(settings, viewModel) }
        item { NightSection(settings, viewModel) }
        item { AboutSection() }
    }
}

fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private val itemColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

private enum class StatusKind { Good, Info, Warning, Off }

@Composable
private fun StatusIcon(kind: StatusKind) {
    val (icon, tint) = when (kind) {
        StatusKind.Good -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
        StatusKind.Info -> Icons.Rounded.Info to MaterialTheme.colorScheme.onSurfaceVariant
        StatusKind.Warning -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
        StatusKind.Off -> Icons.Rounded.Info to MaterialTheme.colorScheme.outline
    }
    Icon(icon, null, tint = tint)
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = itemColors,
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Choice(options: List<T>, selected: T, label: @Composable (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

// ---------------------------------------------------------------- Display

@Composable
private fun LookSection(settings: AppSettings, viewModel: MainViewModel) {
    SectionHeader(stringResource(R.string.section_look))
    SettingsGroup {
        ListItem(
            colors = itemColors,
            headlineContent = { Text(stringResource(R.string.clock_format)) },
            supportingContent = {
                Choice(
                    ClockFormat.entries, settings.clockFormat,
                    label = {
                        stringResource(
                            when (it) {
                                ClockFormat.SYSTEM -> R.string.clock_system
                                ClockFormat.H12 -> R.string.clock_12
                                ClockFormat.H24 -> R.string.clock_24
                            },
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { v -> viewModel.update { it.copy(clockFormat = v) } }
            },
        )
        ListItem(
            colors = itemColors,
            headlineContent = { Text(stringResource(R.string.accent_color)) },
            supportingContent = {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { a -> AccentSwatch(a, a == settings.accent) { viewModel.update { it.copy(accent = a) } } }
                }
            },
        )
        SwitchRow(stringResource(R.string.brightness_system), settings.brightness == null) { useSystem ->
            viewModel.update { it.copy(brightness = if (useSystem) null else 0.5f) }
        }
        AnimatedVisibility(settings.brightness != null) {
            var value by remember(settings.brightness) { mutableFloatStateOf(settings.brightness ?: 0.5f) }
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.brightness_level, (value * 100).roundToInt())) },
                supportingContent = {
                    Slider(
                        value = value, onValueChange = { value = it }, valueRange = 0.05f..1f,
                        onValueChangeFinished = { viewModel.update { it.copy(brightness = value) } },
                    )
                },
            )
        }
    }
}

@Composable
private fun WhileShowingSection(settings: AppSettings, viewModel: MainViewModel) {
    SectionHeader(stringResource(R.string.section_while_showing))
    SettingsGroup {
        SwitchRow(stringResource(R.string.standby_double_tap), settings.doubleTapToExit, stringResource(R.string.standby_double_tap_desc)) { v ->
            viewModel.update { it.copy(doubleTapToExit = v) }
        }
        SwitchRow(stringResource(R.string.clock_overlay), settings.showClockOverlay, stringResource(R.string.clock_overlay_desc)) { v ->
            viewModel.update { it.copy(showClockOverlay = v) }
        }
        SwitchRow(stringResource(R.string.burn_in), settings.burnInProtection, stringResource(R.string.burn_in_desc)) { v ->
            viewModel.update { it.copy(burnInProtection = v) }
        }
        // Android decides the screensaver's orientation; Standby can choose.
        if (settings.standbyEnabled) {
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.standby_orientation)) },
                supportingContent = {
                    Choice(
                        options = StandbyOrientation.entries,
                        selected = settings.standbyOrientation,
                        label = {
                            stringResource(
                                when (it) {
                                    StandbyOrientation.AUTO -> R.string.orientation_auto
                                    StandbyOrientation.PORTRAIT -> R.string.orientation_portrait
                                    StandbyOrientation.LANDSCAPE -> R.string.orientation_landscape
                                },
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { v -> viewModel.update { it.copy(standbyOrientation = v) } }
                },
            )
        }
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val name = accent.name.lowercase().replaceFirstChar { it.uppercase() }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(accent.argb))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- Night

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightSection(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val use24h = android.text.format.DateFormat.is24HourFormat(context)
    fun label(minutes: Int) = formatTimeWithAmPm(LocalTime.of(minutes / 60, minutes % 60), use24h)

    SectionHeader(stringResource(R.string.section_night))
    SettingsGroup {
        SwitchRow(stringResource(R.string.night_mode), settings.nightModeEnabled, stringResource(R.string.night_mode_desc)) { v ->
            viewModel.update { it.copy(nightModeEnabled = v) }
        }
        AnimatedVisibility(settings.nightModeEnabled || settings.nightSaverId != null) {
            Column {
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { editing = true },
                    headlineContent = { Text(stringResource(R.string.night_start)) },
                    trailingContent = { Text(label(settings.nightStartMinutes), style = MaterialTheme.typography.titleMedium) },
                )
                ListItem(
                    colors = itemColors,
                    modifier = Modifier.clickable { editing = false },
                    headlineContent = { Text(stringResource(R.string.night_end)) },
                    trailingContent = { Text(label(settings.nightEndMinutes), style = MaterialTheme.typography.titleMedium) },
                )
            }
        }
        AnimatedVisibility(settings.nightModeEnabled) {
            Column {
                SwitchRow(stringResource(R.string.night_red), settings.nightRedTint, stringResource(R.string.night_red_desc)) { v ->
                    viewModel.update { it.copy(nightRedTint = v) }
                }
                var dim by remember(settings.nightDim) { mutableFloatStateOf(settings.nightDim) }
                ListItem(
                    colors = itemColors,
                    headlineContent = { Text(stringResource(R.string.night_dim, (dim * 100).roundToInt())) },
                    supportingContent = {
                        Slider(
                            value = dim, onValueChange = { dim = it }, valueRange = 0f..0.9f,
                            onValueChangeFinished = { viewModel.update { it.copy(nightDim = dim) } },
                        )
                    },
                )
            }
        }
    }

    editing?.let { isStart ->
        val initial = if (isStart) settings.nightStartMinutes else settings.nightEndMinutes
        val state = rememberTimePickerState(initial / 60, initial % 60, use24h)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (isStart) R.string.night_start else R.string.night_end)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    viewModel.update { if (isStart) it.copy(nightStartMinutes = m) else it.copy(nightEndMinutes = m) }
                    editing = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- Notifications

@Composable
private fun NotificationsSection(settings: AppSettings, viewModel: MainViewModel) {
    SectionHeader(stringResource(R.string.section_notifications))
    SettingsGroup { NotificationSettings(settings, viewModel) }
}

/** Options on the Now Playing screensaver's page: it needs notification access to see the music. */
@Composable
fun NowPlayingOptions(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(NotificationStore.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = NotificationStore.hasAccess(context)
        onPauseOrDispose { }
    }
    if (!access) {
        ListItem(
            colors = itemColors,
            headlineContent = { Text(stringResource(R.string.notif_access)) },
            supportingContent = { Text(stringResource(R.string.nowplaying_no_access)) },
            leadingContent = { StatusIcon(StatusKind.Info) },
            trailingContent = { TextButton(onClick = { context.tryStart(NotificationStore.settingsIntent(context)) }) { Text(stringResource(R.string.action_allow)) } },
        )
    }
    ListItem(
        colors = itemColors,
        headlineContent = { Text(stringResource(R.string.nowplaying_style)) },
        supportingContent = {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NowPlayingStyles.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.nowPlayingStyle == id,
                        onClick = { viewModel.update { it.copy(nowPlayingStyle = id) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    )
    SwitchRow(stringResource(R.string.music_takeover), settings.musicTakeover, stringResource(R.string.music_takeover_desc)) { v ->
        viewModel.update { it.copy(musicTakeover = v) }
    }
}

/** Notification access and display options. Also shown on the Notifications screensaver's page. */
@Composable
fun NotificationSettings(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(NotificationStore.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = NotificationStore.hasAccess(context)
        onPauseOrDispose { }
    }
    ListItem(
        colors = itemColors,
        headlineContent = { Text(stringResource(R.string.notif_access)) },
        supportingContent = { Text(stringResource(if (access) R.string.notif_access_granted else R.string.notif_access_desc)) },
        leadingContent = { StatusIcon(if (access) StatusKind.Good else StatusKind.Info) },
        trailingContent = if (access) null else {
            { TextButton(onClick = { context.tryStart(NotificationStore.settingsIntent(context)) }) { Text(stringResource(R.string.action_allow)) } }
        },
    )
    AnimatedVisibility(access) {
        Column {
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.notif_show)) },
                supportingContent = {
                    Column {
                        Choice(
                            NotificationsMode.entries, settings.notificationsMode,
                            label = {
                                stringResource(
                                    when (it) {
                                        NotificationsMode.OFF -> R.string.notif_off
                                        NotificationsMode.ICONS -> R.string.notif_icons
                                        NotificationsMode.PREVIEWS -> R.string.notif_previews
                                    },
                                )
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { v -> viewModel.update { it.copy(notificationsMode = v) } }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                when (settings.notificationsMode) {
                                    NotificationsMode.OFF -> R.string.notif_off_desc
                                    NotificationsMode.ICONS -> R.string.notif_icons_desc
                                    NotificationsMode.PREVIEWS -> R.string.notif_previews_desc
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
            if (settings.notificationsMode != NotificationsMode.OFF) {
                SwitchRow(stringResource(R.string.notif_night), settings.notificationsAtNight, stringResource(R.string.notif_night_desc)) { v ->
                    viewModel.update { it.copy(notificationsAtNight = v) }
                }
            }
            Text(
                stringResource(R.string.notif_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Shuffle

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShownSection(settings: AppSettings, viewModel: MainViewModel, onOpenGallery: () -> Unit) {
    SectionHeader(stringResource(R.string.section_shown))
    SettingsGroup {
        val current = Savers.find(settings.selectedSaverId)
        ListItem(
            colors = itemColors,
            modifier = Modifier.clickable(onClick = onOpenGallery),
            leadingContent = { Icon(current.icon, null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = {
                Text(
                    if (settings.shuffleEnabled) {
                        val count = settings.shuffleIds.count { Savers.findOrNull(it) != null }.takeIf { it > 0 } ?: Savers.all.size
                        pluralStringResource(R.plurals.shown_shuffling, count, count)
                    } else saverName(current),
                )
            },
            supportingContent = { Text(stringResource(R.string.shown_change)) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        )
        SwitchRow(stringResource(R.string.shuffle), settings.shuffleEnabled, stringResource(R.string.shuffle_desc)) { v ->
            viewModel.update { it.copy(shuffleEnabled = v) }
        }
        AnimatedVisibility(settings.shuffleEnabled) {
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.shuffle_when)) },
                supportingContent = {
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.shuffleMode == ShuffleMode.ON_START,
                            onClick = { viewModel.update { it.copy(shuffleMode = ShuffleMode.ON_START) } },
                            label = { Text(stringResource(R.string.shuffle_each_start)) },
                        )
                        listOf(5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(
                                selected = settings.shuffleMode == ShuffleMode.TIMED && settings.shuffleMinutes == m,
                                onClick = { viewModel.update { it.copy(shuffleMode = ShuffleMode.TIMED, shuffleMinutes = m) } },
                                label = { Text(stringResource(R.string.minutes_short, m)) },
                            )
                        }
                    }
                },
            )
        }
        val night = settings.nightSaverId?.let { Savers.findOrNull(it) }
        ListItem(
            colors = itemColors,
            modifier = Modifier.clickable(onClick = onOpenGallery),
            leadingContent = { Icon(night?.icon ?: Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(R.string.shown_night)) },
            supportingContent = {
                Text(
                    if (night == null) stringResource(R.string.shown_night_none)
                    else stringResource(R.string.shown_night_some, saverName(night), nightTime(settings.nightStartMinutes), nightTime(settings.nightEndMinutes)),
                )
            },
            trailingContent = {
                if (night != null) {
                    IconButton(onClick = { viewModel.update { it.copy(nightSaverId = null) } }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.shown_night_clear))
                    }
                }
            },
        )
        // Also on the Now Playing screen's own page.
        SwitchRow(stringResource(R.string.music_takeover), settings.musicTakeover, stringResource(R.string.music_takeover_desc)) { v ->
            viewModel.update { it.copy(musicTakeover = v) }
        }
    }
}

// ---------------------------------------------------------------- About

@Composable
private fun AboutSection() {
    SectionHeader(stringResource(R.string.section_about))
    SettingsGroup {
        ListItem(
            colors = itemColors,
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) },
        )
        UpdateRow()
        Text(
            stringResource(R.string.about_weather_credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}
