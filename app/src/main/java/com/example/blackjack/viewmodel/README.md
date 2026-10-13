# Package viewmodel

## Sauvegarde et reprise — 13 octobre 2026

Au démarrage, `GameViewModel` lit le solde et appelle `restoreGameProgress()`.
La partie retrouvée remplace le modèle initial avec sa mise, son identifiant
et son sabot. La fin du chargement et les cartes sont publiées ensemble.
Après distribution, Tirer ou Rester, `saveCurrentGame()` bloque les actions
pendant l'écriture. Un échec permet de réessayer sans rejouer ni payer à nouveau.
Une progression est réglée avec `finishGameProgress()` et le même identifiant.
Un blackjack immédiat utilise encore la sauvegarde finale directe.

Une interruption utilise le statut INTERRUPTED : ses cartes sont conservées,
sans paiement, et une nouvelle partie est permise. Elle est exclue de
l'historique des parties réglées; ses distributions comptent en statistiques.
La colonne texte existante suffit, sans migration Room.

Compilation, 108 tests Kotlin et 75 tests Android ciblés réussis sur
Pixel_10_Pro (7,18 s), dont la recréation du ViewModel et l'échec/reprise d'une
sauvegarde intermédiaire. La fermeture réelle du processus sur Pixel 8 reste
à démontrer. Le sabot entre deux parties après fermeture n'est pas restauré;
la reprise concerne une partie en cours sauvegardée avec succès.
PB-11 et PB-15 restent en cours; aucun statut Excel, commit ou push.

Le test Compose `standSavesOnceAndUpdatesHistoryAndSqlStatistics` passe aussi
en portrait (25,275 s) et paysage (24,722 s) sur Pixel_10_Pro : actions,
règlement, historique et statistiques. Il ne simule pas la mort du processus.

## Notes des étapes précédentes

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
Une erreur conserve la dernière liste reçue pendant l'observation et termine
le chargement. Aucun détail de partie
ni commande de suppression n'est ajouté. HistoryScreen reçoit maintenant cet état via le NavHost.

`StatisticsViewModel.kt` et `StatisticsUiState.kt` observent maintenant
`repository.observeStatistics()` et `repository.observeRankStatistics()`.
L'état contient le résumé `GameStatistics`, les statistiques des 13 rangs,
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
changements. GameViewModel conserve un `MutableStateFlow` privé et expose
`asStateFlow()` en lecture seule.
Depuis le 12 octobre 2026, HistoryViewModel utilise `stateIn()` avec
`SharingStarted.WhileSubscribed(5_000)`, comme CalculViewModel dans Atelier3.
`map` transforme les listes en HistoryUiState et `catch` publie les erreurs.
`flow` et `emitAll` gardent un dernier état propre à chaque observation pour
conserver sa liste si une erreur survient ensuite.
L'observation démarre au premier abonné et s'arrête cinq secondes après
le départ du dernier. Le StateFlow conserve sa dernière valeur pendant
l'arrêt; au retour de l'écran, Room fournit l'historique actuel. Lire
`uiState.value` seul ne démarre pas la lecture. L'état initial indique
le chargement. Une erreur ne déclenche pas de boucle de nouvelle tentative;
une observation peut redémarrer après un arrêt puis un nouvel abonnement.
Compose l'observe avec `collectAsStateWithLifecycle()`, afin
d'adapter cette observation au cycle de vie de l'écran.

StatisticsViewModel utilise maintenant le même `stateIn()` avec
`WhileSubscribed(5_000)`. `summaryStates` prépare le résumé et `rankStates`
prépare les rangs : map transforme les données, catch conserve les dernières
données de la lecture et signale une erreur. Chaque lecture émet d'abord
son état de chargement. `combine` assemble leurs dernières valeurs dans un
seul StatisticsUiState, sans calcul statistique supplémentaire en Kotlin.
Le chargement attend les deux réponses, sauf erreur. L'échec d'une lecture
n'empêche pas l'autre de s'actualiser. Sans abonné, les deux lectures s'arrêtent
après cinq secondes et reprennent à la prochaine observation par l'écran.

Vérification du bloc StatisticsViewModel/stateIn : compilation réussie;
sur Pixel 8, 9 tests StatisticsViewModel, 5 tests Factory et 2 parcours
d'interface passent. Une ancienne attente du test Factory a été corrigée
de 10,5 à 14 pour un Blackjack naturel à mise 7, conformément au paiement
du modèle déjà approuvé; aucun paiement de production n'a changé dans ce bloc.
Les tests vérifient le délai d'arrêt des deux lectures, leur reprise après
sauvegarde pendant l'arrêt, le chargement et les erreurs indépendantes.
PB-13, PB-14 et PB-15 concernés; combine/stateIn démontrables en Sprint Review.
Aucun statut Excel changé, aucun commit ni push effectué dans ce bloc.

Vérification du bloc stateIn : compilation application/tests réussie et
7 tests ciblés sur Pixel 8 réussis (6 HistoryViewModelTest et le parcours
Partie → Historique → Statistiques). Le délai d'arrêt et la reprise après
une insertion pendant l'arrêt sont vérifiés. PB-12 et PB-15 concernés;
l'utilisation de stateIn peut être présentée en Sprint Review. Aucun statut
Excel changé, aucun commit ni push effectué dans ce bloc.

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
la mise pendant le jeu et les sauvegardes. Le ViewModel conserve son `Shoe`
entre les parties. `startGame()` crée un nouveau sabot si le nombre de paquets
change ou s'il reste moins de quatre cartes (choix A, 12 octobre 2026).
Avec exactement quatre cartes, le sabot est conservé. Chaque nouvelle partie
reçoit un nouveau `BlackjackGame` mais peut partager le sabot précédent.
Le constructeur interne permet aux tests de préparer des sabots presque vides
et des parties déterministes. Aucun renouvellement n'a lieu pendant un tour :
si les quatre cartes initiales ne suffisent pas pour finir la partie, celle-ci
peut encore être interrompue. Le sabot reste en mémoire, sans persistance
après destruction du processus. Cette étape ne change pas la sauvegarde des
parties abandonnées ni l'intégration du Split.

Vérification du renouvellement : compilation application/tests réussie et
101 tests unitaires réussis. Sur Pixel 8 : 22 GameViewModelTest et le test
du sélecteur de paquets passent (23 tests, 49,561 s). Le sélecteur passe
aussi en paysage (1 test, 38,664 s). Les tests couvrent exactement quatre
cartes restantes, zéro à trois cartes, réutilisation du même sabot et
changement du nombre de paquets. Les textes français et anglais ont été
alignés; l'anglais n'a pas été vérifié visuellement dans ce bloc.
PB-03 et PB-15 concernés; les statuts Scrum ne sont pas déclarés terminés
et aucune Sprint Review réelle n'est inventée. Aucun commit ni push effectué.

`hit()` et `stand()` transmettent les actions au modèle puis publient
un nouvel état. Le ViewModel ne calcule pas les mains ni les résultats.
Pendant le tour du joueur, la première carte du croupier devient une
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
