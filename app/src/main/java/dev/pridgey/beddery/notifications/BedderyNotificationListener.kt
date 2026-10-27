package dev.pridgey.beddery.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Reads the phone's notifications so screensavers can show them. Nothing leaves the phone and
 * nothing is stored: the list lives in memory only while the app is running.
 */
class BedderyNotificationListener : NotificationListenerService() {

    private val icons = HashMap<String, ImageBitmap?>()
    private val labels = HashMap<String, String>()

    override fun onListenerConnected() {
        NotificationStore.connected = true
        refresh()
        dev.pridgey.beddery.media.NowPlayingStore.reconnect()
    }

    override fun onListenerDisconnected() {
        NotificationStore.connected = false
        NotificationStore.publish(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications ?: return
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access was revoked", e)
            return
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't read notifications", e)
            return
        }
        val ranking = runCatching { currentRanking }.getOrNull()
        val items = active.mapNotNull { sbn -> toItem(sbn, ranking) }
            .sortedByDescending { it.postedAt }
            .take(MAX_ITEMS)
        NotificationStore.publish(items)
    }

    private fun toItem(sbn: StatusBarNotification, rankingMap: RankingMap?): NotificationItem? {
        val n = sbn.notification ?: return null
        val ranking = Ranking()
        val ranked = rankingMap?.getRanking(sbn.key, ranking) == true
        val visibilityOverride = if (ranked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ranking.lockscreenVisibilityOverride
        } else Ranking.VISIBILITY_NO_OVERRIDE
        val verdict = NotificationFilter.judge(
            ownPackage = sbn.packageName == packageName,
            ongoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            category = n.category,
            importance = if (ranked) ranking.importance else android.app.NotificationManager.IMPORTANCE_UNSPECIFIED,
            ambient = ranked && ranking.isAmbient,
            visibility = if (visibilityOverride != Ranking.VISIBILITY_NO_OVERRIDE) visibilityOverride else n.visibility,
        )
        if (verdict == NotificationFilter.Verdict.HIDE) return null
        val private = verdict == NotificationFilter.Verdict.SHOW_PRIVATE
        // Private notifications may offer a redacted public version; otherwise show no content.
        val source = if (private) n.publicVersion else n
        val title = source?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val text = source?.extras?.let { e ->
            (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        }?.takeIf { it.isNotBlank() }
        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName(sbn.packageName),
            icon = icon(sbn.packageName, n.smallIcon),
            title = title,
            text = text,
            postedAt = sbn.postTime,
            isPrivate = private,
        )
    }

    private fun appName(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
        }.getOrNull() ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    private fun icon(pkg: String, icon: Icon?): ImageBitmap? {
        icon ?: return null
        val key = if (icon.type == Icon.TYPE_RESOURCE) "$pkg:${icon.resId}" else "$pkg:${System.identityHashCode(icon)}"
        return icons.getOrPut(key) {
            runCatching {
                val drawable = icon.loadDrawable(this) ?: return@runCatching null
                val size = ICON_PX
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, size, size)
                drawable.setTint(android.graphics.Color.WHITE)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()
        }.also { if (icons.size > 200) icons.clear() }
    }

    private companion object {
        const val TAG = "BedderyNotifications"
        const val MAX_ITEMS = 24
        const val ICON_PX = 72
    }
}
