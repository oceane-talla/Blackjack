package com.example.blackjack.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Observe les parties sauvegardées en passant uniquement par le repository.
class HistoryViewModel(private val repository: BlackjackRepository) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = flow {
        // Propre à chaque reprise de l'observation; conserve la liste en cas d'erreur.
        var lastState = HistoryUiState()
        emitAll(
            repository.observeHistory()
                // map transforme chaque liste de Room en données pour l'écran.
                .map { games ->
                    lastState = HistoryUiState(games = games, isLoading = false)
                    lastState
                }
                // catch laisse passer l'annulation et publie une erreur de lecture.
                .catch { exception ->
                    Log.e("HistoryViewModel", "Failed to observe game history.", exception)
                    emit(lastState.copy(isLoading = false, hasError = true))
                }
        )
    }.stateIn(
        // L'observation est annulée lorsque le ViewModel est détruit.
        scope = viewModelScope,
        // Démarre au premier abonné; s'arrête 5 secondes après le départ du dernier.
        // L'écran s'abonne avec collectAsStateWithLifecycle(), comme dans Atelier3.
        started = SharingStarted.WhileSubscribed(5_000),
        // Disponible immédiatement, avant la première réponse de Room.
        initialValue = HistoryUiState()
    )
}
