# Package database.dao

Mise à jour du 13 octobre : `saveGameProgress()` accepte aussi le statut
INTERRUPTED avec la phase INTERRUPTED, sans résultat global ni paiement.
Les cartes restent enregistrées; la lecture de reprise ne sélectionne que
IN_PROGRESS, ce qui permet une nouvelle partie. Une sauvegarde interrompue
peut être réessayée; une partie FINISHED reste protégée. Le ViewModel appelle
maintenant les opérations de progression, de reprise et de règlement via
le repository. Aucune migration : le statut est une colonne texte existante.

## Sauvegarde intermédiaire et lecture de reprise

`saveGameProgress(game, hands, drawnCards, shoeCards)` conserve l'état d'une
partie IN_PROGRESS. Avec un identifiant 0, elle insère la partie et retourne
l'identifiant généré. Avec cet identifiant, les appels suivants actualisent la
même partie et remplacent ses détails : cartes distribuées et sabot, puis
mains supprimés, mains réinsérées avant leurs cartes. Les statistiques ne
comptent donc pas plusieurs fois les anciennes distributions.

La transaction exige un résultat global et une variation globale absents,
une phase, un index de main valide et un nombre de paquets positif. Une main
peut déjà être perdue après Split, mais aucune variation de jetons ne doit
être réglée. Une partie terminée ne peut pas être écrasée. Créer une autre
partie en attente échoue si une partie IN_PROGRESS existe déjà.

`@Transaction` englobe la mise à jour, les suppressions et les insertions.
Si la dernière carte du sabot échoue, l'ancienne sauvegarde revient entièrement;
pour une première sauvegarde, aucune ligne partielle n'est conservée.
Cette opération ne crée pas et ne modifie pas le solde.

`loadGameProgress()` lit la partie IN_PROGRESS et ses trois listes dans une
transaction, afin d'obtenir un état cohérent. Elle retourne `null` s'il n'y en
a pas. `GameProgress` regroupe ces quatre valeurs dans une simple `data class`
déclarée avec le DAO : ce n'est pas une Entity ni une nouvelle table.
Les mains sont triées par numéro, les distributions par `drawOrder` et le
sabot par `position`. `getGame(id)` et `getShoeCards(id)` permettent aussi les
lectures ciblées. `@Update` modifie une ligne existante et retourne le nombre
de lignes modifiées; la transaction exige exactement une ligne.

Le DAO retourne les données telles qu'elles ont été enregistrées. Les anciennes
parties dont les informations de reprise sont nulles ne sont pas complétées
artificiellement. La validation des règles de jeu et la restauration du modèle
restent dans les prochaines étapes. Le repository expose maintenant ces
opérations; le ViewModel ne les appelle pas encore. Le règlement d'une progression est
maintenant disponible dans `finishGameProgress()`, décrit ci-dessous.

Vérification de ce bloc : compilation des APK réussie, 101 tests unitaires
et 55 tests Android ciblés réussis sur Pixel 8 (27,452 s), dont neuf nouveaux
tests `GameProgressDaoTest`. Ils couvrent la conservation après réouverture,
les listes ordonnées, le remplacement sans double comptage, les erreurs de
première sauvegarde et de mise à jour, les protections de l'historique, le
sabot vide et l'absence de paiement intermédiaire. Les migrations, statistiques
et opérations existantes du repository restent vérifiées par les autres tests.
Aucun nouveau test d'interface n'est exécuté ici : les écrans n'utilisent pas
encore ces opérations. PB-11 et PB-15 avancent sans statut Terminé, sans
modification du backlog Excel, commit ni push.

## Règlement de la même partie sauvegardée

`finishGameProgress(game, hands, drawnCards, shoeCards)` attend une partie
FINISHED avec un identifiant existant, une phase FINISHED, ses résultats
et variations définitifs. Elle actualise cette partie sans créer un second
identifiant, remplace ses mains, distributions et sabot par les données finales,
puis applique la variation nette au solde. Les règles et montants restent
calculés par le modèle, pas par cette opération du DAO.

Tout se passe dans une transaction : une erreur de carte, de sabot ou de
paiement conserve l'ancienne progression IN_PROGRESS et l'ancien solde.
Après succès, la partie apparaît une fois dans l'historique et n'est plus
retournée par `loadGameProgress()`. Le sabot final reste disponible par
`getShoeCards(gameId)` pour conserver ses cartes restantes.

Une répétition du même règlement retourne simplement l'identifiant enregistré.
Elle ne réécrit pas les détails et ne paie pas à nouveau. La comparaison utilise
le contenu des cartes, leur ordre et leurs liens, sans leurs identifiants
générés par Room. Un règlement différent pour cette même partie terminée
est refusé; l'historique et le solde restent inchangés.

`insertGameWithDetails()` reste disponible pour l'enregistrement direct d'une
partie terminée sans progression préalable (notamment le chemin encore utilisé
par le ViewModel via le repository). Ce chemin n'est pas modifié. Le repository
expose désormais le règlement d'une progression ayant déjà un identifiant.
La restauration du modèle et le branchement au ViewModel restent à faire.

Vérification du bloc règlement : compilation des APK et 101 tests unitaires
réussis. Sur Pixel 8, 64 tests Android ciblés réussissent (18,43 s), dont neuf
nouveaux tests `GameSettlementDaoTest`. Ils vérifient le même identifiant,
la conservation après réouverture, le sabot final, les demandes répétées et
concurrentes, le refus d'un règlement modifié, les pertes, égalités et un Split
gagnant/perdant. Des erreurs de distribution, de sabot et de paiement restaurent
la progression et le solde précédents. Les tests du repository existant passent.
Pas de nouveau test UI : cette opération n'est pas encore appelée depuis
les écrans. PB-11 et PB-15 avancent sans statut Terminé ni modification Excel,
commit ou push.

## Opérations déjà utilisées par l'application

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
automatique d'une ligne existante ni suppression n'est ajouté par cette
transaction de règlement final. Les suppressions de détails décrites ci-dessus
appartiennent uniquement à la mise à jour d'une progression.

Dans les requêtes SQL, `:gameId` reçoit le paramètre Kotlin `gameId`.
`@Query` permet à Room de vérifier les tables et colonnes à la compilation.

Les tests Android `BlackjackDaoTest.kt` vérifient ce comportement avec
SQLite. Le repository est utilisé par `GameViewModel`; l'instance partagée
est fournie par `BlackjackApplication`. La sauvegarde finale est branchée aux
écrans; la sauvegarde intermédiaire reste à brancher.
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
