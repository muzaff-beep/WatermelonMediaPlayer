package com.watermelon.player

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.watermelon.player.rust.WatermelonCore
import com.watermelon.player.ui.navigation.MainNavGraph
import com.watermelon.player.ui.theme.WatermelonPlayerTheme
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install crash-to-file handler before any other code
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(throwable)
            // Pass to default handler (which shows the "App stopped" dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate(savedInstanceState)

        try {
            val pluginsDir = File(filesDir, "plugins")
            pluginsDir.mkdirs()
            WatermelonCore.init(pluginsDir)
        } catch (e: Exception) {
            Log.e("MainActivity", "Engine init failed", e)
            saveCrashLog(e)
        }

        setContent {
            WatermelonPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    MainNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onDestroy() {
        WatermelonCore.destroy()
        super.onDestroy()
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val logFile = File(downloadDir, "watermelon_crash.txt")
            val writer = PrintWriter(FileWriter(logFile, true))
            writer.println("=== Crash ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
            throwable.printStackTrace(writer)
            writer.flush()
            writer.close()
            Log.e("MainActivity", "Crash log saved to ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Could not save crash log", e)
        }
    }
}