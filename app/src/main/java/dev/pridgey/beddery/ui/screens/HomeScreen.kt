package dev.pridgey.beddery.ui.screens

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.MainViewModel
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.ShuffleMode
import dev.pridgey.beddery.savers.Saver
import dev.pridgey.beddery.savers.SaverCategory
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Savers
import dev.pridgey.beddery.savers.rememberSaverContext
import dev.pridgey.beddery.standby.StandbyActivity
import dev.pridgey.beddery.system.Updater
import dev.pridgey.beddery.system.showsBanner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** How long the gallery must sit still before thumbnails are drawn again. */
private const val STILL_SETTLE_MS = 450L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(settings: AppSettings, viewModel: MainViewModel, onOpenSaver: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { Updater.checkIfDue(context) }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (tab == 0) R.string.app_name else R.string.tab_settings)) },
                actions = {
                    IconButton(onClick = { context.startActivity(StandbyActivity.intent(context, preview = true)) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_preview))
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.AutoAwesomeMosaic, null) },
                    label = { Text(stringResource(R.string.tab_screensavers)) },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Tune, null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
            if (t == 0) {
                GalleryTab(settings, viewModel, onOpenSaver, padding, onSetup = { tab = 1 })
            } else {
                SettingsTab(settings, viewModel, padding, onOpenGallery = { tab = 0 })
            }
        }
    }
}

@Composable
private fun GalleryTab(
    settings: AppSettings,
    viewModel: MainViewModel,
    onOpenSaver: (String) -> Unit,
    padding: PaddingValues,
    onSetup: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<SaverCategory?>(null) }
    var refinedOnly by rememberSaveable { mutableStateOf(false) }
    val previewContext = rememberSaverContext(settings, preview = true)
    val savers = Savers.all.filter { (filter == null || it.category == filter) && (!refinedOnly || it.refined) }
    val update by Updater.state.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val stills = remember { StillScheduler() }
    LaunchedEffect(grid) {
        snapshotFlow { grid.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                stills.paused = true
            } else {
                // People often flick again right away: wait for the list to settle first.
                delay(STILL_SETTLE_MS)
                stills.paused = false
            }
        }
    }
    CompositionLocalProvider(LocalStillScheduler provides stills) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!settings.onboardingDone) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "setup") {
                    SetupCard(onSetup = onSetup, onDismiss = { viewModel.update { it.copy(onboardingDone = true) } })
                }
            }
            if (update.showsBanner) item(span = { GridItemSpan(maxLineSpan) }, key = "update") { UpdateBanner() }
            item(span = { GridItemSpan(maxLineSpan) }, key = "now") { NowShowing(settings) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == null && !refinedOnly,
                        onClick = { filter = null; refinedOnly = false },
                        label = { Text(stringResource(R.string.category_all)) },
                    )
                    SaverCategory.entries.forEach { c ->
                        FilterChip(
                            selected = filter == c,
                            onClick = { filter = if (filter == c) null else c },
                            label = { Text(stringResource(c.label)) },
                        )
                    }
                    FilterChip(
                        selected = refinedOnly,
                        onClick = { refinedOnly = !refinedOnly },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.category_refined)) },
                    )
                }
            }
            items(savers, key = { it.id }) { saver ->
                SaverCard(saver, settings, previewContext, onClick = { onOpenSaver(saver.id) })
            }
        }
    }
}

@Composable
private fun SetupCard(onSetup: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NightsStay, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.action_dismiss)) }
            }
            Text(
                stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onSetup) { Text(stringResource(R.string.setup_action)) }
        }
    }
}

@Composable
private fun NowShowing(settings: AppSettings) {
    val label = if (settings.shuffleEnabled) {
        val count = settings.shuffleIds.count { Savers.findOrNull(it) != null }.takeIf { it > 0 } ?: Savers.all.size
        if (settings.shuffleMode == ShuffleMode.ON_START) pluralStringResource(R.plurals.now_shuffling_start, count, count)
        else pluralStringResource(R.plurals.now_shuffling, count, count, settings.shuffleMinutes)
    } else {
        stringResource(R.string.now_showing, saverName(Savers.find(settings.selectedSaverId)))
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (settings.shuffleEnabled) Icons.Rounded.Shuffle else Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val night = settings.nightSaverId?.let { Savers.findOrNull(it) }
        if (night != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.now_night, saverName(night), nightTime(settings.nightStartMinutes)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun AppSettings.isInRotation(id: String): Boolean =
    if (shuffleEnabled) shuffleIds.isEmpty() || id in shuffleIds else selectedSaverId == id

@Composable
private fun SaverCard(saver: Saver, settings: AppSettings, context: SaverContext, onClick: () -> Unit) {
    val active = settings.isInRotation(saver.id)
    val activeLabel = stringResource(if (settings.shuffleEnabled) R.string.state_in_shuffle else R.string.state_current)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.semantics { if (active) stateDescription = activeLabel },
    ) {
        Box {
            SaverStill(saver, settings, context, Modifier.fillMaxWidth().padding(8.dp))
            if (saver.refined) RefinedBadge(Modifier.align(Alignment.TopStart).padding(16.dp), onDark = true)
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (settings.shuffleEnabled) Icons.Rounded.Shuffle else Icons.Rounded.Check, null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(saver.icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(saver.title), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(stringResource(saver.category.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Aurora", or "Aurora (Refined)" for the polished version, so the two are never confused. */
@Composable
fun saverName(saver: Saver): String {
    val title = stringResource(saver.title)
    return if (saver.refined) stringResource(R.string.saver_refined_name, title) else title
}
