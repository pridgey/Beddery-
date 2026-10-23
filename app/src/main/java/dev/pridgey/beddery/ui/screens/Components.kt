package dev.pridgey.beddery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.savers.Saver
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SaverHost

/**
 * Renders a screensaver at full phone size, then scales it down to fit, so thumbnails
 * look exactly like the real thing.
 */
@Composable
fun SaverPreview(
    saver: Saver,
    settings: AppSettings,
    context: SaverContext,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
) {
    val config = LocalConfiguration.current
    val vw = config.screenWidthDp.dp
    val vh = config.screenHeightDp.dp
    BoxWithConstraints(
        modifier
            .aspectRatio(vw / vh)
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
            .clearAndSetSemantics { },
    ) {
        val scale = maxWidth / vw
        Box(
            Modifier
                .requiredSize(vw, vh)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            SaverHost(settings = settings, saverOverride = saver.id, preview = true, context = context)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(dir) + other.calculateStartPadding(dir),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(dir) + other.calculateEndPadding(dir),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}

/** Small pill marking the polished version of a screensaver. */
@Composable
fun RefinedBadge(modifier: Modifier = Modifier, onDark: Boolean = false) {
    val bg = if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onTertiaryContainer
    Text(
        androidx.compose.ui.res.stringResource(dev.pridgey.beddery.R.string.badge_refined).uppercase(),
        color = fg,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
