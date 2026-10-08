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

Ce README réserve le package pour PB-01. Room et la base ne sont pas encore
implémentés. Les dépendances, le schéma et ses relations restent à valider
avant leur création.
