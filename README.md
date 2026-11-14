# Fredonne

Application libre (Android & iOS, Kotlin Multiplatform / Compose Multiplatform) pour écrire une mélodie sur son téléphone, l'écouter avec un timbre acoustique réaliste et la transposer dans une autre tonalité ou pour un instrument transpositeur. Tout reste sur l'appareil.

## Principes

- Gratuite et libre (MPL-2.0)
- Aucune permission Internet, aucun traceur, aucun compte
- 100 % hors-ligne : les partitions restent sur le téléphone
- Formats ouverts : MusicXML 4.0 (`.musicxml`), Standard MIDI File (`.mid`), partition PDF vectorielle (`.pdf`)

## Fonctionnalités implémentées

- **Saisie mélodique fluide** : curseur d'insertion, rangée de durées (ronde à double-croche, pointée, silence), mini clavier de piano défilable par octave.
- **Synthèse acoustique hors-ligne à 44,1 kHz (`ToneSynthesizer`)** :
  - 5 familles de timbres (`WARM_KEYS`, `FLUTE`, `REED`, `BRASS`, `STRING`) avec partiels inharmoniques ($f_n = n f_0 \sqrt{1 + B n^2}$, Fletcher 1964), pondération d'isosonie **ISO 226:2023** et réverbérateur de **Schroeder (1962) / Moorer (1979)** (4 filtres en peigne passe-bas parallèles + 2 diffuseurs passe-tout en série).
- **Transposition & musicologie computationnelle (`MusicTheory`)** :
  - Instruments transpositeurs (Si♭, Mi♭, Fa, Ut, Guitare 8va) et changement de tonalité.
  - Résolution enharmonique contextuelle inspirée de **PS13 (Meredith, 2006)** et simplification automatique des armures théoriques dépassant 6 altérations.
  - Détection automatique de la tonalité par corrélation de Pearson sur les profils cognitifs de **Krumhansl & Schmuckler (1990)**.
- **Gravure & vérificateur en toile de fond (`Verifier`, `StaffCanvas`)** :
  - Espacement horizontal non linéaire des durées selon la loi logarithmique de **Gourlay (1987)** et règles de hampes d'**Elaine Gould**.
  - Vérification non bloquante des durées de mesure en fractions exactes (`Fraction`), de la tessiture réelle de l'instrument et du contrepoint à deux voix (quintes et octaves parallèles, croisements de voix selon **Fux / Tymoczko**, et rugosité sensorielle en bande critique selon **Plomp & Levelt 1965 / Sethares 1993**).
- **Import / Export formats ouverts (`MusicXmlCodec`, `MidiCodec`, `ScorePdfGenerator`)** :
  - Import et export **MusicXML 4.0** (`<score-partwise version="4.0">` avec `<transpose>` pour les instruments transpositeurs).
  - Import et export **MIDI standard** (`.mid`, SMF Format 0).
  - Export **PDF A4 vectoriel** sans filigrane.
  - Annuler et rétablir multi-niveaux.

## Architecture

| Module | Rôle |
|---|---|
| `core:music` | Modèle musical en fractions exactes (`Fraction`), transposition, orthographe enharmonique PS13 & détection tonale Krumhansl–Schmuckler (`Theory.kt`), vérificateur de mesures, tessiture et contrepoint (`Verifier.kt`), codecs `MusicXmlCodec`, `MidiCodec` et `ScorePdfGenerator`. Kotlin Multiplatform pur, couvert par des tests unitaires. |
| `shared` | Interface Compose Multiplatform (`FredonneApp.kt`), état de l'éditeur (`EditorState.kt`), synthétiseur acoustique (`ToneSynthesizer.kt`), lecteur audio (`TonePlayer`) et exporteur de fichiers (`ScoreExporter`) pour Android et iOS. |
| `app` | Application Android (`MainActivity.kt`) et tests unitaires / captures. |
| `iosApp` | Coque iOS / Xcode (`FredonneApp.swift`). |

Détails et choix de conception dans [`docs/conception.md`](docs/conception.md).

## Contribuer

Les idées, les retours d'utilisation et les corrections sont bienvenus dans les tickets. Contact : pilmil.studio@proton.me

## Licence

[MPL-2.0](LICENSE). Une application de Pilmil Studio.
