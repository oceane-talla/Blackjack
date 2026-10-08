# Package viewmodel

Ce package contiendra les ViewModels : les classes qui conservent l'état utile
aux écrans et coordonnent les actions de l'utilisateur.

Fichiers prévus dans `ARCHITECTURE.md`, à créer aux étapes correspondantes :

- `GameViewModel.kt` : coordonner la mise et la partie en cours.
- `HistoryViewModel.kt` : observer les parties enregistrées.
- `StatisticsViewModel.kt` : observer les statistiques fournies par le repository.

L'état sera exposé avec `StateFlow`, qui fournit une valeur actuelle et ses
changements. Compose l'observera avec `collectAsStateWithLifecycle()`, afin
d'adapter cette observation au cycle de vie de l'écran.

Chemins prévus :

- Action de jeu : Compose → ViewModel → Model. Le modèle applique les règles,
  puis le ViewModel actualise l'état affiché.
- Données persistées : Compose → ViewModel → Repository → DAO → Room → SQLite.
  Un ViewModel n'accède jamais directement au DAO ni à Room.

Un ViewModel conserve l'état lors d'une rotation, mais ne garantit pas sa
conservation après l'arrêt du processus Android.

Ce README réserve le package pour PB-01. Aucun ViewModel ni StateFlow n'est
encore implémenté par cette étape.
