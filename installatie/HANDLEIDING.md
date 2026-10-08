# Bis Screensavert installeren

Je hebt nodig: je Mac, je Chromecast met Google TV aan, en beide op hetzelfde wifi-netwerk.
Dit duurt de eerste keer zo'n 5 minuten. Daarna gaat een update in 1 minuut.

## 1. Eenmalig op de tv: USB-foutopsporing aanzetten

Ontwikkelaarsopties heb je al aan. Zet nu nog één schakelaar om:

1. Ga op de tv naar **Instellingen › Systeem › Ontwikkelaarsopties**.
2. Zet **USB-foutopsporing** aan en bevestig met OK.

(Ondanks de naam werkt dit ook via wifi.)

## 2. Het IP-adres van je tv opzoeken

**Instellingen › Netwerk en internet ›** kies je wifi-netwerk. Je ziet daar het **IP-adres**,
bijvoorbeeld `192.168.1.23`. Schrijf het op of laat dit scherm open staan.

## 3. Downloaden

1. Open de downloadpagina: **https://github.com/SjoerdjeBis/bis-screensavert/releases/tag/laatste**
   (je moet ingelogd zijn op GitHub).
2. Klik op **BisScreenSaver.zip**. Safari pakt hem automatisch uit in je map Downloads;
   je krijgt een map **BisScreenSaver**.

## 4. Het installatiescript starten

1. Open **Terminal** (druk op ⌘ + spatie, typ `Terminal`, druk op Enter).
2. Typ `bash` en een **spatie**. Nog niet op Enter drukken.
3. Sleep het bestand **installeer.command** uit de map BisScreenSaver in het Terminal-venster.
   Er verschijnt nu een lange regel met het pad naar het bestand.
4. Druk op **Enter**.

Het script vraagt om het IP-adres van je tv. Typ het in en druk op Enter.

## 5. Toestemming geven op de tv

Op de tv verschijnt de vraag of je foutopsporing wilt toestaan. Vink **Altijd toestaan vanaf
deze computer** aan en kies **Toestaan**. Het script doet de rest:

- de app installeren;
- hem instellen als screensaver;
- de app laten zien wat Spotify afspeelt (voor het muziekscherm);
- als je wilt: Google Foto's koppelen. Dat kan ook later; zie **GOOGLE-FOTOS.md**;
- als je wilt: sfeerbeelden van Pexels en Pixabay koppelen; zie **SFEER.md**.

Aan het eind kun je de screensaver meteen testen.

## Lukt koppelen met de Mac niet? Zo kan het zonder Mac

Op nieuwere Google TV's werkt koppelen via de Mac soms niet. Zo installeer en stel je de app helemaal op de tv zelf in:

1. Open **Downloader** op de tv en typ dit adres:
   `https://github.com/SjoerdjeBis/bis-screensavert/releases/download/laatste/BisScreensavert.apk`
2. Vraagt de tv om toestemming? Kies **Instellingen** en zet **Downloader** aan bij *Onbekende apps installeren*. Ga terug en kies **Installeren**.
3. Open **Bis Screensavert** en kies bovenaan **Nog geen screensaver: instellen**. De app stelt zichzelf in via *Draadloze foutopsporing*:
   1. Scan de QR-code op de tv met je telefoon. Je telefoon moet op dezelfde wifi zitten.
   2. Druk op Home en ga naar **Instellingen › Systeem › Ontwikkelaarsopties › Draadloze foutopsporing**. Zet het aan.
   3. Onder *IP-adres en poort* staat iets als `192.168.68.61:41363`. Vul het getal na de dubbele punt op je telefoon in bij **Poort**.
   4. Kies op de tv **Apparaat koppelen met koppelingscode** en laat dat venster open. Vul de code en de poort uit dat venster op je telefoon in en tik op **Koppelen en instellen**.
   5. Je telefoon laat zien of het gelukt is. De app is dan de screensaver, heeft muziektoegang en mag zichzelf bijwerken. Draadloze foutopsporing mag daarna weer uit.
4. Sleutels vul je in via je telefoon (zie *Sleutels invullen via je telefoon*).

Springt de screensaver na een Google-update terug? Zet Draadloze foutopsporing aan, kies in de app opnieuw **Screensaver instellen** en vul alleen de poort in. Koppelen hoeft maar één keer.

## Zo werkt de app

- **Keuzescherm:** open *Bis Screensavert* tussen je apps. Kies met OK wat je wilt zien:
  Kunst, Luchtopnames, Sfeer, Mijn foto's & video's, Eigen mix of Spotify. Je keuze start meteen
  en wordt ook je screensaver. Met Terug ga je terug; met ▶ spring je naar het volgende beeld.
- **Eigen mix:** kies OK op de kaart en vink aan wat je wilt zien: kunst, luchtopnames, sfeer
  en/of je eigen foto's en video's. De screensaver wisselt ze af.
- **SmartTube:** opent je YouTube-afspeellijst in SmartTube.
- **Spotify** (kaart): speelt Spotify op de tv, dan toont de screensaver hoes, titel en foto's
  van de artiest. Speelt er niets, dan zie je je eigen mix. Met de afstandsbediening:
  **OK** = pauze/verder, **◀ ▶** = vorige/volgende.
- **Muziek** (rij onder de kaarten): kies **Spotify** (de app speelt zelf niets) of **Jazz**
  (rustige jazz, met **Volume** zacht, middel of luid; gaat Spotify toch spelen, dan zwijgt de
  jazz). Zie GELUID.md.
- **Sfeer:** haardvuur, regen, zee, sterren en meer, als beeld. In het voorbeeld stem je een clip weg met **▼**.
- **Geen herhalingen:** wat je de laatste tijd zag, komt niet snel terug, ook niet na een
  herstart, en kunst van dezelfde kunstenaar komt niet twee keer achter elkaar.
- **Instellingen** (onderste rij): tijd per beeld, uitleg bij kunst, klok, sfeerthema's,
  foto's beheren en sleutels invullen.

## Goed om te weten

- **Wanneer start de screensaver?** Als de tv een tijdje niets doet. Hoe lang dat duurt stel je
  in onder **Instellingen › Systeem › Energie en energie** (de precieze naam verschilt per versie).
- **Na een systeemupdate** van Google kan de screensaver terugspringen naar die van Google.
  Voer stap 4 dan opnieuw uit.
- **Nieuwe versie?** Download de zip opnieuw en voer stap 3 en 4 opnieuw uit. Het IP-adres
  en de Google-sleutels hoeven niet nog eens; het script onthoudt ze.
- **Weer terug naar de screensaver van Google?** Verwijder de app via
  Instellingen › Apps › Bis Screensavert › Verwijderen.

## Sleutels invullen via je telefoon

Kies op het keuzescherm **Sleutels invullen**. Scan de QR-code met je telefoon (zelfde wifi als
de tv), plak de sleutels voor Pexels, Pixabay, Jamendo en/of Google Foto's en tik **Opslaan op de
tv**. Lege velden laat de tv zoals ze zijn.

## Met de hand: de adb-commando's

Het installatiescript voert alleen deze commando's uit. Werkt het script niet, typ ze dan zelf in
Terminal. Vervang `192.168.1.23` door het IP-adres van je tv.

```bash
ADB=~/.bis-screensaver/platform-tools/adb          # door het script gedownload
$ADB connect 192.168.1.23:5555                     # daarna op de tv "Toestaan"
$ADB devices                                       # moet "device" tonen
$ADB install -r ~/Downloads/BisScreenSaver/BisScreenSaver.apk
$ADB shell settings put secure screensaver_enabled 1
$ADB shell settings put secure screensaver_components nl.bis.screensaver/.ScreenSaverDream
$ADB shell cmd notification allow_listener nl.bis.screensaver/nl.bis.screensaver.MediaListener
$ADB shell appops set nl.bis.screensaver REQUEST_INSTALL_PACKAGES allow
$ADB shell am start -n com.android.systemui/.Somnambulator      # screensaver nu testen
$ADB disconnect 192.168.1.23:5555
```

Nog geen adb? Download het eenmalig:

```bash
mkdir -p ~/.bis-screensaver && cd ~/.bis-screensaver
curl -fLO https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
unzip -o platform-tools-latest-darwin.zip
```

Terug naar de screensaver van Google: `$ADB uninstall nl.bis.screensaver` (dit wist ook je
instellingen en opgeslagen foto's op de tv).
