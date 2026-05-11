package com.watermelon.player.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

object PiPManager {
    fun enterPiP(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }
}