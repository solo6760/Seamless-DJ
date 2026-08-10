package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.DjDeckScreen
import com.example.ui.screens.DjSettingsScreen
import com.example.ui.screens.GuestRequestScreen
import com.example.ui.screens.MusicSourcesScreen

object Routes {
    const val DECK = "deck"
    const val SOURCES = "sources"
    const val REQUESTS = "requests"
    const val SETTINGS = "settings"
}

@Composable
fun MainScreen(
    viewModel: DjViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DECK,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Routes.DECK) {
            DjDeckScreen(
                viewModel = viewModel,
                onNavigateToSources = { navController.navigate(Routes.SOURCES) },
                onNavigateToRequests = { navController.navigate(Routes.REQUESTS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SOURCES) {
            MusicSourcesScreen(
                viewModel = viewModel,
                onBackToDeck = { navController.popBackStack() }
            )
        }

        composable(Routes.REQUESTS) {
            GuestRequestScreen(
                viewModel = viewModel,
                onBackToDeck = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            DjSettingsScreen(
                viewModel = viewModel,
                onBackToDeck = { navController.popBackStack() }
            )
        }
    }
}
