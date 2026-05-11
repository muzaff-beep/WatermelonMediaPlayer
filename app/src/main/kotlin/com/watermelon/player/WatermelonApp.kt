package com.watermelon.player

import android.app.Application
import com.watermelon.player.database.MediaDatabase

class WatermelonApp : Application() {
    // DB instance can be accessed via MediaDatabase.getDatabase(this)
    // Koin initialization can go here later

    override fun onCreate() {
        super.onCreate()
        // Initialize anything global
    }
}