package com.example.blackjack.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// Observe les statistiques via le repository ; les calculs restent dans les requêtes SQL.
class StatisticsViewModel(private val repository: BlackjackRepository) : ViewModel() {
    // Source modifiable privée et accès en lecture seule destiné à Compose.
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    // Démarre une seule observation lors de la création de cette instance.
    init {
        // L'observation est annulée lorsque le ViewModel est définitivement supprimé.
        viewModelScope.launch {
            repository.observeStatistics()
                .catch { exception ->
                    // Conserve les dernières valeurs reçues si la lecture échoue.
                    // catch laisse passer l'annulation de la coroutine.
                    Log.e("StatisticsViewModel", "Failed to observe game statistics.", exception)
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
                .collect { statistics ->
                    // Room transmet un nouveau résumé quand une table concernée change.
                    _uiState.value = StatisticsUiState(statistics = statistics, isLoading = false)
                }
        }
    }
}
