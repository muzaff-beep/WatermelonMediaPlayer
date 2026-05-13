// app/src/main/kotlin/com/watermelon/player/storage/UnifiedStorageAccess.kt
// Abstraction over Android MediaStore and Storage Access Framework.
// Provides a unified API for scanning and accessing video files.

package com.watermelon.player.storage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.util.Log
import com.watermelon.player.database.VideoEntity
import java.io.File

/**
 * Unified access layer for video files across MediaStore, SAF, and direct file paths.
 */
class UnifiedStorageAccess(private val context: Context) {

    private val TAG = "UnifiedStorageAccess"
    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Query all video files from MediaStore.
     * Returns a list of VideoEntity ready for database insertion.
     */
    fun queryMediaStore(): List<VideoEntity> {
        val videos = mutableListOf<VideoEntity>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            contentResolver.query(collection, projection, null, null, sortOrder)
        } else {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (c.moveToNext()) {
                val dataPath = c.getString(dataCol) ?: ""
                val uri = if (dataPath.isNotEmpty() && File(dataPath).exists()) {
                    Uri.fromFile(File(dataPath)).toString()
                } else {
                    ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        c.getLong(idCol)
                    ).toString()
                }
                videos.add(
                    VideoEntity(
                        uri = uri,
                        displayName = c.getString(nameCol) ?: "Unknown",
                        mimeType = c.getString(mimeCol) ?: "video/*",
                        durationMs = c.getLong(durCol),
                        sizeBytes = c.getLong(sizeCol),
                        dateAdded = c.getLong(dateCol),
                        width = c.getInt(widthCol),
                        height = c.getInt(heightCol),
                        isVisible = true
                    )
                )
            }
        }
        Log.d(TAG, "MediaStore query returned ${videos.size} videos")
        return videos
    }

    /**
     * Query videos from a specific directory using SAF or direct file access.
     * @param directoryUri URI of the directory to scan
     * @param recursive whether to scan subdirectories
     */
    fun queryDirectory(directoryUri: Uri, recursive: Boolean = true): List<VideoEntity> {
        val videos = mutableListOf<VideoEntity>()
        try {
            if (directoryUri.scheme == "file") {
                val dir = File(directoryUri.path!!)
                if (dir.isDirectory) {
                    scanDirectory(dir, recursive, videos)
                }
            } else if (directoryUri.scheme == "content") {
                scanContentDirectory(directoryUri, recursive, videos)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query directory: ${directoryUri}", e)
        }
        Log.d(TAG, "Directory scan returned ${videos.size} videos from $directoryUri")
        return videos
    }

    /**
     * Scan a direct file directory for video files.
     */
    private fun scanDirectory(dir: File, recursive: Boolean, results: MutableList<VideoEntity>) {
        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m2ts", "flv", "wmv", "3gp")
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && recursive) {
                scanDirectory(file, true, results)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in videoExtensions) {
                    results.add(
                        VideoEntity(
                            uri = Uri.fromFile(file).toString(),
                            displayName = file.name,
                            mimeType = mimeTypeForExtension(ext),
                            durationMs = 0,
                            sizeBytes = file.length(),
                            dateAdded = file.lastModified() / 1000,
                            width = 0,
                            height = 0,
                            isVisible = true
                        )
                    )
                }
            }
        }
    }

    /**
     * Scan a content URI directory using SAF.
     */
    private fun scanContentDirectory(uri: Uri, recursive: Boolean, results: MutableList<VideoEntity>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2) ?: ""
                val size = cursor.getLong(3)
                val modified = cursor.getLong(4)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR && recursive) {
                    scanContentDirectory(docUri, true, results)
                } else if (mime.startsWith("video/")) {
                    results.add(
                        VideoEntity(
                            uri = docUri.toString(),
                            displayName = name,
                            mimeType = mime,
                            durationMs = 0,
                            sizeBytes = size,
                            dateAdded = modified / 1000,
                            width = 0,
                            height = 0,
                            isVisible = true
                        )
                    )
                }
            }
        }
    }

    /**
     * Map file extension to MIME type.
     */
    private fun mimeTypeForExtension(ext: String): String = when (ext) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "m2ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "3gp" -> "video/3gpp"
        else -> "video/*"
    }
}