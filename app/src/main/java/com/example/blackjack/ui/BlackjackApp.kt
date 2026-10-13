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
    // Le FAB et les écrans Mise/Partie lisent le même état et le même ViewModel.
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
    val fabLabel = when (currentRoute) {
        BlackjackDestination.BET -> if (resumeRound) R.string.resume_game else R.string.open_table
        BlackjackDestination.GAME -> when {
            gameState.canHit -> R.string.hit
            gameState.canChangeBet -> R.string.new_game
            else -> R.string.hit
        }
        else -> if (resumeRound) R.string.resume_game else R.string.new_game
    }
    val fabEnabled = when (currentRoute) {
        BlackjackDestination.BET -> gameState.canStartGame || resumeRound
        BlackjackDestination.GAME -> gameState.canHit || gameState.canChangeBet
        else -> true
    }
    val onFabClick: () -> Unit = when (currentRoute) {
        BlackjackDestination.BET -> if (resumeRound) {
            { navController.navigate(BlackjackDestination.GAME) { launchSingleTop = true } }
        } else {
            {
                if (gameViewModel.startGame()) {
                    navController.navigate(BlackjackDestination.GAME) { launchSingleTop = true }
                }
            }
        }
        BlackjackDestination.GAME -> when {
            gameState.canHit -> ({ gameViewModel.hit() })
            gameState.canChangeBet -> ({ openBetScreen() })
            else -> ({ })
        }
        else -> if (resumeRound) {
            { navController.navigate(BlackjackDestination.GAME) { launchSingleTop = true } }
        } else {
            { openBetScreen() }
        }
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
                            navController.navigate(route) {
                                // On évite d'empiler les onglets; leur parcours est conservé.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                // Accueil reste la racine, sans restaurer le parcours de Jouer.
                                restoreState = route != BlackjackDestination.HOME
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
    ) { innerPadding ->
        BlackjackNavHost(
            navController = navController,
            gameState = gameState,
            gameViewModel = gameViewModel,
            historyViewModel = historyViewModel,
            statisticsViewModel = statisticsViewModel,
            // Le FAB garde sa zone : aucun bouton défilant ne peut être caché dessous.
            modifier = Modifier.padding(innerPadding).padding(bottom = 80.dp)
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
