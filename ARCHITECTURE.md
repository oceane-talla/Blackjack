# Blackjack Android — rôle des fichiers et fonctions prévues

Document de travail mis à jour le 8 octobre 2026, basé sur notre discussion et les contraintes consignées dans `AGENTS.md`.

## Comment lire ce document

Ce document décrit la structure visée et sert à comprendre où placer chaque responsabilité. Les noms des fonctions proposées pourront être ajustés ensemble avant leur implémentation. Leur présence ici ne signifie pas que le code existe déjà ni que toutes les décisions sont prises.

**État actuel :** `MainActivity`, `BlackjackApp`, `BlackjackDestination`, `BlackjackNavHost`, les cinq écrans, `ScreenContent` et les fichiers du thème existent. Les écrans sont provisoires : la navigation est implémentée, mais les mises, le déroulement du jeu et la persistance restent à construire. `Card`, `Hand` et `Shoe` sont maintenant implémentés dans le package `model`, sans branchement aux écrans. Les vérifications figurent à la section 9. Les autres classes ci-dessous restent à construire; leurs packages sont documentés par des README.

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
│   │   └── PlayingCard.kt
│   └── theme
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── viewmodel
│   ├── GameViewModel.kt
│   ├── HistoryViewModel.kt
│   └── StatisticsViewModel.kt
│
├── model
│   ├── Card.kt
│   ├── Hand.kt
│   ├── Shoe.kt
│   └── BlackjackGame.kt
│
├── repository
│   └── BlackjackRepository.kt
│
└── database
    ├── BlackjackDatabase.kt
    ├── dao/BlackjackDao.kt
    └── entity
        ├── GameEntity.kt
        └── DrawnCardEntity.kt
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

Une initialisation différée avec `lazy` pourra créer ces objets lors du premier accès. Il n'est pas nécessaire d'ajouter une fonction `onCreate()` vide : on ne l'ajoutera que si une initialisation concrète le demande.

**Intégration à prévoir :** déclarer cette classe dans le manifeste Android et fournir le repository aux ViewModels. Le mécanisme simple de création des ViewModels sera expliqué et choisi à cette étape.

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
ouvre une nouvelle préparation de partie, sans restaurer une ancienne table.

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
| `BlackjackNavHost(navController, modifier)` | Définir le `NavHost`, la destination de départ et les écrans disponibles. D'autres paramètres pourront devenir nécessaires lors du branchement des ViewModels. |

Les blocs `composable(...)` déclareront les destinations. Les actions reçues des écrans déclencheront les appels à `navController.navigate(...)` ou le retour arrière.

**Pourquoi séparer ce fichier :** `BlackjackApp` organise la structure commune; `BlackjackNavHost` décrit les chemins entre écrans. La navigation ne contient pas les règles du Blackjack.

### ui/screens/home/HomeScreen.kt

**Rôle :** afficher l'accueil, conservé selon ton choix.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `HomeScreen(...)` | Afficher le contenu de l'accueil et transmettre l'action permettant d'aller à la mise. |
| Paramètre `onStartGame` — prévu | Informer la navigation que l'utilisateur veut commencer. C'est une fonction reçue en paramètre, appelée un callback. |

**État actuel :** `ui/screens/home/HomeScreen.kt` affiche l'accueil et son bouton de démarrage. `onStartGame` transmet l'action au NavHost, qui ouvre la mise. Aucun ViewModel dédié n'est nécessaire tant que cet écran ne gère pas de données qui le justifient.

### ui/screens/bet/BetScreen.kt

**Rôle :** afficher le solde disponible, la mise sélectionnée et les contrôles pour choisir cette mise.

| Fonction ou callback prévu | À quoi il sert |
| --- | --- |
| `BetScreen(...)` | Dessiner l'écran à partir de l'état fourni. |
| `onBetChanged(amount)` | Transmettre le montant choisi au `GameViewModel`. |
| `onConfirmBet()` | Demander le lancement de la partie. |

Les valeurs des jetons, la mise minimale et la mise maximale restent à confirmer avec l'énoncé. L'écran peut désactiver un bouton selon l'état reçu, mais la validation réelle du montant ne dépend pas uniquement du bouton.

**Étape PB-02 :** l'écran est provisoire. Le callback actuel `onOpenTable` sert au bouton « Voir la table » pour vérifier le parcours; aucune mise n'est encore sélectionnée ni transmise.

### ui/screens/game/GameScreen.kt

**Rôle :** présenter la table : croupier en haut, mains du joueur plus bas, cartes, mise, total visible et actions.

| Fonction ou callback prévu | À quoi il sert |
| --- | --- |
| `GameScreen(...)` | Afficher l'état actuel de la partie. |
| `onHit()` | Transmettre « Tirer ». |
| `onStand()` | Transmettre « Rester ». |
| `onSplit()` | Transmettre la demande de séparation de la main, lorsque l'état l'autorise. |
| `onNewGame()` | Demander une nouvelle partie selon le parcours validé. |

**Limite :** cet écran ne calcule ni la valeur des As, ni le gagnant, ni le paiement. Il ne révèle pas non plus une carte cachée en affichant accidentellement le total complet du croupier.

**Étape PB-02 :** l'écran présente les emplacements du croupier et du joueur. Seul `onNewGame` est actuellement branché, pour retourner à la mise; les cartes et les actions de jeu restent à construire.

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

Les indicateurs exacts et leur définition doivent être vérifiés dans l'énoncé avant d'écrire les requêtes. L'écran ne recompte pas lui-même toutes les parties.

### ui/components/PlayingCard.kt

**Rôle :** fournir un composable réutilisable pour afficher une carte traditionnelle ou son dos.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `PlayingCard(card, faceDown, modifier)` | Afficher la face de la carte ou le dos selon `faceDown`. Les paramètres seront ajustés si une carte cachée ne doit pas être transmise à l'UI. |
| `cardDrawableResource(card)` — utilitaire privé éventuel | Associer le rang et la couleur de la carte à une ressource graphique Android. À ajouter uniquement si ce découpage facilite le code. |

Les images seront des ressources du projet, par exemple dans `res/drawable`. La couleur d'une carte signifie ici pique, cœur, carreau ou trèfle, pas seulement rouge ou noir.

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

### viewmodel/HistoryViewModel.kt

**Rôle :** observer l'historique fourni par le repository et exposer un état lisible par l'écran.

| Élément prévu | À quoi il sert |
| --- | --- |
| `uiState` | Exposer l'historique et les états utiles d'affichage. |
| Observation de `repository.observeHistory()` | Recevoir les changements de la base, sans demander à l'utilisateur de recharger manuellement la liste. |

Une fonction `loadHistory()` n'est pas nécessaire si l'observation est continue. Une fonction pour consulter les cartes d'une partie sera ajoutée seulement si le détail fait partie du besoin.

### viewmodel/StatisticsViewModel.kt

**Rôle :** observer les statistiques du repository et les préparer pour l'affichage.

| Élément prévu | À quoi il sert |
| --- | --- |
| `uiState` | Exposer les statistiques et l'état de chargement ou d'erreur utile. |
| Observation de `repository.observeStatistics()` | Recevoir les agrégats provenant de SQLite. |

Des fonctions de filtrage seront ajoutées uniquement si des filtres sont demandés. Aucun indicateur supplémentaire n'est décidé ici.

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

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `validateBet(amount, availableChips)` | Vérifier la mise selon les limites retenues et les jetons disponibles. |
| `startRound(bet)` | Initialiser les mains, distribuer les cartes et vérifier les situations de départ. |
| `hit()` | Ajouter une carte à la main active et traiter un éventuel dépassement. |
| `stand()` | Terminer la main active et passer à la suite de la partie. |
| `canSplit()` | Vérifier l'admissibilité du Split et la disponibilité des jetons nécessaires. |
| `split()` | Séparer les cartes, répartir les mises et organiser les tours des mains. |
| `playDealerTurn()` — interne | Faire jouer le croupier selon la règle de 17, lorsque les tours du joueur sont terminés. |
| `determineResults()` — interne | Comparer les mains admissibles au croupier et produire victoire, défaite ou égalité. |
| `calculateChipChange()` — interne | Calculer la variation des jetons selon les résultats et le traitement des mises retenu. |

Les noms sont des propositions : nous éviterons de créer plusieurs fonctions qui appliquent deux fois la même règle. Les fonctions internes ne seront pas exposées à l'écran si elles ne correspondent pas à une action utilisateur.

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

La sauvegarde doit être **atomique** : on enregistre la partie et ses cartes ensemble, ou rien. Une transaction au niveau du DAO permet d'éviter une partie enregistrée avec la moitié de ses cartes.

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

Ces notions suivent la [documentation Android sur Room](https://developer.android.com/training/data-storage/room). La version de Room et ses dépendances seront choisies séparément avant leur installation.

### database/BlackjackDatabase.kt

**Rôle :** déclarer la base Room et fournir son DAO.

| Fonction ou élément prévu | À quoi il sert |
| --- | --- |
| `@Database(...)` | Indiquer les Entity incluses et la version du schéma. |
| Héritage de `RoomDatabase` | Utiliser les services de Room. |
| `blackjackDao()` | Fournir le DAO permettant les opérations sur les données. |

L'instance sera créée et conservée par `BlackjackApplication`. On évite deux mécanismes concurrents de création de la base. Cette classe n'a pas besoin d'une méthode spécifique pour chaque requête.

### database/dao/BlackjackDao.kt

**Rôle :** déclarer précisément les opérations SQL. Room fournit leur implémentation.

| Fonction prévue | À quoi elle sert |
| --- | --- |
| `insertGame(game)` — `suspend` | Insérer le résumé d'une partie et obtenir son identifiant. |
| `insertDrawnCards(cards)` — `suspend` | Insérer les cartes avec l'identifiant de leur partie. |
| `insertGameWithCards(...)` — transaction `suspend` | Coordonner les deux insertions comme une sauvegarde unique. |
| `observeHistory()` — `Flow` | Lire les parties dans l'ordre retenu et publier les changements. |
| `getDrawnCards(gameId)` — `suspend`, selon le besoin | Lire les cartes associées à une partie dans leur ordre enregistré. |
| `observeStatistics()` — `Flow`, nom provisoire | Calculer les agrégats SQLite demandés. Plusieurs requêtes pourront être nécessaires selon l'énoncé. |

Les statistiques pourront utiliser `COUNT`, `SUM`, `AVG` ou `GROUP BY` si ces opérations correspondent aux indicateurs demandés. Nous choisirons les requêtes à partir du besoin, pas à partir d'une liste arbitraire de fonctions SQL.

Une lecture observable retourne un `Flow`; une écriture ponctuelle utilise une fonction `suspend`. Ces mécanismes permettent les opérations asynchrones et les mises à jour observables décrites dans la [documentation Room sur les requêtes asynchrones](https://developer.android.com/training/data-storage/room/async-queries).

### database/entity/GameEntity.kt

**Rôle :** décrire comment le résumé d'une partie est enregistré dans la base. Ce n'est pas le moteur `BlackjackGame`.

Ce fichier contiendra principalement des propriétés, pas des fonctions de jeu.

**Données envisagées, à valider avant le schéma :** identifiant, date, mise, résultat et variation des jetons. Les totaux utiles et la représentation des résultats d'un Split dépendront des exigences.

Pour une partie séparée en plusieurs mains, une seule valeur « résultat » peut être insuffisante : une main peut gagner et l'autre perdre. La relation entre une partie, ses mains et leurs mises devra être décidée avant d'implémenter les tables.

**Différence essentielle :** `BlackjackGame` applique les règles en mémoire; `GameEntity` décrit les données conservées. Enregistrer un résumé final ne suffit pas à permettre la reprise d'une partie interrompue. Cette reprise n'est pas ajoutée implicitement au périmètre.

### database/entity/DrawnCardEntity.kt

**Rôle :** décrire une carte distribuée et son contexte d'enregistrement.

Ce fichier contiendra principalement des propriétés.

**Données envisagées, à valider :** identifiant, `gameId`, rang, couleur, destinataire joueur/croupier, main concernée après Split et ordre de distribution. Un identifiant d'exemplaire dans le sabot sera utile seulement si le besoin le justifie.

Plusieurs lignes de cartes pourront appartenir à une même partie grâce à `gameId`. La politique de suppression, les index et les contraintes de relation seront décidés avec le schéma.

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
4. `BlackjackDao` enregistre la partie et les cartes dans une transaction.
5. Room exécute les opérations dans SQLite.
6. Le `Flow` d'historique publie les données actualisées.
7. `HistoryViewModel` expose ce nouvel état à `HistoryScreen`.

`suspend` permet à une fonction de suspendre son exécution pendant une opération asynchrone. Ce mot-clé ne crée pas, à lui seul, un thread de fond. `viewModelScope` fournit le cadre de lancement des coroutines liées au ViewModel; les API asynchrones de Room prennent en charge leurs accès à la base.

## 8. Décisions encore à confirmer

Ce document ne fixe pas silencieusement les éléments suivants :

- nombre de jeux utilisé par l'application et moment du renouvellement du sabot; le modèle reçoit déjà un nombre configurable et renvoie `null` lorsqu'il est vide;
- solde initial, limites de mise et traitement durable du solde après fermeture;
- paiement du Blackjack naturel et convention de calcul des jetons : mise déjà retirée ou non;
- traitement du 17 souple et conditions détaillées du Split;
- représentation persistée des mains, mises et résultats après Split;
- statistiques exactes exigées et leur unité de comptage : partie ou main;
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
