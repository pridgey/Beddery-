package dev.pridgey.beddery

import android.app.Notification
import android.app.NotificationManager
import dev.pridgey.beddery.notifications.NotificationFilter
import dev.pridgey.beddery.notifications.NotificationFilter.Verdict
import dev.pridgey.beddery.notifications.NotificationItem
import dev.pridgey.beddery.notifications.byApp
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationFilterTest {
    private fun judge(
        own: Boolean = false, ongoing: Boolean = false, summary: Boolean = false, category: String? = null,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT, ambient: Boolean = false, visibility: Int = Notification.VISIBILITY_PUBLIC,
    ) = NotificationFilter.judge(own, ongoing, summary, category, importance, ambient, visibility)

    @Test fun ordinaryMessageShows() = assertEquals(Verdict.SHOW, judge(category = Notification.CATEGORY_MESSAGE))

    @Test fun ourOwnAndOngoingAreHidden() {
        assertEquals(Verdict.HIDE, judge(own = true))
        assertEquals(Verdict.HIDE, judge(ongoing = true))
        assertEquals(Verdict.HIDE, judge(summary = true))
    }

    @Test fun mediaNavigationAndServicesAreHidden() {
        assertEquals(Verdict.HIDE, judge(category = Notification.CATEGORY_TRANSPORT))
        assertEquals(Verdict.HIDE, judge(category = Notification.CATEGORY_NAVIGATION))
        assertEquals(Verdict.HIDE, judge(category = Notification.CATEGORY_SERVICE))
    }

    @Test fun silentAndMinimizedAreHidden() {
        assertEquals(Verdict.HIDE, judge(importance = NotificationManager.IMPORTANCE_MIN))
        assertEquals(Verdict.HIDE, judge(ambient = true))
    }

    @Test fun lockScreenPrivacyIsRespected() {
        assertEquals(Verdict.HIDE, judge(visibility = Notification.VISIBILITY_SECRET))
        assertEquals(Verdict.SHOW_PRIVATE, judge(visibility = Notification.VISIBILITY_PRIVATE))
    }

    @Test fun groupsByAppNewestFirst() {
        fun item(key: String, pkg: String, t: Long) = NotificationItem(key, pkg, pkg, null, null, null, t, false)
        val apps = listOf(item("1", "mail", 10), item("2", "chat", 30), item("3", "mail", 20)).byApp()
        assertEquals(listOf("chat", "mail"), apps.map { it.packageName })
        assertEquals("3", apps[1].latest.key)
        assertEquals(2, apps[1].items.size)
    }
}
