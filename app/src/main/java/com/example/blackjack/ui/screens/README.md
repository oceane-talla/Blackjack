# Package ui.screens

Les écrans reçoivent un état et des callbacks (fonctions appelées lors des clics).
Ils affichent les données, sans appeler Room ni calculer les règles du Blackjack.

- HomeScreen : accueil et cartes décoratives, bouton vers la mise ou la partie active.
- BetScreen : solde, jetons de sélection rapide, curseur entier de 1 à 100, distribution.
- GameScreen : mains, totaux, carte cachée, Tirer/Rester, règlement et sauvegarde.
- HistoryScreen : liste Room observable, chargement, liste vide et erreur de lecture.
- StatisticsScreen : indicateurs calculés par SQLite, chargement et erreur de lecture.

ScreenContent fournit le défilement vertical. HistoryScreen utilise LazyColumn pour
ne composer que les lignes visibles d’un historique potentiellement long.

Exemple : Tirer → onHit → GameViewModel.hit → BlackjackGame.hit → nouvel état.
À la fin : ViewModel → Repository → DAO → Room, puis les Flow de lecture actualisent
l’historique et les statistiques. Les boutons lisent canHit/canStand/canStartGame :
ils n’essaient pas de reproduire les règles du modèle.

La mise est bloquée pendant le jeu et tant que sa sauvegarde n’est pas résolue.
La partie standard est branchée; Split reste une étape distincte.
