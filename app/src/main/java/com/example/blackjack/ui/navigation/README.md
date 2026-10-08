# Package ui.navigation

Ce package contient les routes et les chemins entre les écrans.
Une route est l'identifiant d'une destination, par exemple `"home"`.

Fichiers présents, conformément à `ARCHITECTURE.md` :

- `BlackjackDestination.kt` : centraliser les identifiants de l'accueil,
  de la mise, de la partie, de l'historique et des statistiques.
- `BlackjackNavHost.kt` : associer chaque route au composable de son écran
  et définir la destination de départ.

Un composable est une fonction Compose qui décrit une partie de l'interface.
Le `NavHost` affiche celui qui correspond à la destination actuelle.
Le contrôleur de navigation permet de changer de destination et de revenir
en arrière.

Exemple prévu : l'accueil appelle `onStartGame`, une fonction reçue en
paramètre appelée callback. La navigation traite cet événement et demande
au contrôleur d'afficher l'écran de mise.

La navigation choisit quel écran afficher. Les règles du Blackjack restent
dans le modèle, et les données persistées passent par les ViewModels et
le repository.

Actuellement, `BlackjackNavHost.kt` contient les cinq destinations : accueil,
mise, table, historique et statistiques. `BlackjackApp` crée le contrôleur et
le transmet à `BlackjackNavHost`, avec l'espacement fourni par le `Scaffold`.

Pour PB-02, l'option B a été choisie : quatre onglets Accueil, Jouer, Historique
et Statistiques. Jouer ouvre la mise; la table appartient au même onglet.
Le FAB ouvre une nouvelle préparation de partie depuis n'importe quel écran.

Les onglets utilisent `popUpTo` pour éviter une pile de visites répétées,
`saveState` et `restoreState` pour conserver leur parcours, et
`launchSingleTop` pour éviter de dupliquer la destination au sommet.
Accueil est l'exception : `restoreState` y vaut `false`, pour rester à
l'accueil au lieu de réouvrir le parcours de Jouer qui vient d'être sauvegardé.
Le FAB efface le parcours sauvegardé de Jouer avec `clearBackStack` avant
d'ouvrir une nouvelle mise. Le retour depuis la table mène à la mise,
puis à l'accueil. Un onglet principal revient à l'accueil avec Retour.

La navigation fonctionne avec des écrans provisoires. Les règles, les mises
et la persistance seront implémentées dans leurs PBI respectifs.
