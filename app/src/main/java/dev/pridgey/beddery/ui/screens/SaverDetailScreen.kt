package dev.pridgey.beddery.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.MainViewModel
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.savers.Savers
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.rememberSaverContext
import dev.pridgey.beddery.standby.StandbyActivity
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SaverDetailScreen(saverId: String, settings: AppSettings, viewModel: MainViewModel, onBack: () -> Unit, onOpenSaver: (String) -> Unit = {}) {
    val saver = Savers.findOrNull(saverId)
    if (saver == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val previewContext = rememberSaverContext(settings, preview = true)
    val isCurrent = !settings.shuffleEnabled && settings.selectedSaverId == saver.id
    val inShuffle = saver.id in settings.shuffleIds
    val atNight = settings.nightSaverId == saver.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(saverName(saver)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    SaverPreview(saver, settings, previewContext, Modifier.heightIn(max = 420.dp), corner = 28.dp)
                    FilledTonalIconButton(
                        onClick = { context.startActivity(StandbyActivity.intent(context, preview = true, saverId = saver.id)) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) { Icon(Icons.Rounded.Fullscreen, stringResource(R.string.action_preview_full)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.detail_preview_tip), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(saver.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(saver.category.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (saver.refined) {
                    Spacer(Modifier.width(10.dp))
                    RefinedBadge()
                }
                if (saver.supportsNotifications) {
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Rounded.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.detail_shows_notifications), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(saver.description), style = MaterialTheme.typography.bodyLarge)
            val counterpart = Savers.counterpart(saver.id)
            if (counterpart != null) {
                if (saver.refined) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.detail_refined_note, stringResource(counterpart.title)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { onOpenSaver(counterpart.id) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(if (saver.refined) R.string.detail_compare_original else R.string.detail_compare_refined)) },
                )
            }
            Spacer(Modifier.height(20.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isCurrent) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.state_current))
                    }
                } else {
                    Button(onClick = {
                        viewModel.update { it.copy(selectedSaverId = saver.id, shuffleEnabled = false) }
                    }) { Text(stringResource(R.string.action_use)) }
                }
                FilterChip(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    selected = inShuffle,
                    onClick = {
                        viewModel.update {
                            val ids = if (inShuffle) it.shuffleIds - saver.id else it.shuffleIds + saver.id
                            it.copy(shuffleIds = ids)
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.Shuffle, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    label = { Text(stringResource(if (inShuffle) R.string.action_in_shuffle else R.string.action_add_to_shuffle)) },
                )
                FilterChip(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    selected = atNight,
                    onClick = { viewModel.update { it.copy(nightSaverId = if (atNight) null else saver.id) } },
                    leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    label = { Text(stringResource(if (atNight) R.string.action_night_screen else R.string.action_use_at_night)) },
                )
            }
            if (atNight) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.detail_night_note, nightTime(settings.nightStartMinutes), nightTime(settings.nightEndMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.shuffleEnabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.shuffle_is_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (inShuffle) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { viewModel.update { it.copy(shuffleEnabled = true) } },
                    label = { Text(stringResource(R.string.action_turn_on_shuffle)) },
                )
            }

            if (saver.configurable) {
                Spacer(Modifier.height(16.dp))
                SaverOptions(saver.id, settings, viewModel)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A night-hours boundary, minutes since midnight, in the phone's clock style. */
@Composable
fun nightTime(minutes: Int): String {
    val use24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    return formatTimeWithAmPm(LocalTime.of(minutes / 60, minutes % 60), use24h)
}
