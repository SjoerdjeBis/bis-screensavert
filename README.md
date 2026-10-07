# Bis Screensavert

Een eigen screensaver voor Chromecast met Google TV, in de Bis-huisstijl.

- **Keuzescherm** met Slimme mix, Kunst, Luchtopnames, Mijn foto's & video's en Eigen mix.
- **Kunst:** 119 schilderijen uit het Art Institute of Chicago (publiek domein) met korte
  Nederlandse toelichting.
- **Luchtopnames:** de aerials van de Apple TV.
- **Muziek:** speelt Spotify op de tv, dan neemt een "nu speelt"-scherm het over met hoes en
  artiestfoto's (TheAudioDB, Deezer); bediening met OK en ◀ ▶.
- **Mijn foto's & video's:** gekozen via de fotokiezer van Google Foto's en op de tv bewaard.

Installeren: zie [installatie/HANDLEIDING.md](installatie/HANDLEIDING.md) en, voor Google Foto's,
[installatie/GOOGLE-FOTOS.md](installatie/GOOGLE-FOTOS.md).

## Techniek

- Android TV-app in Kotlin; keuzescherm en fotobeheer in Jetpack Compose, de diavoorstelling
  in gewone Views met Media3 voor video. De screensaver is een `DreamService` (`ScreenSaverDream`).
- Bouwen gebeurt in GitHub Actions; elke push zet een nieuwe zip op de release `laatste`.
- `signing/bis-screensaver.jks` is een vaste sleutel voor deze hobby-app, zodat updates
  over de vorige versie heen installeren. Niet hergebruiken voor iets anders.
- Kunstcollectie bijwerken: workflow *Kunstdata ophalen* vult `data/aic_raw.json`; vertalingen
  staan in `data/vertalingen/`; `python3 tools/build_kunst_nl.py` maakt er
  `app/src/main/assets/kunst_nl.json` van.
- Iconen: `design/icoon/*.svg` (standaard: `venster.svg`).
