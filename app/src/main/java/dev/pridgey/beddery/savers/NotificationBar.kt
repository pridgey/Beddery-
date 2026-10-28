package dev.pridgey.beddery.savers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.NotificationsMode
import dev.pridgey.beddery.notifications.AppNotifications
import dev.pridgey.beddery.notifications.NotificationItem
import dev.pridgey.beddery.notifications.NotificationStore
import dev.pridgey.beddery.notifications.byApp
import kotlinx.coroutines.delay

private const val BANNER_MS = 8_000L

/** Live notifications, or a believable sample for previews. */
@Composable
fun rememberNotifications(sample: Boolean): List<NotificationItem> {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NotificationStore.ensureConnected(context) }
    val live by NotificationStore.items.collectAsStateWithLifecycle()
    return if (sample && live.isEmpty()) remember { sampleNotifications() } else live
}

fun sampleNotifications(now: Long = System.currentTimeMillis()): List<NotificationItem> = listOf(
    NotificationItem("s1", SAMPLE_CHAT, "Messages", null, "Alex", "Landed! See you in the morning", now - 4 * 60_000, false),
    NotificationItem("s2", SAMPLE_CHAT, "Messages", null, "Mum", "Sleep well x", now - 38 * 60_000, false),
    NotificationItem("s3", SAMPLE_MAIL, "Mail", null, "Your order has shipped", "Arriving Thursday", now - 95 * 60_000, false),
    NotificationItem("s4", SAMPLE_CAL, "Calendar", null, "Dentist", "Tomorrow at 9:30", now - 3 * 3_600_000, false),
)

private const val SAMPLE_CHAT = "sample.chat"
private const val SAMPLE_MAIL = "sample.mail"
private const val SAMPLE_CAL = "sample.calendar"

internal fun fallbackGlyph(pkg: String): ImageVector = when (pkg) {
    SAMPLE_CHAT -> Icons.Rounded.ChatBubble
    SAMPLE_MAIL -> Icons.Rounded.Mail
    SAMPLE_CAL -> Icons.Rounded.CalendarMonth
    else -> Icons.Rounded.Notifications
}

/** The app's glyph in a soft round chip. */
@Composable
fun AppGlyph(app: AppNotifications?, item: NotificationItem?, size: Dp, background: Color = Color.White.copy(alpha = 0.12f)) {
    val icon = item?.icon ?: app?.icon
    val pkg = item?.packageName ?: app?.packageName.orEmpty()
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.52f), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.9f)))
        } else {
            Icon(fallbackGlyph(pkg), null, Modifier.size(size * 0.5f), tint = Color.White.copy(alpha = 0.9f))
        }
    }
}

/**
 * Small row of app icons for apps with unread notifications. When something new arrives it
 * briefly expands into a banner (with content only in [NotificationsMode.PREVIEWS]).
 */
@Composable
fun NotificationBar(mode: NotificationsMode, modifier: Modifier = Modifier, iconSize: Dp = 34.dp, sample: Boolean = false) {
    if (mode == NotificationsMode.OFF) return
    val items = rememberNotifications(sample)
    val apps = remember(items) { items.byApp() }
    val shown = rememberArrival(items)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            shown != null,
            enter = fadeIn(tween(500)) + expandVertically(spring(stiffness = 300f), expandFrom = Alignment.Top),
            exit = fadeOut(tween(500)) + shrinkVertically(tween(600)),
        ) {
            val b = shown ?: return@AnimatedVisibility
            Banner(b, mode, Modifier.padding(bottom = 12.dp))
        }
        AnimatedVisibility(apps.isNotEmpty(), enter = fadeIn(tween(800)), exit = fadeOut(tween(800))) {
            Row(horizontalArrangement = Arrangement.spacedBy(iconSize * 0.3f), verticalAlignment = Alignment.CenterVertically) {
                apps.take(MAX_ICONS).forEach { app -> CountedGlyph(app, iconSize) }
                if (apps.size > MAX_ICONS) {
                    Text("+${apps.size - MAX_ICONS}", color = Color.White.copy(alpha = 0.6f), fontSize = (iconSize.value * 0.38f).sp)
                }
            }
        }
    }
}

private const val MAX_ICONS = 5

/** The newest notification for a few seconds after it lands, then null. Older ones never pop up. */
@Composable
fun rememberArrival(items: List<NotificationItem>): NotificationItem? {
    var seen by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var banner by remember { mutableStateOf<NotificationItem?>(null) }
    val newest = items.maxByOrNull { it.postedAt }
    LaunchedEffect(newest?.key, newest?.postedAt) {
        if (newest != null && newest.postedAt > seen) {
            seen = newest.postedAt
            banner = newest
            delay(BANNER_MS)
            banner = null
        }
    }
    // Gone too if it was dismissed elsewhere.
    return banner?.takeIf { b -> items.any { it.key == b.key } }
}

@Composable
internal fun CountedGlyph(app: AppNotifications, size: Dp) {
    Box {
        AppGlyph(app, null, size)
        if (app.items.size > 1) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = size * 0.18f, y = -size * 0.12f)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2B2D33))
                    .padding(horizontal = size * 0.14f, vertical = size * 0.02f),
            ) {
                Text("${app.items.size}", color = Color.White.copy(alpha = 0.85f), fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
            }
        }
    }
}

@Composable
private fun Banner(item: NotificationItem, mode: NotificationsMode, modifier: Modifier = Modifier) {
    val showContent = mode == NotificationsMode.PREVIEWS && !item.isPrivate
    Row(
        modifier
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF16171B).copy(alpha = 0.92f))
            .padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(null, item, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.appName, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                if (showContent) item.title ?: item.appName else stringResource(R.string.notification_new),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showContent && item.text != null) {
                Spacer(Modifier.height(1.dp))
                Text(item.text, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
