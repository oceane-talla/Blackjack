# Package viewmodel

Mise à jour du 12 octobre 2026 : le solde est maintenant enregistré dans
Room indépendamment de l'historique. À sa création, `GameViewModel` appelle
`repository.loadBalance()` et bloque les mises et le démarrage pendant cette
lecture. Une erreur permet de réessayer, sans remettre le solde à 1000.
La fin d'une partie sauvegarde l'historique et la variation du solde dans
une même transaction. La mise choisie et une partie en cours restent en
mémoire. Les mentions antérieures de la persistance décrivent l'état de
ces étapes à leur date.

Ce package contient les ViewModels : les classes qui conservent l'état utile
aux écrans et coordonnent les actions de l'utilisateur.

Fichiers présents :

- `GameViewModel.kt` : coordonner la mise et la partie en cours.
- `GameUiState.kt` : regrouper les données affichables et les erreurs identifiées
  par `GameError` (mise refusée, interruption, échec de sauvegarde).

`HistoryViewModel.kt` et `HistoryUiState.kt` observent l'historique fourni par
le repository. L'état contient les résumés des parties, le chargement initial
et un indicateur d'erreur de lecture. Room transmet automatiquement une
nouvelle liste lorsque les données changent, en conservant le tri du DAO.
Une erreur conserve la dernière liste reçue et termine le chargement;
l'observation échouée ne redémarre pas automatiquement. Aucun détail de partie
ni commande de suppression n'est ajouté. HistoryScreen reçoit maintenant cet état via le NavHost.

`StatisticsViewModel.kt` et `StatisticsUiState.kt` observent maintenant
`repository.observeStatistics()`. L'état contient le résumé `GameStatistics`,
le chargement initial et l'erreur éventuelle. Les compteurs, sommes, moyennes
et proportions viennent de SQLite : le ViewModel ne charge pas toute la liste
des parties pour les recalculer. Une erreur conserve les dernières valeurs;
elle termine l'observation sans reprise automatique. StatisticsScreen reçoit
cet état via le NavHost.

Vérification du 9 octobre 2026 : les cinq tests `StatisticsViewModelTest`
et les six tests SQL `BlackjackStatisticsTest` passent avec les 46 tests
Android existants sur Pixel 8 (57 au total). Compilation réussie et 74
tests unitaires réussis. Les scénarios et commandes sont documentés dans
`ARCHITECTURE.md`. PB-13 et PB-15 avancent; PB-14 attend le branchement
et la construction de l'écran. Aucun de ces PBI n'est déclaré terminé.

L'état est exposé avec `StateFlow`, qui fournit une valeur actuelle et ses
changements. Le ViewModel conserve un `MutableStateFlow` privé et expose
`asStateFlow()` en lecture seule, comme dans `Atelier3`.
Compose l'observe avec `collectAsStateWithLifecycle()`, afin
d'adapter cette observation au cycle de vie de l'écran.

Chemins utilisés :

- Action de jeu : Compose → ViewModel → Model. Le modèle applique les règles,
  puis le ViewModel actualise l'état affiché.
- Données persistées : Compose → ViewModel → Repository → DAO → Room → SQLite.
  Un ViewModel n'accède jamais directement au DAO ni à Room.

Un ViewModel conserve l'état lors d'une rotation, mais ne garantit pas sa
conservation après l'arrêt du processus Android.

## Jeu, mise et règlement

`selectBet(amount)` demande au modèle de valider une mise de 1 à 100,
compatible avec les jetons disponibles. Une mise invalide ne remplace
pas la précédente; la mise ne change pas pendant une partie active.
Le solde initial est de 1000 jetons et la mise initiale de 1.

`selectDeckCount(count)` conserve un choix de 1 à 8 paquets, verrouillé comme
la mise pendant le jeu et les sauvegardes. `startGame()` démarre avec un sabot
neuf contenant le nombre choisi. Le constructeur public crée le modèle avec
`Shoe(count)`. Le constructeur interne permet aux tests de fournir des
parties déterministes, comme dans les tests du modèle.

`hit()` et `stand()` transmettent les actions au modèle puis publient
un nouvel état. Le ViewModel ne calcule pas les mains ni les résultats.
Pendant le tour du joueur, la deuxième carte du croupier devient une
entrée `null` dans l'état affichable et `dealerTotal` est aussi `null`.
Les valeurs complètes restent uniquement dans le modèle. À la fin,
les cartes et le total du croupier sont révélés.

`selectedBet` est la mise sélectionnée pour la prochaine partie;
`roundBet` garde celle de la partie courante ou terminée. Le solde reste
inchangé pendant le jeu puis reçoit une seule fois la variation nette
calculée par `calculateChipChange(roundBet)`. Une interruption ne produit
ni règlement ni sauvegarde. La mise sélectionnée reste conservée entre
les parties; si elle dépasse le solde restant, il faut choisir une mise
abordable pour démarrer.

## Sauvegarde et erreurs

Une partie terminée est sauvegardée automatiquement avec
`viewModelScope.launch`, en passant par le repository. `isSaving`
indique l'écriture en cours et `savedGameId` reçoit l'identifiant retourné.
Une nouvelle partie et un changement de mise attendent cette sauvegarde,
pour garder les données de la partie stables et éviter les doublons.

En cas d'erreur, le ViewModel publie `SAVE_FAILED` et écrit l'exception
dans Logcat. `retrySave()` permet de reprendre l'écriture avec la même
date et la même mise, sans appliquer le paiement une deuxième fois.
La partie reste conservée en mémoire jusqu'à la reprise. GameScreen propose
Réessayer la sauvegarde lorsque canRetrySave est vrai.
Une annulation de coroutine n'est pas transformée en erreur de sauvegarde.

L'état contient des codes d'erreur, pas des textes français ou anglais :
l'écran les associe aux ressources françaises et anglaises.
Ces méthodes sont destinées aux actions de l'interface, sur le thread principal.

`BlackjackApplication` partage maintenant la base et le repository.
`BlackjackViewModelFactory` reçoit ce repository et construit l'un des trois
ViewModels selon la classe demandée. Une classe non prise en charge produit
une erreur explicite. La conversion `as T` suit le mécanisme du cours :
chaque branche vérifie le type demandé avant de retourner le ViewModel.

Selon le choix A, `MainActivity` expose `gameViewModel`, `historyViewModel`
et `statisticsViewModel` avec `by lazy` et `ViewModelProvider(this, factory)`.
Le premier accès demande l'instance; le Provider la conserve dans le
ViewModelStore de l'Activity. La Factory construit, mais ne conserve pas
elle-même les ViewModels. Lors d'une rotation, une nouvelle Activity retrouve
ces mêmes instances. Un changement d'onglet ne les supprime pas.

Les écrans ne consomment pas encore ces propriétés : leur transmission depuis
`MainActivity` à Compose reste à faire. Mise et Partie devront recevoir le
même `gameViewModel`, sans en créer chacun un autre. Le solde et la mise sont
en mémoire : leur conservation après arrêt du processus reste une décision
ultérieure. L'Application et les ViewModels sont recréés après cet arrêt,
alors que le fichier SQLite conserve les parties déjà enregistrées.

Vérification du bloc Factory et partage, le 9 octobre 2026 : compilation
réussie et 74 tests unitaires réussis. Les cinq tests de Factory et les trois
tests de partage passent sur Pixel 8. Ils vérifient notamment une vraie
rotation conservant les trois ViewModels et la mise sélectionnée. Les cinq
tests de navigation passent en portrait puis en paysage, soit 18 exécutions
Android réussies pour ce bloc. Les commandes et limites sont consignées
dans `ARCHITECTURE.md`. Les écrans ne sont pas encore branchés aux données.

## Vérification du 8 octobre 2026

La compilation finale réussit et les 74 tests Kotlin passent (73 métier
et le test d'exemple). Quatre tests vérifient les limites de mise et les
soldes insuffisants, négatifs, non finis ou contenant des demi-jetons.

Sur Pixel 8, les 15 tests de `GameViewModelTest` passent avec les 12 tests
repository et les 15 tests DAO : 42 tests Android réussis. Ils vérifient
les états, le masquage, les actions, les paiements uniques, les mises
conservées, le nouveau modèle, les interruptions, le solde épuisé et les
sauvegardes sans doublons. Un échec volontaire de la première écriture
vérifie la reprise : une seule partie est finalement enregistrée et le
solde n'est pas crédité deux fois. Les actions sont exécutées sur le
thread principal Android; les sauvegardes utilisent Room réel en mémoire.

Les commandes et limites de ces preuves sont dans la section 9 de
`ARCHITECTURE.md`. PB-05, PB-06, PB-07, PB-10, PB-11 et PB-15 avancent,
sans être déclarés terminés ni modifier le backlog Excel.
