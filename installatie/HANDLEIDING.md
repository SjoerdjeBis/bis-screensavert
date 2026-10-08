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

## Zo werkt de app

- **Keuzescherm:** open *Bis Screensavert* tussen je apps. Kies met OK wat je wilt zien:
  Slimme mix, Kunst, Luchtopnames, Mijn foto's & video's of Eigen mix. Je keuze start meteen
  en wordt ook je screensaver. Met Terug ga je terug; met ▶ spring je naar het volgende beeld.
- **Slimme mix:** past zich aan het tijdstip aan: 's ochtends kunst, 's avonds rustiger met
  je eigen foto's, 's nachts alleen luchtopnames zonder tekst.
- **Muziek:** speelt Spotify op de tv, dan toont de screensaver hoes, titel en foto's van de
  artiest. Met de afstandsbediening: **OK** = pauze/verder, **◀ ▶** = vorige/volgende.
  Elke andere knop maakt de tv wakker.
- **Sfeer:** haardvuur, regen, zee, sterren en meer. In het voorbeeld stem je een clip weg met **▼**.
- **Sfeerlijst:** opent je YouTube-afspeellijst in SmartTube.
- **Geen herhalingen:** wat je de laatste tijd zag, komt niet snel terug, ook niet na een
  herstart, en kunst van dezelfde kunstenaar komt niet twee keer achter elkaar.
- **Instellingen** staan onderaan het keuzescherm: tijd per beeld, uitleg bij kunst, klok,
  eigen mix en of muziek het scherm mag overnemen.

## Goed om te weten

- **Wanneer start de screensaver?** Als de tv een tijdje niets doet. Hoe lang dat duurt stel je
  in onder **Instellingen › Systeem › Energie en energie** (de precieze naam verschilt per versie).
- **Na een systeemupdate** van Google kan de screensaver terugspringen naar die van Google.
  Voer stap 4 dan opnieuw uit.
- **Nieuwe versie?** Download de zip opnieuw en voer stap 3 en 4 opnieuw uit. Het IP-adres
  en de Google-sleutels hoeven niet nog eens; het script onthoudt ze.
- **Weer terug naar de screensaver van Google?** Verwijder de app via
  Instellingen › Apps › Bis Screensavert › Verwijderen.
