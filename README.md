# Fredonne

Appli Android libre pour écrire une mélodie sur son téléphone et la transposer dans une autre tonalité. Tout reste sur l'appareil.

> Projet en démarrage. Ce dépôt ne contient pour l'instant que la conception.

## Principes

- Gratuite et libre (MPL-2.0)
- Aucune permission Internet, aucun traceur, aucun compte
- Hors ligne, les partitions restent sur le téléphone
- Formats ouverts : MusicXML, MIDI, ABC

## Première version (prévue)

- Saisie d'une mélodie à une voix : curseur d'insertion, rangée de durées, mini clavier de piano, correction par glissement
- Lecture par le synthétiseur MIDI du téléphone, avec retour sonore à chaque note
- Transposition vers une autre tonalité ou par intervalle, avec les instruments transpositeurs (Si bémol, Mi bémol, Fa)
- Import et export de fichiers MusicXML et MIDI, par le sélecteur de fichiers Android (sans autorisation de stockage)
- Annuler et rétablir sans limite, mode paysage, zoom

## Hors périmètre pour l'instant

Scan de partitions par photo, partitions d'orchestre, deuxième portée, accords, paroles. Le scan demande un modèle de reconnaissance embarqué ou un serveur : il ne sera étudié que s'il respecte les principes ci-dessus.

## Architecture prévue

| Module | Rôle |
|---|---|
| `core:music` | Modèle musical, transposition, lecture et écriture MusicXML et MIDI. Kotlin pur, couvert par des tests |
| `core:engraving` | Mise en page de la portée à partir du modèle |
| `app` | Interface Jetpack Compose, saisie, lecture sonore |

Détails et choix de conception dans [`docs/conception.md`](docs/conception.md).

## Contribuer

Les idées, les retours d'utilisation et les corrections sont bienvenus dans les tickets. Contact : pilmil.studio@proton.me

## Licence

[MPL-2.0](LICENSE). Une application de Pilmil Studio.

## Note sur la fabrication

Une partie du code et de la documentation de ce projet est écrite avec l'aide de Claude (Anthropic), sous la direction et la relecture de l'auteur.
