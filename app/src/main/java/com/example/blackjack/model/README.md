# Package model

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

Les mises, les paiements et le Split ne sont pas implémentés ici.
Les écrans et ViewModels ne sont pas encore branchés à ces classes;
le masquage de la carte du croupier sera géré lors de ce branchement.

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
