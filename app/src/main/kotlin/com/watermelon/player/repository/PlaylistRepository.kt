// app/src/main/kotlin/com/watermelon/player/repository/PlaylistRepository.kt
package com.watermelon.player.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

data class Playlist(val id: Long, val name: String)
data class PlaylistEntry(val playlistId: Long, val videoUri: String, val position: Int)

class PlaylistRepository(context: Context) : AutoCloseable {

    private val helper = PlaylistDatabaseHelper(context)
    private val db: SQLiteDatabase = helper.writableDatabase

    suspend fun createPlaylist(name: String): Long {
        val values = ContentValues().apply {
            put("name", name)
        }
        val id = db.insertOrThrow("playlists", null, values)
        Log.d("PlaylistRepo", "Created playlist: id=$id, name=$name")
        return id
    }

    suspend fun deletePlaylist(id: Long) {
        db.delete("playlist_entries", "playlist_id = ?", arrayOf(id.toString()))
        db.delete("playlists", "id = ?", arrayOf(id.toString()))
        Log.d("PlaylistRepo", "Deleted playlist: id=$id")
    }

    suspend fun addVideoToPlaylist(playlistId: Long, videoUri: String, position: Int) {
        val values = ContentValues().apply {
            put("playlist_id", playlistId)
            put("video_uri", videoUri)
            put("position", position)
        }
        db.insertWithOnConflict("playlist_entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.d("PlaylistRepo", "Added to playlist $playlistId: $videoUri at $position")
    }

    suspend fun removeVideoFromPlaylist(playlistId: Long, videoUri: String) {
        db.delete(
            "playlist_entries",
            "playlist_id = ? AND video_uri = ?",
            arrayOf(playlistId.toString(), videoUri)
        )
        Log.d("PlaylistRepo", "Removed from playlist $playlistId: $videoUri")
    }

    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        db.query("playlists", arrayOf("id", "name"), null, null, null, null, "name ASC").use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Playlist(cursor.getLong(0), cursor.getString(1)))
            }
        }
        return list
    }

    fun getPlaylistVideos(playlistId: Long): List<PlaylistEntry> {
        val entries = mutableListOf<PlaylistEntry>()
        val cursor = db.query(
            "playlist_entries",
            arrayOf("playlist_id", "video_uri", "position"),
            "playlist_id = ?",
            arrayOf(playlistId.toString()),
            null,
            null,
            "position ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                entries.add(
                    PlaylistEntry(
                        playlistId = it.getLong(0),
                        videoUri = it.getString(1),
                        position = it.getInt(2)
                    )
                )
            }
        }
        return entries
    }

    override fun close() {
        helper.close()
    }

    private class PlaylistDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context, "watermelon_playlists.db", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE playlist_entries (
                    playlist_id INTEGER NOT NULL,
                    video_uri TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY (playlist_id, video_uri),
                    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS playlist_entries")
            db.execSQL("DROP TABLE IF EXISTS playlists")
            onCreate(db)
        }
    }
}