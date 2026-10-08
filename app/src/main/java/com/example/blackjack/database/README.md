# Package database

`BlackjackDatabase.kt` déclare la base Room, ses trois tables (`GameEntity`,
`HandEntity`, `DrawnCardEntity`) et l'accès à `BlackjackDao`.

- `dao` : déclarer les lectures et écritures.
- `entity` : décrire les tables et les données enregistrées.

Room organise l'accès à SQLite, le moteur qui conserve les données sur
l'appareil. La classe de base est `abstract` : Room génère la classe
concrète à la compilation, comme l'implémentation du DAO.

La configuration reprend les outils utilisés dans `Atelier3` :
Room 3.0.3, le compilateur `room3-compiler` avec KSP 2.3.6 et
`sqlite-framework` 2.7.1 pour `AndroidSQLiteDriver`.

Le schéma commence à la version 1. `exportSchema = false` reprend la
configuration du cours : aucun fichier de schéma n'est exporté.
Avant de modifier une base déjà utilisée, il faudra décider de sa
migration; aucune suppression automatique des données n'est configurée.

Les tests Android ouvrent une base en mémoire pour les opérations et
une base de test sur disque pour vérifier la conservation après fermeture.
Cette dernière porte un nom réservé aux tests et est supprimée ensuite.

L'instance partagée sera créée dans `BlackjackApplication`, une classe
Android distincte du composable `BlackjackApp`. Elle n'est pas encore
implémentée dans ce bloc, comme le repository et leur branchement aux écrans.

Chemin prévu : Compose → ViewModel → Repository → DAO → Room → SQLite.

Compose et les ViewModels passeront par le repository pour accéder aux
données persistées. Les règles du jeu resteront dans le package `model`.

Ce bloc avance PB-10 et PB-11 sans les déclarer terminés.
