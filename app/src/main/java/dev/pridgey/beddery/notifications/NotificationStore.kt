package dev.pridgey.beddery.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    /** The app's small status-bar glyph, drawn white. */
    val icon: ImageBitmap?,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    /** The app asked to hide the content on the lock screen. */
    val isPrivate: Boolean,
)

/** One app's notifications, newest first. */
@Immutable
data class AppNotifications(val packageName: String, val appName: String, val icon: ImageBitmap?, val items: List<NotificationItem>) {
    val latest: NotificationItem get() = items.first()
}

fun List<NotificationItem>.byApp(): List<AppNotifications> =
    sortedByDescending { it.postedAt }
        .groupBy { it.packageName }
        .map { (pkg, list) -> AppNotifications(pkg, list.first().appName, list.firstNotNullOfOrNull { it.icon }, list) }

/** Live notifications, filled in by [BedderyNotificationListener] once the user grants access. */
object NotificationStore {
    private val state = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = state.asStateFlow()

    @Volatile
    var connected: Boolean = false
        internal set

    internal fun publish(list: List<NotificationItem>) {
        state.value = list
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Android sometimes leaves a granted listener unbound after the app was killed; ask it to reconnect. */
    fun ensureConnected(context: Context) {
        if (!connected && hasAccess(context)) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, BedderyNotificationListener::class.java)) }
        }
    }

    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, BedderyNotificationListener::class.java).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
}

/** Which notifications are worth showing on a bedside screen. Kept free of Android types so it can be unit tested. */
object NotificationFilter {
    private val hiddenCategories = setOf(
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS, Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_ALARM, Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_CALL,
    )

    enum class Verdict { HIDE, SHOW, SHOW_PRIVATE }

    fun judge(
        ownPackage: Boolean,
        ongoing: Boolean,
        groupSummary: Boolean,
        category: String?,
        importance: Int,
        ambient: Boolean,
        visibility: Int,
    ): Verdict = when {
        ownPackage || ongoing || groupSummary -> Verdict.HIDE
        category in hiddenCategories -> Verdict.HIDE
        importance != NotificationManager.IMPORTANCE_UNSPECIFIED && importance <= NotificationManager.IMPORTANCE_MIN -> Verdict.HIDE
        ambient -> Verdict.HIDE
        visibility == Notification.VISIBILITY_SECRET -> Verdict.HIDE
        visibility == Notification.VISIBILITY_PRIVATE -> Verdict.SHOW_PRIVATE
        else -> Verdict.SHOW
    }
}
