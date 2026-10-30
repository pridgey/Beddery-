package dev.pridgey.beddery.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.R
import dev.pridgey.beddery.system.TipJar

/** "Leave a tip", which opens a sheet of Google Play tips. Tips unlock nothing; they just say thanks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportRow() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val thanks by TipJar.thanks.collectAsStateWithLifecycle()
    val tipped = remember(thanks) { TipJar.hasTipped(context) }
    ListItem(
        colors = itemColors,
        modifier = Modifier.clickable { open = true },
        leadingContent = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(stringResource(R.string.support_tip)) },
        supportingContent = { Text(stringResource(if (tipped) R.string.support_thanks_short else R.string.support_summary)) },
    )
    if (!open) return

    LaunchedEffect(Unit) { TipJar.load(context) }
    val state by TipJar.state.collectAsStateWithLifecycle()
    // Only thank for tips made while this sheet is open.
    val thanksAtOpen = remember { thanks }
    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(
                stringResource(if (thanks > thanksAtOpen) R.string.support_thanks else R.string.support_tip),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.support_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            when (val s = state) {
                TipJar.State.Loading -> Box(Modifier.padding(16.dp)) { CircularProgressIndicator() }
                TipJar.State.Unavailable -> Text(
                    stringResource(R.string.support_unavailable), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                is TipJar.State.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.tips.forEach { tip ->
                        FilledTonalButton(onClick = { context.findActivity()?.let { TipJar.buy(it, tip) } }) { Text(tip.price) }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
