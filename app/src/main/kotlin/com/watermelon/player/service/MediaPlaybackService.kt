// app/src/main/kotlin/com/watermelon/player/service/PlaybackService.kt
package com.watermelon.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.watermelon.player.MainActivity

class PlaybackService : Service() {

    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_URI) ?: return START_NOT_STICKY
                startPlayback(uri)
            }
            ACTION_PAUSE -> player?.pause()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun startPlayback(uri: String) {
        player?.release()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Watermelon Player")
            .setContentText("Playing in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0))
            .addAction(
                R.drawable.icon_02,
                "Pause",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, PlaybackService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.icon_03,
                "Stop",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "watermelon_playback"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.watermelon.player.action.PLAY"
        const val ACTION_PAUSE = "com.watermelon.player.action.PAUSE"
        const val ACTION_STOP = "com.watermelon.player.action.STOP"
        const val EXTRA_URI = "extra_uri"
    }
}