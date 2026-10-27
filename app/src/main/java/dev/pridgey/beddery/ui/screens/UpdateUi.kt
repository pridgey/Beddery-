package dev.pridgey.beddery.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.R
import dev.pridgey.beddery.system.UpdateState
import dev.pridgey.beddery.system.Updater

/** Picks up an update after the user grants install permission and returns. */
@Composable
private fun ResumeUpdates() {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        Updater.onResume(context)
        onPauseOrDispose { }
    }
}

/** The "Updates" row in About: status, and the one action that fits it. */
@Composable
fun UpdateRow() {
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    ResumeUpdates()
    val (text, action) = when {
        !Updater.supported -> stringResource(R.string.update_debug) to null
        else -> when (val s = state) {
            UpdateState.Idle -> stringResource(R.string.update_tap_to_check) to (stringResource(R.string.update_check) to { Updater.check(context) })
            UpdateState.Checking -> stringResource(R.string.update_checking) to null
            UpdateState.UpToDate -> stringResource(R.string.update_up_to_date) to (stringResource(R.string.update_check) to { Updater.check(context) })
            is UpdateState.Available -> stringResource(R.string.update_available, s.release.version) to (stringResource(R.string.update_install) to { Updater.install(context, s.release) })
            is UpdateState.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt()) to null
            is UpdateState.Installing -> stringResource(R.string.update_installing) to null
            is UpdateState.NeedsPermission -> stringResource(R.string.update_permission) to (stringResource(R.string.update_permission_action) to { context.startActivity(Updater.permissionIntent(context)) })
            is UpdateState.Failed -> stringResource(R.string.update_failed, s.reason) to (stringResource(R.string.update_retry) to {
                s.release?.let { Updater.install(context, it) } ?: Updater.check(context)
            })
        }
    }
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(R.string.update_title)) },
        supportingContent = {
            Column {
                Text(text)
                (state as? UpdateState.Downloading)?.let {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        trailingContent = when {
            busy && state !is UpdateState.Downloading -> { { CircularProgressIndicator(Modifier.size(24.dp)) } }
            action != null -> { { FilledTonalButton(onClick = action.second) { Text(action.first) } } }
            else -> null
        },
    )
}

/** Shown at the top of the gallery while an update is waiting. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    ResumeUpdates()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.NeedsPermission -> s.release
        else -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.update_available, release.version), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.update_banner_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            when (val s = state) {
                is UpdateState.Downloading -> LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                is UpdateState.Installing -> Text(stringResource(R.string.update_installing), color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(vertical = 10.dp))
                is UpdateState.NeedsPermission -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.update_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = { context.startActivity(Updater.permissionIntent(context)) }) { Text(stringResource(R.string.update_permission_action)) }
                }
                else -> FilledTonalButton(onClick = { Updater.install(context, release) }) { Text(stringResource(R.string.update_install)) }
            }
        }
    }
}
