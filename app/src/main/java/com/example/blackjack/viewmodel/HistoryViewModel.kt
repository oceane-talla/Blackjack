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

// Observe les parties sauvegardées en passant uniquement par le repository.
class HistoryViewModel(private val repository: BlackjackRepository) : ViewModel() {
    // Le ViewModel peut modifier l'état ; l'écran reçoit un accès en lecture seule.
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // init s'exécute à la création de cette instance : une seule observation est démarrée.
    init {
        // La coroutine est annulée lorsque le ViewModel est définitivement supprimé.
        viewModelScope.launch {
            repository.observeHistory()
                // catch traite les erreurs du Flow, sans transformer son annulation en erreur.
                .catch { exception ->
                    Log.e("HistoryViewModel", "Failed to observe game history.", exception)
                    // copy conserve la dernière liste reçue si la lecture échoue ensuite.
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
                .collect { games ->
                    // Room émet la liste initiale, puis une nouvelle liste lorsque les données changent.
                    // Aucun bouton de rechargement n'est nécessaire pour voir une nouvelle partie.
                    _uiState.value = HistoryUiState(games = games, isLoading = false)
                }
        }
    }
}
