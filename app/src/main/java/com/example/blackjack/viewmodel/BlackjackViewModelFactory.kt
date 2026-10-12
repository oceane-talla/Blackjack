package com.example.blackjack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.blackjack.repository.BlackjackRepository

// Explique à ViewModelProvider comment construire nos ViewModels avec leur repository.
// La Factory construit ; ViewModelProvider conserve et retrouve les instances de l'Activity.
class BlackjackViewModelFactory(
    private val repository: BlackjackRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // modelClass indique quel ViewModel Android demande. Chaque branche reçoit le même repository.
        val viewModel = when (modelClass) {
            GameViewModel::class.java -> GameViewModel(repository)
            HistoryViewModel::class.java -> HistoryViewModel(repository)
            StatisticsViewModel::class.java -> StatisticsViewModel(repository)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }

        // T est le type demandé. Les branches ci-dessus vérifient qu'on construit ce type précis.
        // Le compilateur ne peut pas déduire ce lien à travers Class<T>, d'où cette conversion.
        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
