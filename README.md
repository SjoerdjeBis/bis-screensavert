# Bis ScreenSaver

Een eigen screensaver voor Chromecast met Google TV.

**Fase 1 (nu):** kunstwerken uit het Art Institute of Chicago (publiek domein, met toelichting)
afgewisseld met de luchtopnames van de Apple TV-screensaver.

**Gepland:** Spotify "now playing" met artiestfoto's, foto's en video's uit Google Foto's via de
fotokiezer, eigen sfeervideo's, en een beheerpagina op je telefoon.

Installeren: zie [installatie/HANDLEIDING.md](installatie/HANDLEIDING.md).

## Techniek

- Android TV-app in Kotlin; de screensaver is een `DreamService` (`ScreenSaverDream`).
- Bouwen gebeurt in GitHub Actions; elke push zet een nieuwe zip op de release `laatste`.
- `signing/bis-screensaver.jks` is een vaste sleutel voor deze hobby-app, zodat updates
  over de vorige versie heen installeren. Niet hergebruiken voor iets anders.
