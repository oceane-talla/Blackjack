# Package repository

Ce package contiendra le point d'accès aux données persistées pour les
ViewModels. Le repository utilise le DAO pour lire et enregistrer les données.

Fichier prévu dans `ARCHITECTURE.md`, à créer à l'étape correspondante :

- `BlackjackRepository.kt` : enregistrer les parties terminées et fournir
  l'historique ainsi que les statistiques aux ViewModels.

Chemin prévu : Compose → ViewModel → Repository → DAO → Room → SQLite.

Exemple prévu : lorsque la partie se termine, le ViewModel transmet son
résultat au repository. Celui-ci prépare les Entity, puis demande au DAO
d'enregistrer la partie et ses cartes ensemble.

Une Entity décrit une table de la base; un DAO déclare les opérations sur les
données. Les règles du Blackjack restent dans le package `model`.

Le repository fournira les lectures observables sous forme de `Flow`, qui
publie les changements de données. Les écritures utiliseront des fonctions
`suspend`, qui peuvent suspendre leur exécution pendant une opération
asynchrone sans bloquer le thread appelant.

Ce README réserve le package pour PB-01. Le repository n'est pas encore
implémenté.
