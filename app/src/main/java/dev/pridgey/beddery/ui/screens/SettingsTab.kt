package dev.pridgey.beddery.ui.screens

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
fun SettingsTab(settings: AppSettings, viewModel: MainViewModel, padding: PaddingValues) {
    LazyColumn(contentPadding = padding + PaddingValues(bottom = 24.dp)) {
        item { LaunchSection(settings, viewModel) }
        item { DisplaySection(settings, viewModel) }
        item { NightSection(settings, viewModel) }
        item { ShuffleSection(settings, viewModel) }
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

// ---------------------------------------------------------------- How it starts

@Composable
private fun LaunchSection(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    var dream by remember { mutableStateOf(LaunchStatus.dream(context)) }
    var standby by remember { mutableStateOf(LaunchStatus.standby(context)) }
    LifecycleResumeEffect(Unit) {
        dream = LaunchStatus.dream(context)
        standby = LaunchStatus.standby(context)
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        standby = LaunchStatus.standby(context)
    }

    SectionHeader(stringResource(R.string.section_launch))
    SettingsGroup {
        // Android's own screensaver
        val dreamText = when {
            !dream.available -> stringResource(R.string.dream_unavailable)
            dream.selected == true && dream.whileCharging == true -> stringResource(R.string.dream_ready)
            dream.selected == true -> stringResource(R.string.dream_selected_check_charging)
            else -> stringResource(R.string.dream_instructions)
        }
        ListItem(
            colors = itemColors,
            headlineContent = { Text(stringResource(R.string.dream_title)) },
            supportingContent = { Text(dreamText) },
            leadingContent = {
                StatusIcon(
                    when {
                        !dream.available -> StatusKind.Off
                        dream.selected == true && dream.whileCharging != false -> StatusKind.Good
                        else -> StatusKind.Info
                    },
                )
            },
            trailingContent = if (dream.available) {
                {
                    TextButton(onClick = {
                        viewModel.update { it.copy(onboardingDone = true) }
                        context.tryStart(LaunchStatus.dreamSettingsIntent())
                    }) { Text(stringResource(R.string.action_open)) }
                }
            } else null,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Standby mode
        ListItem(
            colors = itemColors,
            modifier = Modifier.clickable(role = Role.Switch) { toggleStandby(!settings.standbyEnabled, standby, viewModel, notificationPermission::launch) },
            headlineContent = { Text(stringResource(R.string.standby_title)) },
            supportingContent = {
                Text(stringResource(if (dream.available) R.string.standby_desc else R.string.standby_desc_recommended))
            },
            leadingContent = { StatusIcon(if (settings.standbyEnabled && standby.ready) StatusKind.Good else if (settings.standbyEnabled) StatusKind.Warning else StatusKind.Off) },
            trailingContent = {
                Switch(
                    checked = settings.standbyEnabled,
                    onCheckedChange = { toggleStandby(it, standby, viewModel, notificationPermission::launch) },
                )
            },
        )
        AnimatedVisibility(settings.standbyEnabled) {
            Column {
                if (!standby.notificationsAllowed) {
                    FixRow(stringResource(R.string.fix_notifications), stringResource(R.string.action_allow)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.tryStart(LaunchStatus.appNotificationSettingsIntent(context))
                    }
                }
                if (!standby.fullScreenAllowed) {
                    FixRow(stringResource(R.string.fix_full_screen), stringResource(R.string.action_allow)) {
                        context.tryStart(LaunchStatus.fullScreenSettingsIntent(context))
                    }
                }
                if (!standby.batteryUnrestricted) {
                    FixRow(stringResource(R.string.fix_battery), stringResource(R.string.action_open), optional = true) {
                        context.tryStart(LaunchStatus.batterySettingsIntent())
                    }
                }
                SwitchRow(stringResource(R.string.standby_on_plug), settings.standbyOnPlugIn) { v -> viewModel.update { it.copy(standbyOnPlugIn = v) } }
                SwitchRow(stringResource(R.string.standby_on_lock), settings.standbyOnScreenOff, stringResource(R.string.standby_on_lock_desc)) { v ->
                    viewModel.update { it.copy(standbyOnScreenOff = v) }
                }
                SwitchRow(stringResource(R.string.standby_double_tap), settings.doubleTapToExit, stringResource(R.string.standby_double_tap_desc)) { v ->
                    viewModel.update { it.copy(doubleTapToExit = v) }
                }
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
                if (dream.available) {
                    Text(
                        stringResource(R.string.standby_both_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}

private fun toggleStandby(
    enable: Boolean,
    status: dev.pridgey.beddery.system.StandbyStatus,
    viewModel: MainViewModel,
    requestNotifications: (String) -> Unit,
) {
    viewModel.setStandbyEnabled(enable)
    if (enable && !status.notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestNotifications(Manifest.permission.POST_NOTIFICATIONS)
    }
}

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
private fun FixRow(text: String, action: String, optional: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (optional) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            color = if (optional) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
        )
        TextButton(onClick = onClick) { Text(action) }
    }
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
private fun DisplaySection(settings: AppSettings, viewModel: MainViewModel) {
    SectionHeader(stringResource(R.string.section_display))
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
        SwitchRow(stringResource(R.string.clock_overlay), settings.showClockOverlay, stringResource(R.string.clock_overlay_desc)) { v ->
            viewModel.update { it.copy(showClockOverlay = v) }
        }
        SwitchRow(stringResource(R.string.burn_in), settings.burnInProtection, stringResource(R.string.burn_in_desc)) { v ->
            viewModel.update { it.copy(burnInProtection = v) }
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
        AnimatedVisibility(settings.nightModeEnabled) {
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

// ---------------------------------------------------------------- Shuffle

@Composable
private fun ShuffleSection(settings: AppSettings, viewModel: MainViewModel) {
    SectionHeader(stringResource(R.string.section_shuffle))
    SettingsGroup {
        SwitchRow(stringResource(R.string.shuffle), settings.shuffleEnabled, stringResource(R.string.shuffle_desc)) { v ->
            viewModel.update { it.copy(shuffleEnabled = v) }
        }
        AnimatedVisibility(settings.shuffleEnabled) {
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.shuffle_every)) },
                supportingContent = {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(
                                selected = settings.shuffleMinutes == m,
                                onClick = { viewModel.update { it.copy(shuffleMinutes = m) } },
                                label = { Text(stringResource(R.string.minutes_short, m)) },
                            )
                        }
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- About

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    SectionHeader(stringResource(R.string.section_about))
    SettingsGroup {
        ListItem(
            colors = itemColors,
            headlineContent = { Text(stringResource(R.string.app_name)) },
            supportingContent = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) },
            trailingContent = {
                OutlinedButton(onClick = { context.startActivity(StandbyActivity.intent(context, preview = true)) }) {
                    Text(stringResource(R.string.action_preview))
                }
            },
        )
        Text(
            stringResource(R.string.about_weather_credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}
