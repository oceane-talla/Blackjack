# Package ui.components

`ScreenContent` garde le titre, la description et le défilement vertical communs.
BlackjackApp réserve une zone sous le contenu pour que le FAB ne recouvre aucun contrôle.

`CasinoComponents.kt` regroupe de petites fonctions de présentation :
- `CasinoPanel` : panneau vert bordé de doré;
- `ValueRow` : une étiquette et sa valeur;
- `PlayingCard` : rang et image vectorielle de la sorte, ou dos pour une carte null;
- `chipText` : nombre localisé, avec au maximum une décimale;
- `resultLabel` : correspondance entre résultat sauvegardé et ressource traduite.

Les images des quatre sortes et du dos sont dans res/drawable/card_*.xml.
Une carte cachée ne révèle aucune valeur dans son texte d’accessibilité.
Aucune règle du Blackjack ni accès à la base ne se trouve ici.
