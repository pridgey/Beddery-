package dev.pridgey.beddery.ui.preview

import android.view.Window
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.WindowBrightness
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.rememberSettings
import dev.pridgey.beddery.savers.LocalSaverInput
import dev.pridgey.beddery.savers.Saver
import dev.pridgey.beddery.savers.SaverHost
import dev.pridgey.beddery.savers.SaverInput
import dev.pridgey.beddery.savers.Savers
import dev.pridgey.beddery.savers.rememberSaverContext
import dev.pridgey.beddery.ui.screens.RefinedBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val CHROME_MS = 3_500L

/**
 * Full-screen preview. Swipe left and right to browse every screensaver (refined versions sit
 * right next to their originals for easy comparison). Tap to show or hide the controls.
 */
@Composable
fun PreviewBrowser(window: Window?, startId: String?, onClose: () -> Unit) {
    val s = rememberSettings()
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val savers = Savers.all
    val startPage = remember {
        val id = startId ?: s.currentSaverId()
        savers.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = startPage) { savers.size }
    val saverContext = rememberSaverContext(s, preview = false, demo = true)
    WindowBrightness(window, s, s.isNight(saverContext.now.toLocalTime()))
    val input = remember { SaverInput() }
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()

    var chrome by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) } // bumps to restart the auto-hide timer
    LaunchedEffect(chrome, touch) {
        if (chrome) {
            delay(CHROME_MS)
            chrome = false
        }
    }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != startPage && !s.previewHintSeen) repo.update { it.copy(previewHintSeen = true) }
        chrome = true
        touch++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CompositionLocalProvider(LocalSaverInput provides input) {
            HorizontalPager(
                state = pager,
                key = { savers[it].id },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { at ->
                            input.tap(at)
                            chrome = !chrome
                            touch++
                        }
                    },
            ) { page ->
                // A gentle depth effect while swiping between pages.
                val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = lerp(1f, 0.92f, offset)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.5f, offset)
                        },
                ) {
                    SaverHost(settings = s, saverOverride = savers[page].id, context = saverContext)
                }
            }
        }

        val saver = savers[pager.currentPage.coerceIn(savers.indices)]
        Chrome(
            visible = chrome || pager.isScrollInProgress,
            saver = saver,
            index = pager.currentPage,
            count = savers.size,
            settings = s,
            showSwipeHint = !s.previewHintSeen,
            onClose = onClose,
            onUse = {
                scope.launch { repo.update { it.copy(selectedSaverId = saver.id, shuffleEnabled = false) } }
                touch++
            },
            onToggleShuffle = {
                scope.launch {
                    repo.update {
                        val ids = if (saver.id in it.shuffleIds) it.shuffleIds - saver.id else it.shuffleIds + saver.id
                        it.copy(shuffleIds = ids)
                    }
                }
                touch++
            },
        )
    }
}

private fun AppSettings.currentSaverId(): String =
    if (shuffleEnabled) shuffleIds.firstOrNull { Savers.findOrNull(it) != null } ?: selectedSaverId else selectedSaverId

@Composable
private fun Chrome(
    visible: Boolean,
    saver: Saver,
    index: Int,
    count: Int,
    settings: AppSettings,
    showSwipeHint: Boolean,
    onClose: () -> Unit,
    onUse: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    val isCurrent = !settings.shuffleEnabled && settings.selectedSaverId == saver.id
    val inShuffle = saver.id in settings.shuffleIds
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { -it / 3 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            ) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                    modifier = Modifier.align(Alignment.CenterStart),
                ) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                AnimatedContent(
                    saver,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "title",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                ) { sv ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(sv.title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (sv.refined) {
                                Spacer(Modifier.width(8.dp))
                                RefinedBadge(onDark = true)
                            }
                        }
                        Text(stringResource(sv.category.label), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
                Text(
                    "${index + 1} / $count", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 3 },
            exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 48.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onUse,
                        enabled = !isCurrent,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White, contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.16f), disabledContentColor = Color.White,
                        ),
                        modifier = Modifier.height(48.dp),
                    ) {
                        AnimatedContent(isCurrent, label = "use") { current ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (current) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.preview_showing_while_charging))
                                } else {
                                    Text(stringResource(if (settings.shuffleEnabled) R.string.action_use_instead else R.string.action_use))
                                }
                            }
                        }
                    }
                    FilledTonalIconToggleButton(
                        checked = inShuffle,
                        onCheckedChange = { onToggleShuffle() },
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White,
                            checkedContainerColor = MaterialTheme.colorScheme.primary, checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, stringResource(if (inShuffle) R.string.action_in_shuffle else R.string.action_add_to_shuffle))
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (showSwipeHint) SwipeHint() else PageDots(index, count)
            }
        }
    }
}

@Composable
private fun SwipeHint() {
    val pulse = rememberInfiniteTransition(label = "hint")
    val nudge by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "nudge")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.graphicsLayer { translationX = -nudge * 6.dp.toPx() },
        )
        Text(stringResource(R.string.preview_swipe_hint), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.graphicsLayer { translationX = nudge * 6.dp.toPx() },
        )
    }
}

/** A compact, iOS-style windowed page indicator: at most 9 dots, shrinking towards the ends. */
@Composable
private fun PageDots(index: Int, count: Int) {
    val window = 9
    val first = (index - window / 2).coerceIn(0, (count - window).coerceAtLeast(0))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
        for (i in first until minOf(count, first + window)) {
            val edge = (i == first && first > 0) || (i == first + window - 1 && first + window < count)
            val size = if (i == index) 8.dp else if (edge) 4.dp else 6.dp
            Box(
                Modifier
                    .size(size)
                    .alpha(if (i == index) 1f else 0.4f)
                    .background(Color.White, RoundedCornerShape(50)),
            )
        }
    }
}
