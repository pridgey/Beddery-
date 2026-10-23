package dev.pridgey.beddery.savers

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.system.PlugType
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------- Time formatting ----------

fun formatClock(time: LocalTime, use24h: Boolean): String =
    if (use24h) "%02d:%02d".format(time.hour, time.minute)
    else "%d:%02d".format(hour12(time.hour), time.minute)

fun hour12(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

private val amPmFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("a", Locale.getDefault()) }
fun amPm(time: LocalTime): String = amPmFormatter.format(time)

private val longDateFormatter: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"))
}
fun formatLongDate(date: LocalDate): String = longDateFormatter.format(date)

private val shortDateFormatter: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"))
}
fun formatShortDate(date: LocalDate): String = shortDateFormatter.format(date)

fun formatTimeWithAmPm(time: LocalTime, use24h: Boolean): String =
    if (use24h) formatClock(time, true) else "${formatClock(time, false)} ${amPm(time)}"

fun formatDuration(context: Context, millis: Long): String {
    val totalMin = ((millis + 59_999) / 60_000).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0 -> context.getString(R.string.duration_minutes, m)
        m == 0 -> context.getString(R.string.duration_hours, h)
        else -> context.getString(R.string.duration_hours_minutes, h, m)
    }
}

/** e.g. "Charging wirelessly · Full in 45 min" */
fun chargingLabel(context: Context, ctx: SaverContext): String {
    val b = ctx.battery
    val state = when {
        b.isFull && b.isPluggedIn -> context.getString(R.string.battery_charged)
        !b.isPluggedIn -> context.getString(R.string.battery_on_battery)
        b.plug == PlugType.WIRELESS -> context.getString(R.string.battery_charging_wireless)
        b.plug == PlugType.DOCK -> context.getString(R.string.battery_charging_dock)
        else -> context.getString(R.string.battery_charging)
    }
    val remaining = b.chargeTimeRemainingMs?.let { context.getString(R.string.battery_full_in, formatDuration(context, it)) }
    return listOfNotNull(state, remaining).joinToString(" · ")
}

// ---------- Animation helpers ----------

/**
 * Runs [onFrame] every display frame and returns the elapsed time in seconds.
 * Read the returned state inside a draw lambda so only drawing (not composition) repeats each frame.
 */
@Composable
fun rememberFrameTime(onFrame: (dt: Float, t: Float) -> Unit = { _, _ -> }): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val callback by rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                val t = (now - start) / 1e9f
                callback(dt, t)
                time.floatValue = t
            }
        }
    }
    return time
}

/** Slowly drifts content a few pixels every minute so static elements never burn into OLED panels. */
@Composable
fun Modifier.burnInShift(enabled: Boolean): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    var target by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        val range = with(density) { 10.dp.roundToPx() }
        val random = Random(System.nanoTime())
        while (true) {
            delay(60_000)
            target = IntOffset(random.nextInt(-range, range + 1), random.nextInt(-range, range + 1))
        }
    }
    val offset by animateIntOffsetAsState(target, tween(4000), label = "burnIn")
    return this.offset { offset }
}

fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(center.x + (sin(rad) * radius).toFloat(), center.y - (cos(rad) * radius).toFloat())
}

// ---------- Shared UI ----------

val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** Small alarm + battery line used under clocks. */
@Composable
fun StatusLine(ctx: SaverContext, fontSize: TextUnit, color: Color = Color.White.copy(alpha = 0.6f), showBattery: Boolean = true) {
    val context = LocalContext.current
    val iconSize = with(LocalDensity.current) { (fontSize * 1.2f).toDp() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        ctx.nextAlarm?.let { alarm ->
            Icon(Icons.Rounded.Alarm, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(iconSize * 0.3f))
            Text(formatTimeWithAmPm(alarm.toLocalTime(), ctx.use24h), color = color, fontSize = fontSize, style = TabularNumbers)
            if (showBattery) Spacer(Modifier.width(iconSize))
        }
        if (showBattery) {
            Icon(
                if (ctx.battery.isFull) Icons.Rounded.BatteryFull else Icons.Rounded.BatteryChargingFull,
                contentDescription = null, tint = color, modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(iconSize * 0.3f))
            val label = remember(ctx.battery) { chargingLabel(context, ctx) }
            Text("${ctx.battery.level}% · $label", color = color, fontSize = fontSize, style = TabularNumbers)
        }
    }
}

/** Compact clock shown on top of ambient screensavers. */
@Composable
fun ClockOverlay(ctx: SaverContext, modifier: Modifier = Modifier) {
    val t = ctx.now.toLocalTime()
    Column(
        modifier.drawBehind {
            // Soft shadow so the clock stays readable over busy animations.
            val r = size.maxDimension * 0.85f
            drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), center, r), Offset(center.x - r, center.y - r), Size(r * 2, r * 2))
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatClock(t, ctx.use24h), color = Color.White.copy(alpha = 0.85f),
                fontSize = 44.sp, fontWeight = FontWeight.Light, style = TabularNumbers,
            )
            if (!ctx.use24h) {
                Text(
                    amPm(t), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    modifier = Modifier.offset(x = 4.dp, y = (-8).dp),
                )
            }
        }
        Text(formatLongDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Spacer(Modifier.size(4.dp))
        StatusLine(ctx, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
    }
}
