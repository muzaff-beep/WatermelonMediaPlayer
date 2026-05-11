package com.watermelon.player.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.watermelon.player.ui.screens.FolderVisibilityScreen
import com.watermelon.player.ui.screens.LibraryScreen
import com.watermelon.player.ui.screens.PlayerScreen
import com.watermelon.player.ui.screens.SettingsScreen
import java.io.File

@Composable
fun MainNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onVideoClick = { uri, title ->
                    navController.navigate("player/${Uri.encode(uri.toString())}/${Uri.encode(title)}")
                },
                onNavigateToFolders = {
                    navController.navigate("folders")
                }
            )
        }

        composable("folders") {
            FolderVisibilityScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            "player/{videoUri}/{title}",
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("videoUri") ?: return@composable
            val videoUri = Uri.parse(uriString)
            PlayerScreen(videoUri = videoUri)
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}