# Package database

`BlackjackDatabase.kt` déclare la base Room, ses cinq tables (`GameEntity`,
`HandEntity`, `DrawnCardEntity`, `PlayerBalanceEntity`, `ShoeCardEntity`)
et l'accès à `BlackjackDao`.

- `dao` : déclarer les lectures et écritures.
- `entity` : décrire les tables et les données enregistrées.

Room organise l'accès à SQLite, le moteur qui conserve les données sur
l'appareil. La classe de base est `abstract` : Room génère la classe
concrète à la compilation, comme l'implémentation du DAO.

La configuration reprend les outils utilisés dans `Atelier3` :
Room 3.0.3, le compilateur `room3-compiler` avec KSP 2.3.6 et
`sqlite-framework` 2.7.1 pour `AndroidSQLiteDriver`.

Le schéma est à la version 4. `MIGRATION_1_2` ajoute le solde à 1000 jetons
en conservant l'historique existant. `MIGRATION_2_3` conserve ce solde et
recopie parties, mains et cartes pour autoriser un résultat et une variation
de jetons absents. `GameEntity.status` distingue IN_PROGRESS et FINISHED;
les anciennes parties deviennent FINISHED. `MIGRATION_3_4` ajoute les colonnes
de reprise et la table du sabot restant, sans modifier les résultats ni le solde.
`BlackjackApplication` enregistre les trois migrations.
`exportSchema = false` reprend la configuration du cours : aucun
fichier de schéma n'est exporté. Aucune suppression automatique n'est configurée.

Les tests Android ouvrent une base en mémoire pour les opérations et
une base de test sur disque pour vérifier la conservation après fermeture.
Cette dernière porte un nom réservé aux tests et est supprimée ensuite.

L'instance partagée est créée dans `BlackjackApplication`, une classe
Android distincte du composable `BlackjackApp`, déclarée dans le manifeste.
`database` et `repository` utilisent `by lazy` : un premier accès les crée,
puis les suivants retrouvent les mêmes instances pendant ce processus.
La base utilise `applicationContext`, `AndroidSQLiteDriver` et le fichier
`blackjack.db`, avec les migrations 1 vers 2, 2 vers 3 puis 3 vers 4,
sans suppression automatique.
La Factory fournit ce repository aux ViewModels conservés par l'Activity;
les écrans observent maintenant leurs états avec `collectAsStateWithLifecycle()`.

Chemin prévu : Compose → ViewModel → Repository → DAO → Room → SQLite.

Compose et les ViewModels passeront par le repository pour accéder aux
données persistées. Les règles du jeu resteront dans le package `model`.

Ce bloc avance PB-10 et PB-11 sans les déclarer terminés.

## Préparation de la reprise — 12 octobre 2026

Choix A : adapter les tables existantes. null signifie « pas encore réglé »
pour result et chipChange dans games/hands; 0.0 reste un règlement sans
variation. L'historique et les compteurs de parties/mains, résultats, gains
et mises concernent seulement FINISHED. Les statistiques de cartes utilisent
toutes les cartes déjà enregistrées, y compris celles d'une partie en cours.
La transaction de règlement final refuse une partie ou une main non réglée.

Ce bloc prépare le schéma et les lectures. GameViewModel sauvegarde encore
seulement à la fin : aucune sauvegarde intermédiaire ni reprise après fermeture
n'est branchée. Le stockage du sabot restant, de la phase et de la main active
est décrit ci-dessous. Aucune fonctionnalité de reprise
complète ni User Story n'est déclarée terminée ici.

Vérification : compilation réussie, 101 tests unitaires et 44 tests Android
réussis sur Pixel 8. Les migrations depuis les versions 1 et 2 conservent
les données; les tests vérifient aussi les relations, les identifiants,
le solde, les statistiques et le règlement final depuis l'interface.

## Stockage de l'état de reprise — version 4

Choix A : `games` reçoit `phase`, `activeHandIndex` (0 ou 1) et `deckCount`.
La mise reste dans `initialBet`; les mains et cartes distribuées restent dans
leurs tables existantes. Les nouvelles colonnes sont nullables : une ancienne
partie ne contient pas d'état de reprise fiable, et la migration n'en invente pas.

`shoe_cards` contient les cartes restantes : `gameId`, `position`, `rank`, `suit`.
La clé primaire `(gameId, position)` interdit deux cartes à la même position
dans un sabot sauvegardé, mais autorise plusieurs exemplaires du même rang et
de la même sorte. La clé étrangère exige une partie existante. Les positions
reproduisent la liste de `Shoe` à partir de 0 : sa dernière carte sera tirée
en premier. Il faudra donc charger les lignes avec `ORDER BY position`.
Ces cartes ne participent pas aux statistiques des cartes distribuées.

Cette étape ajoute uniquement le schéma et sa migration. Les opérations du
DAO sont ajoutées dans le bloc suivant; la conversion dans le repository,
la restauration du modèle et le branchement au ViewModel restent à implémenter. Aucun nouveau comportement
de reprise n'est encore disponible dans l'interface.

Vérification de ce bloc : compilation des APK réussie, 101 tests unitaires
et 47 tests Android ciblés réussis sur Pixel 8 (122,468 s). Les tests couvrent
les migrations depuis les versions 1, 2 et 3, la conservation des informations
de reprise et de l'ordre du sabot après réouverture, les exemplaires identiques,
les contraintes et l'exclusion du sabot des statistiques. Le scénario
d'interface « Rester » et sauvegarde finale passe également en portrait.
La reprise par l'application et une nouvelle vérification en paysage ne
sont pas couvertes par ce bloc. PB-11 et PB-15 concernés, sans statut Terminé
ni modification du backlog Excel. Aucun commit ni push effectué.

## Opérations DAO de progression

`saveGameProgress()` insère puis actualise la même partie IN_PROGRESS avec
ses mains, cartes distribuées et cartes restantes. Une transaction protège
la sauvegarde complète et conserve l'état précédent en cas d'erreur.
`loadGameProgress()` relit ces quatre éléments ensemble dans `GameProgress`.
Le sabot est trié par position; les distributions sont triées par drawOrder.
Aucune de ces opérations ne crée ni ne modifie le solde.

`finishGameProgress()` règle maintenant cette même partie et conserve son
sabot final. Sa transaction inclut résultats, détails et variation du solde;
réessayer le même règlement ne paie pas deux fois. Les nouvelles opérations
sont exposées par le repository et doivent encore être appelées par le ViewModel. L'application
continue pour l'instant à sauvegarder uniquement ses parties terminées.

Vérification du bloc DAO : compilation réussie, 101 tests unitaires et 55 tests
Android ciblés réussis sur Pixel 8, dont neuf nouveaux tests de progression.
La réouverture, l'ordre des listes, le remplacement sans cartes en double,
la restauration de l'ancienne sauvegarde après erreur et le solde inchangé
sont vérifiés. Aucune reprise complète n'est encore démontrable depuis l'UI.

Vérification complémentaire du règlement de progression : compilation,
101 tests unitaires et 64 tests Android ciblés réussis sur Pixel 8, dont neuf
nouveaux tests de règlement. La même partie est terminée une seule fois,
avec son sabot final; les nouvelles tentatives, même concurrentes, ne
rajoutent aucun paiement. Les échecs conservent l'ancienne progression et
le solde. Le repository expose ces opérations; le ViewModel doit encore les utiliser.
