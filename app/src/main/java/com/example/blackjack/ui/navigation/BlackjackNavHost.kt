package com.example.blackjack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.blackjack.ui.screens.bet.BetScreen
import com.example.blackjack.ui.screens.game.GameScreen
import com.example.blackjack.ui.screens.history.HistoryScreen
import com.example.blackjack.ui.screens.home.HomeScreen
import com.example.blackjack.ui.screens.statistics.StatisticsScreen
import com.example.blackjack.viewmodel.GameUiState
import com.example.blackjack.viewmodel.GameViewModel
import com.example.blackjack.viewmodel.HistoryViewModel
import com.example.blackjack.viewmodel.StatisticsViewModel

@Composable
fun BlackjackNavHost(
    navController: NavHostController,
    gameState: GameUiState,
    gameViewModel: GameViewModel,
    historyViewModel: HistoryViewModel,
    statisticsViewModel: StatisticsViewModel,
    modifier: Modifier = Modifier
) {
    fun openTable() {
        navController.navigate(BlackjackDestination.GAME) { launchSingleTop = true }
    }
    NavHost(navController, startDestination = BlackjackDestination.HOME, modifier = modifier) {
        composable(BlackjackDestination.HOME) {
            HomeScreen(onStartGame = {
                if (gameState.roundBet != null && !gameState.canChangeBet) openTable()
                else navController.navigate(BlackjackDestination.BET) { launchSingleTop = true }
            })
        }
        composable(BlackjackDestination.BET) {
            BetScreen(gameState,
                onSelectBet = { gameViewModel.selectBet(it) },
                onSelectDeckCount = { gameViewModel.selectDeckCount(it) },
                onRetryBalanceLoad = { gameViewModel.retryLoadBalance() },
                onOpenTable = {
                    // Le modèle doit accepter la distribution avant d'ouvrir la table.
                    if (gameViewModel.startGame()) openTable()
                },
                onResume = { openTable() }
            )
        }
        composable(BlackjackDestination.GAME) {
            GameScreen(gameState,
                onHit = { gameViewModel.hit() },
                onStand = { gameViewModel.stand() },
                onSplit = { gameViewModel.split() },
                onRetrySave = { gameViewModel.retrySave() },
                onNewGame = {
                    navController.clearBackStack(BlackjackDestination.BET)
                    navController.navigate(BlackjackDestination.BET) {
                        // Fonctionne aussi si le FAB a repris la table depuis Accueil.
                        popUpTo(BlackjackDestination.HOME)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BlackjackDestination.HISTORY) {
            val state by historyViewModel.uiState.collectAsStateWithLifecycle()
            HistoryScreen(state)
        }
        composable(BlackjackDestination.STATISTICS) {
            val state by statisticsViewModel.uiState.collectAsStateWithLifecycle()
            StatisticsScreen(state)
        }
    }
}
