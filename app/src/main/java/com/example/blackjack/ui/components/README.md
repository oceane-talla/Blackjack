# Package ui.components

Ce package contient les éléments d'interface réutilisables entre les
écrans. Un composable est une fonction Compose qui décrit un élément
visuel à partir des paramètres reçus.

Fichier présent pour PB-02 :

- `ScreenContent.kt` : afficher un titre, une description et le contenu
  propre à chaque écran dans une colonne qui peut défiler.

Le paramètre `content` est une fonction composable exécutée dans la colonne.
Par exemple, HomeScreen lui fournit son bouton de démarrage. `ColumnScope`
permet au contenu d'utiliser les possibilités de mise en page d'une colonne.
L'espacement inférieur permet de faire défiler les boutons au-dessus du FAB.

Fichier prévu dans `ARCHITECTURE.md`, à créer à l'étape correspondante :

- `PlayingCard.kt` : afficher une carte traditionnelle ou son dos.

Exemple prévu : `GameScreen` transmet une carte et son état de visibilité
à `PlayingCard`. Le composable choisit la ressource graphique appropriée
et affiche la carte. Les images seront conservées dans les ressources
Android du projet, par exemple `res/drawable`.

Le modèle `Card` représente le rang et la couleur de la carte : pique,
cœur, carreau ou trèfle. Le composable connaît sa représentation visuelle
Android. Les calculs des mains restent dans le package `model`.

Ce README réserve le package pour PB-01. `PlayingCard` et les ressources
graphiques ne sont pas encore implémentés. Leur présentation sera validée
avant leur création.
