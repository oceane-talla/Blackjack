# Package repository

Ce package contient le point d'accès aux données persistées pour les
ViewModels. Le repository utilise le DAO pour lire et enregistrer les données.

Fichier présent :

- `BlackjackRepository.kt` : enregistrer une partie standard terminée,
  fournir l'historique et lire ses mains et cartes.

Chemin prévu : Compose → ViewModel → Repository → DAO → Room → SQLite.

`saveFinishedGame(game, bet, playedAt)` refuse une partie inachevée ou une
mise non positive. Il demande au modèle `calculateChipChange(bet)`, puis
construit une `GameEntity`, une `HandEntity` et les `DrawnCardEntity` à partir
de `dealtCards`. Les noms des enums (`WIN`, `ACE`, etc.) deviennent les
chaînes enregistrées. La date est fournie en millisecondes; si elle est
omise, la fonction utilise l'heure au moment de la demande de sauvegarde.

Les `gameId = 0` des détails sont provisoires. Le DAO récupère l'identifiant
de la partie insérée, l'applique aux mains et cartes et enregistre le tout
dans sa transaction. Le repository retourne cet identifiant et laisse
remonter les erreurs. La sauvegarde ne modifie pas la partie en mémoire.

Une Entity décrit une table de la base; un DAO déclare les opérations sur les
données. Les règles du Blackjack restent dans le package `model`.

`observeHistory()` fournit le `Flow` du DAO : les parties les plus récentes
en premier. `getHands(gameId)` et `getDrawnCards(gameId)` sont des lectures
ponctuelles `suspend`. La sauvegarde est aussi `suspend`; ces appels
s'utilisent dans une coroutine.

Le repository reçoit un `BlackjackDao` dans son constructeur. Il ne crée
pas sa propre base : l'instance partagée dans `BlackjackApplication` et le
branchement des ViewModels restent à faire. Il prend en charge une seule
main, comme le modèle actuel. Le Split et les statistiques restent à
implémenter. Éviter un double enregistrement de la même partie sera aussi
une responsabilité du futur parcours de sauvegarde dans le ViewModel.

Les tests Android sont dans `BlackjackRepositoryTest.kt`. Ils utilisent
Room et SQLite réels avec des bases isolées, sans modifier l'historique
de production. Ce bloc avance PB-10 et PB-11, sans les déclarer terminés.

Vérification du 8 octobre 2026 : les 12 tests repository et les 15 tests
DAO passent ensemble sur Pixel 8 (27 tests Android). La compilation et
les 70 tests Kotlin passent aussi. Aucune interface n'est modifiée ni
testée dans ce bloc. Voir la section 9 de `ARCHITECTURE.md` pour les
commandes et les scénarios vérifiés.
