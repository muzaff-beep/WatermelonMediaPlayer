package com.watermelon.player

import android.app.Application
import com.watermelon.player.scan.ScanScheduler

class WatermelonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScanScheduler.schedule(this)
    }
}