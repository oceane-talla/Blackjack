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
    // Les tests peuvent préparer un sabot presque vide pour vérifier son renouvellement.
    private val createShoe: (Int) -> Shoe = { Shoe(numberOfDecks = it) },
    // Une nouvelle partie peut recevoir le sabot déjà utilisé par la précédente.
    private val createGame: (Shoe) -> BlackjackGame
) : ViewModel() {
    // Le ViewModel conserve le sabot; le modèle conserve seulement la partie courante.
    constructor(repository: BlackjackRepository) : this(
        repository,
        createGame = { shoe -> BlackjackGame(shoe) }
    )

    // Modèle contenant la partie réelle, ses règles et la carte cachée du croupier.
    private var shoe = createShoe(1)
    private var game = createGame(shoe)
    private var gameDeckCount = 1
    // Empêche d'ajouter ou de retirer les jetons plusieurs fois pour la même partie.
    private var roundSettled = false
    // Date de fin en millisecondes, conservée si la sauvegarde doit être réessayée.
    private var finishedAt: Long = 0
    private var progressId: Long = 0
    private var startedAt: Long = 0

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

    // Attend le solde et la partie sauvegardée avant d'autoriser les actions.
    private fun loadBalance() {
        _uiState.value = _uiState.value.copy(isLoadingBalance = true, canStartGame = false, error = null)
        viewModelScope.launch {
            try {
                val chips = repository.loadBalance()
                val restored = repository.restoreGameProgress()
                if (restored != null) {
                    shoe = restored.shoe
                    game = restored.game
                    gameDeckCount = restored.deckCount
                    progressId = restored.gameId
                    startedAt = restored.playedAt
                    _uiState.value = _uiState.value.copy(
                        chips = chips,
                        selectedBet = restored.bet, roundBet = restored.bet,
                        selectedDeckCount = restored.deckCount
                    )
                    publishGameState(save = false)
                    return@launch
                }
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

        // Quatre cartes suffisent à la distribution initiale (choix A).
        // Aucun renouvellement en plein tour : un épuisement reste une interruption.
        val deckCount = _uiState.value.selectedDeckCount
        val replaceShoe = gameDeckCount != deckCount || shoe.remainingCards < 4
        if (replaceShoe) {
            shoe = createShoe(deckCount)
            gameDeckCount = deckCount
        }
        if (game.phase != GamePhase.READY || replaceShoe) game = createGame(shoe)
        roundSettled = false
        progressId = 0
        startedAt = System.currentTimeMillis()
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

    // Demande au modèle de séparer la paire, puis sauvegarde les deux mains avec la même partie.
    fun split(): Boolean {
        val state = _uiState.value
        if (!state.canSplit) return false

        val accepted = game.split(checkNotNull(state.roundBet), state.chips)
        publishGameState()
        return accepted
    }

    // Réessaie seulement la sauvegarde : ne rejoue pas et ne règle pas à nouveau les jetons.
    fun retrySave() {
        if (_uiState.value.canRetrySave) saveCurrentGame()
    }

    // Après une action, transforme les données du modèle en état destiné à l'écran.
    private fun publishGameState(save: Boolean = true) {
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

        // L'énoncé cache la première carte du croupier pendant le tour du joueur.
        val hideDealerCard = game.phase == GamePhase.PLAYER_TURN
        _uiState.value = state.copy(
            isLoadingBalance = false,
            chips = chips,
            playerCards = game.playerCards,
            playerHands = game.playerHands,
            activeHandIndex = game.activeHandIndex,
            dealerCards = game.dealerCards.mapIndexed { index, card ->
                // Les indices commencent à 0 : 0 désigne la première carte.
                // null masque seulement l'affichage ; la vraie carte reste dans le modèle.
                if (hideDealerCard && index == 0) null else card
            },
            playerTotal = game.playerValue,
            dealerTotal = if (hideDealerCard) null else game.dealerValue,
            phase = game.phase,
            result = game.result,
            playerHasBlackjack = game.playerHasBlackjack,
            chipChange = chipChange,
            // Attend le règlement et la sauvegarde avant d'autoriser une nouvelle partie.
            // La mise sélectionnée doit aussi rester compatible avec le nouveau solde.
            canStartGame = game.phase in listOf(GamePhase.READY, GamePhase.FINISHED, GamePhase.INTERRUPTED) && !newlyFinished &&
                game.validateBet(state.selectedBet, chips),
            canHit = game.canHit,
            canStand = game.canStand,
            canSplit = !state.isSaving && state.error != GameError.SAVE_FAILED &&
                state.roundBet?.let { game.canSplit(it, chips) } == true,
            error = if (game.phase == GamePhase.INTERRUPTED) GameError.ROUND_INTERRUPTED else null
        )
        // Chaque action sauvegarde l'état; une restauration seule ne réécrit pas les données.
        if (save) saveCurrentGame()
    }

    // Sauvegarde la progression, le règlement ou l'interruption selon la phase du modèle.
    private fun saveCurrentGame() {
        val state = _uiState.value
        // Le résultat reste stable jusqu'à la fin de cette sauvegarde ou de sa reprise.
        val finishedGame = game
        val bet = checkNotNull(state.roundBet)
        val playedAt = if (game.phase == GamePhase.FINISHED) finishedAt else startedAt
        // Empêche une nouvelle partie de remplacer celle qu'on est en train de sauvegarder.
        _uiState.value = state.copy(isSaving = true, canStartGame = false,
            canHit = false, canStand = false, canSplit = false, error = null)

        // Lance une coroutine liée au ViewModel pour attendre l'écriture sans bloquer l'interface.
        viewModelScope.launch {
            try {
                // Le repository passe par le DAO et Room, puis retourne l'identifiant créé.
                val id = when {
                    finishedGame.phase != GamePhase.FINISHED -> repository.saveGameProgress(
                        finishedGame, bet, gameDeckCount, shoe.remainingCardList, progressId, playedAt)
                    progressId > 0 -> repository.finishGameProgress(
                        finishedGame, bet, gameDeckCount, shoe.remainingCardList, progressId, playedAt)
                    // Un blackjack à la distribution est déjà terminé, sans progression préalable.
                    else -> repository.saveFinishedGame(finishedGame, bet, playedAt)
                }
                progressId = id
                // Rend la main à l'écran et autorise une nouvelle partie si la mise est abordable.
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedGameId = if (game.phase == GamePhase.FINISHED) id else null,
                    canHit = game.canHit, canStand = game.canStand,
                    canSplit = game.canSplit(bet, _uiState.value.chips),
                    error = if (game.phase == GamePhase.INTERRUPTED) GameError.ROUND_INTERRUPTED else null,
                    canStartGame = game.phase in listOf(GamePhase.FINISHED, GamePhase.INTERRUPTED) &&
                        game.validateBet(bet, _uiState.value.chips)
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
