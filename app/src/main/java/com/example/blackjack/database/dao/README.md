# Package database.dao

Ce package contiendra le DAO, pour Data Access Object : l'interface qui
déclare les opérations de lecture et d'écriture. Room fournira son
implémentation à partir des annotations et des requêtes SQL.

Fichier prévu dans `ARCHITECTURE.md`, à créer à l'étape correspondante :

- `BlackjackDao.kt` : insérer les parties et leurs cartes, observer
  l'historique et calculer les statistiques demandées avec SQL.

Le repository appellera le DAO. Le DAO utilisera Room pour accéder à SQLite.

Les lectures observables retourneront un `Flow`, qui publie de nouvelles
données lorsque la base change. Les écritures ponctuelles seront des
fonctions `suspend`, utilisables depuis les coroutines.

L'enregistrement d'une partie et de ses cartes devra utiliser une
transaction : toutes les insertions réussissent ensemble, ou aucune n'est
conservée. Cela évite un historique contenant une partie enregistrée avec
seulement une partie de ses cartes.

Ce README réserve le package pour PB-01. Aucun DAO ni requête SQL n'est
encore implémenté. Les requêtes exactes dépendront du schéma et des
statistiques validés.
