package com.watermelon.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Log.w("MainActivity", "Some permissions were denied")
        }
        launchUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(throwable)
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

        val neededPermissions = getRequiredPermissions()
        if (neededPermissions.isEmpty()) {
            launchUI()
        } else {
            requestPermissionLauncher.launch(neededPermissions)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // Storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Notification permission (required for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        return permissions.toTypedArray()
    }

    private fun launchUI() {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Could not save crash log", e)
        }
    }
}