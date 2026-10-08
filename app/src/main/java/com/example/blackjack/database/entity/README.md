# Package database.entity

Ce package contiendra les Entity de Room : les classes qui décrivent les
tables. Chaque objet Entity représente une ligne enregistrée.

Fichiers prévus dans `ARCHITECTURE.md`, à créer aux étapes correspondantes :

- `GameEntity.kt` : décrire le résumé d'une partie enregistrée.
- `DrawnCardEntity.kt` : décrire une carte distribuée et son contexte dans
  la partie.

Une clé primaire identifiera chaque ligne. Une clé étrangère pourra relier
une carte à sa partie, selon les relations du schéma retenu.

Les propriétés exactes restent à valider : date, mise, résultat, variation
des jetons, rang et couleur des cartes, destinataire et ordre de
distribution. Le schéma devra aussi représenter correctement les mains
et leurs résultats après un Split.

Ces classes décriront les données conservées. `BlackjackGame` et `Card`,
dans le package `model`, resteront responsables de la représentation et
des règles du jeu en mémoire.

Ce README réserve le package pour PB-01. Les Entity, les tables et leurs
relations ne sont pas encore implémentées.
