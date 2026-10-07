# Bis ScreenSaver installeren

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

1. Open de downloadpagina: **https://github.com/SjoerdjeBis/Claude-rest/releases/tag/laatste**
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

## 5. Toestaan op de tv

Op de tv verschijnt de vraag of je foutopsporing wilt toestaan. Vink **Altijd toestaan vanaf
deze computer** aan en kies **Toestaan**. Het script doet de rest: app installeren en
instellen als screensaver. Aan het eind kun je hem meteen testen.

## Goed om te weten

- **Wanneer start hij?** Zodra de tv een tijdje niets doet. Hoe lang dat duurt stel je in onder
  **Instellingen › Systeem › Energie en energie › Wanneer inactief** (de precieze naam kan per
  versie iets verschillen).
- **Zelf starten:** de app staat ook gewoon tussen je apps als *Bis ScreenSaver*.
- **Na een systeemupdate** van Google kan de screensaver terugspringen naar die van Google.
  Voer stap 4 dan opnieuw uit.
- **Nieuwe versie?** Download de zip opnieuw en voer stap 3 en 4 opnieuw uit. Het IP-adres
  hoeft niet nog eens, het script onthoudt het.
- **Weer terug naar de screensaver van Google?** Verwijder de app via
  Instellingen › Apps › Bis ScreenSaver › Verwijderen.
