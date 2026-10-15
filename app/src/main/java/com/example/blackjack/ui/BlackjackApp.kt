package com.example.blackjack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blackjack.viewmodel.GameViewModel
import com.example.blackjack.viewmodel.HistoryViewModel
import com.example.blackjack.viewmodel.StatisticsViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.blackjack.R
import com.example.blackjack.ui.components.chipText
import com.example.blackjack.viewmodel.GameError
import com.example.blackjack.ui.navigation.BlackjackDestination
import com.example.blackjack.ui.navigation.BlackjackNavHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlackjackApp(gameViewModel: GameViewModel, historyViewModel: HistoryViewModel, statisticsViewModel: StatisticsViewModel) {
    // Le FAB d'accueil suit le même état de partie que les écrans Mise et Partie.
    val gameState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val resumeRound = gameState.roundBet != null && !gameState.canChangeBet
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BlackjackDestination.HOME
    // La mise et la table appartiennent toutes les deux à l'onglet Jouer.
    val selectedTab = if (currentRoute == BlackjackDestination.GAME) {
        BlackjackDestination.BET
    } else {
        currentRoute
    }
    fun openBetScreen() {
        navController.clearBackStack(BlackjackDestination.BET)
        navController.navigate(BlackjackDestination.BET) {
            popUpTo(BlackjackDestination.HOME)
            launchSingleTop = true
        }
    }
    val fabLabel = when {
        gameState.isLoadingBalance -> R.string.loading_balance
        resumeRound -> R.string.resume_game
        else -> R.string.new_game
    }
    val fabEnabled = !gameState.isLoadingBalance &&
        gameState.error != GameError.BALANCE_LOAD_FAILED &&
        (resumeRound || gameState.canStartGame)
    val onFabClick: () -> Unit = if (resumeRound) {
        { navController.navigate(BlackjackDestination.GAME) { launchSingleTop = true } }
    } else {
        { openBetScreen() }
    }
    val tabRoutes = listOf(
        BlackjackDestination.HOME,
        BlackjackDestination.BET,
        BlackjackDestination.HISTORY,
        BlackjackDestination.STATISTICS
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(destinationTitle(currentRoute)))
                        // Le même état que Mise/Partie; aucun accès à Room depuis Compose.
                        val balance = if (gameState.isLoadingBalance ||
                            gameState.error == GameError.BALANCE_LOAD_FAILED
                        ) "—" else chipText(gameState.chips)
                        Text(
                            stringResource(R.string.balance_amount, balance),
                            modifier = Modifier.testTag("global_balance"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (currentRoute == BlackjackDestination.BET ||
                        currentRoute == BlackjackDestination.GAME
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                for (route in tabRoutes) {
                    NavigationBarItem(
                        modifier = Modifier.testTag("navigation_$route"),
                        selected = selectedTab == route,
                        onClick = {
                            val movingBetweenReports =
                                currentRoute in listOf(
                                    BlackjackDestination.HISTORY,
                                    BlackjackDestination.STATISTICS
                                ) && route in listOf(
                                    BlackjackDestination.HISTORY,
                                    BlackjackDestination.STATISTICS
                                )
                            if (movingBetweenReports) {
                                // Depuis Statistiques, Retour doit retrouver Historique, et inversement.
                                navController.navigate(route) { launchSingleTop = true }
                            } else {
                                navController.navigate(route) {
                                    // Les autres onglets conservent leur pile, dont la partie en cours.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = route != BlackjackDestination.HOME
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destinationIcon(route)),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(destinationTitle(route))) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == BlackjackDestination.HOME) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.testTag("new_game_fab").then(
                        if (fabEnabled) Modifier else Modifier.semantics { disabled() }
                    ),
                    onClick = if (fabEnabled) onFabClick else ({ }),
                    containerColor = if (fabEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = if (fabEnabled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    icon = {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    },
                    text = { Text(stringResource(fabLabel)) }
                )
            }
        }
    ) { innerPadding ->
        BlackjackNavHost(
            navController = navController,
            gameState = gameState,
            gameViewModel = gameViewModel,
            historyViewModel = historyViewModel,
            statisticsViewModel = statisticsViewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

private fun destinationTitle(route: String): Int = when (route) {
    BlackjackDestination.BET -> R.string.destination_bet
    BlackjackDestination.GAME -> R.string.destination_game
    BlackjackDestination.HISTORY -> R.string.destination_history
    BlackjackDestination.STATISTICS -> R.string.destination_statistics
    else -> R.string.destination_home
}

private fun destinationIcon(route: String): Int = when (route) {
    BlackjackDestination.BET -> R.drawable.ic_play
    BlackjackDestination.HISTORY -> R.drawable.ic_history
    BlackjackDestination.STATISTICS -> R.drawable.ic_statistics
    else -> R.drawable.ic_home
}
