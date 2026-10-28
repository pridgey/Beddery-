package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.NotificationsMode
import dev.pridgey.beddery.notifications.AppNotifications
import dev.pridgey.beddery.notifications.NotificationItem
import dev.pridgey.beddery.notifications.byApp
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.fallbackGlyph
import dev.pridgey.beddery.savers.rememberArrival
import dev.pridgey.beddery.savers.rememberNotifications

// Notifications on the Now Playing styles. Each style shows them its own way, but all follow
// the same rules: nothing when notifications are off (or off at night), and only the app's name,
// never the message, in Icons-only mode or for notifications marked private.

@Immutable
internal data class Notices(
    val apps: List<AppNotifications>,
    /** The newest notification, for a few seconds after it lands. */
    val arrival: NotificationItem?,
    val mode: NotificationsMode,
) {
    val count: Int get() = apps.sumOf { it.items.size }
    val waiting: Boolean get() = apps.isNotEmpty()

    /** Whether this notification's sender and message may be shown, not just its app. */
    fun reveals(item: NotificationItem): Boolean = mode == NotificationsMode.PREVIEWS && !item.isPrivate

    companion object {
        val None = Notices(emptyList(), null, NotificationsMode.OFF)
    }
}

@Composable
internal fun rememberNotices(ctx: SaverContext): Notices {
    if (ctx.notifications == NotificationsMode.OFF) return Notices.None
    val items = rememberNotifications(sample = ctx.demo)
    val arrival = rememberArrival(items)
    val apps = remember(items) { items.byApp() }
    return Notices(apps, arrival, ctx.notifications)
}

/** The heading and line to show for [item]: sender and message, or just the app and "New notification". */
@Composable
internal fun noticeLines(notices: Notices, item: NotificationItem): Pair<String, String?> =
    if (notices.reveals(item)) {
        (item.title ?: item.appName) to item.text
    } else {
        item.appName to stringResource(R.string.notification_new)
    }

@Composable
internal fun noticeCountLabel(notices: Notices): String =
    pluralStringResource(R.plurals.notifications_count, notices.count, notices.count)

/** The app's own small icon, or a stand-in, in one colour. */
@Composable
internal fun NoticeGlyph(item: NotificationItem?, app: AppNotifications?, tint: Color, size: Dp) {
    val icon = item?.icon ?: app?.icon
    if (icon != null) {
        Image(icon, null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
    } else {
        Icon(fallbackGlyph(item?.packageName ?: app?.packageName.orEmpty()), null, Modifier.size(size), tint = tint)
    }
}

/** The icons of the first [max] apps with notifications waiting, side by side. */
@Composable
internal fun NoticeGlyphs(notices: Notices, tint: Color, size: Dp, gap: Dp, max: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        notices.apps.take(max).forEach { NoticeGlyph(null, it, tint, size) }
    }
}

/**
 * Shows [content] while a notification is arriving: it settles in, then fades away. The last
 * notification is kept on screen while it fades, so the card doesn't empty out as it leaves.
 */
@Composable
internal fun ArrivalCard(notices: Notices, modifier: Modifier = Modifier, content: @Composable (NotificationItem) -> Unit) {
    var last by remember { mutableStateOf<NotificationItem?>(null) }
    notices.arrival?.let { last = it }
    AnimatedVisibility(
        notices.arrival != null, modifier,
        enter = fadeIn(tween(500)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 260f), initialScale = 0.85f),
        exit = fadeOut(tween(700)) + scaleOut(tween(700), targetScale = 0.96f),
    ) {
        last?.let { content(it) }
    }
}

private val CountWords = arrayOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

/** "four notifications", the way a person would say it. */
internal fun sayCount(n: Int): String {
    val number = CountWords.getOrNull(n) ?: n.toString()
    return if (n == 1) "$number notification" else "$number notifications"
}
