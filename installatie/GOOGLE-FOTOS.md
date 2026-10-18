# Google Foto's en Google Drive koppelen

Eenmalig, ongeveer 10 minuten. Google laat apps alleen bij je foto's als je zelf een
(gratis) "project" aanmaakt. Dat project is van jou; niemand anders kan erbij.

De namen van knoppen kunnen iets afwijken: Google verandert dit scherm nogal eens.
Kom je er niet uit, maak dan een schermafbeelding en vraag het mij.

## 1. Project aanmaken

1. Ga op je computer naar **https://console.cloud.google.com** en log in met het Google-account
   van je foto's. Accepteer de voorwaarden als daarom gevraagd wordt.
2. Klik bovenaan op de projectkiezer (naast "Google Cloud") › **Nieuw project**.
3. Naam: `Bis Screensavert` › **Maken**. Kies daarna bovenaan dit nieuwe project.

## 2. De fotokiezer en Drive aanzetten

1. Typ in de zoekbalk bovenaan `Photos Picker API` en klik op het resultaat.
2. Klik op **Inschakelen**.
3. Doe hetzelfde voor `Google Drive API`.

## 3. Toestemmingsscherm instellen

1. Typ in de zoekbalk `Google Auth Platform` (of ga via het menu naar
   **API's en services › OAuth-toestemmingsscherm**) en klik op **Aan de slag**.
2. App-naam: `Bis Screensavert`. Ondersteunings-e-mail: je eigen adres. **Volgende**.
3. Doelgroep: **Extern**. **Volgende**.
4. Contactgegevens: je eigen adres. **Volgende**, vink het akkoord aan, **Maken**.
5. Ga naar **Doelgroep** (Audience) › **Testgebruikers** › **Gebruikers toevoegen**,
   vul je eigen Gmail-adres in en klik **Opslaan**.
6. Zodat de koppeling niet elke 7 dagen verloopt: kies bij **Doelgroep** onder
   *Publicatiestatus* **App publiceren** (of *Naar productie*) en bevestig. Je hoeft de app
   niet te laten verifiëren; Google toont bij het inloggen dan alleen een waarschuwing.
   Weigert Google dit, laat hem dan in testmodus: dan vraagt de tv elke 7 dagen opnieuw om
   in te loggen en wordt de Drive-map tot die tijd niet bijgewerkt.

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

## Een Drive-map bijhouden

Makkelijker dan elke keer kiezen: zet je foto's in een map in Google Drive en laat de tv die
map bijhouden.

1. Maak in Google Drive een map, bijvoorbeeld `Tv`, en zet er foto's en video's in. Vanaf je
   telefoon kan dat met de Drive-app (**+** › **Uploaden**), of vanuit Google Foto's met
   **Delen** › **Opslaan in Drive**. Submappen mogen ook.
2. Open op de tv **Foto's beheren** › **Drive-map koppelen**. Was je eerder alleen voor de
   fotokiezer ingelogd, dan vraagt de tv je één keer opnieuw in te loggen. Laat daarbij het
   vinkje voor Google Drive aan staan.
3. Kies de map met de afstandsbediening. De tv haalt alles op.

Daarna gaat het vanzelf: elke keer dat de app of de screensaver start (hooguit eens per half
uur) kijkt de tv wat er in de map is veranderd. Nieuwe bestanden komen erbij; wat je uit de map
haalt, verdwijnt ook van de tv. Wat je op de tv zelf verwijdert, haalt hij niet opnieuw op.
Foto's worden verkleind tot tv-formaat opgeslagen, dus grote camerafoto's nemen weinig ruimte in.
Video's worden niet opgeslagen: die speelt de tv rechtstreeks uit Drive af. Ze kosten dus geen
opslag, maar hebben wel internet nodig. Zonder internet, of als de koppeling met Google is
verlopen, slaat de screensaver ze over. Video's die al vóór deze versie uit Drive waren
opgeslagen, haalt de tv bij de eerstvolgende keer bijwerken van de tv af; ze spelen daarna
gewoon verder via Drive.

Video's die je via de fotokiezer van Google Foto's hebt toegevoegd, blijven op de tv staan.
Google geeft de app daarvoor alleen een link die na een uur verloopt, dus streamen kan daar
niet. Wil je ook die streamen: zet ze in de Drive-map en verwijder ze daarna in
**Foto's beheren** of op je telefoon (snelknop: video's boven 100 MB).

**De datum in de screensaver** komt uit de foto zelf (de opnamedatum die de camera erin zet).
Staat die er niet in, dan uit de bestandsnaam, zoals `PXL_20230815_…` of `IMG-20230815-WA…`.
Lukt beide niet, dan staat er geen datum; de dag van uploaden wordt nooit getoond. Upload je
foto's daarom liefst rechtstreeks: in de Drive-app **+** › **Uploaden** en kies ze uit je galerij.
Een foto eerst downloaden en dan uploaden kan de opnamedatum en de originele naam kwijtraken.

Wil je meteen bijwerken: **Foto's beheren** › **Nu bijwerken**. Een andere map kiezen of
stoppen kan via **Andere map**.

## Beheren op je telefoon

In **Foto's beheren** staat linksonder een QR-code. Scan die één keer en zet de pagina op
het beginscherm van je telefoon (Delen › Zet op beginscherm). Het adres blijft hetzelfde.

- Groeperen per maand van opname of per keer toegevoegd; sorteren op datum, op hoe vaak iets
  te zien was of op grootte.
- Snelknoppen: alles kiezen wat 10× of vaker te zien was, of video's boven 100 MB.
- **Toevoegen uit Google Foto's** kan hier ook: de fotokiezer opent op je telefoon en de tv
  haalt de rest op.
- De pagina werkt als de tv aan staat met de app of de screensaver open. Krijgt de tv van je
  router een ander IP-adres, scan de QR-code dan opnieuw. Wil je dat voorkomen, geef de
  Chromecast in je router een vast IP-adres.

## Goed om te weten

- De foto's en video's worden **op de tv opgeslagen**. Ze blijven dus ook als de koppeling
  verloopt. Nieuwe foto's in een album van Google Foto's komen er niet vanzelf bij; die in
  een gekoppelde Drive-map wel.
- Zolang je project in de **testmodus** staat, vraagt Google na 7 dagen opnieuw om in te
  loggen (zie stap 3.6). Bij **Foto's beheren** staat dan **Opnieuw inloggen**.
- De Chromecast heeft weinig opslag (een paar GB vrij). Kies via de fotokiezer liever korte
  video's; langere zet je beter in de Drive-map. In **Foto's beheren** zie je hoeveel ruimte er
  nog is.

## Als het niet lukt

- **"Het doorstuuradres in Google Cloud klopt niet"** of `redirect_uri_mismatch`: de
  omleidings-URI uit stap 5.3 moet letterlijk overeenkomen, zonder extra / aan het eind.
- **Je telefoon blijft hangen op "Even geduld"**: tik op **Naar de tv**. Werkt dat ook
  niet, controleer dan of je telefoon op dezelfde wifi zit als de tv.
- **Pagina niet gevonden (404) na inloggen**: de doorgeefpagina staat nog niet aan
  (stap 4), of GitHub is nog bezig. Wacht een paar minuten.
- **"De Google-sleutels kloppen niet"**: plak ze opnieuw via **Sleutels invullen**.
- **"Zet in Google Cloud de Google Drive API aan"**: stap 2.3 is nog niet gedaan.
- **"Google gaf geen toegang tot Drive"**: bij het inloggen stond het vinkje voor Drive uit.
  Kies opnieuw **Drive-map koppelen** en laat het aan staan.
- **"Toegang geweigerd"** of **"access_denied"**: controleer of je eigen Gmail-adres
  als testgebruiker is toegevoegd (stap 3.5).
