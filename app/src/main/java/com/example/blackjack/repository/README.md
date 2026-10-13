# Package repository

## Progression — 12 octobre 2026

`saveGameProgress()` convertit un `BlackjackGame` commencé en données Room :
phase, main active, mise, nombre de paquets, mains, distributions et sabot
restant ordonné. Un identifiant 0 crée la sauvegarde; les appels suivants
réutilisent l'identifiant retourné. Aucun paiement n'est effectué ici.

`loadGameProgress()` retourne ces données dans `GameProgress`, ou `null`.
Cette lecture fournit les données brutes du moteur de jeu.

Depuis le 13 octobre, `restoreGameProgress()` reconstruit `Shoe` et
`BlackjackGame`, puis retourne un `RestoredGame` avec la mise, le nombre de
paquets, l'identifiant et la date à conserver. `null` signifie qu'aucune partie
n'est en attente. Les noms des enums, positions, références de mains et
totaux sont vérifiés; une erreur remonte sans effacer la sauvegarde ni payer.
La reconstruction ne distribue aucune carte. La reprise du tour du croupier
n'est pas automatique. Le ViewModel appelle cette fonction au démarrage.

Vérification du 13 octobre : compilation et 108 tests Kotlin réussis;
24 tests Android du repository réussis sur l'émulateur disponible
Pixel_10_Pro (3,657 s). Les tests couvrent la réouverture puis la continuation
et le règlement de la même partie, le Split restauré et le refus d'une phase
inconnue sans suppression des données. Pas de nouvelle vérification UI ni
de validation Pixel 8 dans ce bloc. PB-11 et PB-15 restent en cours.

`finishGameProgress()` utilise les résultats et variations calculés par le
modèle, puis demande au DAO de terminer la même partie et de payer une seule
fois. L'identifiant et la date doivent rester identiques lors d'une nouvelle
tentative. Les erreurs remontent à l'appelant; la transaction conserve la
sauvegarde et le solde précédents.

Ce nouveau chemin accepte les deux mains d'un Split. Le ViewModel utilise
ces opérations pour la partie en cours et son règlement. Un blackjack
immédiat utilise encore `saveFinishedGame()`, sans progression préalable.
Une partie interrompue reçoit le statut INTERRUPTED, sans paiement, pour
libérer la prochaine partie tout en conservant ses distributions.
`remainingCards` est fourni explicitement : Shoe permet maintenant de lire
et restaurer sa liste ordonnée. Sa dernière carte est la prochaine tirée.

Vérification : compilation réussie, 101 tests Kotlin et 74 tests Android
ciblés réussis sur Pixel 8 (19,317 s), dont 10 nouveaux tests dans
`GameProgressRepositoryTest`. Ils vérifient la conversion du vrai modèle,
la réouverture, le Split, les As séparés, les erreurs et l'absence de double
paiement. Aucun nouveau test UI : ces opérations ne sont pas branchées aux
écrans. PB-11 et PB-15 avancent sans être déclarés terminés.

## Ancien chemin de sauvegarde finale

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
un repository, fournis à la Factory des ViewModels. Les écrans sont branchés
aux ViewModels. L'ancien chemin de sauvegarde prend en charge une seule
main. `GameViewModel` utilise ce chemin pour la
sauvegarde automatique et évite les tentatives simultanées et les doubles
enregistrements d'une partie.

Les tests Android sont dans `BlackjackRepositoryTest.kt`. Ils utilisent
Room et SQLite réels avec des bases isolées, sans modifier l'historique
de production. Ce bloc avance PB-10 et PB-11, sans les déclarer terminés.

Vérification du 8 octobre 2026 : les 12 tests repository et les 15 tests
DAO passent ensemble sur Pixel 8 (27 tests Android). La compilation et
les 70 tests Kotlin passent aussi. Aucune interface n'est modifiée ni
testée dans ce bloc. Voir la section 9 de `ARCHITECTURE.md` pour les
commandes et les scénarios vérifiés.
