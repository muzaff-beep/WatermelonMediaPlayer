// app/src/main/kotlin/com/watermelon/player/MainActivity.kt
// Single Activity for phone and TV. Determines layout from intent.

package com.watermelon.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.watermelon.player.platform.*
import com.watermelon.player.rust.WatermelonCore
import com.watermelon.player.ui.navigation.MainNavGraph
import com.watermelon.player.ui.theme.WatermelonPlayerTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Rust engine
        val pluginsDir = File(filesDir, "plugins")
        pluginsDir.mkdirs()
        WatermelonCore.init(pluginsDir)

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
}