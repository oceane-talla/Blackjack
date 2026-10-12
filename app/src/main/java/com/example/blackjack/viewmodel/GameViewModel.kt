package com.example.blackjack.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.Shoe
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Reçoit les actions de l'écran, demande au modèle de jouer et publie les données à afficher.
class GameViewModel internal constructor(
    // Intermédiaire pour sauvegarder une partie, sans accéder directement au DAO ou à Room.
    private val repository: BlackjackRepository,
    // Fonction créant une nouvelle partie ; les tests peuvent fournir des cartes prévisibles.
    private val createGame: (Int) -> BlackjackGame
) : ViewModel() {
    // Chaque partie reçoit un sabot neuf contenant le nombre de paquets choisi.
    constructor(repository: BlackjackRepository) : this(
        repository,
        { deckCount -> BlackjackGame(Shoe(numberOfDecks = deckCount)) }
    )

    // Modèle contenant la partie réelle, ses règles et la carte cachée du croupier.
    private var game = createGame(1)
    private var gameDeckCount = 1
    // Empêche d'ajouter ou de retirer les jetons plusieurs fois pour la même partie.
    private var roundSettled = false
    // Date de fin en millisecondes, conservée si la sauvegarde doit être réessayée.
    private var finishedAt: Long = 0

    // État observable modifiable uniquement à l'intérieur du ViewModel.
    private val _uiState = MutableStateFlow(GameUiState())
    // Accès en lecture au même état : Compose pourra l'observer pour actualiser l'écran.
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        loadBalance()
    }

    // Le bouton de l'écran Mise relance seulement une lecture ayant échoué.
    fun retryLoadBalance() {
        if (_uiState.value.error == GameError.BALANCE_LOAD_FAILED && !_uiState.value.isLoadingBalance) {
            loadBalance()
        }
    }

    // Attend Room avant d'autoriser une partie : 1000 n'est pas un solde de secours.
    private fun loadBalance() {
        _uiState.value = _uiState.value.copy(isLoadingBalance = true, canStartGame = false, error = null)
        viewModelScope.launch {
            try {
                val chips = repository.loadBalance()
                _uiState.value = _uiState.value.copy(
                    chips = chips, isLoadingBalance = false,
                    canStartGame = game.validateBet(_uiState.value.selectedBet, chips)
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e("GameViewModel", "Failed to load the chip balance.", exception)
                _uiState.value = _uiState.value.copy(
                    isLoadingBalance = false, error = GameError.BALANCE_LOAD_FAILED
                )
            }
        }
    }

    // Appelé lors du choix d'une mise ; retourne true si le montant est accepté.
    fun selectBet(amount: Int): Boolean {
        val state = _uiState.value
        // Bloque les changements pendant la partie ou tant qu'une sauvegarde reste à terminer.
        if (!state.canChangeBet) return false
        // Le modèle vérifie les limites de mise et le solde disponible.
        if (!game.validateBet(amount, state.chips)) {
            _uiState.value = state.copy(error = GameError.BET_NOT_ALLOWED)
            return false
        }

        // copy crée un nouvel état en conservant les propriétés non mentionnées.
        _uiState.value = state.copy(selectedBet = amount, canStartGame = true, error = null)
        return true
    }

    // Le choix est verrouillé pendant le jeu et les sauvegardes, comme la mise.
    fun selectDeckCount(count: Int): Boolean {
        if (!_uiState.value.canChangeBet || count !in 1..8) return false
        _uiState.value = _uiState.value.copy(selectedDeckCount = count)
        return true
    }

    // Appelé par le bouton pour commencer ; retourne le résultat de la distribution initiale.
    fun startGame(): Boolean {
        if (!_uiState.value.canStartGame) return false

        // Remplace aussi le premier sabot si le choix a changé avant de distribuer.
        val deckCount = _uiState.value.selectedDeckCount
        if (game.phase != GamePhase.READY || gameDeckCount != deckCount) {
            game = createGame(deckCount)
            gameDeckCount = deckCount
        }
        roundSettled = false
        // Fixe la mise de cette partie et efface les informations du règlement précédent.
        // Le solde reste inchangé : seule la variation nette sera appliquée à la fin.
        _uiState.value = _uiState.value.copy(
            roundBet = _uiState.value.selectedBet,
            chipChange = null, savedGameId = null, error = null
        )
        // Le modèle distribue les cartes et vérifie notamment les Blackjacks naturels.
        val started = game.startRound()
        publishGameState()
        return started
    }

    // Appelé par Tirer ; le modèle décide des conséquences de la carte reçue.
    fun hit(): Boolean {
        if (!_uiState.value.canHit) return false
        val accepted = game.hit()
        publishGameState()
        return accepted
    }

    // Appelé par Rester ; le modèle fait jouer le croupier et détermine le résultat.
    fun stand(): Boolean {
        if (!_uiState.value.canStand) return false
        val accepted = game.stand()
        publishGameState()
        return accepted
    }

    // Réessaie seulement la sauvegarde : ne rejoue pas et ne règle pas à nouveau les jetons.
    fun retrySave() {
        if (_uiState.value.canRetrySave) saveFinishedGame()
    }

    // Après une action, transforme les données du modèle en état destiné à l'écran.
    private fun publishGameState() {
        val state = _uiState.value
        val finished = game.phase == GamePhase.FINISHED
        // Vrai uniquement si cette partie est terminée mais n'a pas encore été réglée.
        val newlyFinished = finished && !roundSettled
        var chips = state.chips
        var chipChange = state.chipChange
        if (newlyFinished) {
            // Une partie terminée doit avoir une mise ; checkNotNull vérifie cette condition.
            // Le modèle calcule le gain ou la perte, puis on l'applique une seule fois au solde.
            chipChange = game.calculateChipChange(checkNotNull(state.roundBet))
            chips += chipChange
            roundSettled = true
            finishedAt = System.currentTimeMillis()
        }

        // Pendant le tour du joueur, l'écran ne doit pas connaître la deuxième carte ni le total.
        val hideDealerCard = game.phase == GamePhase.PLAYER_TURN
        _uiState.value = state.copy(
            chips = chips,
            playerCards = game.playerCards,
            dealerCards = game.dealerCards.mapIndexed { index, card ->
                // Les indices commencent à 0 : 1 désigne la deuxième carte.
                // null masque seulement l'affichage ; la vraie carte reste dans le modèle.
                if (hideDealerCard && index == 1) null else card
            },
            playerTotal = game.playerValue,
            dealerTotal = if (hideDealerCard) null else game.dealerValue,
            phase = game.phase,
            result = game.result,
            playerHasBlackjack = game.playerHasBlackjack,
            chipChange = chipChange,
            // Attend le règlement et la sauvegarde avant d'autoriser une nouvelle partie.
            // La mise sélectionnée doit aussi rester compatible avec le nouveau solde.
            canStartGame = !game.canHit && !newlyFinished &&
                game.validateBet(state.selectedBet, chips),
            canHit = game.canHit,
            canStand = game.canStand,
            error = if (game.phase == GamePhase.INTERRUPTED) GameError.ROUND_INTERRUPTED else null
        )
        // Déclenche automatiquement l'enregistrement lorsqu'une partie vient de se terminer.
        if (newlyFinished) saveFinishedGame()
    }

    // Confie la partie terminée au repository et publie le succès ou l'échec de l'enregistrement.
    private fun saveFinishedGame() {
        val state = _uiState.value
        // Le résultat reste stable jusqu'à la fin de cette sauvegarde ou de sa reprise.
        val finishedGame = game
        val bet = checkNotNull(state.roundBet)
        val playedAt = finishedAt
        // Empêche une nouvelle partie de remplacer celle qu'on est en train de sauvegarder.
        _uiState.value = state.copy(isSaving = true, canStartGame = false, error = null)

        // Lance une coroutine liée au ViewModel pour attendre l'écriture sans bloquer l'interface.
        viewModelScope.launch {
            try {
                // Le repository passe par le DAO et Room, puis retourne l'identifiant créé.
                val id = repository.saveFinishedGame(finishedGame, bet, playedAt)
                // Rend la main à l'écran et autorise une nouvelle partie si la mise est abordable.
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedGameId = id,
                    canStartGame = game.validateBet(bet, _uiState.value.chips)
                )
            } catch (exception: CancellationException) {
                // Une annulation de coroutine doit se propager, sans devenir une erreur de sauvegarde.
                throw exception
            } catch (exception: Exception) {
                // Conserve la partie pour réessayer ; les jetons déjà réglés ne sont pas modifiés.
                Log.e("GameViewModel", "Failed to save the finished game.", exception)
                _uiState.value = _uiState.value.copy(
                    isSaving = false, error = GameError.SAVE_FAILED
                )
            }
        }
    }
}
