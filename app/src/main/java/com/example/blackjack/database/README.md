# Package database

`BlackjackDatabase.kt` déclare la base Room, ses quatre tables (`GameEntity`,
`HandEntity`, `DrawnCardEntity`, `PlayerBalanceEntity`) et l'accès à `BlackjackDao`.

- `dao` : déclarer les lectures et écritures.
- `entity` : décrire les tables et les données enregistrées.

Room organise l'accès à SQLite, le moteur qui conserve les données sur
l'appareil. La classe de base est `abstract` : Room génère la classe
concrète à la compilation, comme l'implémentation du DAO.

La configuration reprend les outils utilisés dans `Atelier3` :
Room 3.0.3, le compilateur `room3-compiler` avec KSP 2.3.6 et
`sqlite-framework` 2.7.1 pour `AndroidSQLiteDriver`.

Le schéma est à la version 2. `MIGRATION_1_2` ajoute le solde à 1000 jetons
en conservant l'historique existant. `BlackjackApplication` enregistre cette
migration. `exportSchema = false` reprend la configuration du cours : aucun
fichier de schéma n'est exporté. Aucune suppression automatique n'est configurée.

Les tests Android ouvrent une base en mémoire pour les opérations et
une base de test sur disque pour vérifier la conservation après fermeture.
Cette dernière porte un nom réservé aux tests et est supprimée ensuite.

L'instance partagée est créée dans `BlackjackApplication`, une classe
Android distincte du composable `BlackjackApp`, déclarée dans le manifeste.
`database` et `repository` utilisent `by lazy` : un premier accès les crée,
puis les suivants retrouvent les mêmes instances pendant ce processus.
La base utilise `applicationContext`, `AndroidSQLiteDriver` et le fichier
`blackjack.db`, avec la migration 1 vers 2 et sans suppression automatique.
La Factory fournit ce repository aux ViewModels conservés par l'Activity;
les écrans observent maintenant leurs états avec `collectAsStateWithLifecycle()`.

Chemin prévu : Compose → ViewModel → Repository → DAO → Room → SQLite.

Compose et les ViewModels passeront par le repository pour accéder aux
données persistées. Les règles du jeu resteront dans le package `model`.

Ce bloc avance PB-10 et PB-11 sans les déclarer terminés.
