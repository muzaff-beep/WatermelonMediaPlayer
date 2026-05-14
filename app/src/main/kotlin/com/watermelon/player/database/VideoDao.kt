// app/src/main/kotlin/com/watermelon/player/database/VideoDao.kt
package com.watermelon.player.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VideoDao(context: Context) {
    private val helper = VideoDatabaseHelper(context)
    private val db: SQLiteDatabase = helper.writableDatabase

    fun getAllVisible(): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        val cursor = db.query("videos", null, "isVisible = 1", null, null, null, "dateAdded DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEntity(it))
            }
        }
        return list
    }

    fun getAll(): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        val cursor = db.query("videos", null, null, null, null, null, "dateAdded DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEntity(it))
            }
        }
        return list
    }

    fun getByUri(uri: String): VideoEntity? {
        val cursor = db.query("videos", null, "uri = ?", arrayOf(uri), null, null, null)
        cursor.use {
            if (it.moveToFirst()) return cursorToEntity(it)
        }
        return null
    }

    fun insertAll(videos: List<VideoEntity>) {
        db.beginTransaction()
        try {
            for (video in videos) {
                val values = ContentValues().apply {
                    put("uri", video.uri)
                    put("displayName", video.displayName)
                    put("mimeType", video.mimeType)
                    put("durationMs", video.durationMs)
                    put("sizeBytes", video.sizeBytes)
                    put("dateAdded", video.dateAdded)
                    put("width", video.width)
                    put("height", video.height)
                    put("isVisible", if (video.isVisible) 1 else 0)
                }
                db.insertWithOnConflict("videos", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun update(video: VideoEntity) {
        val values = ContentValues().apply {
            put("displayName", video.displayName)
            put("durationMs", video.durationMs)
            put("sizeBytes", video.sizeBytes)
            put("width", video.width)
            put("height", video.height)
            put("isVisible", if (video.isVisible) 1 else 0)
        }
        db.update("videos", values, "uri = ?", arrayOf(video.uri))
    }

    fun setVisibility(uri: String, visible: Boolean) {
        val values = ContentValues().apply {
            put("isVisible", if (visible) 1 else 0)
        }
        db.update("videos", values, "uri = ?", arrayOf(uri))
    }

    fun deleteByUri(uri: String) {
        db.delete("videos", "uri = ?", arrayOf(uri))
    }

    fun deleteAllExcept(uris: List<String>) {
        val placeholders = uris.joinToString(",") { "?" }
        db.delete("videos", "uri NOT IN ($placeholders)", uris.toTypedArray())
    }

    private fun cursorToEntity(cursor: android.database.Cursor): VideoEntity {
        return VideoEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            uri = cursor.getString(cursor.getColumnIndexOrThrow("uri")),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow("displayName")),
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mimeType")),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")),
            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("sizeBytes")),
            dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow("dateAdded")),
            width = cursor.getInt(cursor.getColumnIndexOrThrow("width")),
            height = cursor.getInt(cursor.getColumnIndexOrThrow("height")),
            isVisible = cursor.getInt(cursor.getColumnIndexOrThrow("isVisible")) == 1
        )
    }

    private class VideoDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context, "watermelon_videos.db", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE videos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uri TEXT NOT NULL UNIQUE,
                    displayName TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    sizeBytes INTEGER NOT NULL DEFAULT 0,
                    dateAdded INTEGER NOT NULL DEFAULT 0,
                    width INTEGER NOT NULL DEFAULT 0,
                    height INTEGER NOT NULL DEFAULT 0,
                    isVisible INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS videos")
            onCreate(db)
        }
    }
}