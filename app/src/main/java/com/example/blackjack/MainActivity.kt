package com.example.blackjack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.example.blackjack.ui.BlackjackApp
import com.example.blackjack.ui.theme.BlackjackTheme
import com.example.blackjack.viewmodel.BlackjackViewModelFactory
import com.example.blackjack.viewmodel.GameViewModel
import com.example.blackjack.viewmodel.HistoryViewModel
import com.example.blackjack.viewmodel.StatisticsViewModel

class MainActivity : ComponentActivity() {
    // Chaque Activity utilise le repository de l'Application, conservée lors d'une rotation.
    private val viewModelFactory: BlackjackViewModelFactory by lazy {
        val blackjackApplication = application as BlackjackApplication
        BlackjackViewModelFactory(blackjackApplication.repository)
    }

    // by lazy ne demande l'instance qu'au premier accès.
    // this rattache le ViewModel à l'Activity : Mise et Partie devront recevoir cette même valeur.
    // Lors d'une rotation, ViewModelProvider retrouve l'instance déjà conservée par Android.
    val gameViewModel: GameViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[GameViewModel::class.java]
    }

    // Ces deux ViewModels partagent aussi le repository, avec leur propre état observable.
    val historyViewModel: HistoryViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[HistoryViewModel::class.java]
    }

    val statisticsViewModel: StatisticsViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[StatisticsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackjackTheme {
                    BlackjackApp(gameViewModel, historyViewModel, statisticsViewModel)
            }
        }
        // Le fond casino est sombre, même lorsque le téléphone utilise le thème clair.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
