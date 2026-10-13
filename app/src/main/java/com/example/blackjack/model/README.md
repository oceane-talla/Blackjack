# Package model

Restauration ajoutée le 12 octobre 2026 : `Shoe(savedCards)` copie un sabot
ordonné sans le mélanger; `remainingCardList` fournit une copie de ses cartes.
`BlackjackGame.restore()` reconstruit les mains à partir des `DealtCard`,
restaure leurs résultats, la main active et la phase, sans distribution ni
paiement. Le modèle ne reçoit aucune Entity Room. L'ordre de distribution,
les références de mains et la cohérence du résultat global sont vérifiés;
ce n'est pas encore une validation complète de toute sauvegarde corrompue.
Une phase interrompue reste interrompue; restaurer une phase DEALER_TURN ne
relance pas automatiquement le croupier. Le branchement au ViewModel reste
à faire, donc aucune reprise après fermeture n'est encore démontrée dans l'UI.

Compilation et 108 tests Kotlin réussis, dont sept tests dans
`GameRestorationTest` : ordre du sabot, copies indépendantes, continuation
standard et Split, blackjack naturel, As séparés, interruption et données
invalides. Aucun nouveau test Android pour cette étape limitée au modèle.
PB-11 et PB-15 avancent, sans modification du backlog Excel ni action Git.

Ce package contient les bases du Blackjack en Kotlin, indépendantes de
l'interface Android et de la base de données.

Fichiers présents, créés dans les blocs choisis le 8 octobre 2026 :

- `Card.kt` : `Card` est une `data class` qui représente le rang et la couleur
  d'une carte. Les `enum class` `Rank` et `Suit` définissent les valeurs
  autorisées. `baseValue` donne 10 aux figures et initialement 11 à l'As.
- `Hand.kt` : conserver les cartes, ajouter une carte, calculer le total et
  détecter le dépassement. Pour chaque As réduit de 11 à 1, le total diminue
  de 10. `hasBlackjackValue()` reconnaît uniquement un 21 en deux cartes;
  `BlackjackGame` vérifie le Blackjack naturel à la distribution initiale.
- `Shoe.kt` : recevoir un nombre de paquets strictement positif, créer 52
  cartes par paquet et mélanger localement. `drawCard()` retire une carte,
  ou renvoie `null` si le sabot est vide, selon ton choix explicite.
- `BlackjackGame.kt` : distribuer les cartes, gérer Tirer et Rester, faire
  jouer le croupier et produire victoire, défaite ou égalité. Il contient
  aussi les `enum class` `GamePhase` et `GameResult` pour les valeurs
  possibles de la phase et du résultat.
- `DealtCard.kt` : représenter une carte distribuée, son destinataire
  (`handNumber = 1` pour le joueur, `null` pour le croupier) et son ordre
  global de distribution, à partir de 1.
- `GameStatistics.kt` : recevoir les résultats calculés par SQLite (compteurs,
  gains, pertes, mises et proportions de cartes). Cette `data class` ne contient
  ni règles du jeu, ni SQL, ni annotation `@Entity` : elle ne crée pas de table.

`Hand.cards` fournit une copie de la liste : la main se modifie par
`addCard()`. `remainingCards` indique la taille de la réserve du sabot.
`shuffle()` mélange uniquement les cartes restantes, sans réintroduire
les cartes pigées. Plusieurs paquets peuvent contenir des cartes de mêmes
valeurs, mais chaque objet créé représente un exemplaire distinct.

Exemple du chemin des données, utilisable sans écran :

```kotlin
val shoe = Shoe(numberOfDecks = 1)
val hand = Hand()
val card = shoe.drawCard()
if (card != null) {
    hand.addCard(card)
}
val total = hand.calculateValue()
```

Le paquet unique est un exemple, pas une décision sur le nombre de paquets
utilisé dans l'application. Le constructeur de `Shoe` n'a pas de nombre
par défaut et le sabot ne se renouvelle pas automatiquement.

La partie standard fonctionne avec un sabot fourni par l'appelant :

```kotlin
val shoe = Shoe(numberOfDecks = 1)
val game = BlackjackGame(shoe)
game.startRound()
```

`startRound()` distribue joueur, croupier, joueur, croupier. Un Blackjack
naturel termine immédiatement la partie; deux Blackjacks donnent une
égalité. Sinon `hit()` tire une carte et `stand()` lance le tour du
croupier. Un total de 21 obtenu en tirant lance aussi ce tour, sans devenir
un Blackjack naturel. Le croupier s'arrête sur tout 17 ou plus, même As + 6.
Un dépassement du joueur entraîne une défaite sans faire tirer le croupier.

Les mains sont privées; `playerCards` et `dealerCards` fournissent des
copies, `playerValue` et `dealerValue` leurs totaux. `phase`, `result`,
`playerHasBlackjack` et `dealerHasBlackjack` utilisent `private set` :
le code extérieur peut les lire, mais seul le modèle peut les remplacer.
`canHit` et `canStand` indiquent si les actions sont permises.

Le résultat reste `null` tant qu'il n'est pas établi. Si le sabot est
épuisé, la phase devient `INTERRUPTED`, sans victoire, défaite, égalité
ni renouvellement automatique. Les cartes déjà tirées restent retirées.
Les actions renvoient `false` en cas de refus ou d'interruption; ce retour
ne remet pas dans le sabot les cartes déjà distribuées. Une nouvelle
partie est permise après une fin ou une interruption et utilise la même
source de cartes.

Le constructeur `internal` permet aux tests de fournir une fonction
`() -> Card?` : elle ne reçoit aucun paramètre et retourne la prochaine
carte ou `null`. Les scénarios sont ainsi reproductibles, sans modifier
`Shoe` ni ajouter une bibliothèque.

`dealtCards` fournit une copie du suivi global des cartes effectivement
distribuées. Une nouvelle partie vide ce suivi; un tirage impossible
n'ajoute aucune entrée. Le repository utilise ce suivi pour sauvegarder
l'ordre et le destinataire de chaque carte.

`calculateChipChange(bet)` fournit une variation nette en `Double` pour
une partie terminée et une mise positive : défaite `-bet`, égalité `0`,
victoire ordinaire `+bet`, Blackjack naturel gagnant `+2 * bet`, conformément
à l'énoncé (page 4). Deux Blackjacks naturels restent une égalité.
La fonction ne modifie pas un solde : celui-ci sera géré à son étape.
`validateBet(amount, availableChips)` accepte les montants de 1 à 100
qui ne dépassent pas un solde fini. `GameViewModel` utilise cette validation
et applique la variation nette au solde une seule fois à la fin de la partie.
Le modèle gère maintenant le Split (voir ci-dessous). Son branchement au
repository, au ViewModel et au bouton de l'écran reste à réaliser.
La partie standard est branchée; le ViewModel masque la première carte
du croupier pendant le tour du joueur.

### Split dans le modèle — 12 octobre 2026

`split(bet, availableChips)` vérifie d'abord `canSplit()` : deux cartes de
même rang, solde couvrant les deux mises et aucun Split déjà effectué.
Il crée deux mains, puis distribue une carte à chacune. La deuxième carte
initiale du joueur appartient désormais à la main 2; son `drawOrder` ne
change pas, et aucune carte n'est repigée ou dupliquée dans le suivi.

`playerHands` fournit une liste de `PlayerHandState`, des copies consultables
contenant numéro, cartes, total, indicateur de Blackjack et résultat.
`activeHandIndex` vaut 0 puis 1 : `playerCards`, `playerValue`, `hit()` et
`stand()` concernent cette main active. Rester, dépasser 21 ou atteindre 21
passe à la seconde main; le croupier joue après l'arrêt des deux mains.
Si elles sont toutes perdues par dépassement, il n'a pas besoin de tirer.

Choix A/B/B de l'utilisateur (règle corrigée le 12 octobre) : un 21 après
Split est ordinaire, payé une fois la mise s'il gagne, et fait égalité contre
un 21 du croupier. Il n'est jamais marqué comme Blackjack naturel.
Chaque As séparé reçoit une seule carte
et s'arrête automatiquement; des résultats différents produisent le résumé
`MIXED`, même si le bilan de jetons est positif ou négatif. Chaque main
garde WIN, LOSS ou DRAW. `calculateHandChipChange(handNumber, bet)` calcule
son paiement; `calculateChipChange(bet)` additionne les deux. Le modèle ne
modifie pas lui-même un solde. Un tirage impossible interrompt la partie
sans autoriser son règlement.

Le repository refuse temporairement les parties avec Split : il faut encore
adapter sa conversion des deux mains avant toute sauvegarde. Aucun bouton
Split n'est ajouté à l'interface par cette étape.

Les tests se trouvent dans `app/src/test/java/com/example/blackjack/model` :
`CardTest`, `HandTest`, `ShoeTest` et `BlackjackGameTest`. Ils s'exécutent
sur l'ordinateur avec JUnit, sans émulateur. Les bases concernent PB-03
et PB-04; la partie standard avance PB-04, PB-06 et PB-07 dans le modèle.
Aucun PBI n'est marqué terminé dans le backlog.

Vérification du 8 octobre 2026 : la compilation réussit et les 21 tests du
modèle initial passent (3 pour les cartes, 11 pour les mains et 7 pour le
sabot), ainsi que les 30 tests de la partie standard : 51 tests métier.

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Vérification complémentaire du 8 octobre 2026 : 18 tests sont ajoutés
pour le suivi des cartes et le calcul des jetons. Les 48 tests de
`BlackjackGameTest` passent avec les 21 autres tests métier et le test
d'exemple : 70 tests Kotlin réussis. Les scénarios et commandes sont
consignés dans la section 9 de `ARCHITECTURE.md`.

Après ajout de `validateBet`, quatre tests complémentaires passent :
52 tests dans `BlackjackGameTest`, 74 tests Kotlin au total. Le bloc
ViewModel est aussi vérifié par 15 tests Android sur Pixel 8.
