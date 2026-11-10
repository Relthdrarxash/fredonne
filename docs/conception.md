# Conception de Fredonne

Ce document fixe les choix avant d'écrire du code. Il évoluera avec le projet.

## Public visé

Chanteurs, chorales, harmonies et fanfares, élèves de conservatoire, instrumentistes qui transposent (clarinette, saxophone, trompette, cor) ou jouent avec un capodastre. Besoin commun : noter ou changer une mélodie simple en quelques secondes, sans compte ni abonnement.

## Promesse

Écris ou transpose une mélodie en quelques secondes, sans connexion.

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

Une fois la mélodie en données, transposer revient à décaler les hauteurs et changer l'armure. Cas à traiter : choix de la tonalité d'arrivée, intervalle libre, instruments transpositeurs, gestion des altérations (dièses ou bémols selon la tonalité d'arrivée), tessiture indicative. Logique en Kotlin pur dans `core:music`, avec tests unitaires.

## Formats

- MusicXML (import et export), y compris le format compressé `.mxl` en lecture.
- MIDI (import et export).
- ABC en lecture, plus tard.
- Aucune permission de stockage : sélecteur de fichiers et partage Android.

## Sauvegarde

Pas de sauvegarde automatique dans le cloud. L'utilisateur exporte ses partitions et peut les passer d'un téléphone à l'autre.

## Rendu de la portée

Dessiné dans Compose avec la police Bravura (SMuFL, licence SIL OFL). Première version limitée aux mélodies à une voix.

## Feuille de route

1. Modèle musical, transposition et tests (`core:music`).
2. Lecture et écriture MusicXML et MIDI.
3. Rendu de la portée et lecture sonore.
4. Saisie tactile.
5. Fiche Play, site, test interne puis fermé.
6. Plus tard : accords, deuxième portée, paroles, ABC. Scan de partitions seulement s'il respecte les principes du projet.

## Idées à valider avant de construire

Tester la promesse auprès de quelques musiciens (chanteurs, instrumentistes transpositeurs), lire les avis récents de Flat et de Notion Mobile sur Google Play, et vérifier la disponibilité du nom sur le Play Store.
