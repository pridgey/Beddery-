package dev.pridgey.beddery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object PhotoLoader {
    /** Decodes [uri] downsampled so its longest edge is roughly [maxEdgePx]. Returns null if unreadable. */
    suspend fun load(context: Context, uri: Uri, maxEdgePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= maxEdgePx) sample *= 2
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }
}
