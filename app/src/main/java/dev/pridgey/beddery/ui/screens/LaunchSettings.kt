package dev.pridgey.beddery.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.pridgey.beddery.MainViewModel
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.system.LaunchStatus
import dev.pridgey.beddery.system.OemSetup

private enum class Method { DREAM, STANDBY }

/** One step of the setup checklist. [done] is null when Android won't tell us; [unknown] then replaces the default wording. */
private class Step(
    val title: String,
    val detail: String,
    val done: Boolean?,
    val action: String?,
    val optional: Boolean = false,
    val unknown: String? = null,
    val onAction: () -> Unit = {},
)

/**
 * How Beddery starts, as one decision: pick a method (with a recommendation for this phone),
 * then follow a short checklist that ticks itself off as you go.
 */
@Composable
fun LaunchSection(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    var dream by remember { mutableStateOf(LaunchStatus.dream(context)) }
    var standby by remember { mutableStateOf(LaunchStatus.standby(context)) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        dream = LaunchStatus.dream(context)
        standby = LaunchStatus.standby(context)
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        standby = LaunchStatus.standby(context)
    }
    val recommended = if (dream.available) Method.DREAM else Method.STANDBY
    val method = when {
        settings.standbyEnabled -> Method.STANDBY
        dream.available -> Method.DREAM
        else -> null
    }
    fun choose(m: Method) {
        viewModel.update { it.copy(onboardingDone = true) }
        if (m == Method.STANDBY && !settings.standbyEnabled) {
            viewModel.setStandbyEnabled(true)
            if (!standby.notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (m == Method.DREAM && settings.standbyEnabled) {
            viewModel.setStandbyEnabled(false)
        }
    }
    val openDream = { context.tryStart(LaunchStatus.dreamSettingsIntent()); Unit }
    val brand = remember { OemSetup.brand() }
    val opened = stringResource(R.string.setup_opened)
    val oemSteps = OemSetup.steps(brand).map { oem ->
        // Android can't see these switches, so once opened the step counts as "probably done".
        Step(
            stringResource(oem.title), stringResource(oem.detail),
            if (oem.id in settings.oemStepsOpened) null else false, stringResource(R.string.action_open), unknown = opened,
        ) {
            OemSetup.open(context, oem) { context.tryStart(it) }
            viewModel.update { it.copy(oemStepsOpened = it.oemStepsOpened + oem.id) }
        }
    }

    val steps = when (method) {
        Method.DREAM -> listOf(
            Step(
                stringResource(R.string.setup_dream_select), stringResource(R.string.setup_dream_select_detail),
                dream.selected, stringResource(R.string.action_open), onAction = openDream,
            ),
            Step(
                stringResource(R.string.setup_dream_charging), stringResource(R.string.setup_dream_charging_detail),
                dream.whileCharging, stringResource(R.string.action_open), onAction = openDream,
            ),
        )
        Method.STANDBY -> listOfNotNull(
            Step(
                stringResource(R.string.setup_standby_notifications), stringResource(R.string.setup_standby_notifications_detail),
                standby.notificationsAllowed, stringResource(R.string.action_allow),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.tryStart(LaunchStatus.appNotificationSettingsIntent(context))
            },
            Step(
                stringResource(R.string.setup_standby_full_screen), stringResource(R.string.setup_standby_full_screen_detail),
                standby.fullScreenAllowed, stringResource(R.string.action_allow),
            ) { context.tryStart(LaunchStatus.fullScreenSettingsIntent(context)) },
            // Phones with their own battery rules get those steps instead of Android's.
            if (oemSteps.isEmpty()) Step(
                stringResource(R.string.setup_standby_battery), stringResource(R.string.setup_standby_battery_detail),
                standby.batteryUnrestricted, stringResource(R.string.action_open), optional = true,
            ) { context.tryStart(LaunchStatus.batterySettingsIntent()) } else null,
        ) + oemSteps
        null -> emptyList()
    }
    val remaining = steps.count { !it.optional && it.done == false }

    SectionHeader(stringResource(R.string.section_launch))
    SettingsGroup {
        Column(Modifier.padding(16.dp)) {
            StatusLine(method, remaining, steps.any { it.done == null })
            Spacer(Modifier.height(16.dp))
            // Side by side normally; stacked when large text would squeeze each card to a few letters a line.
            val stacked = LocalDensity.current.fontScale >= LARGE_TEXT
            val methodCards: @Composable (Modifier) -> Unit = { cardModifier ->
                MethodCard(
                    icon = Icons.Rounded.Bedtime,
                    title = stringResource(R.string.dream_title),
                    detail = stringResource(if (dream.available) R.string.method_dream_detail else R.string.method_dream_unavailable),
                    selected = method == Method.DREAM,
                    recommended = recommended == Method.DREAM,
                    enabled = dream.available,
                    modifier = cardModifier,
                ) { choose(Method.DREAM) }
                MethodCard(
                    icon = Icons.Rounded.Power,
                    title = stringResource(R.string.standby_short),
                    detail = stringResource(R.string.method_standby_detail),
                    selected = method == Method.STANDBY,
                    recommended = recommended == Method.STANDBY,
                    enabled = true,
                    modifier = cardModifier,
                ) { choose(Method.STANDBY) }
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { methodCards(Modifier.fillMaxWidth()) }
            } else {
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    methodCards(Modifier.weight(1f).fillMaxHeight())
                }
            }
            TextButton(onClick = { comparing = true }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.launch_compare))
            }
        }
        AnimatedContent(method, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "checklist") { m ->
            Column {
                if (m != null) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        stringResource(R.string.setup_steps), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
                }
                if (m == Method.STANDBY && brand != null) {
                    Text(
                        stringResource(R.string.oem_footer, stringResource(brand.label)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                if (m == Method.STANDBY) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SwitchRow(stringResource(R.string.standby_on_plug), settings.standbyOnPlugIn) { v -> viewModel.update { it.copy(standbyOnPlugIn = v) } }
                    SwitchRow(stringResource(R.string.standby_on_lock), settings.standbyOnScreenOff, stringResource(R.string.standby_on_lock_desc)) { v ->
                        viewModel.update { it.copy(standbyOnScreenOff = v) }
                    }
                }
                if (m == Method.DREAM) {
                    Text(
                        stringResource(R.string.setup_dream_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    if (comparing) {
        CompareSheet(
            dreamAvailable = dream.available,
            current = method,
            onDismiss = { comparing = false },
            onChoose = { m ->
                comparing = false
                choose(m)
                if (m == Method.DREAM && dream.selected != true) openDream()
            },
        )
    }
}

@Composable
private fun StatusLine(method: Method?, remaining: Int, uncertain: Boolean) {
    val ready = method != null && remaining == 0
    val (text, color) = when {
        method == null -> stringResource(R.string.setup_status_choose) to MaterialTheme.colorScheme.onSurfaceVariant
        ready && uncertain -> stringResource(R.string.setup_status_probably) to MaterialTheme.colorScheme.primary
        ready -> stringResource(if (method == Method.DREAM) R.string.setup_status_ready_dream else R.string.setup_status_ready_standby) to MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.setup_status_steps, remaining, remaining) to MaterialTheme.colorScheme.tertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    recommended: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "border")
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent, label = "bg")
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .alpha(if (enabled) 1f else 0.5f)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recommended && enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.badge_recommended),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, step: Step) {
    ListItem(
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            val done = step.done == true
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                else Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        headlineContent = {
            Text(step.title + if (step.optional) "  ·  " + stringResource(R.string.setup_optional) else "")
        },
        supportingContent = {
            Text(
                when (step.done) {
                    true -> stringResource(R.string.setup_done)
                    null -> step.unknown ?: stringResource(R.string.setup_unknown)
                    false -> step.detail
                },
            )
        },
        trailingContent = if (step.done != true && step.action != null) {
            { FilledTonalButton(onClick = step.onAction) { Text(step.action) } }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareSheet(dreamAvailable: Boolean, current: Method?, onDismiss: () -> Unit, onChoose: (Method) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.compare_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CompareHeading(Icons.Rounded.Bedtime, stringResource(R.string.dream_title), Modifier.weight(1f))
                CompareHeading(Icons.Rounded.Power, stringResource(R.string.standby_short), Modifier.weight(1f))
            }
            listOf(
                Triple(R.string.compare_row_starts, R.string.compare_dream_starts, R.string.compare_standby_starts),
                Triple(R.string.compare_row_setup, R.string.compare_dream_setup, R.string.compare_standby_setup),
                Triple(R.string.compare_row_works, R.string.compare_dream_works, R.string.compare_standby_works),
                Triple(R.string.compare_row_background, R.string.compare_dream_background, R.string.compare_standby_background),
            ).forEach { (label, a, b) ->
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(a), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(b), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.compare_pick), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (dreamAvailable) R.string.compare_pick_dream else R.string.compare_pick_standby),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pick = if (dreamAvailable) Method.DREAM else Method.STANDBY
                    Button(onClick = { onChoose(pick) }, enabled = current != pick) {
                        Text(stringResource(if (pick == Method.DREAM) R.string.compare_use_dream else R.string.compare_use_standby))
                    }
                    if (dreamAvailable) {
                        TextButton(onClick = { onChoose(Method.STANDBY) }, enabled = current != Method.STANDBY) { Text(stringResource(R.string.compare_use_standby)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareHeading(icon: ImageVector, title: String, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}
