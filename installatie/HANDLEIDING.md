# Bis Screensavert

Een eigen screensaver voor je Chromecast met Google TV. Alles gaat op de tv zelf en met je
telefoon; een computer is niet nodig.

## Installeren

1. Open **Downloader** op de tv en typ dit adres:
   `https://github.com/SjoerdjeBis/bis-screensavert/releases/download/laatste/BisScreensavert.apk`
2. Vraagt de tv om toestemming? Kies **Instellingen**, zet **Downloader** aan bij
   *Onbekende apps installeren*, ga terug en kies **Installeren**.

## Instellen als screensaver (eenmalig)

Google TV heeft geen scherm om zelf een screensaver te kiezen. De app stelt zichzelf in via
*Draadloze foutopsporing*:

1. Open Bis Screensavert en kies bovenaan **Nog geen screensaver: instellen**.
2. Scan de QR-code met je telefoon (op dezelfde wifi als de tv).
3. Druk op Home en ga naar **Instellingen › Systeem › Ontwikkelaarsopties › Draadloze
   foutopsporing**. Zet het aan. Onder *IP-adres en poort* staat iets als `192.168.68.61:41363`;
   vul het getal na de dubbele punt op je telefoon in bij **Poort**.
4. Kies op de tv **Apparaat koppelen met koppelingscode** en laat dat venster open. Vul de code en
   de poort uit dat venster op je telefoon in en tik op **Koppelen en instellen**.
5. Je telefoon meldt of het gelukt is. De app is dan de screensaver, mag zien wat Spotify speelt
   en mag zichzelf bijwerken. Draadloze foutopsporing mag daarna weer uit.

**Springt de screensaver na een Google-update terug?** Zet Draadloze foutopsporing aan, kies in de
app opnieuw **Screensaver instellen** en vul alleen de poort in. Koppelen hoeft maar één keer.

## Sleutels invullen

Sommige onderdelen hebben een gratis sleutel nodig. Kies op het keuzescherm **Sleutels invullen**,
scan de QR-code en plak ze op je telefoon. Lege velden laat de tv zoals ze zijn.

- **Sfeer** (Pixabay en/of Pexels; één is genoeg):
  - Pixabay: maak een account op pixabay.com en ga naar **https://pixabay.com/api/docs/**.
    Ingelogd staat je sleutel bij "Parameters" › "key".
  - Pexels: account op pexels.com, dan **https://www.pexels.com/api/new/** (persoonlijk gebruik).
- **Muziek** (Jamendo): account op **https://devportal.jamendo.com**, dan *My Applications* ›
  *Create a new application* (naam `Bis Screensavert`, niet-commercieel). Kopieer de Client ID.
- **Google Foto's**: zie **GOOGLE-FOTOS.md**.

## Zo werkt de app

**Kaarten** (met OK start je keuze meteen en wordt die je screensaver):

- **Kunst:** ruim 400 werken uit drie musea, met korte Nederlandse uitleg.
- **Natuur:** de mooiste natuurfoto's uit Nederland, met de Nederlandse soortnaam.
- **Toen:** zwart-witfoto's uit het Nationaal Archief, bij voorkeur van deze dag in een ander jaar,
  met hoe lang geleden het was.
- **Luchtopnames:** de aerials van de Apple TV.
- **Sfeer:** haardvuur, regen, zee, sterren en meer, zonder tekst. Thema's kies je onder
  *Sfeerthema's*.
- **Mijn foto's & video's:** uit je eigen Google Foto's.
- **Eigen mix:** vink aan wat je wilt zien; de screensaver wisselt het af.
- **Spotify:** speelt Spotify op de tv, dan zie je hoes, titel en foto's van de artiest
  (OK = pauze, ◀ ▶ = vorige/volgende). Speelt er niets, dan je eigen mix.
- **SmartTube:** opent je YouTube-afspeellijst in SmartTube.

**Muziek:** kies **Spotify** (de app speelt zelf niets) of rustige, instrumentale muziek van
Jamendo: **Piano**, **Gitaar** (Spaans) of **Country** (met *Volume*). Gaat Spotify spelen, dan
zwijgt die muziek. Bij elk nieuw nummer staat linksboven
5 seconden wat er speelt.

**Instellingen:** tijd per beeld, uitleg bij kunst, klok, sfeerthema's, foto's beheren en
sleutels invullen.

**In het voorbeeld** (een kaart gekozen, nog niet als screensaver):
**▶** volgend beeld, **▼** dit beeld nooit meer tonen (werkt ook in je mix), **▲** dit nummer
nooit meer spelen. In de echte screensaver maakt elke knop de tv wakker.

## Goed om te weten

- **Bijwerken:** verschijnt er op het keuzescherm een knop met een nieuwe versie, druk erop.
  Of installeer opnieuw via Downloader met hetzelfde adres; je instellingen blijven bewaard.
- **Wanneer start de screensaver?** Stel je in onder Instellingen › Systeem › Energie (de precieze
  naam verschilt per versie).
- **Geen herhalingen:** wat je de laatste tijd zag, komt niet snel terug, ook niet na een herstart.
- **Opslag:** alleen je eigen foto's en video's staan op de tv; de rest komt live van internet.
- **Terug naar de screensaver van Google?** Instellingen › Apps › Bis Screensavert › Verwijderen.
  Dat wist ook je instellingen en opgeslagen foto's.
