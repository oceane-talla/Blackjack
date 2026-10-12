# Package repository

Depuis le 12 octobre 2026, `loadBalance()` permet au ViewModel de retrouver
le solde indépendant enregistré dans Room. `saveFinishedGame()` passe par
la transaction du DAO qui conserve ensemble la partie et sa variation de
jetons. Les ViewModels n'accèdent jamais directement à Room.

Ce package contient le point d'accès aux données persistées pour les
ViewModels. Le repository utilise le DAO pour lire et enregistrer les données.

Fichier présent :

- `BlackjackRepository.kt` : enregistrer une partie standard terminée,
  fournir l'historique, les statistiques et lire ses mains et cartes.

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

`observeStatistics()` transmet le `Flow<GameStatistics>` du DAO sans refaire
les calculs en Kotlin. Les résultats sont comptés par main, les mises par
partie, et les cartes incluent le joueur et le croupier. Les répartitions
rouge/noir et par sorte sont calculées en SQL. Room observe les trois tables
utilisées par cette requête.

Le repository reçoit un `BlackjackDao` dans son constructeur. Il ne crée
pas sa propre base : `BlackjackApplication` partage maintenant une base et
un repository, fournis à la Factory des ViewModels. Leur branchement aux
écrans reste à faire. Il prend en charge une seule
main, comme le modèle actuel. La sauvegarde du Split reste à
implémenter. `GameViewModel` utilise maintenant ce repository pour la
sauvegarde automatique et évite les tentatives simultanées et les doubles
enregistrements d'une partie. Son branchement à l'interface reste à faire.

Les tests Android sont dans `BlackjackRepositoryTest.kt`. Ils utilisent
Room et SQLite réels avec des bases isolées, sans modifier l'historique
de production. Ce bloc avance PB-10 et PB-11, sans les déclarer terminés.

Vérification du 8 octobre 2026 : les 12 tests repository et les 15 tests
DAO passent ensemble sur Pixel 8 (27 tests Android). La compilation et
les 70 tests Kotlin passent aussi. Aucune interface n'est modifiée ni
testée dans ce bloc. Voir la section 9 de `ARCHITECTURE.md` pour les
commandes et les scénarios vérifiés.
