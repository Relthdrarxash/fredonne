# Maquettes de Fredonne

Propositions de design pour l'application. Ce sont des **maquettes**, pas l'appli : elles servent à discuter de l'interface avant d'écrire le rendu et la saisie.

![Aperçu des douze écrans](apercu.png)

## Écrans

| | | | |
|---|---|---|---|
| ![](01-presentation-1-ecrire.png) | ![](02-presentation-2-transposer.png) | ![](03-presentation-3-confidentialite.png) | ![](04-presentation-4-instrument.png) |
| Présentation 1 : écrire | Présentation 2 : transposer | Présentation 3 : vie privée | Présentation 4 : instrument |
| ![](05-accueil.png) | ![](06-nouvelle-partition.png) | ![](07-editeur.png) | ![](08-verificateur.png) |
| Accueil | Nouvelle partition | Éditeur | Vérificateur |
| ![](09-transposer.png) | ![](10-tempo-modulation-metrique.png) | ![](11-partager.png) | ![](12-reglages.png) |
| Transposer | Tempo et modulation métrique | Partager | Réglages |

## Ce qu'elles montrent

- **Saisie au pouce :** curseur d'insertion sur la portée, rangée de durées (ronde à double croche, point, silence, triolet), clavier de piano en notes françaises avec choix de l'octave. Chaque note posée est jouée.
- **Vérificateur en arrière-plan :** une mesure incomplète ou trop longue est soulignée, avec une proposition de correction. Il souligne, il ne bloque jamais, et ne signale pas la mesure en cours d'écriture.
- **Transposition :** douze tonalités, sens (plus haut, plus bas, le plus proche), écriture pour un instrument transpositeur, aperçu de l'armure.
- **Modulation métrique :** l'utilisateur indique l'équivalence entre une valeur de l'ancien tempo et une du nouveau, Fredonne calcule le tempo et vérifie les durées.
- **Partage :** PDF sans filigrane, MusicXML, MIDI, image, au choix en notation pour l'instrument ou en sons réels.
- **Vie privée :** aucun compte, aucune connexion, présentée dès le premier lancement.

## Direction visuelle

- Papier crème, encre sombre, une seule couleur d'accent (terracotta).
- Titres en Newsreader, interface en Figtree, portée en Bravura (polices libres, licence SIL OFL).
- Cibles tactiles de 44 px au minimum, texte contrasté (au moins 4,5:1).

## Pas encore dessiné

Mode sombre, mode paysage, tablette, état vide de l'accueil, aide à la première saisie.
