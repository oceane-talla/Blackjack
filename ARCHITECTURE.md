# Blackjack Android — rôle des fichiers et fonctions prévues

Document de travail mis à jour le 12 octobre 2026, basé sur notre discussion et les contraintes consignées dans `AGENTS.md`.

## Comment lire ce document

Ce document décrit la structure visée et sert à comprendre où placer chaque responsabilité. Les noms des fonctions proposées pourront être ajustés ensemble avant leur implémentation. Leur présence ici ne signifie pas que le code existe déjà ni que toutes les décisions sont prises.

**État actuel :** `MainActivity`, `BlackjackApp`, `BlackjackDestination`, `BlackjackNavHost`, les cinq écrans, `ScreenContent` et les fichiers du thème existent. Les écrans sont branchés aux ViewModels : mise, partie standard, historique et statistiques sont affichés dans une interface verte et dorée. Les vérifications de cette intégration sont consignées à la fin du document. `Card`, `Hand`, `Shoe`, `DealtCard` et la partie standard de `BlackjackGame` sont implémentés, avec le suivi des distributions, la validation des mises et le calcul de variation nette des jetons. Room 3.0.3 et KSP sont configurés; les quatre Entity (dont `PlayerBalanceEntity` pour le solde), `BlackjackDao`, `BlackjackDatabase` et `BlackjackRepository` existent. `GameViewModel` et `GameUiState` coordonnent le jeu, le solde et la sauvegarde. `HistoryViewModel` et `StatisticsViewModel` exposent les lectures observables du repository, avec leurs états d'affichage. Les statistiques sont calculées par SQLite et retournées dans `GameStatistics`. `BlackjackApplication` partage la base et le repository; `BlackjackViewModelFactory` construit les trois ViewModels, récupérés avec une portée Activity dans `MainActivity`. Le branchement observe les StateFlow avec collectAsStateWithLifecycle(). Les vérifications figurent à la section 9.

`HomeScreen` est conservé conformément à ton choix et se trouve maintenant dans `ui/screens/home`. La navigation a été extraite dans `ui/navigation/BlackjackNavHost.kt`.

## Arborescence visée

```text
com.example.blackjack
│
├── MainActivity.kt
├── BlackjackApplication.kt
│
├── ui
│   ├── BlackjackApp.kt
│   ├── navigation
│   │   ├── BlackjackDestination.kt
│   │   └── BlackjackNavHost.kt
│   ├── screens
│   │   ├── home/HomeScreen.kt
│   │   ├── bet/BetScreen.kt
│   │   ├── game/GameScreen.kt
│   │   ├── history/HistoryScreen.kt
│   │   └── statistics/StatisticsScreen.kt
│   ├── components
│   │   ├── ScreenContent.kt
│   │   └── CasinoComponents.kt
│   └── theme
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── viewmodel
│   ├── GameViewModel.kt
│   ├── GameUiState.kt
│   ├── BlackjackViewModelFactory.kt
│   ├── HistoryViewModel.kt
│   ├── HistoryUiState.kt
│   ├── StatisticsViewModel.kt
│   └── StatisticsUiState.kt
│
├── model
│   ├── Card.kt
│   ├── Hand.kt
│   ├── Shoe.kt
│   ├── BlackjackGame.kt
│   ├── DealtCard.kt
│   └── GameStatistics.kt
│
├── repository
│   └── BlackjackRepository.kt
│
└── database
    ├── BlackjackDatabase.kt
    ├── dao/BlackjackDao.kt
    └── entity
        ├── GameEntity.kt
        ├── HandEntity.kt
        ├── DrawnCardEntity.kt
        └── PlayerBalanceEntity.kt
```

## Répartition générale des responsabilités

| Partie | Responsabilité | Exemple |
| --- | --- | --- |
| UI / Compose | Afficher les données et transmettre les actions | Afficher les cartes; transmettre « Tirer ». |
| Navigation | Choisir quel écran afficher | Passer de la mise à la table. |
| ViewModel | Gérer l'état de l'écran et coordonner les actions | Appeler le modèle, publier le nouveau total. |
| Model | Appliquer les règles du Blackjack | Calculer une main contenant des As. |
| Repository | Fournir l'accès aux données persistées | Enregistrer une partie terminée. |
| DAO | Définir les opérations SQL exécutées par Room | Insérer une partie; observer l'historique. |
| Room / SQLite | Stocker les données sur l'appareil | Conserver l'historique après fermeture. |

Le chemin obligatoire pour accéder aux données persistées est :

```text
Compose → ViewModel → Repository → DAO → Room → SQLite
```

Les règles du jeu suivent aussi un chemin plus court : `Compose → ViewModel → Model`. Calculer une main ne nécessite pas une requête SQL. En revanche, un ViewModel n'accède jamais directement au DAO.

## 1. Démarrage et services partagés

### MainActivity.kt

**Rôle :** être l'unique Activity, c'est-à-dire le point d'entrée Android qui accueille notre interface Compose.

| Fonction ou appel | À quoi il sert |
| --- | --- |
| `onCreate(savedInstanceState)` | Préparer l'Activity lorsqu'Android la crée. |
| `enableEdgeToEdge()` | Permettre à l'interface d'occuper la fenêtre jusqu'aux zones des barres système. |
| `setContent { ... }` | Installer l'interface Compose. C'est un appel Android existant. |
| `BlackjackTheme { BlackjackApp() }` | Appliquer le thème et afficher le composable racine. |
| `WindowCompat.getInsetsController(...)` | Configurer des icônes système claires pour le fond casino sombre. |
| `viewModelFactory` | Fournir le repository de l'Application aux constructeurs des ViewModels. |
| `gameViewModel`, `historyViewModel`, `statisticsViewModel` | Récupérer les instances conservées au niveau de l'Activity, au premier accès. |

**Pourquoi ce fichier reste petit :** Android gère le démarrage; les détails des écrans appartiennent à `ui`. Il ne calcule pas les mains et n'exécute pas de requêtes SQL.

Le bloc Kotlin `apply` regroupe les deux réglages du contrôleur d'insets.
`isAppearanceLightStatusBars = false` indique que le fond n'est pas clair :
les icônes doivent donc être claires. [Documentation Android : icônes système](https://developer.android.com/develop/ui/views/layout/edge-to-edge#system-bar-icons).

### BlackjackApplication.kt

**Rôle :** conserver les services partagés à l'échelle de l'application, notamment une seule instance de la base de données et le repository qui l'utilise.

Ce fichier correspond à une classe Android `Application`. Il est différent de `BlackjackApp`, qui est un composable.

| Élément prévu | À quoi il sert |
| --- | --- |
| Propriété `database` | Fournir l'instance partagée de `BlackjackDatabase`. |
| Propriété `repository` | Fournir `BlackjackRepository`, construit avec le DAO de cette base. |

Les deux propriétés sont implémentées avec `by lazy` : le premier accès crée
l'objet, puis les suivants retournent la même instance pour ce processus.
La base utilise `applicationContext`, `AndroidSQLiteDriver` et le fichier
`blackjack.db`. Aucune méthode `onCreate()` vide n'est nécessaire.

**Intégration actuelle :** le manifeste déclare
`android:name=".BlackjackApplication"`. `MainActivity` récupère son repository
pour construire `BlackjackViewModelFactory`, selon la méthode d'Atelier3.
Les tables et la version du schéma ne changent pas.

### viewmodel/BlackjackViewModelFactory.kt

La Factory reçoit le repository partagé. `create(modelClass)` construit un
`GameViewModel`, `HistoryViewModel` ou `StatisticsViewModel` selon la classe
demandée; une autre classe provoque une erreur explicite. La conversion `as T`
est limitée au résultat de ces branches qui vérifient le type demandé.

Le choix A de l'utilisateur rattache les trois instances à l'Activity :
`MainActivity` les récupère avec `ViewModelProvider(this, viewModelFactory)`
dans des propriétés `by lazy`. Le Provider retrouve les ViewModels dans le
ViewModelStore de l'Activity, y compris après une rotation. La Factory crée
une instance seulement si elle n'y existe pas encore. Le passage d'un onglet
à un autre ne détruit pas ces instances.

Les propriétés sont transmises à Compose. Mise et Partie reçoivent le même
`gameViewModel` de `MainActivity`. Aucun ViewModel n'est construit dans les
écrans. Les instances ne sont pas conservées après un arrêt du processus;
la conservation du solde après fermeture reste une décision séparée.

## 2. Package ui : interface et navigation

### ui/BlackjackApp.kt

**Rôle :** construire la structure commune de l'application.

| Fonction ou élément | À quoi il sert |
| --- | --- |
| `BlackjackApp()` | Créer le contrôleur de navigation et organiser l'interface générale. |
| `Scaffold` | Définir les emplacements des barres, du FAB et du contenu. |
| `TopAppBar` | Afficher le titre de la destination; proposer Retour pour la mise et la table. |
| Barre de navigation inférieure | Quatre onglets retenus : Accueil, Jouer, Historique et Statistiques. |
| `ExtendedFloatingActionButton` | Ouvrir une nouvelle préparation de partie sur l'écran de mise. |
| `BlackjackAppPreview()` | Afficher un aperçu de la structure dans Android Studio. |

**État actuel :** ce fichier appelle `BlackjackNavHost(...)` et lui transmet le contrôleur ainsi que le `Modifier` contenant l'espacement du `Scaffold`.

`currentBackStackEntryAsState()` observe la destination actuelle. Lorsque
celle-ci change, Compose actualise le titre de la barre supérieure et
l'onglet sélectionné. L'annotation `@OptIn` indique explicitement l'utilisation
de la `TopAppBar`, encore marquée comme API expérimentale dans Material 3.

La barre inférieure conserve le parcours des onglets avec `saveState` et
`restoreState`, sauf pour Accueil : cet onglet reste sur la destination de
départ et ne restaure pas le parcours sauvegardé de Jouer.
`popUpTo` retire les visites précédentes de la pile active,
et `launchSingleTop` évite de dupliquer la destination au sommet. Le FAB
ouvre une nouvelle préparation lorsque la partie précédente est sauvegardée;
sinon, il reprend la partie active ou sa sauvegarde à réessayer.

### ui/navigation/BlackjackDestination.kt

**Rôle :** centraliser les identifiants des destinations : accueil, mise, partie, historique et statistiques.

Une **route** est l'identifiant utilisé pour reconnaître un écran, par exemple `"home"`.

| Élément prévu | À quoi il sert |
| --- | --- |
| Identifiants `HOME`, `BET`, `GAME`, `HISTORY`, `STATISTICS` | Éviter de répéter des chaînes différentes pour désigner le même écran. |

Ce fichier contient un `object` avec cinq constantes de routes. Il ne possède pas de fonction par destination.

### ui/navigation/BlackjackNavHost.kt

**Rôle :** associer les routes aux composables des écrans.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `BlackjackNavHost(navController, gameState, gameViewModel, historyViewModel, statisticsViewModel, modifier)` | Définir les destinations, observer les lectures et relier les actions aux ViewModels. |

Les blocs `composable(...)` déclarent les destinations. Les actions reçues des écrans appellent les ViewModels et la navigation appropriée.

**Pourquoi séparer ce fichier :** `BlackjackApp` organise la structure commune; `BlackjackNavHost` décrit les chemins entre écrans. La navigation ne contient pas les règles du Blackjack.

### ui/screens/home/HomeScreen.kt

**Rôle :** afficher l'accueil, conservé selon ton choix.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `HomeScreen(...)` | Afficher le contenu de l'accueil et transmettre l'action permettant d'aller à la mise. |
| Paramètre `onStartGame` — prévu | Informer la navigation que l'utilisateur veut commencer. C'est une fonction reçue en paramètre, appelée un callback. |

**État actuel :** l'accueil affiche deux cartes décoratives et son bouton de démarrage. `onStartGame` transmet l'action au NavHost, qui ouvre la mise ou reprend la partie active. Aucun ViewModel dédié n'est nécessaire.

### ui/screens/bet/BetScreen.kt

**Rôle :** afficher le solde disponible, la mise sélectionnée et les contrôles pour choisir cette mise.

| Fonction ou callback prévu | À quoi il sert |
| --- | --- |
| `BetScreen(...)` | Dessiner l'écran à partir de l'état fourni. |
| `onSelectBet(amount)` | Transmettre le montant choisi au `GameViewModel`. |
| `onOpenTable()` | Demander le lancement de la partie. |
| `onResume()` | Reprendre une partie qui interdit actuellement de changer la mise. |

La mise validée est un entier de 1 à 100, dans la limite du solde. Les jetons proposent 1, 5, 10, 25, 50 et 100; le curseur permet tous les entiers. La validation réelle reste dans le ViewModel et le modèle.

**État actuel :** la sélection et la distribution sont branchées. Une partie active ou une sauvegarde non résolue propose uniquement Reprendre.

### ui/screens/game/GameScreen.kt

**Rôle :** présenter la table : croupier en haut, mains du joueur plus bas, cartes, mise, total visible et actions.

| Fonction ou callback prévu | À quoi il sert |
| --- | --- |
| `GameScreen(...)` | Afficher l'état actuel de la partie. |
| `onHit()` | Transmettre « Tirer ». |
| `onStand()` | Transmettre « Rester ». |
| `onSplit()` — futur | Split reste une étape distincte, sans bouton actuellement. |
| `onNewGame()` | Demander une nouvelle partie selon le parcours validé. |
| `onRetrySave()` | Relancer la sauvegarde échouée via le ViewModel. |

**Limite :** cet écran ne calcule ni la valeur des As, ni le gagnant, ni le paiement. Il ne révèle pas non plus une carte cachée en affichant accidentellement le total complet du croupier.

**État actuel :** cartes, totaux, Tirer/Rester, résultat, solde et sauvegarde sont affichés depuis GameUiState. Le changement de mise dépend de canChangeBet.

### ui/screens/history/HistoryScreen.kt

**Rôle :** afficher les parties enregistrées, leurs mises et leurs résultats selon le contenu demandé.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `HistoryScreen(...)` | Afficher les données fournies par `HistoryViewModel`, ainsi qu'un état vide ou une erreur si nécessaire. |

L'ouverture d'un détail de partie ne sera ajoutée que si elle est demandée ou retenue. On ne prévoit pas automatiquement un bouton pour supprimer l'historique.

### ui/screens/statistics/StatisticsScreen.kt

**Rôle :** afficher les statistiques produites par les requêtes SQLite prévues au projet.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `StatisticsScreen(...)` | Présenter les valeurs fournies par `StatisticsViewModel`. |

Les indicateurs sont ceux de GameStatistics, issus des choix validés pour les requêtes SQL. L'écran ne recompte pas lui-même les parties.

### ui/components/CasinoComponents.kt

**Rôle :** fournir un composable réutilisable pour afficher une carte traditionnelle ou son dos.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `PlayingCard(card, modifier)` | Afficher la face de la carte ou le dos lorsque card vaut null, sans révéler la carte cachée à l'UI. |
| `CasinoPanel`, `ValueRow` | Partager la présentation des panneaux et de leurs valeurs. |
| `chipText`, `resultLabel` | Formater les nombres et choisir les ressources de résultat. |

Les images des quatre sortes et du dos sont des ressources vectorielles dans `res/drawable`. Le rang est superposé en texte. La sorte signifie pique, cœur, carreau ou trèfle, pas seulement rouge ou noir.

**Pourquoi l'image ne va pas dans `Card` :** le modèle représente une carte de jeu; le composable connaît sa représentation Android.

Le choix du dos par l'utilisateur reste une amélioration facultative du backlog, après les exigences obligatoires. Il n'est pas implémenté par ce document.

### ui/theme/Color.kt, Theme.kt et Type.kt

| Fichier | Contenu et utilité |
| --- | --- |
| `Color.kt` | Couleurs communes : inspiration tapis vert, accents dorés et contraste lisible. Les valeurs précises seront validées. |
| `Theme.kt` | Fonction `BlackjackTheme(...)` qui applique les couleurs et la typographie aux composables. |
| `Type.kt` | Styles de texte communs, par exemple les tailles et les graisses de caractères. |

Pour PB-02, le thème utilise une palette casino commune : tapis vert, surfaces vert foncé, accents dorés et texte clair. Les couleurs du fond d'écran Android ne remplacent plus cette palette. Les cartes et leurs détails visuels restent à approuver à leur étape.

## 3. Package viewmodel : état et coordination

Un **ViewModel** conserve l'état nécessaire à l'écran pendant les recréations de l'Activity, comme une rotation. Il ne garantit pas, à lui seul, la conservation après la fermeture du processus Android. [Référence Android : ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel).

Un **StateFlow** expose la valeur actuelle de cet état et ses changements. L'UI l'observera avec `collectAsStateWithLifecycle()` afin d'adapter l'observation au cycle de vie de l'écran.

Les états devront porter des noms explicites. Leur forme précise sera définie avec les besoins des écrans; on n'ajoute pas ici de classes d'état artificielles.

### viewmodel/GameViewModel.kt

**Rôle :** coordonner la mise et la partie en cours, appeler les règles du modèle et exposer l'état à l'UI.

| Fonction ou propriété prévue | À quoi elle sert |
| --- | --- |
| `uiState` | Exposer un `StateFlow` en lecture seule : cartes visibles, mains, mise, solde, phase, actions autorisées et erreurs utiles. |
| `selectBet(amount)` | Mettre à jour la mise choisie et présenter les éventuelles erreurs de validation. |
| `startGame()` | Demander au modèle de démarrer avec la mise retenue et publier l'état résultant. |
| `hit()` | Demander une carte au modèle pour la main active, puis publier le résultat. |
| `stand()` | Demander au modèle de terminer le tour de la main active. |
| `split()` | Demander au modèle de vérifier et d'exécuter le Split. |
| `saveFinishedGame()` — fonction privée prévue | Envoyer une partie terminée au repository dans `viewModelScope`, gérer le résultat de l'écriture et éviter un double enregistrement. |

Le modèle reste responsable de la validité des actions. Le ViewModel transforme leur résultat en état affichable. Les écrans de mise et de partie devront accéder au **même** `GameViewModel` pendant leur parcours; créer une instance différente par écran ferait perdre la mise ou la partie.

**État actuel — option B :** `GameViewModel` et `GameUiState` sont implémentés
avec les choix de l'utilisateur : un paquet neuf par partie et variation
nette du solde appliquée à la fin. `uiState` expose un `StateFlow` en lecture
seule; le `MutableStateFlow` est privé. `selectBet`, `startGame`, `hit` et
`stand` coordonnent le modèle. `validateBet` dans le modèle vérifie une mise
de 1 à 100 et les jetons disponibles. Le solde initial est 1000, la mise 1,
et la dernière mise valide est conservée entre les parties.

`selectedBet` décrit la prochaine mise et `roundBet` celle de la partie
en cours ou terminée. Le règlement ne se produit qu'une fois. Une partie
interrompue ne produit aucun paiement ni enregistrement. Le ViewModel
fournit une entrée `null` pour la deuxième carte du croupier et un total
`null` pendant le tour du joueur, sans exposer ces valeurs cachées.

La sauvegarde automatique passe par le repository dans `viewModelScope`.
`isSaving` et `savedGameId` rendent son état observable. Les nouvelles
parties et changements de mise attendent la fin de l'écriture. Un échec
publie `SAVE_FAILED` et conserve la partie : `retrySave` reprend l'écriture
avec la même date, sans repayer ni créer plusieurs tentatives simultanées.
Les erreurs sont aussi journalisées; l'annulation d'une coroutine est
propagée. Les messages et le contrôle de reprise seront définis avec l'UI.

Le constructeur public reçoit seulement le repository, sans accès au DAO.
Le constructeur interne reçoit une fonction créant le modèle pour les
scénarios de test déterministes. Cette organisation reprend les mécanismes
de `CalculViewModel` dans `Atelier3`, sans ajouter de dépendance.
La Factory et l'instance partagée existent; Compose observe maintenant les états.
La mise et le solde ne sont pas persistés après arrêt du processus.

### viewmodel/HistoryViewModel.kt

**Rôle :** observer l'historique fourni par le repository et exposer un état lisible par l'écran.

| Élément prévu | À quoi il sert |
| --- | --- |
| `uiState` | Exposer l'historique et les états utiles d'affichage. |
| Observation de `repository.observeHistory()` | Recevoir les changements de la base, sans demander à l'utilisateur de recharger manuellement la liste. |

Une fonction `loadHistory()` n'est pas nécessaire si l'observation est continue. Une fonction pour consulter les cartes d'une partie sera ajoutée seulement si le détail fait partie du besoin.

**État actuel — 9 octobre 2026 :** `HistoryViewModel` et `HistoryUiState`
sont implémentés et commentés. Le ViewModel observe `repository.observeHistory()`
dans `viewModelScope` et expose les parties, le chargement initial et une erreur
éventuelle dans un `StateFlow`. Une erreur termine l'observation et conserve
la dernière liste reçue; aucune reprise automatique n'est ajoutée.
`HistoryScreen` est branché à cet état via le NavHost.

La compilation de l'application et des tests Android réussit, avec 74 tests
unitaires sans échec. Les quatre tests `HistoryViewModelTest` passent sur
Pixel 8 : historique vide après chargement, tri date/identifiant décroissants,
actualisation automatique après insertion dans Room et conservation de la
dernière liste après une erreur de lecture simulée. Les bases de test sont
isolées en mémoire. Les tests d'interface ne sont pas relancés car aucun
écran n'est modifié. Ces preuves avancent PB-11 et PB-15 sans les déclarer
terminés ni modifier le backlog Excel.

### viewmodel/StatisticsViewModel.kt

**Rôle :** observer les statistiques du repository et les préparer pour l'affichage.

| Élément prévu | À quoi il sert |
| --- | --- |
| `uiState` | Exposer les statistiques et l'état de chargement ou d'erreur utile. |
| Observation de `repository.observeStatistics()` | Recevoir les agrégats provenant de SQLite. |

Des fonctions de filtrage seront ajoutées uniquement si des filtres sont demandés. Aucun indicateur supplémentaire n'est décidé ici.

**État actuel — 9 octobre 2026 :** `StatisticsViewModel` et `StatisticsUiState`
observent `repository.observeStatistics()` dans `viewModelScope`. L'état expose
le résumé `GameStatistics`, le chargement initial et une erreur éventuelle.
Les calculs sont effectués en SQL, sans collection de parties à recompter dans
Compose ou le ViewModel. Une erreur conserve les dernières valeurs et termine
l'observation sans reprise automatique. L'annulation du ViewModel se propage
normalement. `StatisticsScreen` affiche maintenant ces valeurs via le NavHost.

### model/GameStatistics.kt

Cette `data class` reçoit les colonnes de la requête de statistiques. Elle
n'est pas une Entity et ne crée aucune table ni migration. Les choix C et A
de l'utilisateur fixent les deux répartitions de cartes et les résultats par
main. Définitions utilisées pour PB-13 :

| Indicateur | Source et définition |
| --- | --- |
| Parties | Nombre de lignes de `games`, une seule même après Split. |
| Mains et résultats | Lignes de `hands`, victoires, défaites et égalités par main. |
| Blackjacks | Mains du joueur marquées naturelles, y compris en cas d'égalité. |
| Gains | Somme des variations nettes positives des mains, sans les mises rendues. |
| Pertes | Somme des variations négatives des mains, exprimée en montant positif. |
| Mise moyenne et maximale | `AVG(totalBet)` et `MAX(totalBet)` par partie. |
| Cartes | Toutes les lignes distribuées de `drawn_cards`, joueur et croupier. |
| Rouge/noir | Cœurs + carreaux / trèfles + piques, parmi toutes les cartes. |
| Quatre sortes | Cœur, carreau, trèfle et pique, parmi toutes les cartes. |

Les pourcentages sont de 0 à 100 sans arrondi SQL. Sans cartes, ils valent
`null`; sans parties, la mise moyenne et maximale valent aussi `null`.
Les compteurs et sommes vides valent zéro. Une main gagnante et une perdante
après Split alimentent chacune leur résultat et leur montant, même si le
bilan de la partie est nul. Le résultat résumé du Split reste à définir pour
l'historique : cette étape ne l'invente pas et ne rend pas le Split jouable.

## 4. Package model : règles du Blackjack

Ces classes doivent pouvoir fonctionner dans des tests Kotlin sans écran, Activity ou base Android.

### model/Card.kt

**Rôle :** représenter une carte par son rang et sa couleur.

| Élément prévu | À quoi il sert |
| --- | --- |
| `rank` | Identifier As, 2 à 10, Valet, Dame ou Roi. |
| `suit` | Identifier pique, cœur, carreau ou trèfle. |
| `baseValue` — propriété calculée proposée | Fournir une valeur de base : figures = 10; As initialement = 11; autres rangs = leur nombre. |

Ce fichier contient une `data class Card`, c'est-à-dire une classe Kotlin qui représente des données. Les `enum class` `Rank` et `Suit` limitent les rangs et les couleurs aux valeurs autorisées, conformément à l'option A choisie. `baseValue` lit la valeur définie dans `Rank`.

**L'As ne peut pas choisir sa valeur finale dans `Card` seul :** il faut connaître les autres cartes de la main.

### model/Hand.kt

**Rôle :** représenter une main et calculer ses caractéristiques à partir de ses cartes.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `addCard(card)` | Ajouter une carte à la main, sans aller la chercher elle-même dans le sabot. |
| `calculateValue()` | Calculer le meilleur total sans dépasser 21 si possible, en réduisant des As de 11 à 1 au besoin. |
| `isBust()` | Indiquer que le total dépasse 21. |
| `hasBlackjackValue()` | Reconnaître une main de deux cartes valant 21. Le modèle de partie décide ensuite si elle est admissible comme Blackjack naturel. |

**Exemples :** As + 9 = 20; As + 9 + 5 = 15; As + As + 9 = 21.

**État actuel :** les quatre fonctions sont implémentées. Le constructeur
accepte une liste initiale facultative; `Hand()` crée une main vide.
La propriété `cards` fournit une copie de la liste. Les modifications de
la main passent par `addCard()`.

Une main de deux cartes valant 21 après un Split ne doit pas recevoir automatiquement le traitement d'un Blackjack naturel : ce contexte appartient à `BlackjackGame` et la règle exacte doit être confirmée.

### model/Shoe.kt

**Rôle :** représenter le sabot, soit la réserve de cartes à distribuer.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `shuffle()` | Mélanger les cartes disponibles. |
| `drawCard()` | Retirer puis retourner une carte disponible pour éviter de distribuer deux fois la même carte physique. |
| `remainingCards` — propriété | Indiquer le nombre de cartes restantes. |

**État actuel :** `Shoe(numberOfDecks)` construit et mélange 52 cartes par
paquet. Le nombre de paquets est un paramètre obligatoire et strictement
positif; aucun nombre par défaut n'est décidé pour l'application.
`drawCard()` retire la dernière carte de la réserve mélangée. Lorsque la
réserve est vide, il renvoie `null`, selon ton choix du 8 octobre 2026.
`shuffle()` mélange uniquement les cartes restantes, sans remplissage.

Un sabot de plusieurs jeux peut contenir plusieurs cartes de même rang
et de même couleur : ce sont des exemplaires physiques distincts.
Les tests interdisent la répétition d'un même objet distribué et vérifient
le nombre de copies de chaque rang/couleur dans plusieurs paquets.

### model/BlackjackGame.kt

**Rôle :** appliquer les règles métier et gérer les phases de la partie : distribution, tour du joueur, tour du croupier et résultat.

| Fonction — état précisé ci-dessous | À quoi elle sert |
| --- | --- |
| `validateBet(amount, availableChips)` | Vérifier la mise selon les limites retenues et les jetons disponibles. |
| `startRound()` | Initialiser les mains, distribuer les cartes et vérifier les situations de départ. La mise sera ajoutée lors de l'étape des jetons. |
| `hit()` | Ajouter une carte à la main active et traiter un éventuel dépassement. |
| `stand()` | Terminer la main active et passer à la suite de la partie. |
| `canSplit()` | Vérifier l'admissibilité du Split et la disponibilité des jetons nécessaires. |
| `split()` | Séparer les cartes, répartir les mises et organiser les tours des mains. |
| `playDealerTurn()` — interne | Faire jouer le croupier selon la règle de 17, lorsque les tours du joueur sont terminés. |
| `determineResult()` — interne | Comparer la main du joueur au croupier et produire victoire, défaite ou égalité. Le traitement de plusieurs mains viendra avec le Split. |
| `calculateChipChange(bet)` — implémentée | Fournir la variation nette d'une partie terminée pour une mise positive, sans modifier le solde. |

Les noms sont des propositions : nous éviterons de créer plusieurs fonctions qui appliquent deux fois la même règle. Les fonctions internes ne seront pas exposées à l'écran si elles ne correspondent pas à une action utilisateur.

**État actuel — option B choisie le 8 octobre 2026 :** `startRound()`,
`hit()`, `stand()`, `playDealerTurn()` et `determineResult()` sont
implémentées pour une seule main. `calculateChipChange(bet)` calcule aussi
les jetons : `-bet` pour une défaite, `0` pour une égalité, `+bet` pour une
victoire ordinaire et `+1.5 * bet` pour un Blackjack naturel gagnant,
selon le choix A de l'utilisateur. Deux Blackjacks naturels donnent `0`.
`validateBet(amount, availableChips)` accepte une mise de 1 à 100 abordable
avec un solde fini. `GameViewModel` applique la variation au solde à la fin.
Le Split reste à faire.

`DealtCard` et la liste `dealtCards` conservent l'ordre global et le
destinataire de chaque carte effectivement distribuée. La liste exposée
est une copie; une nouvelle partie recommence à l'ordre 1. Un tirage
impossible n'ajoute aucune carte au suivi.

Le constructeur public reçoit un `Shoe` existant. Le constructeur
`internal` reçoit une fonction `() -> Card?`, sans paramètre, qui renvoie
une carte ou `null`. Les tests lui fournissent un ordre fixe; le
constructeur public lui transmet un appel à `shoe.drawCard()`. Aucune
bibliothèque de simulation ni modification du sabot n'est nécessaire.

Les `enum class` `GamePhase` et `GameResult`, dans ce même fichier,
décrivent respectivement la phase (`READY`, `PLAYER_TURN`, `DEALER_TURN`,
`FINISHED`, `INTERRUPTED`) et le résultat (`WIN`, `LOSS`, `DRAW`).
Le résultat vaut `null` avant le règlement ou en cas d'interruption.
`private set` empêche le code extérieur de remplacer ces valeurs.
Les mains restent privées; les listes exposées sont des copies et les
totaux sont calculés par `Hand`.

Règles de ce bloc :

- distribution alternée : joueur, croupier, joueur, croupier;
- Blackjack naturel reconnu uniquement dans les deux cartes initiales;
  deux Blackjacks donnent une égalité, sinon le détenteur gagne immédiatement;
- dépasser 21 termine la partie en défaite, sans faire tirer le croupier;
- atteindre 21 en tirant déclenche le tour du croupier, sans transformer
  ce total en Blackjack naturel;
- le croupier tire sous 17 et s'arrête sur tout total de 17 ou plus,
  y compris As + 6, conformément au critère PB-07 du backlog;
- croupier au-dessus de 21 : victoire; sinon comparaison des totaux;
- si une carte manque, la partie passe à `INTERRUPTED`, sans résultat et
  sans renouvellement du sabot, selon l'option A choisie;
- une distribution interrompue peut conserver les cartes déjà tirées;
  elles restent retirées du sabot et aucun résultat partiel n'est attribué.

Les actions renvoient `true` si elles aboutissent, `false` si elles sont
refusées ou interrompues. Une action interrompue peut déjà avoir distribué
des cartes : `false` ne signifie donc pas une annulation des tirages.
`canHit` et `canStand` sont vrais uniquement pendant le tour du joueur.
`startRound()` refuse de remplacer une partie en cours; après une fin ou
une interruption, il remet les mains à zéro en conservant la même source
de cartes. Le nombre de paquets reste choisi par l'appelant.

Le modèle conserve les cartes complètes du croupier pour calculer les
résultats. Le masquage de sa deuxième carte devra être appliqué dans
l'état destiné à l'interface lors du branchement du jeu.

**Ce fichier ne contient pas de SQL.** Il produit le résultat du jeu; le ViewModel demande ensuite sa sauvegarde au repository.

## 5. Package repository : accès aux données

### repository/BlackjackRepository.kt

**Rôle :** être le point d'accès aux données persistées pour les ViewModels, en utilisant `BlackjackDao`.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `saveFinishedGame(...)` — `suspend` | Convertir le résultat de partie dans les données de sauvegarde, puis demander l'enregistrement cohérent de la partie et de ses cartes. |
| `observeHistory()` | Retourner le `Flow` d'historique fourni par le DAO, avec une conversion si l'UI en a besoin. |
| `observeStatistics()` | Retourner les statistiques calculées par SQL. |
| `getDrawnCards(gameId)` — `suspend`, selon le besoin | Consulter les cartes d'une partie enregistrée si un détail est demandé. |

Le repository ne remplace pas le moteur de jeu et ne décide pas qu'un As vaut 1. Il pourra contenir de petites conversions privées entre résultat métier et Entity, uniquement lorsqu'elles seront nécessaires.

La sauvegarde doit être **atomique** : on enregistre la partie, ses mains et ses cartes ensemble, ou rien. Une transaction au niveau du DAO permet d'éviter une partie enregistrée avec la moitié de ses cartes.

**État actuel :** `BlackjackRepository` reçoit le DAO et implémente
`saveFinishedGame(game, bet, playedAt)`, `observeHistory()`, `observeStatistics()`, `getHands(gameId)`
et `getDrawnCards(gameId)`. Il convertit la partie standard terminée en
Entity, après avoir demandé au modèle `calculateChipChange(bet)`. La date
peut être fournie explicitement; sinon l'heure de sauvegarde est utilisée.
Le DAO remplace les identifiants provisoires et effectue la transaction.
La méthode retourne l'identifiant généré et laisse remonter les erreurs.
Le modèle actuel fournit une seule main; la sauvegarde du Split attend
son étape. Les trois ViewModels, leur Factory et l'instance partagée existent;
leur branchement aux écrans reste à faire.

## 6. Package database : Room et SQLite

### Les termes à comprendre

| Terme | Signification dans notre projet |
| --- | --- |
| SQLite | Moteur qui conserve les données dans une base locale sur l'appareil. |
| Room | Bibliothèque Android qui organise l'accès à SQLite et vérifie les requêtes SQL déclarées à la compilation. |
| Entity | Classe qui décrit une table; chaque objet représente une ligne. |
| DAO | Data Access Object : interface qui déclare les lectures et écritures de la base. |
| `@Entity` | Annotation qui identifie une classe comme description de table. |
| `@PrimaryKey` | Identifie la clé qui distingue chaque ligne. |
| Clé étrangère | Relie une ligne à une autre table, par exemple une carte enregistrée à sa partie. |
| `@Dao` | Identifie l'interface des opérations de données. |
| `@Query` | Associe une fonction du DAO à une requête SQL. |
| `@Insert` | Déclare une insertion de données. |
| `@Transaction` | Regroupe les opérations qui doivent réussir ensemble. |
| `@Database` | Déclare la base, ses Entity et sa version. |
| Migration | Décrit comment faire évoluer les tables sans perdre les données existantes. |

Ces notions suivent la [documentation Android sur Room](https://developer.android.com/training/data-storage/room). Room 3.0.3 est choisi et autorisé le 8 octobre 2026, comme dans `Atelier3`. `room3-runtime` fournit les API et annotations; `room3-compiler`, configuré avec KSP 2.3.6, valide le schéma et génère les classes concrètes. `sqlite-framework` 2.7.1 fournit `AndroidSQLiteDriver`, utilisé pour ouvrir les bases de test. Ces outils reprennent la configuration du cours. [Versions officielles de Room 3](https://developer.android.com/jetpack/androidx/releases/room3).

### database/BlackjackDatabase.kt

**Rôle :** déclarer la base Room et fournir son DAO.

| Fonction ou élément prévu | À quoi il sert |
| --- | --- |
| `@Database(...)` | Indiquer les Entity incluses et la version du schéma. |
| Héritage de `RoomDatabase` | Utiliser les services de Room. |
| `blackjackDao()` | Fournir le DAO permettant les opérations sur les données. |

La classe est présente avec les trois Entity, `version = 1` et `exportSchema = false`, comme dans `Atelier3`. Elle est abstraite : Room génère sa classe concrète. Aucun fichier de schéma n'est exporté; une migration devra être décidée avant de modifier une base déjà utilisée. Aucune suppression automatique des données n'est configurée.

Les tests ouvrent une base en mémoire ou un fichier réservé aux tests avec `AndroidSQLiteDriver`. L'instance de production est créée et conservée par `BlackjackApplication`, au premier accès. Cette classe n'a pas besoin d'une méthode spécifique pour chaque requête.

### database/dao/BlackjackDao.kt

**Rôle :** déclarer précisément les opérations SQL. Room fournit leur implémentation.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `insertGame(game)` — `suspend` | Insérer le résumé d'une partie et obtenir son identifiant. |
| `insertHands(hands)` — `suspend` | Insérer les mains du joueur avec l'identifiant de leur partie. |
| `insertDrawnCards(cards)` — `suspend` | Insérer les cartes avec l'identifiant de leur partie. |
| `insertGameWithDetails(...)` — transaction `suspend` | Coordonner l'insertion de la partie, puis de ses mains, puis de ses cartes comme une sauvegarde unique. |
| `observeHistory()` — `Flow` | Lire les parties par date décroissante, puis identifiant décroissant en cas de dates identiques, et publier les changements. |
| `getHands(gameId)` — `suspend` | Lire les mains associées à une partie par numéro croissant. |
| `getDrawnCards(gameId)` — `suspend` | Lire ses cartes par `drawOrder` croissant. |
| `observeStatistics()` — `Flow<GameStatistics>` | Calculer et observer les compteurs, montants et proportions depuis les trois tables. |

Toutes les fonctions de sauvegarde et d'historique du tableau sont implémentées dans l'interface `@Dao`. `@Insert` et `@Query` permettent à Room de générer les opérations. Dans une requête, `:gameId` reçoit le paramètre Kotlin du même nom.

`insertGameWithDetails` récupère l'identifiant retourné par `insertGame`, puis utilise `map` et `copy` pour le donner aux mains et aux cartes sans modifier les objets reçus. Les mains sont insérées avant leurs cartes pour respecter les clés étrangères. `@Transaction` conserve toutes ces écritures ensemble ou annule tout si une insertion échoue. L'erreur remonte à l'appelant, sans remplacement automatique des lignes existantes.

Les statistiques utilisent `COUNT`, `SUM`, `AVG`, `MAX` et `CASE WHEN`.
Chaque table est résumée en une seule ligne, puis `CROSS JOIN` assemble
ces trois résumés : les cartes ne multiplient donc pas les mises ou les gains.
`COALESCE` donne zéro aux sommes vides et `NULLIF` évite une division par zéro.
Room observe les trois tables pour publier les changements dans le même Flow.

Une lecture observable retourne un `Flow`; une écriture ponctuelle utilise une fonction `suspend`. Ces mécanismes permettent les opérations asynchrones et les mises à jour observables décrites dans la [documentation Room sur les requêtes asynchrones](https://developer.android.com/training/data-storage/room/async-queries).

### database/entity/GameEntity.kt

**Rôle :** décrire comment le résumé d'une partie est enregistré dans la base. Ce n'est pas le moteur `BlackjackGame`.

**État actuel :** cette `data class` est annotée `@Entity(tableName = "games")`.
Chaque objet décrit une ligne de la table `games`, sans fonction de jeu.

| Propriété | Donnée conservée |
| --- | --- |
| `id: Long` | Clé primaire générée par Room lors de l'insertion; 0 avant insertion. |
| `playedAt: Long` | Date en millisecondes depuis le 1er janvier 1970. |
| `initialBet: Int` | Mise de départ. |
| `totalBet: Int` | Total des mises, y compris la seconde main après un Split. |
| `result: String` | Résultat résumé de la partie; la convention pour des résultats différents après Split reste à choisir. |
| `chipChange: Double` | Variation totale des jetons, pouvant contenir une moitié de jeton sans décider du paiement. |
| `dealerTotal: Int` | Total final du croupier. |

L'option A à trois tables est choisie le 8 octobre 2026. Une partie reste
une seule ligne même après Split; les résultats détaillés appartiennent
à `HandEntity`. Le repository devra assurer la cohérence entre résumé,
totaux et détails lorsqu'il sera implémenté.

**Différence essentielle :** `BlackjackGame` applique les règles en mémoire; `GameEntity` décrit les données conservées. Enregistrer un résumé final ne suffit pas à permettre la reprise d'une partie interrompue. Cette reprise n'est pas ajoutée implicitement au périmètre.

### database/entity/HandEntity.kt

**Rôle :** conserver la mise et le résultat de chaque main du joueur,
avec une ligne pour une partie standard et deux lignes après un Split.
La table est nommée `hands`.

La clé primaire est **composée** de `gameId` et `handNumber` : la main 1
de la partie 12 est différente de la main 1 de la partie 13. Cette paire
évite aussi d'enregistrer deux fois la même main d'une partie.
La clé étrangère `gameId` référence `GameEntity.id`.

Les autres colonnes sont `bet: Int`, `total: Int`, `result: String`
(`WIN`, `LOSS` ou `DRAW`), `isNaturalBlackjack: Boolean` et
`chipChange: Double`. Le numéro est 1 pour la première main et 2 pour
la seconde après Split; les règles du Split ne sont pas implémentées
par cette Entity. La clé primaire composée commence par `gameId` et
fournit l'index utilisé pour retrouver les mains d'une partie.

### database/entity/DrawnCardEntity.kt

**Rôle :** décrire une carte distribuée et son contexte d'enregistrement.

**État actuel :** la table déclarée est `drawn_cards`. Chaque objet
contient `id: Long` généré lors de l'insertion, `gameId: Long`,
`handNumber: Int?`, `rank: String`, `suit: String` et `drawOrder: Int`.
Les noms anglais de `Rank` et `Suit` seront utilisés, comme `ACE`
et `HEARTS`, sans dépendre de la langue de l'interface.

Le destinataire est représenté par `handNumber` : `null` désigne le
croupier; 1 ou 2 désigne une main du joueur. Cela évite une colonne
supplémentaire qui pourrait contredire la main associée.

Deux clés étrangères sont déclarées : `gameId` vers la partie et
`(gameId, handNumber)` vers la main. Pour le croupier, la seconde
relation n'est pas exigée car `handNumber` est `null`; la relation à
la partie reste obligatoire. [Clés étrangères composites SQLite](https://www.sqlite.org/foreignkeys.html#fk_composite).

L'index `(gameId, handNumber)` facilite l'accès aux cartes d'une main.
L'index unique `(gameId, drawOrder)` évite deux cartes au même emplacement
de distribution d'une partie. `drawOrder` commence à 1 et concerne
tous les tirages, joueur et croupier compris; le modèle devra fournir
ce suivi lors du branchement de la sauvegarde. Aucune unicité n'est
imposée sur rang/couleur : plusieurs paquets contiennent des exemplaires
de mêmes valeurs.

Les clés étrangères gardent l'action par défaut `NO_ACTION` : une
suppression qui laisserait des références invalides est refusée.
Aucune suppression en cascade ni fonctionnalité de suppression n'est
ajoutée. [Clés étrangères Room 3](https://developer.android.com/reference/androidx/room3/ForeignKey).

**Différence avec `Card` :** `Card` représente la carte utilisée par les règles du jeu; `DrawnCardEntity` ajoute le contexte nécessaire pour la retrouver dans une partie enregistrée.

## 7. Chemin complet d'une action

### Le joueur appuie sur « Tirer »

1. `GameScreen` appelle le callback `onHit`.
2. Ce callback appelle `GameViewModel.hit()`.
3. Le ViewModel demande à `BlackjackGame` d'appliquer l'action.
4. `BlackjackGame` vérifie que l'action est autorisée et demande une carte à `Shoe`.
5. La carte est ajoutée à `Hand`, qui calcule son total.
6. Le modèle traite les conséquences, comme un dépassement de 21.
7. Le ViewModel publie le nouvel état dans `uiState`.
8. Compose observe cet état et réaffiche les cartes et les actions autorisées.

### La partie se termine et entre dans l'historique

1. `BlackjackGame` produit les résultats et la variation des jetons.
2. `GameViewModel` demande la sauvegarde dans `viewModelScope`.
3. `BlackjackRepository` prépare les données de sauvegarde.
4. `BlackjackDao` enregistre la partie, ses mains et ses cartes dans une transaction.
5. Room exécute les opérations dans SQLite.
6. Le `Flow` d'historique publie les données actualisées.
7. `HistoryViewModel` expose ce nouvel état à `HistoryScreen`.

`suspend` permet à une fonction de suspendre son exécution pendant une opération asynchrone. Ce mot-clé ne crée pas, à lui seul, un thread de fond. `viewModelScope` fournit le cadre de lancement des coroutines liées au ViewModel; les API asynchrones de Room prennent en charge leurs accès à la base.

## 8. Décisions encore à confirmer

Ce document ne fixe pas silencieusement les éléments suivants :

- conservation durable du solde et de la mise après fermeture : le ViewModel commence à 1000 jetons, accepte les mises de 1 à 100 et garde la mise entre les parties en mémoire;
- conditions détaillées du Split; l'arrêt sur tout 17 est appliqué dans la partie standard selon le critère PB-07;
- convention du résultat résumé d'une partie dont les mains ont des résultats différents après Split; la structure à trois tables est choisie et chaque main conserve déjà son résultat propre;
- détails des maquettes de jeu, de mise et des données (pour PB-02, les barres et le FAB sont validés avec l'option B).

Ces décisions devront être vérifiées dans l'énoncé puis discutées lorsque nécessaire. Si l'énoncé impose une règle, cette règle prévaut sur une proposition de ce document.

## 9. Vérifications prévues

| Partie | Ce qu'il faudra vérifier |
| --- | --- |
| `Card` et `Hand` | Figures, plusieurs As, total 21, dépassement et main de deux cartes. |
| `Shoe` | Distribution sans réutiliser le même exemplaire physique. |
| `BlackjackGame` | Tours, croupier, résultats, mises, jetons et Split selon les règles validées. |
| Repository et DAO | Sauvegarde cohérente, historique observable et statistiques exactes. |
| UI et navigation | Parcours entre écrans, données partagées, cartes cachées et affichage Pixel 8 en portrait/paysage. |

### Vérifications des bases du modèle — 8 octobre 2026

L'option A choisie est implémentée : `Card`, `Hand` et `Shoe` fonctionnent
en Kotlin, sans interface Android ni accès à la base. La commande
`gradlew.bat :app:testDebugUnitTest :app:assembleDebug` a réussi.
Les 21 tests du modèle passent, sans échec ni test ignoré :

| Tests | Nombre | Vérifications principales |
| --- | --- | --- |
| `CardTest` | 3 | Valeur des cartes numériques, des figures et de l'As. |
| `HandTest` | 11 | Totaux, plusieurs As, ajout de carte, dépassement et 21 en deux cartes. |
| `ShoeTest` | 7 | Composition des paquets, exemplaires distincts, compteur, mélange, sabot vide et paramètres invalides. |

Le comportement choisi pour un sabot vide est vérifié : `drawCard()`
renvoie `null`, sans créer de nouvelles cartes. Le nombre de paquets
reste configurable par le constructeur, sans valeur par défaut.

Cette étape avance les bases de PB-03 et PB-04. Elle n'implémente pas
`BlackjackGame`, les règles de règlement, ni leur branchement aux
ViewModels et aux écrans. Les tests Android ne sont pas relancés ici :
aucune interface ou navigation n'a changé et les classes sont testées
directement sur l'ordinateur. Aucun statut du backlog n'est modifié.

### Vérifications de la partie standard — 8 octobre 2026

L'option B pour `BlackjackGame` et l'option A pour l'épuisement du sabot
sont implémentées. `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
a réussi après correction d'une liste de cartes insuffisante dans un
scénario de test. Les 30 tests de `BlackjackGameTest` passent, ainsi que
les 21 tests existants du modèle : 51 tests métier sans échec ni test
ignoré. Le test d'exemple initial passe également (52 tests au total).

Les scénarios vérifient la distribution alternée, l'utilisation d'un
vrai sabot, les trois cas de Blackjack naturel, Tirer, les As, le
dépassement, 21 en trois cartes, Rester, les tirages successifs du
croupier, l'arrêt à 17 dur/souple et au-dessus, les trois résultats,
les actions invalides, une nouvelle partie et l'épuisement de la source
pendant la distribution, le tour du joueur ou celui du croupier.

Cette étape avance PB-04, PB-06 et PB-07 dans la couche Model.
Elle ne livre pas les mises, les paiements, le Split, les ViewModels,
les cartes graphiques ou le branchement des écrans. Aucun PBI n'est
marqué terminé et le fichier Excel n'est pas modifié. Les tests sur
émulateur ne sont pas relancés : ce bloc ne change aucune interface
et se vérifie avec des tests Kotlin sur l'ordinateur.

### Vérifications de la préparation Room — 8 octobre 2026

L'option A à trois Entity est implémentée et Room 3.0.3 est ajouté
avec l'autorisation de l'utilisateur. La commande
`gradlew.bat :app:testDebugUnitTest :app:assembleDebug` a réussi.
Les 51 tests métier existants passent, ainsi que le test d'exemple
(52 tests au total, sans échec ni test ignoré).

Les classes et leurs annotations compilent. La validation du schéma
par le compilateur Room et les tests réels d'insertion, de lecture,
de clés étrangères et d'unicité attendent le DAO et la base, qui
n'existent pas encore. Aucun test artificiel des simples propriétés
des `data class` n'est ajouté. Les tests sur émulateur ne sont pas
relancés car aucune interface ne change et aucune base n'est ouverte.

Cette étape prépare PB-10 et PB-11 sans les déclarer terminés.
Le backlog Excel n'est pas modifié. Le repository, la sauvegarde,
l'historique et le calcul des statistiques restent à implémenter.

### Vérifications du DAO et de la base Room — 8 octobre 2026

L'option B (DAO et base ensemble), avec configuration KSP, est implémentée
après autorisation de l'utilisateur. La commande
`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
a réussi. Room a validé les tables et requêtes et généré les classes
`BlackjackDatabase_Impl` et `BlackjackDao_Impl`. Les 51 tests métier
existants et le test d'exemple passent (52 tests, aucun échec ni test ignoré).

Les 15 tests de `BlackjackDaoTest` passent sur un émulateur Pixel 8
temporaire. Ils vérifient la sauvegarde des trois tables, la liaison avec
l'identifiant généré, deux mains aux résultats distincts, le tri de
l'historique et des cartes, ainsi qu'une nouvelle émission du `Flow`.
Ils vérifient aussi les clés étrangères (y compris une carte liée à la
mauvaise partie), les cartes du croupier avec `handNumber = null`, les
doublons de main et de position de distribution, l'annulation de toute
la transaction après erreur et les cartes de même rang/couleur à des
positions différentes. Un test ferme puis rouvre un fichier SQLite et
retrouve le résumé, les mains et les cartes. Ce fichier est réservé aux
tests et supprimé ensuite.

Après installation des deux APK sur Pixel 8, les tests ont été lancés avec :

```text
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.database.BlackjackDaoTest com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
```

Les tests de navigation ne sont pas relancés : aucune interface ne change.
Le repository, l'instance partagée dans `BlackjackApplication`, les
ViewModels et la sauvegarde depuis le jeu restent à implémenter.
Les requêtes de statistiques ne font pas partie de ce bloc.
PB-10 et PB-11 avancent sans être déclarés terminés; le backlog Excel
n'est pas modifié. La sauvegarde transactionnelle, le `Flow` observable
et la conservation après réouverture peuvent être présentés en Sprint Review
à partir de ces tests, sans prétendre que l'historique est déjà affiché.

### Vérifications du suivi, des paiements et du repository — 8 octobre 2026

Les tests reportés par l'utilisateur sont maintenant ajoutés et exécutés.
18 tests complètent `BlackjackGameTest` (48 tests dans ce fichier) et
12 tests Android sont ajoutés dans `BlackjackRepositoryTest`.
La compilation de l'application et des tests Android réussit avec :

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Les 70 tests Kotlin passent : 69 tests métier et le test d'exemple.
Aucun échec, erreur ni test ignoré n'est présent dans les rapports JUnit.
Les nouveaux scénarios vérifient l'ordre global et le destinataire des
cartes, les tirages du joueur et du croupier, la remise à zéro entre deux
parties, les copies de listes, les actions refusées et l'absence de carte
fictive après épuisement du sabot. Les paiements couvrent la victoire
ordinaire, le Blackjack naturel à 1,5 avec demi-jetons, 21 en trois cartes,
le dépassement du croupier, les défaites, les égalités et deux Blackjacks.
Les mises non positives et le règlement d'une partie inachevée sont refusés.

Sur un Pixel 8 temporaire, les 12 tests repository et les 15 tests DAO
existants passent ensemble : 27 tests Android réussis. Le repository est
vérifié avec Room et SQLite réels, pas avec un faux DAO. Les tests couvrent
les trois tables, tous les champs convertis, les identifiants générés,
les paiements, les lectures, le tri et les nouvelles émissions du Flow.
Une partie inachevée ou une mise invalide n'écrit rien et ne modifie pas
les données précédentes. Une nouvelle partie ne mélange pas ses cartes
avec celles déjà sauvegardées. La fermeture et réouverture d'un fichier
SQLite réservé aux tests conserve le résumé, la main et les cartes.

Après installation des APK sur Pixel 8, la commande Android est :

```text
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.repository.BlackjackRepositoryTest,com.example.blackjack.database.BlackjackDaoTest com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
```

Les bases de test sont isolées et le fichier réservé au repository est
supprimé après les tests. L'émulateur temporaire est fermé ensuite.
Les tests de navigation ne sont pas relancés : aucune interface ne change.
Aucune modification du code de production n'a été nécessaire dans ce bloc.

Ces preuves peuvent alimenter la Sprint Review pour PB-07, PB-10, PB-11
et PB-15 sans déclarer ces PBI terminés ni modifier le backlog Excel.
Les ViewModels, l'instance partagée, le solde, le branchement aux écrans,
le Split et les statistiques restent à implémenter et à tester à leur étape.

### Vérifications de GameViewModel — 8 octobre 2026

L'option B est implémentée avec les choix explicites : paquet neuf d'un
jeu par partie et variation nette appliquée au solde à la fin. Les outils
AndroidX déjà présents suffisent; aucune dépendance n'est ajoutée.

La première compilation des tests utilisait une version du code antérieure
à l'ajout de `roundBet` et a échoué sur ce champ. Après relance complète
sur les sources finales, l'application et les tests Android compilent.
Les 74 tests Kotlin passent : 73 tests métier et le test d'exemple,
sans échec, erreur ni test ignoré. Quatre nouveaux tests couvrent la
validation des mises entre 1 et 100 avec le solde disponible.

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Sur un Pixel 8 temporaire, les 15 tests `GameViewModelTest`, les 12 tests
repository et les 15 tests DAO passent ensemble (42 tests Android).
Les nouveaux tests vérifient les 1000 jetons initiaux, la sélection et la
conservation de mise, les actions refusées pendant le jeu ou l'écriture,
la deuxième carte et le total cachés du croupier, la publication StateFlow,
la révélation finale, les paiements ordinaires et naturels avec demi-jetons,
les égalités, le dépassement et le tour automatique après 21.
Ils vérifient également les interruptions sans paiement ni sauvegarde,
les nouvelles parties avec un modèle neuf, l'indépendance de la prochaine
mise et de la mise de la partie terminée, l'épuisement du solde et les
sauvegardes uniques. Un DAO déléguant à Room simule uniquement l'échec
de la première écriture : la reprise conserve le paiement et n'enregistre
qu'une seule partie, sans tentatives simultanées.

Les actions du ViewModel sont exécutées sur le thread principal Android.
Les bases sont isolées en mémoire et les ViewModelStore sont nettoyés
pour annuler les coroutines après les tests. Les autres tests de persistance
conservent leurs fichiers réservés aux tests et les suppriment ensuite.

```text
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.viewmodel.GameViewModelTest,com.example.blackjack.repository.BlackjackRepositoryTest,com.example.blackjack.database.BlackjackDaoTest com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
```

L'émulateur temporaire est fermé après vérification. Les tests de navigation
et de rotation ne sont pas relancés : les écrans ne sont pas modifiés et
ne sont pas encore branchés au ViewModel. La conservation lors de la rotation
devra être vérifiée après création et partage de l'instance par Android.

Ces résultats peuvent alimenter la Sprint Review pour PB-05, PB-06, PB-07,
PB-10, PB-11 et PB-15 sans déclarer ces PBI terminés ni modifier le backlog
Excel. La Factory, l'instance Room partagée, le branchement Compose, les
autres ViewModels, le Split et les statistiques restent à leurs étapes.

### Vérifications des statistiques — 9 octobre 2026

Les choix C et A sont implémentés : proportions rouge/noir et par sorte,
résultats par main avec nombre de parties distinct. Aucun schéma ni dépendance
ne change. `GameStatistics`, `StatisticsUiState` et `StatisticsViewModel`
sont ajoutés; `observeStatistics()` traverse le repository jusqu'au DAO.

La compilation de l'application et des tests Android réussit. Les 74 tests
unitaires passent, sans erreur ni échec, avec la commande :

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Sur Pixel 8, les six tests `BlackjackStatisticsTest` et les cinq tests
`StatisticsViewModelTest` passent avec les 46 tests existants d'historique,
jeu, repository et DAO : 57 tests Android réussis. La première tentative
s'est arrêtée avant les tests; les journaux montraient aussi une ANR système
pendant le démarrage de l'émulateur. La relance après démarrage réussit.

Les tests vérifient la base vide, les proportions nulles sans cartes,
les sortes absentes à zéro lorsqu'il existe d'autres cartes, les proportions
fractionnaires, la mise moyenne fractionnaire, les gains avec demi-jetons,
les pertes positives et la mise maximale totale par partie. Le jeu de données
de trois parties et quatre mains vérifie un Split gagnant/perdant et une
égalité avec Blackjack naturel : les cartes ne multiplient pas les montants.
Un simple total de 21 sans le marqueur naturel n'est pas compté comme Blackjack.

Le même Flow réagit aux insertions dans chacune des trois tables. Une vraie
partie du modèle, sauvegardée par le repository, actualise le ViewModel déjà
créé. Les tests vérifient aussi le chargement, les erreurs avant et après un
premier résultat, la conservation des dernières valeurs et l'annulation de
l'observation lors du nettoyage du ViewModelStore. Les erreurs de lecture sont
simulées uniquement dans ces tests; les calculs utilisent Room et SQLite réels.

```text
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.database.BlackjackStatisticsTest,com.example.blackjack.viewmodel.StatisticsViewModelTest,com.example.blackjack.viewmodel.HistoryViewModelTest,com.example.blackjack.viewmodel.GameViewModelTest,com.example.blackjack.repository.BlackjackRepositoryTest,com.example.blackjack.database.BlackjackDaoTest com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
```

Les bases sont isolées et les coroutines nettoyées. Le Pixel 8 temporaire est
fermé après vérification. Les tests d'interface et de rotation ne sont pas
relancés : aucun écran n'est modifié. Le Split jouable, la Factory, l'instance
Room partagée et le branchement aux écrans restent à leurs étapes.
Ces résultats peuvent alimenter la Sprint Review pour PB-13 et PB-15.
PB-14 attend l'interface; aucun PBI n'est déclaré terminé et l'Excel est inchangé.

### Vérifications des objets partagés et de la Factory — 9 octobre 2026

Le choix A est implémenté avec `BlackjackApplication`, sa déclaration dans
le manifeste, `BlackjackViewModelFactory` et les propriétés de `MainActivity`.
La base, le repository et les ViewModels sont créés au premier accès.
Le Provider rattache les ViewModels à l'Activity; les écrans ne les consomment
pas encore. Aucune dépendance, table, règle du jeu ou navigation ne change.

La compilation réussit et les 74 tests unitaires passent sans erreur ni échec :

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Les cinq tests `BlackjackViewModelFactoryTest` vérifient la construction des
trois types, le refus d'un type inconnu, la réutilisation dans un même
ViewModelStore malgré une nouvelle Factory, l'indépendance de deux stores
et l'observation du repository fourni. Les écritures utilisent une base
Room isolée en mémoire, avec nettoyage des stores et des coroutines.

Les trois tests `SharedObjectsInstrumentedTest` vérifient l'Application
déclarée, l'identité de la base et du repository, la conservation du
GameViewModel lors de passages entre Mise, Partie et les autres onglets,
et une vraie rotation. L'Activity est recréée, mais les trois ViewModels,
les services partagés et la mise sélectionnée restent identiques. Ces tests
ne sauvegardent aucune partie dans la base partagée de l'application.

Sur Pixel 8, ces huit tests et les cinq tests de navigation en portrait
passent ensemble : 13 tests réussis. La première tentative s'était arrêtée
avant les tests; les journaux montraient une ANR de l'interface système au
démarrage. La relance réussit. Les cinq tests de navigation passent ensuite
en paysage, avec vérification de l'orientation : 18 exécutions Android
réussies au total pour ce bloc.

```text
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.viewmodel.BlackjackViewModelFactoryTest,com.example.blackjack.SharedObjectsInstrumentedTest,com.example.blackjack.NavigationInstrumentedTest -e orientation portrait com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.NavigationInstrumentedTest -e orientation landscape com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
```

Le Pixel 8 temporaire est fermé après vérification. Les 57 tests Android des
couches de jeu et de données, réussis au bloc précédent, ne sont pas relancés :
leur code n'est pas modifié. La Factory a ses tests d'intégration dédiés.
Le branchement Compose, le Split et la persistance du solde restent hors de
ce bloc. Ces preuves soutiennent PB-10, PB-11 et PB-15 et peuvent être montrées
en Sprint Review; aucun PBI n'est déclaré terminé et l'Excel est inchangé.

### Vérifications du PB-02 — 7 octobre 2026

La compilation de l'application et des tests Android a réussi. Les cinq
tests de `NavigationInstrumentedTest.kt` passent sur Pixel 8, en portrait
et en paysage, avec une vérification explicite de l'orientation avant
chaque test (dix exécutions réussies) :

- accueil → mise → table, puis retour vers mise et accueil;
- accès aux onglets, restauration du parcours de Jouer et absence de
  doublons lors d'appuis répétés sur un onglet;
- ouverture d'une nouvelle préparation par le FAB et absence de
  restauration d'une ancienne table.
- accueil → bouton Jouer au Blackjack → mise → onglet Accueil, sans
  utiliser la flèche de retour;
- clics tactiles répétés sur Accueil après restauration du parcours de
  Jouer ou ouverture d'une nouvelle préparation par le FAB.

Le retour par l'onglet Accueil échouait après le bouton de l'accueil ou
une nouvelle préparation. Les deux tests ajoutés ont reproduit l'échec
avant la correction et passent maintenant. `restoreState` est désactivé
uniquement pour Accueil, afin de ne pas réouvrir le parcours qui vient
d'être sauvegardé. Les autres onglets conservent leur restauration.

Les captures sur Pixel 8 ont confirmé l'affichage en portrait (1080 × 2400)
et paysage (2400 × 1080). Une vérification complémentaire en paysage a
confirmé le défilement de la table et le retour à la mise avec son bouton.
Les icônes système sont claires pour rester lisibles sur le fond vert foncé.

Ces vérifications portent sur la navigation du PB-02. Les mises, les cartes,
les règles du jeu et les données persistées restent à implémenter dans
leurs PBI respectifs.

**Lien avec le backlog :** ce document soutient PB-01 et décrit les responsabilités pour la suite. Il ne constitue pas une preuve que ces fonctionnalités sont terminées. On créera les fichiers progressivement, au moment où leur rôle devient nécessaire et après ton choix de l'étape.

## Branchement des écrans — 9 octobre 2026

Choix approuvé : interface casino verte et dorée, cartes traditionnelles et jetons
colorés. Aucun changement des règles, du schéma Room ou des dépendances.

Ordre d’exécution et rôle des fichiers :

1. `MainActivity` récupère les trois ViewModels via la Factory et les passe à
   `BlackjackApp`. Ils appartiennent à l’Activity : les écrans Mise et Partie
   partagent donc le même `GameViewModel`.
2. `BlackjackApp` observe `gameViewModel.uiState` avec
   `collectAsStateWithLifecycle()`. Cette fonction transforme le StateFlow en état
   Compose; une nouvelle valeur provoque l’actualisation des composables qui la
   lisent. L’observation respecte le cycle de vie. Le FAB utilise cet état pour
   afficher Nouvelle partie ou Reprendre la partie. Sa zone est réservée sous le
   contenu : il ne recouvre pas les actions, même après défilement.
3. `BlackjackNavHost` passe cet état et les fonctions à appeler aux écrans.
   `onSelectBet` appelle `selectBet`, `onOpenTable` appelle `startGame` et ne navigue
   que si la distribution est acceptée. `onHit`, `onStand` et `onRetrySave` appellent
   directement les actions correspondantes du ViewModel. Un callback est simplement
   une fonction reçue en paramètre, exécutée lorsqu’un bouton est pressé.
4. `BetScreen` affiche le solde et la mise. Les jetons choisissent des montants
   rapides; FlowRow remet les jetons à la ligne lorsque la largeur manque, sans
   défilement horizontal imbriqué. Le Slider permet tous les entiers de 1 à 100. La validation reste dans
   le ViewModel/modèle. Pendant une partie ou une sauvegarde non résolue, la mise
   est remplacée par un bouton Reprendre.
   Après une partie terminée, Changer la mise ouvre une préparation depuis la
   racine Accueil, même lorsque la table a été reprise avec le FAB.
5. `GameScreen` affiche les cartes et les totaux reçus. `HandPanel` présente une
   main; `PlayingCard` affiche le rang et une ressource vectorielle de la sorte.
   Une carte null affiche uniquement le dos et le texte accessible Carte face
   cachée. `canHit` et `canStand` activent les actions, sans recalculer leurs règles.
   Le résultat, la variation nette, l’enregistrement et les erreurs viennent de l’état.
6. `HistoryScreen` reçoit `HistoryUiState` observé dans le NavHost. `LazyColumn`
   construit les lignes visibles; l’identifiant Room est leur clé stable.
   La date est formatée selon la langue. Chargement, liste vide et erreur sont
   distincts; une erreur ne masque pas les dernières parties reçues.
7. `StatisticsScreen` reçoit `StatisticsUiState` observé de la même façon et affiche
   les valeurs de `GameStatistics`. Aucune somme ni proportion n’est recalculée
   dans Compose. null est affiché par un tiret lorsque la base ne contient pas les
   données nécessaires au calcul d’une moyenne ou d’un pourcentage.
8. `CasinoComponents.kt` contient les panneaux, lignes de valeurs, cartes et petits
   formateurs de présentation. `ScreenContent` conserve le défilement commun.
   Les textes sont dans `values/strings.xml` et `values-en/strings.xml`; les images
   des quatre sortes et du dos sont dans `res/drawable/card_*.xml`.

Exemple de trajet complet : clic Rester → `GameViewModel.stand()` →
`BlackjackGame.stand()` → résultat et variation → nouvel `GameUiState` → affichage.
Le ViewModel sauvegarde ensuite avec le repository → DAO → Room → SQLite.
Les Flow Room émettent alors les nouveaux résumés et statistiques → leurs
ViewModels publient de nouveaux états → les écrans actualisent leur affichage.

Limites conservées : une seule main jouable, Split non branché; le solde de jetons
est conservé pendant la vie du ViewModel, mais pas restauré après arrêt du processus.
Les erreurs de lecture sont affichées sans bouton de relance, car ces ViewModels
ne proposent pas encore une fonction de relance. Une sauvegarde échouée possède,
elle, une fonction de nouvelle tentative déjà implémentée.

Vérification manuelle : choisir une mise, distribuer, changer d’onglet puis reprendre,
terminer avec Tirer/Rester et vérifier le résultat, le solde, l’historique et les
statistiques. Refaire en paysage et en anglais; faire défiler lorsque nécessaire.

Questions de compréhension : pourquoi les écrans Mise et Partie reçoivent-ils le
même état ? Après Rester, qui calcule le résultat et qui déclenche la sauvegarde ?

### Vérifications de cette intégration

Compilation finale réussie :

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Les 74 tests unitaires passent (73 métier et le test d’exemple). Les 68 clés de
chaînes existent dans les deux langues, sans différence de noms. Aucun changement
Room, du repository ou des règles métier dans ce bloc : les tests DAO/repository
et statistiques déjà documentés n’ont pas été répétés sans modification de ces couches.

Sur un Pixel 8 temporaire, trois tests déterministes GameplayUiInstrumentedTest
utilisent une base Room en mémoire et les vrais ViewModels/repository :
- mise de 10, victoire 19 contre 18 avec Rester, solde 1010, une seule sauvegarde,
  actualisation de l’historique et des statistiques, reprise par le FAB et retour
  à l’accueil après Changer la mise;
- mise de 25 conservée entre les onglets, Tirer provoque 26, solde 975 et actions désactivées;
- historique vide explicite, Blackjack naturel avec mise de 5, paiement net de 7,5,
  solde 1007,5 et sauvegarde automatique sans action de jeu.

Les 11 tests du groupe initial en portrait passent : trois parcours de jeu,
cinq parcours NavigationInstrumentedTest et trois SharedObjectsInstrumentedTest,
ces derniers couvrant aussi la vraie rotation. Après les ajustements de disposition,
les trois tests de jeu passent de nouveau dans la version finale en portrait anglais
(Time: 103.239, OK 3 tests).

En paysage français, les cinq tests de navigation et les deux scénarios Blackjack/
dépassement passent. Le scénario victoire/statistiques a d’abord échoué car son
assertion cherchait un compteur hors du viewport. Après ajout du défilement explicite,
sa relance ciblée réussit dans la version finale (Time: 63,012, OK 1 test).
Tous les huit cas distincts ont ainsi été validés en paysage; aucun échec ne reste
sans correction. Les tests font défiler les boutons et titres avant de les utiliser.

Commande des groupes, en adaptant classe et orientation :

```powershell
adb -s emulator-5556 shell am instrument -w -r -e class com.example.blackjack.GameplayUiInstrumentedTest,com.example.blackjack.NavigationInstrumentedTest -e orientation landscape com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner
```

Les essais ont révélé puis permis de corriger le chevauchement du FAB et le
défilement horizontal imbriqué des jetons. Une erreur System UI du démarrage de
l’émulateur a été fermée avant le contrôle visuel final. Les captures finales montrent
la table et la mise en portrait anglais, ainsi que le résultat en paysage français;
les contrôles restent accessibles en faisant défiler. Les textes et nombres sont
localisés. Les captures sont conservées hors du dépôt, dans le dossier de visualisations.

PBI concernés : intégration des mises/parties/règlement (PB-05 à PB-07), navigation
existante PB-02, historique PB-12, écran de statistiques PB-14 et tests PB-15.
La Sprint Review peut démontrer Mise → Partie → sauvegarde → Historique → Statistiques,
avec reprise après changement d’onglet. Aucun PBI n’est marqué Terminé dans Excel :
la validation visuelle par le propriétaire et les critères complets restent nécessaires.
Aucun commit ni push effectué pour ce bloc.

## Choix du nombre de paquets — 9 octobre 2026

Option A approuvée : de 1 à 8 paquets, avec 1 par défaut. BetScreen affiche le nombre
et les boutons −1/+1, désactivés aux limites. Les ressources sont traduites en anglais.
Le réglage utilise le même verrou que la mise : aucun changement pendant la partie,
une sauvegarde en cours ou une sauvegarde échouée à réessayer.

Trajet : bouton → callback onSelectDeckCount du NavHost → GameViewModel.selectDeckCount
→ copie de GameUiState.selectedDeckCount → nouvel affichage. Au clic Distribuer,
startGame transmet ce nombre à createGame(count), qui construit BlackjackGame(Shoe(count)).
La fonction de création reçoit maintenant un Int : les tests peuvent toujours fournir
un jeu déterministe, ou vérifier le nombre transmis avec un vrai Shoe.

Le premier sabot préparé est remplacé si le nombre choisi diffère de 1. Les parties
suivantes reçoivent toujours un sabot neuf. Le nombre choisi reste conservé entre les
parties et lors d’une rotation grâce au ViewModel partagé. Il n’est pas persisté après
arrêt du processus, ni ajouté à l’historique : aucun changement de schéma Room dans ce bloc.
La conservation du solde est ajoutée le 12 octobre 2026 (voir ci-dessous). L’exécution du Split reste en attente.

## Conservation indépendante du solde — 12 octobre 2026

Choix approuvés : table Room indépendante de l'historique, solde initial de
1000 jetons lors du passage de la version 1 à la version 2. Les anciennes
parties, mains et cartes sont conservées; leurs variations ne sont pas
recalculées pour initialiser ce solde.

`PlayerBalanceEntity` représente l'unique ligne `player_balance`, identifiée
par 1. `BlackjackDatabase.MIGRATION_1_2` crée cette table sur une installation
existante; `BlackjackApplication` enregistre cette migration. Une installation
neuve crée sa ligne lors de la première lecture. L'insertion IGNORE conserve
un solde déjà enregistré, y compris zéro et les demi-jetons.

Au démarrage : `GameViewModel` → `BlackjackRepository.loadBalance()` →
`BlackjackDao.getOrCreateBalance()` → Room. `GameUiState.isLoadingBalance`
interdit de commencer ou de modifier une mise pendant la lecture. `BetScreen`
affiche l'attente; si la lecture échoue, il affiche une erreur et un bouton
pour réessayer, sans jouer avec un solde de remplacement.

À la fin : le modèle calcule la variation nette; le ViewModel la présente,
puis le repository demande au DAO d'enregistrer la partie, ses mains, ses
cartes et la variation du solde dans la même transaction. Une erreur annule
toutes ces écritures. Réessayer ne règle pas une seconde fois le solde en
mémoire. Après fermeture, seul le dernier solde sauvegardé est restauré :
la partie en cours, la mise choisie et le nombre de paquets ne sont pas
restaurés. Aucune suppression de l'historique n'est ajoutée à l'interface.

Les mentions précédentes d'un solde uniquement en mémoire décrivent les
étapes antérieures. Cette évolution concerne les jetons et leur persistance;
elle ne déclare aucun PBI terminé et ne modifie pas le backlog Excel.

Vérification de ce bloc : compilation des APK réussie et 79 tests unitaires
réussis. Sur Pixel 8 en portrait, les 82 tests Android passent, dont les trois
tests de `PlayerBalancePersistenceTest` et les 19 tests de `GameViewModelTest`.
Les bases des tests du solde sont isolées de `blackjack.db`. Sont vérifiés :
migration 1 vers 2 avec conservation des parties, mains et cartes; départ à
1000 une seule fois; conservation des demi-jetons après réouverture; solde
indépendant de l'historique; annulation du paiement et de la partie en cas
d'erreur; reprise sans paiement double; restauration par un nouveau ViewModel;
erreur de lecture bloquant le jeu et nouvelle tentative réussie.

En paysage sur Pixel 8, le test
`GameplayUiInstrumentedTest#standSavesOnceAndUpdatesHistoryAndSqlStatistics`
passe également : sélection de mise, partie, paiement unique, historique et
statistiques. Les premiers lancements de l'instrumentation ont été interrompus
avec `Process crashed`, sans diagnostic exploitable; les relances ciblée,
complète et paysage ont réussi.

Commandes : `gradlew.bat :app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest`, puis `adb -s emulator-5554 shell am instrument
-w -r -e orientation portrait
com.example.blackjack.test/androidx.test.runner.AndroidJUnitRunner`.

Vérification : compilation application/tests réussie et 79 tests unitaires réussis.
Sur Pixel 8 : 17 tests GameViewModelTest, quatre GameplayUiInstrumentedTest et une
rotation SharedObjectsInstrumentedTest passent (22 tests, Time: 36.293). Le sélecteur
est aussi testé en paysage (1 test ciblé, Time: 7.97). Aucun échec. La capture portrait
a été inspectée : nombre, boutons, mise et distribution sont lisibles; le contenu défile
sans recouvrir le FAB. Les tests emploient des bases Room en mémoire et ne suppriment
pas l’historique de l’application. Les émulateurs déjà ouverts sont laissés ouverts.

La préparation des parties et les tests avancent : sélection du nombre de paquets,
verrouillage pendant une partie, conservation lors de la rotation, sabot neuf au nombre
choisi. Ces éléments peuvent être démontrés en Sprint Review. Aucun statut du backlog
Excel changé et aucun commit/push effectué.
