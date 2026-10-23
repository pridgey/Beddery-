package dev.pridgey.beddery.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class AgendaEvent(
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val color: Int,
)

object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events that haven't ended yet, within the next [hours]. */
    suspend fun upcoming(context: Context, hours: Long = 36, limit: Int = 8): List<AgendaEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 24 * 3_600_000L) // include all-day events that started at midnight UTC
            ContentUris.appendId(it, now + hours * 3_600_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val zone = ZoneId.systemDefault()
        val events = mutableListOf<AgendaEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection,
                "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(3) == 1
                    // All-day events are stored as UTC midnights.
                    val z = if (allDay) ZoneOffset.UTC else zone
                    val begin = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), z)
                    val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), z)
                    if (!end.isAfter(LocalDateTime.now())) continue
                    events += AgendaEvent(
                        title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "",
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        color = c.getInt(4),
                    )
                }
            }
        }
        events.sortedWith(compareBy({ it.begin.toLocalDate() }, { !it.allDay }, { it.begin })).take(limit)
    }

    /** Days in [from]..[to] that have at least one event, for the month view. */
    suspend fun eventDays(context: Context, from: java.time.LocalDate, to: java.time.LocalDate): Set<java.time.LocalDate> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptySet()
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - 24 * 3_600_000L)
            ContentUris.appendId(it, end)
        }.build()
        val days = HashSet<java.time.LocalDate>()
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                "${CalendarContract.Instances.VISIBLE} = 1", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val z = if (c.getInt(1) == 1) ZoneOffset.UTC else zone
                    val day = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(0)), z).toLocalDate()
                    if (!day.isBefore(from) && !day.isAfter(to)) days += day
                }
            }
        }
        days
    }
}
