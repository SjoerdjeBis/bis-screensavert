# Google Foto's koppelen

Eenmalig, ongeveer 10 minuten. Google laat apps alleen bij je foto's als je zelf een
(gratis) "project" aanmaakt. Dat project is van jou; niemand anders kan erbij.

De namen van knoppen kunnen iets afwijken: Google verandert dit scherm nogal eens.
Kom je er niet uit, maak dan een schermafbeelding en vraag het mij.

## 1. Project aanmaken

1. Ga op je Mac naar **https://console.cloud.google.com** en log in met het Google-account
   van je foto's. Accepteer de voorwaarden als daarom gevraagd wordt.
2. Klik bovenaan op de projectkiezer (naast "Google Cloud") › **Nieuw project**.
3. Naam: `Bis Screensavert` › **Maken**. Kies daarna bovenaan dit nieuwe project.

## 2. De fotokiezer aanzetten

1. Typ in de zoekbalk bovenaan `Photos Picker API` en klik op het resultaat.
2. Klik op **Inschakelen**.

## 3. Toestemmingsscherm instellen

1. Typ in de zoekbalk `Google Auth Platform` (of ga via het menu naar
   **API's en services › OAuth-toestemmingsscherm**) en klik op **Aan de slag**.
2. App-naam: `Bis Screensavert`. Ondersteunings-e-mail: je eigen adres. **Volgende**.
3. Doelgroep: **Extern**. **Volgende**.
4. Contactgegevens: je eigen adres. **Volgende**, vink het akkoord aan, **Maken**.
5. Ga naar **Doelgroep** (Audience) › **Testgebruikers** › **Gebruikers toevoegen**,
   vul je eigen Gmail-adres in en klik **Opslaan**.

## 4. De sleutels maken

1. Ga naar **Clients** › **Client maken**.
2. Applicatietype: **Tv's en apparaten met beperkte invoer**. Naam: `Chromecast`. **Maken**.
3. Je krijgt een **Client-ID** en een **Clientgeheim**. Laat dit venster open, of klik op
   "JSON downloaden" zodat je ze later terugvindt.

## 5. Doorgeven aan de tv

Draai het installatiescript opnieuw (zie HANDLEIDING.md, stap 4). Bij **Stap 6: Google
Foto's** kies je `j` en plak je eerst de Client-ID en daarna het Clientgeheim.
Het script onthoudt ze; de volgende keer hoeft dit niet meer.

## 6. Foto's en video's kiezen

1. Open op de tv **Bis Screensavert** › **Foto's beheren** › **Foto's en video's toevoegen**.
2. **Inloggen:** scan de QR-code met je telefoon (of ga naar google.com/device) en vul de
   code van het tv-scherm in. Google waarschuwt dat de app "niet geverifieerd" is: dat klopt,
   het is je eigen app. Kies **Doorgaan** en geef toestemming.
3. **Kiezen:** scan de tweede QR-code. Google Foto's opent op je telefoon. Vink foto's en
   video's aan en tik op **Klaar**. De tv haalt ze daarna op.

## Goed om te weten

- De foto's en video's worden **op de tv opgeslagen**. Ze blijven dus ook als de koppeling
  verloopt. Nieuwe foto's in een album komen er niet vanzelf bij; die voeg je zelf toe.
- Zolang je project in de **testmodus** staat (dat is prima), vraagt Google na 7 dagen
  opnieuw om in te loggen. Dat merk je alleen als je iets toevoegt: de tv toont dan weer
  een code.
- De Chromecast heeft weinig opslag (een paar GB vrij). Kies liever korte video's.
  In **Foto's beheren** zie je hoeveel ruimte er nog is.

## Als het niet lukt

- **"Google staat de fotokiezer niet toe voor dit soort sleutel"**: dan accepteert Google
  de fotokiezer niet via een tv-code. Laat het me weten; dan bouw ik een variant waarbij je
  inlogt via een pagina op je telefoon.
- **"De Google-sleutels kloppen niet"**: plak ze opnieuw. Verwijder eerst het bestand
  `~/.bis-screensaver/google` op je Mac, dan vraagt het script er weer om.
- **"Toegang geweigerd"** of **"access_denied"**: controleer of je eigen Gmail-adres
  als testgebruiker is toegevoegd (stap 3.5).
