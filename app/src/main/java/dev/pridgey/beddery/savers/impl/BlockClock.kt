package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.max
import kotlin.math.sin

private val Glyphs = mapOf(
    '0' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    '3' to listOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
    '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    '6' to listOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
    '7' to listOf("#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."),
    '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
)

/** Grid size: one line of "HH:MM", or hours stacked over minutes for tall screens. */
private fun gridSize(stacked: Boolean) = if (stacked) 15 to 11 else 7 to 25

private val Palette = listOf(
    Color(0xFF5AC8E8), Color(0xFFF2D45A), Color(0xFFB48AE8), Color(0xFF7AD48A),
    Color(0xFFF07A7A), Color(0xFF6A8AF0), Color(0xFFF2A65A),
)

/** A falling piece: its cells (row, col) where it lands, and its colour. */
private class Piece(val cells: List<Pair<Int, Int>>, val color: Color)

/** The grid cells that spell the time (hours without a leading zero in 12-hour time). */
private fun cellsFor(text: String, stacked: Boolean): List<Pair<Int, Int>> {
    val digits = text.filter { it.isDigit() }.padStart(4, ' ')
    val at = if (stacked) listOf(0 to 0, 0 to 6, 8 to 0, 8 to 6) else listOf(0 to 0, 0 to 6, 0 to 14, 0 to 20)
    val cells = ArrayList<Pair<Int, Int>>()
    for (i in 0 until 4) {
        val g = Glyphs[digits[i]] ?: continue
        val (r0, c0) = at[i]
        for (r in 0 until 7) for (c in 0 until 5) if (g[r][c] == '#') cells += (r0 + r) to (c0 + c)
    }
    if (!stacked) {
        // The colon.
        cells += 2 to 12
        cells += 4 to 12
    }
    return cells
}

/** Splits the cells into small connected pieces (up to four blocks) and orders them to stack from the bottom up. */
private fun piecesFor(text: String, stacked: Boolean): List<Piece> {
    val free = cellsFor(text, stacked).toMutableSet()
    val order = free.sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenBy { it.second })
    val pieces = ArrayList<Piece>()
    var n = 0
    for (start in order) {
        if (start !in free) continue
        val piece = mutableListOf(start)
        free -= start
        val target = if (hash(n * 13 + text.hashCode()) < 0.25f) 3 else 4
        while (piece.size < target) {
            val next = piece.flatMap { (r, c) -> listOf(r to c + 1, r to c - 1, r + 1 to c, r - 1 to c) }
                .filter { it in free }
                .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenBy { it.second })
                .firstOrNull() ?: break
            piece += next
            free -= next
        }
        // Neighbouring pieces are made one after another, so stepping through the palette keeps them distinct.
        val first = (hash(text.hashCode()) * Palette.size).toInt()
        pieces += Piece(piece, Palette[(first + n * 3) % Palette.size])
        n++
    }
    // A piece can only land once everything below it in its columns is in place.
    val placed = ArrayList<Piece>()
    val left = pieces.toMutableList()
    while (left.isNotEmpty()) {
        val ready = left.firstOrNull { p ->
            left.none { o -> o !== p && o.cells.any { (orow, ocol) -> p.cells.any { (r, c) -> c == ocol && orow > r } } }
        } ?: left.first()
        placed += ready
        left -= ready
    }
    return placed
}

private const val STAGGER = 0.17f
private const val FALL = 0.55f
private const val CLEAR = 0.9f

/** Falling blocks that stack themselves into the time, then clear away and rebuild each minute. */
@Composable
fun BlockClockSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val text = formatClock(ctx.now.toLocalTime(), ctx.use24h)
    // What's on screen, what's being cleared, and when each started.
    val state = remember { arrayOfNulls<Any>(4) } // [text, pieces, oldPieces, startTime]

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val u = min(maxWidth, maxHeight)
        val body = with(dens) { (u * 0.042f).toSp() }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val stacked = size.height > size.width * 1.3f
            val key = "$text/$stacked"
            if (state[0] != key) {
                // A new layout (rotation) just rebuilds; a new minute clears the old blocks first.
                state[2] = if ((state[0] as String?)?.endsWith("/$stacked") == true) state[1] else null
                state[1] = piecesFor(text, stacked)
                state[0] = key
                state[3] = t
            }
            @Suppress("UNCHECKED_CAST") val pieces = state[1] as List<Piece>
            @Suppress("UNCHECKED_CAST") val old = state[2] as List<Piece>?
            val since = t - (state[3] as Float)

            val w = size.width
            val h = size.height
            val (rows, cols) = gridSize(stacked)
            val cell = if (stacked) minOf(w * 0.78f / cols, h * 0.5f / rows) else minOf(w * 0.78f / cols, h * 0.44f / rows)
            val gridW = cell * cols
            val gridH = cell * rows
            val origin = Offset((w - gridW) / 2, h * (if (stacked) 0.42f else 0.42f) - gridH / 2)

            drawRect(Brush.verticalGradient(listOf(Color(0xFF07080E), Color(0xFF0B0D16)), 0f, h))
            // The well: faint empty slots and a floor.
            for (r in 0 until rows) for (c in 0 until cols) {
                drawRoundRect(Color.White.copy(alpha = 0.025f), origin + Offset(c * cell + cell * 0.1f, r * cell + cell * 0.1f), Size(cell * 0.8f, cell * 0.8f), CornerRadius(cell * 0.12f))
            }
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent), origin.x - cell, origin.x + gridW + cell), Offset(origin.x - cell, origin.y + gridH + cell * 0.25f), Size(gridW + cell * 2, cell * 0.06f))

            // The previous minute clears: rows flash, then blocks dissolve in a sweep.
            val clearing = old != null && since < CLEAR
            if (clearing) {
                for (p in old!!) for ((r, c) in p.cells) {
                    val flash = 1f - smoothstep(0f, 0.25f, since)
                    val go = smoothstep(0.2f + c * 0.012f, 0.55f + c * 0.012f, since)
                    if (go >= 1f) continue
                    block(origin + Offset(c * cell, r * cell), cell * (1f - go), cell, lerp(p.color, Color.White, flash * 0.7f), 1f - go)
                }
            }
            // New pieces fall in one after another and settle with a little squash.
            val build = since - if (old != null) CLEAR else 0f
            for ((i, p) in pieces.withIndex()) {
                val local = build - i * STAGGER
                if (local < 0f) continue
                val fall = (local / FALL).coerceIn(0f, 1f)
                val drop = (1f - fall * fall) * (origin.y + gridH + cell * 2)
                val landed = local - FALL
                val squash = if (landed in 0f..0.18f) sin(landed / 0.18f * Math.PI.toFloat()) * 0.12f else 0f
                for ((r, c) in p.cells) {
                    val pos = origin + Offset(c * cell, r * cell - drop)
                    block(pos, cell, cell, p.color, 1f, squash)
                }
                if (landed in 0f..0.35f) {
                    // A brief glint where it lands.
                    val a = 1f - landed / 0.35f
                    for ((r, c) in p.cells) {
                        drawRoundRect(Color.White.copy(alpha = 0.25f * a), origin + Offset(c * cell + cell * 0.06f, r * cell + cell * 0.06f), Size(cell * 0.88f, cell * 0.88f), CornerRadius(cell * 0.14f), blendMode = BlendMode.Plus)
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * if (landscape) 0.14f else 0.17f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val label = if (ctx.use24h) formatLongDate(ctx.now.toLocalDate()) else "${amPm(ctx.now.toLocalTime())} · ${formatLongDate(ctx.now.toLocalDate())}"
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = body)
        }
    }
}

/** One block: a rounded tile with a bevelled edge and a soft shine. [size] can shrink it about its centre. */
private fun DrawScope.block(topLeft: Offset, size: Float, cell: Float, color: Color, alpha: Float, squash: Float = 0f) {
    if (size <= 0.5f) return
    val inset = cell * 0.06f + (cell - size) / 2
    val bw = size - cell * 0.12f
    val bh = bw * (1f - squash)
    val o = topLeft + Offset(inset, inset + (bw - bh))
    val r = CornerRadius(cell * 0.14f)
    drawRoundRect(lerp(color, Color.Black, 0.35f).copy(alpha = alpha), o, Size(bw, bh), r)
    drawRoundRect(color.copy(alpha = alpha), o, Size(bw, bh - cell * 0.07f), r)
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f * alpha), Color.Transparent), o, o + Offset(bw * 0.7f, bh * 0.7f)),
        o + Offset(cell * 0.06f, cell * 0.06f), Size(max(1f, bw - cell * 0.12f), max(1f, (bh - cell * 0.07f) * 0.45f)), CornerRadius(cell * 0.1f),
    )
}
