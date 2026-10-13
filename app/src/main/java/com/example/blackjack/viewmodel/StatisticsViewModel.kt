package com.example.blackjack.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Observe les statistiques via le repository; les calculs restent dans SQL.
class StatisticsViewModel(private val repository: BlackjackRepository) : ViewModel() {
    private val summaryStates = flow {
        var lastState = StatisticsUiState()
        // Permet à combine de publier une erreur même si l'autre requête attend encore.
        emit(lastState)
        emitAll(repository.observeStatistics()
            .map { statistics ->
                lastState = StatisticsUiState(statistics = statistics, isLoading = false)
                lastState
            }
            .catch { exception ->
                Log.e("StatisticsViewModel", "Failed to observe game statistics.", exception)
                emit(lastState.copy(isLoading = false, hasError = true))
            })
    }

    private val rankStates = flow {
        var lastState = StatisticsUiState()
        emit(lastState)
        emitAll(repository.observeRankStatistics()
            .map { ranks ->
                lastState = StatisticsUiState(rankStatistics = ranks, isLoading = false)
                lastState
            }
            .catch { exception ->
                Log.e("StatisticsViewModel", "Failed to observe rank statistics.", exception)
                emit(lastState.copy(isLoading = false, hasError = true))
            })
    }

    // combine réunit les dernières valeurs des deux lectures dans l'état de l'écran.
    // Une erreur d'une lecture n'arrête pas l'autre; chacune conserve ses dernières données.
    val uiState: StateFlow<StatisticsUiState> = combine(summaryStates, rankStates) { summary, ranks ->
        val hasError = summary.hasError || ranks.hasError
        StatisticsUiState(
            statistics = summary.statistics,
            rankStatistics = ranks.rankStatistics,
            isLoading = (summary.isLoading || ranks.isLoading) && !hasError,
            hasError = hasError
        )
    }.stateIn(
        scope = viewModelScope,
        // Même fonctionnement qu'Atelier3 et HistoryViewModel.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState()
    )
}
