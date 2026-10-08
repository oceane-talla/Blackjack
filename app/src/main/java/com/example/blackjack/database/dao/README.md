# Package database.dao

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
SQLite. Le repository et l'instance partagée dans l'application restent
à créer; les écrans ne sont donc pas encore reliés à la sauvegarde.
Les requêtes de statistiques restent à définir à leur étape.

Ce bloc avance PB-10 et PB-11 sans les déclarer terminés.

Vérification du 8 octobre 2026 : compilation réussie, 52 tests Kotlin
et 15 tests DAO réussis sur Pixel 8. Les commandes et scénarios sont
consignés dans la section 9 de `ARCHITECTURE.md`.
