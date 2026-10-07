#!/bin/bash
# Bis ScreenSaver installeren op een Chromecast met Google TV, vanaf een Mac.
# Gebruik: open Terminal, typ "bash " (met spatie), sleep dit bestand in het venster en druk op Enter.

PAKKET="nl.bis.screensaver"
SCREENSAVER="$PAKKET/.ScreenSaverDream"
MAP="$HOME/.bis-screensaver"
ADB="$MAP/platform-tools/adb"
HIER="$(cd "$(dirname "$0")" && pwd)"
APK="$HIER/BisScreenSaver.apk"

stap() { echo; echo "──── $1"; }
klaar_met_fout() {
    echo
    echo "✖  $1"
    echo
    read -r -p "Druk op Enter om af te sluiten."
    exit 1
}

clear
echo "Bis ScreenSaver – installatie"
echo "=============================="

[ -f "$APK" ] || klaar_met_fout "BisScreenSaver.apk staat niet naast dit script. Pak de zip opnieuw uit en probeer het nog eens."
mkdir -p "$MAP"

# 1. Het hulpprogramma 'adb' van Google (eenmalig).
if [ ! -x "$ADB" ]; then
    stap "Stap 1: hulpprogramma van Google downloaden (alleen de eerste keer)"
    curl -fL --progress-bar -o "$MAP/platform-tools.zip" \
        https://dl.google.com/android/repository/platform-tools-latest-darwin.zip \
        || klaar_met_fout "Downloaden lukte niet. Controleer je internetverbinding."
    unzip -q -o "$MAP/platform-tools.zip" -d "$MAP" || klaar_met_fout "Uitpakken lukte niet."
    rm -f "$MAP/platform-tools.zip"
    xattr -dr com.apple.quarantine "$MAP/platform-tools" 2>/dev/null
else
    stap "Stap 1: hulpprogramma staat al klaar"
fi

# 2. Het IP-adres van de tv.
stap "Stap 2: verbinden met je tv"
VORIG_IP="$(cat "$MAP/tv-ip" 2>/dev/null)"
if [ -n "$VORIG_IP" ]; then
    read -r -p "IP-adres van je tv [Enter = $VORIG_IP]: " IP
    IP="${IP:-$VORIG_IP}"
else
    echo "Je vindt het IP-adres op de tv onder:"
    echo "  Instellingen › Netwerk en internet › (je wifi-netwerk) › IP-adres"
    read -r -p "IP-adres van je tv (bijvoorbeeld 192.168.1.23): " IP
fi
IP="$(echo "$IP" | tr -d '[:space:]')"
[ -n "$IP" ] || klaar_met_fout "Geen IP-adres ingevuld."
echo "$IP" > "$MAP/tv-ip"

TV="$IP:5555"
"$ADB" start-server >/dev/null 2>&1
"$ADB" disconnect "$TV" >/dev/null 2>&1
UITVOER="$("$ADB" connect "$TV" 2>&1)"

if echo "$UITVOER" | grep -qiE "refused|failed|unable|cannot"; then
    echo
    echo "De tv reageert niet op de gewone manier. Controleer eerst:"
    echo "  • staat de tv aan en zit hij op hetzelfde wifi-netwerk als deze Mac?"
    echo "  • Instellingen › Systeem › Ontwikkelaarsopties › 'USB-foutopsporing' staat AAN"
    echo
    echo "Werkt dat niet, dan kan het ook via 'Draadloze foutopsporing':"
    echo "  1. Zet op de tv in Ontwikkelaarsopties 'Draadloze foutopsporing' aan."
    echo "  2. Open die optie en kies 'Apparaat koppelen met koppelingscode'."
    echo
    read -r -p "Wil je het op die manier proberen? (j/n): " KEUZE
    [ "$KEUZE" = "j" ] || [ "$KEUZE" = "J" ] || klaar_met_fout "Gestopt. Start het script opnieuw als je de instellingen hebt nagekeken."
    read -r -p "Poort achter het IP-adres in het KOPPEL-scherm (het getal na de dubbele punt): " KOPPELPOORT
    read -r -p "Koppelingscode (6 cijfers): " CODE
    "$ADB" pair "$IP:$KOPPELPOORT" "$CODE" || klaar_met_fout "Koppelen lukte niet. Controleer poort en code; die veranderen elke keer."
    echo
    echo "Gekoppeld. Kijk nu in het scherm 'Draadloze foutopsporing' (niet het koppelscherm)"
    read -r -p "naar 'IP-adres en poort'. Welke poort staat daar?: " POORT
    TV="$IP:$POORT"
    "$ADB" connect "$TV" >/dev/null 2>&1
fi

echo
echo "👉  Kijk nu naar je tv. Vraagt hij of je foutopsporing wilt toestaan?"
echo "    Vink 'Altijd toestaan vanaf deze computer' aan en kies 'Toestaan'."
echo
STATUS=""
for i in $(seq 1 60); do
    STATUS="$("$ADB" -s "$TV" get-state 2>/dev/null)"
    [ "$STATUS" = "device" ] && break
    if [ $((i % 5)) -eq 0 ]; then
        "$ADB" connect "$TV" >/dev/null 2>&1
    fi
    printf "."
    sleep 2
done
echo
[ "$STATUS" = "device" ] || klaar_met_fout "Geen verbinding met de tv gekregen. Druk op de tv op 'Toestaan' en start het script opnieuw."
echo "✔  Verbonden met je tv."

# 3. App installeren.
stap "Stap 3: Bis ScreenSaver installeren"
RESULTAAT="$("$ADB" -s "$TV" install -r "$APK" 2>&1)"
if echo "$RESULTAAT" | grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_VERSION_DOWNGRADE"; then
    echo "Oude versie gevonden die niet past; die haal ik eerst weg."
    "$ADB" -s "$TV" uninstall "$PAKKET" >/dev/null 2>&1
    RESULTAAT="$("$ADB" -s "$TV" install "$APK" 2>&1)"
fi
echo "$RESULTAAT" | grep -q "Success" || klaar_met_fout "Installeren mislukt: $RESULTAAT"
echo "✔  App geïnstalleerd."

# 4. Instellen als screensaver.
stap "Stap 4: instellen als screensaver"
"$ADB" -s "$TV" shell settings put secure screensaver_enabled 1
"$ADB" -s "$TV" shell settings put secure screensaver_components "$SCREENSAVER"
INGESTELD="$("$ADB" -s "$TV" shell settings get secure screensaver_components | tr -d '\r')"
[ "$INGESTELD" = "$SCREENSAVER" ] || klaar_met_fout "De tv nam de screensaver-instelling niet over (staat nu op: $INGESTELD)."
echo "✔  Bis ScreenSaver is nu je screensaver."

echo
read -r -p "Wil je de screensaver nu meteen op de tv zien? (j/n): " TEST
if [ "$TEST" = "j" ] || [ "$TEST" = "J" ]; then
    "$ADB" -s "$TV" shell am start -n com.android.systemui/.Somnambulator >/dev/null 2>&1 \
        || "$ADB" -s "$TV" shell monkey -p "$PAKKET" 1 >/dev/null 2>&1
    echo "Kijk op je tv. Druk op een knop van de afstandsbediening om te stoppen."
fi

"$ADB" disconnect "$TV" >/dev/null 2>&1
echo
echo "Klaar! 🎉"
echo "Na een systeemupdate van de tv kan de screensaver terugspringen naar die van Google."
echo "Draai dit script dan gewoon nog een keer."
echo
read -r -p "Druk op Enter om af te sluiten."
