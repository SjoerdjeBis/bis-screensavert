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

## 4. Doorgeefpagina aanzetten (GitHub, eenmalig)

Na het inloggen stuurt Google je telefoon naar een kleine pagina van de app op GitHub.
Die geeft de inlog door aan de tv. Zet die pagina zo aan:

1. Ga naar **https://github.com/SjoerdjeBis/bis-screensavert/settings/pages**.
2. Bij **Source**: **Deploy from a branch**. Bij **Branch**: `claude/elegant-brown-wn493w`
   en map `/docs`. Klik **Save**.
3. Na een paar minuten werkt
   **https://sjoerdjebis.github.io/bis-screensavert/google.html**. Er staat dan
   "Deze pagina hoort bij Bis Screensavert".

## 5. De sleutels maken

1. Ga in Google Cloud naar **Clients** › **Client maken**.
2. Applicatietype: **Webapplicatie**. Naam: `Chromecast`.
3. Bij **Geautoriseerde omleidings-URI's** › **URI toevoegen**, precies dit:
   `https://sjoerdjebis.github.io/bis-screensavert/google.html`
4. **Maken**. Je krijgt een **Client-ID** en een **Clientgeheim**.

Had je eerder een client van het type *Tv's en apparaten met beperkte invoer*? Die werkt
niet voor de fotokiezer; je mag hem verwijderen.

## 6. Doorgeven aan de tv

Open op de tv **Bis Screensavert** › **Sleutels invullen**, scan de QR-code en plak bij
*Google Foto's* de Client-ID en het Clientgeheim. Tik op **Opslaan op de tv**.

## 7. Foto's en video's kiezen

1. Open op de tv **Bis Screensavert** › **Foto's beheren** › **Foto's en video's toevoegen**.
2. **Inloggen:** scan de QR-code met je telefoon (op dezelfde wifi als de tv). Kies je
   Google-account. Google waarschuwt dat de app "niet geverifieerd" is: dat klopt, het is
   je eigen app. Kies **Doorgaan** en geef toestemming. Je telefoon stuurt de inlog
   vanzelf door naar de tv en meldt "Gelukt".
3. **Kiezen:** op de tv verschijnt een tweede QR-code. Scan die; Google Foto's opent op je
   telefoon. Vink foto's en video's aan en tik op **Klaar**. De tv haalt ze daarna op.

## Goed om te weten

- De foto's en video's worden **op de tv opgeslagen**. Ze blijven dus ook als de koppeling
  verloopt. Nieuwe foto's in een album komen er niet vanzelf bij; die voeg je zelf toe.
- Zolang je project in de **testmodus** staat (dat is prima), vraagt Google na 7 dagen
  opnieuw om in te loggen. Dat merk je alleen als je iets toevoegt: de tv toont dan weer
  een QR-code om in te loggen.
- De Chromecast heeft weinig opslag (een paar GB vrij). Kies liever korte video's.
  In **Foto's beheren** zie je hoeveel ruimte er nog is.

## Als het niet lukt

- **"Het doorstuuradres in Google Cloud klopt niet"** of `redirect_uri_mismatch`: de
  omleidings-URI uit stap 5.3 moet letterlijk overeenkomen, zonder extra / aan het eind.
- **Je telefoon blijft hangen op "Even geduld"**: tik op **Naar de tv**. Werkt dat ook
  niet, controleer dan of je telefoon op dezelfde wifi zit als de tv.
- **Pagina niet gevonden (404) na inloggen**: de doorgeefpagina staat nog niet aan
  (stap 4), of GitHub is nog bezig. Wacht een paar minuten.
- **"De Google-sleutels kloppen niet"**: plak ze opnieuw via **Sleutels invullen**.
- **"Toegang geweigerd"** of **"access_denied"**: controleer of je eigen Gmail-adres
  als testgebruiker is toegevoegd (stap 3.5).
