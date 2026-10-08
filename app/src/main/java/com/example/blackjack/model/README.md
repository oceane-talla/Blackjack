# Package model

Ce package contient les bases du Blackjack en Kotlin, indépendantes de
l'interface Android et de la base de données.

Fichiers présents, conformément à l'option A choisie le 8 octobre 2026 :

- `Card.kt` : `Card` est une `data class` qui représente le rang et la couleur
  d'une carte. Les `enum class` `Rank` et `Suit` définissent les valeurs
  autorisées. `baseValue` donne 10 aux figures et initialement 11 à l'As.
- `Hand.kt` : conserver les cartes, ajouter une carte, calculer le total et
  détecter le dépassement. Pour chaque As réduit de 11 à 1, le total diminue
  de 10. `hasBlackjackValue()` reconnaît uniquement un 21 en deux cartes;
  le contexte du Blackjack naturel appartiendra à `BlackjackGame`.
- `Shoe.kt` : recevoir un nombre de paquets strictement positif, créer 52
  cartes par paquet et mélanger localement. `drawCard()` retire une carte,
  ou renvoie `null` si le sabot est vide, selon ton choix explicite.

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

`BlackjackGame.kt` reste à créer à l'étape choisie par l'utilisateur.
Les tours, les mises, les paiements et le Split ne sont pas implémentés ici.
Les écrans et ViewModels ne sont pas encore branchés à ces classes.

Les tests se trouvent dans `app/src/test/java/com/example/blackjack/model` :
`CardTest`, `HandTest` et `ShoeTest`. Ils s'exécutent sur l'ordinateur avec
JUnit, sans émulateur. Cette étape concerne les bases de PB-03 et PB-04;
elle ne marque aucun PBI terminé dans le backlog.

Vérification du 8 octobre 2026 : la compilation réussit et les 21 tests du
modèle passent (3 pour les cartes, 11 pour les mains et 7 pour le sabot).

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```
