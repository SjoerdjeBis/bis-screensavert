# Bis Screensavert

Een eigen screensaver voor Chromecast met Google TV, in de Bis-huisstijl. Gebruik en installatie:
[installatie/HANDLEIDING.md](installatie/HANDLEIDING.md); Google Foto's:
[installatie/GOOGLE-FOTOS.md](installatie/GOOGLE-FOTOS.md).

## Techniek (voor wie hier later verder bouwt)

- **App:** Android TV in Kotlin (`app/`). Keuzescherm en beheerschermen in Jetpack Compose, de
  diavoorstelling in Views met Media3. De screensaver is een `DreamService` (`ScreenSaverDream`).
- **Branch en versies:** `main` is de hoofdbranch; werk op andere branches komt erin via een pull
  request. GitHub Pages (`docs/`) komt van `main`. Elke push, op welke branch ook, bouwt in GitHub Actions (`build.yml`) en zet `BisScreensavert.apk` op de release
  `laatste`; de app werkt zichzelf daarvanaf bij (`Updater.kt`). `main` is beschermd: alleen met een
  groene build. De databots (`kunstdata.yml`, `extra.yml`) pushen daarom op `main` naar een eigen
  branch `bot/…`, maken een pull request, starten de build met de hand (pushes van de bot doen dat
  niet zelf) en voegen samen via auto-merge (`tools/bot_*.sh`). Op andere branches pushen ze direct.
- **Ondertekening:** `signing/bis-screensaver.jks` is een vaste sleutel voor deze hobby-app, zodat
  updates over de vorige versie heen installeren. Niet hergebruiken voor iets anders.
- **Zelf instellen als screensaver:** `SelfSetup.kt` koppelt via Draadloze foutopsporing met de
  eigen tv (libadb-android) en zet `screensaver_components`, de meldingstoegang en de
  installatietoestemming.
- **Telefoonpagina's** draaien op de tv zelf (`PhoneForm.kt`): sleutels (`KeyServer`), koppelen
  (`SelfSetup`) en fotobeheer (`PhoneLibrary`, vast adres op poort 8770).
- **Google Foto's:** Picker API met een OAuth-client van het type *Webapplicatie*. De
  doorstuurpagina is `docs/google.html` via GitHub Pages (bron: deze branch, map `/docs`); daar
  staan ook de startpagina en het privacybeleid voor het toestemmingsscherm.
- **Beeldbronnen:**
  - Kunst: workflow *Kunstdata ophalen* vult `data/*_raw.json` en `data/hashes.json`; vertalingen
    in `data/vertalingen/`; `tools/build_kunst_nl.py` maakt `app/src/main/assets/kunst_nl.json`.
  - Natuur en Toen: workflow *Toen en Natuur ophalen* (`tools/fetch_extra.py`, keurt op formaat,
    scherpte, contrast en dubbelingen) en `tools/build_extra.py` maken `natuur.json` en `toen.json`.
  - Sfeer (Pexels/Pixabay), muziek (Jamendo, `JamendoPlayer`) en artiestfoto's (TheAudioDB, Deezer) komen live.
- **Iconen:** `design/icoon/`.
