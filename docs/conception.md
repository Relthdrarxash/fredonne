# Conception de Fredonne

Ce document fixe les choix avant d'écrire du code. Il évoluera avec le projet.

## Public visé

Chanteurs, chorales, harmonies et fanfares, élèves de conservatoire, instrumentistes qui transposent (clarinette, saxophone, trompette, cor) ou jouent avec un capodastre, étudiants et interprètes de musique contemporaine. Besoin commun : noter, vérifier ou changer une mélodie en quelques secondes, sans compte ni abonnement.

## Promesse

Écris, vérifie ou transpose une mélodie en quelques secondes, sans connexion.

## Saisie

L'enjeu est d'éviter de viser une ligne de portée avec le doigt.

1. Un curseur d'insertion sur la portée, comme dans un traitement de texte.
2. En bas, une rangée de durées (ronde à double croche), point, silence, dièse et bémol, à portée de pouce.
3. Un mini clavier de piano défilable par octave ; chaque appui ajoute la note au curseur et la joue.
4. En secours, un appui sur la portée avec loupe et aimantage sur la ligne la plus proche, puis glissement vertical pour corriger une note.
5. Annuler et rétablir sans limite.

Règles issues de la recherche en interaction homme-machine sur les éditeurs de partitions :
- retour sonore immédiat (fluidité et « liveness ») ;
- possibilité de corriger et déplacer sans casser la structure de la portée ;
- nombre de gestes par note comme mesure à surveiller.

## Transposition

Une fois la mélodie en données, transposer revient à décaler les hauteurs et changer l'armure. Cas à traiter : choix de la tonalité d'arrivée, intervalle libre, instruments transpositeurs, orthographe des altérations (dièses ou bémols selon la tonalité d'arrivée), tessiture indicative. Logique en Kotlin pur dans `core:music`, avec tests unitaires.

## Vérificateur en toile de fond

La musique écrite est très codifiée : beaucoup de règles se vérifient par le calcul. Le vérificateur fonctionne comme un correcteur orthographique : il souligne, il ne bloque jamais.

Vérifications calculables, par niveaux :

1. Durées : le total de chaque mesure correspond au chiffrage. Calcul en fractions exactes (entiers), jamais en décimaux.
2. Tessiture : les notes restent dans l'étendue de l'instrument choisi.
3. Orthographe : altérations cohérentes avec la tonalité, y compris après transposition (Fa dièse ou Sol bémol).
4. Plus tard, avec plusieurs voix : quintes et octaves parallèles, sauts excessifs, croisements de voix.

Ce qui relève du goût (une quinte parallèle est parfois voulue) reste un avertissement que l'on peut ignorer, jamais une erreur. Le vérificateur vit dans `core:music`, avec des tests par règle.

## Modulation métrique et rapports de tempo

Dans la musique contemporaine, un changement de tempo est souvent défini par une équivalence entre une valeur de l'ancien tempo et une valeur du nouveau (modulation métrique, notamment chez Elliott Carter). Le nouveau tempo est donc un rapport exact de l'ancien, et les applis de suivi obligent aujourd'hui à le recalculer à la main.

Exemples :
- noire = 60 ; le triolet de croches devient la nouvelle croche : noire = 90 (rapport 3:2) ;
- noire = 60 ; le quintolet de doubles croches devient la nouvelle double croche : noire = 75 (rapport 5:4).

Fonctions prévues :
- calcul du nouveau tempo à partir de l'équivalence indiquée (« valeur de l'ancien tempo = valeur du nouveau ») ;
- affichage des deux notations ;
- vérification que les durées écrites sont cohérentes avec le tempo ;
- chiffrages irréguliers (5/8, 7/16, mesures composées) et tuplets imbriqués.

Tout se calcule en fractions exactes. MusicXML sait coder les tuplets (`time-modification`) et les indications métronomiques, donc l'export reste compatible. À valider avec un instrumentiste de musique contemporaine avant de figer l'interface.

## Mode étude

Pour toucher le plus de monde possible, Fredonne ne sert pas seulement à écrire : il aide à travailler un passage. Les options sont les mêmes pour tous (élève, choriste, instrumentiste de musique contemporaine) et restent hors ligne. Chaque option est facultative et cachée tant qu'on ne l'ouvre pas, pour ne pas alourdir l'écriture.

Simplifier un passage :
- rythme seul ou hauteurs seules (l'autre dimension est masquée) ;
- remplacer les groupes irréguliers par des valeurs régulières pour apprendre d'abord les hauteurs ;
- version « squelette » qui garde les notes des temps forts ;
- ramener un passage dans une tessiture confortable par changement d'octave.

Travailler le tempo :
- boucle sur quelques mesures, avec tempo qui monte par paliers (par exemple +5 à chaque tour) ;
- décompte avant le départ ;
- métronome programmable qui enchaîne changements de mesure et de tempo, y compris les modulations métriques ;
- subdivisions comptées sous les notes, utiles dans les mesures irrégulières ;
- boucles nommées, retrouvées à la séance suivante.

Travailler les tonalités :
- faire passer un motif dans les 12 tonalités ou en suivant le cycle des quintes, avec la transposition de l'appli ;
- découper une phrase en cellules à répéter puis à enchaîner.

Annotations : respirations, doigtés, repères de travail par mesure.

Un convertisseur métrique complète la modulation métrique : on choisit une zone, on indique un rapport (3:2, par exemple), et les mesures et les durées sont réécrites en conséquence. Les forums de MuseScore montrent que ce besoin est réel et mal servi.

Ces fonctions viennent de demandes lues sur des forums de musiciens et d'applications d'entraînement, surtout jazz et débutants. Leur priorité réelle reste à confirmer avec des instrumentistes, notamment en musique contemporaine (voir le questionnaire).

## Formats

- MusicXML (import et export), y compris le format compressé `.mxl` en lecture.
- MIDI (import et export).
- PDF (export et impression, sans filigrane).
- ABC en lecture, plus tard.
- Aucune permission de stockage : sélecteur de fichiers et partage Android.

## Sauvegarde

Pas de sauvegarde automatique dans le cloud. L'utilisateur exporte ses partitions et peut les passer d'un téléphone à l'autre. Historique des versions conservé localement.

## Rendu de la portée

Dessiné dans Compose avec la police Bravura (SMuFL, licence SIL OFL). Première version limitée aux mélodies à une voix.

## Ce que les grandes applis font payer, et ce qu'on fait gratuitement

D'après les pages des stores et les avis consultés en novembre 2026. Les plaintes les plus fréquentes portent sur la facturation, les abonnements et les relances d'achat.

| Fonction | Ailleurs | Dans Fredonne |
|---|---|---|
| Nombre de partitions | Limité en gratuit (Flat) | Illimité, local |
| Instruments supplémentaires | Abonnement (Flat) | Tous inclus |
| Historique des versions | Abonnement avec cloud (Flat) | Local, gratuit |
| Export et impression sans filigrane | Abonnement (Flat) | Gratuit |
| Éditer, télécharger, imprimer | Abonnement (MuseScore mobile) | Gratuit |
| Changer la tonalité | Dans l'abonnement | Gratuit |
| Compte et cloud | Nécessaire | Aucun |

Ce que Fredonne ne fera pas : cloud, collaboration en temps réel, bibliothèque de partitions en ligne.

## Feuille de route

Chaque étape se termine par une version testable sur le test interne de Google Play.

1. **Fondations (`core:music`)** : modèle musical en fractions exactes, transposition, vérificateur de durées et de tessiture, tests unitaires.
2. **Formats** : lecture et écriture MusicXML et MIDI.
3. **Rendu et son** : portée dessinée, lecture par le synthétiseur MIDI, retour sonore à chaque note.
4. **Saisie tactile** : curseur, durées, mini clavier, correction par glissement, annuler et rétablir.
5. **Version 1.0** : mélodie à une voix, transposition avec instruments transpositeurs, vérificateur de mesures, export MusicXML, MIDI et PDF, historique local. Fiche Play, site, test fermé.
6. **Version 1.x** : orthographe après transposition, capodastre, partage, impression, retours des testeurs.
7. **Version 2** : modulation métrique et convertisseur métrique, chiffrages irréguliers et tuplets, mode étude (boucle, tempo progressif, simplifications, 12 tonalités), deuxième voix avec vérification harmonique, chiffrages d'accords, paroles.
8. **Plus tard** : deuxième portée, lecture ABC, étude du scan de partitions (modèle embarqué, jamais de serveur).

## Mesurer la qualité

- Questionnaire d'utilisabilité standard (SUS), utilisé dans l'enquête sur les éditeurs de partitions, auprès des testeurs du test fermé.
- Nombre de gestes et temps pour saisir une mélodie de référence, mesurés sur quelques parcours.
- Tests auprès de musiciens de profils variés : chanteurs, instrumentistes transpositeurs, un instrumentiste de musique contemporaine.
- Le test fermé de Google Play exige au moins 12 testeurs pendant 14 jours : s'appuyer sur des chorales, harmonies et classes de conservatoire.

## Avant de construire

Tester la promesse auprès de quelques musiciens, lire les avis récents de Flat et de Notion Mobile sur Google Play, vérifier la disponibilité du nom sur le Play Store.

## Direction visuelle

Voir les [maquettes](maquettes/README.md).

- Couleurs : papier `#FBF8F2`, encre `#1E1B18`, accent terracotta `#C0432A`, ambre `#8F5200` pour les avertissements du vérificateur, vert `#2D6A4F` pour la validation.
- Polices : Newsreader (titres), Figtree (interface), Bravura (portée). Toutes sous licence libre (SIL OFL).
- Principes : une seule couleur d'accent, cibles de 44 px au minimum, contraste d'au moins 4,5:1, clavier sous le pouce, aucune information qui ne soit portée que par la couleur.
