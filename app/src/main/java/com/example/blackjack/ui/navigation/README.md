# Package ui.navigation

BlackjackDestination centralise les routes. BlackjackNavHost associe ces routes aux
écrans et relie leurs callbacks aux ViewModels fournis par MainActivity.

BlackjackApp crée le NavController et observe le GameUiState avec
collectAsStateWithLifecycle(), car le FAB dépend de la partie sur tous les onglets.
Le même état est transmis aux écrans Mise et Partie. Le NavHost observe aussi les
états Historique et Statistiques suivant le cycle de vie de leurs destinations.

collectAsStateWithLifecycle transforme le StateFlow observable en état Compose,
qui provoque un nouvel affichage lorsqu’il change, en respectant le cycle de vie.

Les onglets Accueil, Jouer, Historique et Statistiques utilisent popUpTo,
saveState/restoreState et launchSingleTop pour conserver les parcours sans empiler
les visites. Accueil ne restaure pas le parcours de Jouer.

Le FAB ouvre une nouvelle mise lorsque canChangeBet est vrai. Sinon, il affiche
Reprendre la partie et ouvre la table existante, même si la sauvegarde a échoué.
Le retour à la mise ne remplace jamais une partie active : seul Reprendre est alors
proposé. Une distribution acceptée par GameViewModel.startGame ouvre la table.
Les règles de jeu restent dans le modèle et les écritures dans le repository.
