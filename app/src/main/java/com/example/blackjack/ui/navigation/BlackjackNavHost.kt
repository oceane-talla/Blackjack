package com.example.blackjack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.blackjack.ui.screens.bet.BetScreen
import com.example.blackjack.ui.screens.game.GameScreen
import com.example.blackjack.ui.screens.history.HistoryScreen
import com.example.blackjack.ui.screens.home.HomeScreen
import com.example.blackjack.ui.screens.statistics.StatisticsScreen

@Composable
fun BlackjackNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BlackjackDestination.HOME,
        modifier = modifier
    ) {
        composable(route = BlackjackDestination.HOME) {
            HomeScreen(onStartGame = {
                navController.navigate(BlackjackDestination.BET) {
                    launchSingleTop = true
                }
            })
        }
        composable(route = BlackjackDestination.BET) {
            BetScreen(onOpenTable = {
                navController.navigate(BlackjackDestination.GAME) {
                    launchSingleTop = true
                }
            })
        }
        composable(route = BlackjackDestination.GAME) {
            GameScreen(onNewGame = {
                navController.navigate(BlackjackDestination.BET) {
                    popUpTo(BlackjackDestination.BET) // vide la pile vue qu on recommence une nouvelle partie
                    launchSingleTop = true // empeche de rajouter 2 fois la meme destination
                }
            })
        }
        composable(route = BlackjackDestination.HISTORY) {
            HistoryScreen()
        }
        composable(route = BlackjackDestination.STATISTICS) {
            StatisticsScreen()
        }
    }
}
