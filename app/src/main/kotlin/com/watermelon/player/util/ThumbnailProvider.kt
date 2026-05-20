package com.watermelon.player.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailProvider {

    private const val TAG = "ThumbnailProvider"
    private const val MAX_CACHE_SIZE = 64 * 1024 * 1024
    private val cache = LruCache<String, Bitmap>(MAX_CACHE_SIZE)

    suspend fun getThumbnail(context: Context, uriString: String, width: Int, height: Int): Bitmap? {
        val cacheKey = "$uriString-$width-$height"
        cache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    val file = File(uriString.replace("file://", ""))
                    if (file.exists()) {
                        retriever.setDataSource(file.absolutePath)
                    } else {
                        retriever.setDataSource(context, Uri.parse(uriString))
                    }
                    val bitmap = retriever.frameAtTime(
                        1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    val scaled = bitmap?.let {
                        Bitmap.createScaledBitmap(it, width, height, false)
                    }
                    scaled?.let { cache.put(cacheKey, it) }
                    scaled
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate thumbnail for $uriString: ${e.message}")
                null
            }
        }
    }

    fun clearCache() {
        cache.evictAll()
    }
}