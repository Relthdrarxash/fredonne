# Feuille de route — Fredonne

> Dernière mise à jour : novembre 2026. Référence de conception : [docs/conception.md](docs/conception.md).

## 1. Fonctionnalités à venir
- **v1.0** :
  - Import et export de fichiers **MusicXML** (`.musicxml` et `.mxl` compressé).
  - Import et export de fichiers **MIDI** (`.mid`).
  - Export et impression **PDF** sans filigrane.
  - Historique local des versions de partitions.
- **v1.x** :
  - Orthographe enharmonique automatique après transposition (dièses/bémols cohérents avec la tonalité d'arrivée).
  - Gestion du capodastre et partage système sans permission de stockage.
- **v2.0** :
  - Modulation métrique (rapports de tempo exacts en fractions) et chiffrages irréguliers / tuplets imbriqués.
  - Deuxième voix avec vérificateur harmonique (quintes/octaves parallèles en avertissement non bloquant), chiffrages d'accords et paroles.

## 2. Aspect visuel & UX
- Rendu de la portée dessiné avec la police musicale **Bravura (SMuFL, SIL OFL)**.
- Appui direct sur la portée avec loupe et aimantage sur la ligne la plus proche en secours du clavier virtuel.
- Tests de captures d'écran (jour/nuit, écrans étroits, grandes polices) et accessibilité TalkBack sur l'éditeur.

## 3. Qualité de code & sécurité
- Sauvegarde Android configurable par application (`-Ppilmil.allowAutoBackup=false` par défaut dans `app/build.gradle.kts`, aucune permission `INTERNET`).
- Exécution systématique des tests (`:core:music:test`, `:app:testDebugUnitTest`) et du lint (`:app:lintDebug`) en CI.
- À faire : ajouter `ktlint` / `detekt` et des tests unitaires sur l'import/export MusicXML/MIDI.

## 4. Feuille de route iOS
- **État actuel** : 100 % Android (`core:music` est déjà en Kotlin pur sans dépendance Android).
- **Étapes** :
  1. Extraire `core:music` vers un module KMP `commonMain`.
  2. Porter l'éditeur Compose sur Compose Multiplatform (`iosMain` avec `AVAudioEngine` / `AudioUnit` pour le retour sonore).
  3. Ajouter la coque Xcode (`iosApp/`) et la compilation simulateur en CI macOS.
