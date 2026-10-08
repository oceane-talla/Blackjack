# Package database

Ce package contiendra la base de données locale et ses deux sous-packages :

- `dao` : déclarer les lectures et écritures.
- `entity` : décrire les tables et les données enregistrées.

Room est la bibliothèque Android qui organise l'accès à SQLite. SQLite est
le moteur qui conserve les données sur l'appareil.

Fichier prévu dans `ARCHITECTURE.md`, à créer à l'étape correspondante :

- `BlackjackDatabase.kt` : déclarer les Entity incluses, la version du schéma
  et l'accès à `BlackjackDao`.

L'instance de la base sera créée et conservée par `BlackjackApplication`,
afin de partager une seule instance dans l'application. Cette classe
Application sera distincte du composable `BlackjackApp`.

Chemin prévu : Compose → ViewModel → Repository → DAO → Room → SQLite.

Compose et les ViewModels passeront par le repository pour accéder aux
données persistées. Les règles du jeu resteront dans le package `model`.

Room 3.0.3 est ajouté selon le choix autorisé du 8 octobre 2026, comme dans
`Atelier3`. La dépendance `room3-runtime` fournit les annotations des trois
Entity présentes : `GameEntity`, `HandEntity` et `DrawnCardEntity`.

Les classes du DAO et de la base ne sont pas encore créées. Il n'existe
donc pas encore de fichier SQLite ni de sauvegarde. Le compilateur Room
et KSP seront ajoutés au bloc de la base et du DAO. Les annotations
peuvent compiler dès maintenant, mais la génération et la validation
complète du schéma Room attendent ce bloc.

Cette préparation avance PB-10 et PB-11 sans les déclarer terminés.
