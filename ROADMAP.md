# Feuille de route — Fredonne

> Dernière mise à jour : novembre 2026. Référence de conception : [docs/conception.md](docs/conception.md).

## 1. Fonctionnalités implémentées (v1.0 / v1.x)
- **Moteur théorique & formats (`core:music`)** :
  - Modèle musical en fractions exactes (`Fraction`), transposition et instruments transpositeurs (`Instrument`, `Instruments`).
  - Import et export **MusicXML 4.0** (`MusicXmlCodec`, `<score-partwise version="4.0">` avec gestion de `<transpose>`, des points et des tuplets `<time-modification>`).
  - Import et export **MIDI standard** (`MidiCodec`, SMF Format 0 `.mid`).
  - Export **PDF A4 vectoriel** sans filigrane (`ScorePdfGenerator`) avec espacement logarithmique de Gourlay (1987).
  - Orthographe enharmonique contextuelle inspirée de **Meredith PS13 (2006)**, simplification automatique des armures $> 6$ altérations (`respellPractical`) et détection de tonalité par profils de **Krumhansl & Schmuckler (1990)** (`Theory.kt`).
  - Vérificateur de mesures, de tessiture et de contrepoint à deux voix : quintes/octaves parallèles, croisements de voix (**Fux / Tymoczko**) et dissonance sensorielle (**Plomp & Levelt 1965 / Sethares 1993**) (`Verifier.kt`).
- **Synthèse acoustique & interface (`shared`, `app`, `iosApp`)** :
  - Synthétiseur multi-timbres à 44,1 kHz (`ToneSynthesizer` : piano feutré à double corde, flûte, anche, cuivre, cordes), pondération d'isosonie **ISO 226:2023** et réverbérateur de **Schroeder (1962) / Moorer (1979)**.
  - Portée interactive avec espacement logarithmique des durées, mini clavier de piano, historique annuler/rétablir, dialogue d'import/export MusicXML, MIDI et PDF (`FredonneApp.kt`, `ScoreExporter`).

## 2. Prochaines étapes (v2.0)
- **Modulation métrique** (rapports de tempo exacts en fractions à la Elliott Carter) et interface dédiée aux chiffrages irréguliers / tuplets imbriqués.
- **Éditeur graphique à deux portées / deux voix simultanées** branché sur `Verifier.checkTwoVoices`, chiffrages d'accords et paroles.
- **Lecture de fichiers `.mxl` compressés (ZIP)** et notation **ABC**.
- **Police musicale SMuFL Bravura** embarquée et saisie directe par glissement vertical sur la portée avec loupe.
