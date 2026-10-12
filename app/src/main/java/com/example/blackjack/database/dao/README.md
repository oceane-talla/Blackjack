# Package database.dao

Depuis le 12 octobre 2026, `getOrCreateBalance()` lit le solde et crée la
ligne initiale seulement si elle manque. `insertGameWithDetails()` ajoute
aussi la variation nette au solde : partie, mains, cartes et paiement sont
enregistrés dans la même transaction. Une erreur annule toutes ces écritures.

`BlackjackDao.kt` est le DAO (Data Access Object) : l'interface qui déclare
les lectures et écritures de la base. Room génère son implémentation grâce
aux annotations et au compilateur configuré avec KSP.

- `insertGame` insère le résumé et retourne l'identifiant attribué par Room.
- `insertHands` et `insertDrawnCards` insèrent les mains et les cartes.
- `insertGameWithDetails` regroupe ces trois écritures dans une transaction.
- `observeHistory` retourne un `Flow` des parties, des plus récentes aux
  plus anciennes; l'identifiant départage deux dates identiques.
- `getHands` lit les mains d'une partie, dans l'ordre de leur numéro.
- `getDrawnCards` lit ses cartes dans l'ordre de distribution.
- `observeStatistics` retourne un `Flow<GameStatistics>` calculé par SQLite.

Les écritures et lectures ponctuelles sont des fonctions `suspend`, à
appeler dans une coroutine. Le `Flow` fournit une liste puis de nouvelles
listes lorsque la table des parties change.

La sauvegarde complète insère d'abord la partie, puis utilise `map` et
`copy` pour donner son identifiant aux mains et aux cartes sans modifier
les objets reçus. Les mains sont insérées avant les cartes du joueur,
car ces cartes doivent référencer une main existante.

`@Transaction` garantit que toutes ces écritures réussissent ensemble.
Si une carte est invalide, aucune partie ni main de cette sauvegarde
n'est conservée. L'erreur est transmise à l'appelant. Aucun remplacement
automatique d'une ligne existante ni suppression n'est ajouté.

Dans les requêtes SQL, `:gameId` reçoit le paramètre Kotlin `gameId`.
`@Query` permet à Room de vérifier les tables et colonnes à la compilation.

Les tests Android `BlackjackDaoTest.kt` vérifient ce comportement avec
SQLite. Le repository est utilisé par `GameViewModel`; l'instance partagée
est fournie par `BlackjackApplication`. Le branchement aux écrans reste à faire.
Les statistiques résument séparément `games`, `hands` et `drawn_cards`, puis
assemblent les trois lignes de résumé avec `CROSS JOIN`. Joindre les lignes
détaillées ferait compter plusieurs fois les mises et les gains.
`COUNT`, `SUM`, `AVG` et `MAX` produisent les compteurs et montants.
`CASE WHEN` sélectionne les valeurs à additionner. `COALESCE` donne zéro
aux sommes vides; `NULLIF` évite de diviser par zéro pour les proportions.

Choix du 9 octobre 2026 : résultats par main, nombre de parties séparé,
répartitions rouge/noir et cœur/carreau/trèfle/pique. Toutes les cartes
distribuées enregistrées sont comptées, y compris celles du croupier.
Les gains sont les variations positives des mains; les pertes sont les
variations négatives exprimées en montant positif. Un Split gagnant/perdant
peut donc contribuer aux deux sommes. Les Blackjacks naturels du joueur
sont comptés même si le résultat est une égalité. Les mises moyenne et
maximale utilisent `totalBet` par partie. Sans parties ou cartes, les
moyennes, maxima et proportions concernés valent `null`, pas un faux zéro.
Les pourcentages vont de 0 à 100, sans arrondi SQL. Aucun schéma ne change.

Ce bloc avance PB-10 et PB-11 sans les déclarer terminés.

Vérification du 8 octobre 2026 : compilation réussie, 52 tests Kotlin
et 15 tests DAO réussis sur Pixel 8. Les commandes et scénarios sont
consignés dans la section 9 de `ARCHITECTURE.md`.
