# Package database.entity

Ce package contient les Entity de Room : les classes qui décrivent les
tables. Chaque objet Entity représente une ligne enregistrée.

Trois fichiers créés selon l'option A choisie le 8 octobre 2026 :

- `GameEntity.kt` : décrire le résumé d'une partie enregistrée.
- `HandEntity.kt` : conserver séparément la mise, le total, le résultat,
  le Blackjack naturel et la variation des jetons de chaque main du joueur.
- `DrawnCardEntity.kt` : décrire une carte distribuée et son contexte dans
  la partie.

`@Entity` nomme la table. `@PrimaryKey(autoGenerate = true)` permet à Room
de générer les identifiants de partie et de carte lors des insertions.
Leur valeur 0 signifie qu'aucun identifiant n'a encore été attribué.

La clé primaire d'une main est composée de `gameId` et `handNumber` :
la main 1 peut exister dans plusieurs parties, mais une seule fois dans
chaque partie. `@ForeignKey` indique que `gameId` doit référencer une
partie existante; les cartes du joueur doivent aussi référencer une main
de cette même partie. Les contraintes seront appliquées une fois la
base Room créée et utilisée.

Dans `DrawnCardEntity`, `handNumber = null` indique une carte du croupier;
1 ou 2 indique une main du joueur. Rang et couleur sont les noms anglais
de `Rank` et `Suit`, comme `KING` et `SPADES`. `drawOrder` indique la
position de la carte dans la distribution entière, à partir de 1.
Un index unique empêche deux positions identiques dans une partie,
sans interdire des cartes de mêmes valeurs provenant de plusieurs paquets.

La date `playedAt` est stockée en millisecondes. Les mises sont des `Int`;
les variations de jetons sont des `Double` pour permettre des demi-jetons
si le paiement retenu le nécessite. Aucune règle de paiement n'est décidée
par ces classes. La convention du résultat résumé d'une partie avec des
résultats différents après Split reste à choisir lors du règlement.

Exemple de liaison future : une partie porte l'identifiant 12, sa
première main porte `(gameId = 12, handNumber = 1)` et chacune des cartes
de cette main reprend cette paire. Les cartes du croupier portent
`gameId = 12` et `handNumber = null`.

Ces classes décriront les données conservées. `BlackjackGame` et `Card`,
dans le package `model`, resteront responsables de la représentation et
des règles du jeu en mémoire.

Room 3.0.3 est ajouté, mais le DAO, la base et le repository restent à
créer : ces objets ne sont pas encore sauvegardés. La compilation des
annotations ne remplace pas les tests d'insertion et de lecture qui
seront faits avec la base réelle. Cette étape prépare PB-10 et PB-11.

Vérification du 8 octobre 2026 : la compilation avec Room 3.0.3 réussit
et les 51 tests métier existants passent (52 avec le test d'exemple).

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```
