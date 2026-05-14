// app/src/main/kotlin/com/watermelon/player/ui/navigation/MainNavGraph.kt
package com.watermelon.player.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.watermelon.player.ui.screens.*
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player/{videoUri}"
    const val FOLDER_VISIBILITY = "folder_visibility"
    const val SETTINGS = "settings"

    fun player(videoUri: String): String {
        val encoded = URLEncoder.encode(videoUri, "UTF-8")
        return "player/$encoded"
    }
}

@Composable
fun MainNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onVideoSelected = { uri ->
                    navController.navigate(Routes.player(uri))
                },
                onFolderVisibility = {
                    navController.navigate(Routes.FOLDER_VISIBILITY)
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("videoUri") ?: return@composable
            val videoUri = URLDecoder.decode(encodedUri, "UTF-8")
            PlayerScreen(
                videoUri = videoUri,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FOLDER_VISIBILITY) {
            FolderVisibilityScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}