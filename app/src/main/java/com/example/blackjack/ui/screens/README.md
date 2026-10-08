# Package ui.screens

Ce package contient les écrans de l'application. Chaque écran est décrit
par un composable, une fonction Compose qui affiche les données reçues et
transmet les actions de l'utilisateur.

Fichiers et sous-packages présents, conformément à `ARCHITECTURE.md` :

- `home/HomeScreen.kt` : afficher l'accueil et transmettre l'action pour
  commencer une partie.
- `bet/BetScreen.kt` : afficher le solde, la mise sélectionnée et les
  contrôles de mise.
- `game/GameScreen.kt` : afficher la table, les cartes et les actions du jeu.
- `history/HistoryScreen.kt` : afficher les parties enregistrées.
- `statistics/StatisticsScreen.kt` : afficher les statistiques demandées.

Exemple prévu : le joueur appuie sur « Tirer ». `GameScreen` appelle le
callback `onHit`, une fonction reçue en paramètre. Celui-ci transmet l'action
au `GameViewModel`, qui appelle le modèle et actualise l'état affiché.

Les écrans affichent les résultats des règles du Blackjack sans les
calculer eux-mêmes. Pour les données persistées, le chemin prévu est :
Compose → ViewModel → Repository → DAO → Room → SQLite.

Pour PB-02, les cinq écrans utilisent `ScreenContent` pour partager
l'espacement et le défilement. L'accueil mène à la mise avec `onStartGame`.
L'écran de mise appelle `onOpenTable` lorsque « Voir la table » est pressé.
La table appelle `onNewGame` lorsque « Changer la mise » est pressé.
Ces callbacks sont des fonctions reçues en paramètre; le NavHost leur
donne une action de navigation.

Les écrans de mise, de table, d'historique et de statistiques sont
provisoires : ils permettent de vérifier la navigation, sans prétendre
gérer une mise, distribuer des cartes ou lire une base. Les fonctionnalités
du jeu et les données restent à construire dans leurs PBI respectifs.
