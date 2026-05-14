// app/src/main/kotlin/com/watermelon/player/database/FolderVisibilityDao.kt
package com.watermelon.player.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FolderVisibilityDao(context: Context) {
    private val helper = FolderDatabaseHelper(context)
    private val db: SQLiteDatabase = helper.writableDatabase

    fun getAll(): List<FolderVisibility> {
        val list = mutableListOf<FolderVisibility>()
        val cursor = db.query("folder_visibility", null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    FolderVisibility(
                        folderUri = it.getString(it.getColumnIndexOrThrow("folderUri")),
                        isVisible = it.getInt(it.getColumnIndexOrThrow("isVisible")) == 1
                    )
                )
            }
        }
        return list
    }

    fun isVisible(folderUri: String): Boolean? {
        val cursor = db.query("folder_visibility", arrayOf("isVisible"), "folderUri = ?", arrayOf(folderUri), null, null, null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(it.getColumnIndexOrThrow("isVisible")) == 1
        }
        return null
    }

    fun setVisibility(folderVisibility: FolderVisibility) {
        val values = ContentValues().apply {
            put("folderUri", folderVisibility.folderUri)
            put("isVisible", if (folderVisibility.isVisible) 1 else 0)
        }
        db.insertWithOnConflict("folder_visibility", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(folderUri: String) {
        db.delete("folder_visibility", "folderUri = ?", arrayOf(folderUri))
    }

    private class FolderDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context, "watermelon_folders.db", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE folder_visibility (
                    folderUri TEXT PRIMARY KEY,
                    isVisible INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS folder_visibility")
            onCreate(db)
        }
    }
}