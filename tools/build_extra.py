"""Bouwt de app-bestanden voor Nederland van toen en Natuur uit data/extra/.

  app/src/main/assets/toen.json   – Nederlandse beschrijvingen van het Nationaal Archief
  app/src/main/assets/natuur.json – Nederlandse soortnamen van iNaturalist

Elk item: id, titel, onder (regel onder de titel), uitleg, bron, datum, afbeelding, klein.
"""
import json
import re

MAANDEN = ["januari", "februari", "maart", "april", "mei", "juni", "juli", "augustus",
           "september", "oktober", "november", "december"]
LICENTIES = {"cc0": "CC0", "cc-by": "CC BY", "cc-by-sa": "CC BY-SA", "cc-by-nc": "CC BY-NC"}


def load(path, default):
    try:
        return json.load(open(path))
    except FileNotFoundError:
        return default


def nl_date(iso):
    if not iso:
        return None
    y, m, d = iso[:10].split("-")
    return f"{int(d)} {MAANDEN[int(m) - 1]} {y}"


def shorten(text, limit=300):
    text = re.sub(r"\s+", " ", text or "").strip()
    if len(text) <= limit:
        return text
    out = ""
    for sentence in re.split(r"(?<=[.!?])\s+", text):
        if len(out) + len(sentence) + 1 > limit:
            break
        out = (out + " " + sentence).strip()
    return out or text[:limit].rsplit(" ", 1)[0] + "…"


def save(name, items):
    with open(f"app/src/main/assets/{name}.json", "w") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
    print(f"{name}: {len(items)} beelden")


LABELS = r"(?:Collectie / Archief|Reportage / Serie|Beschrijving|Datum|Locatie|Trefwoorden|Persoonsnaam|Fotograaf|Auteursrechthebbende|Materiaalsoort|Nummer \w+|Inventarisnummer|Bestanddeelnummer|Annotatie)"


def field(text, label):
    m = re.search(label + r"\s*:\s*(.*?)\s*(?=" + LABELS + r"\s*:|$)", text)
    return m.group(1).strip() if m else None


toen = []
for t in load("data/extra/toen.json", []):
    desc = t["uitleg"]
    if re.search(r"(?i)portret", field(desc, "Trefwoorden") or ""):
        continue
    beschrijving = field(desc, "Beschrijving") or ""
    # Een deel van de Londense en Indische series heeft Engelse bijschriften; die slaan we over.
    if len(re.findall(r"\b(?:the|of|and|into|with|from|is|are)\b", beschrijving)) >= 2:
        continue
    locatie = field(desc, "Locatie")
    fallback = re.split(r"\s*(?:Bestanddeelnr|Bestanddeelnummer|,)\s*", t["titel"])[0].strip()
    # De eerste zin (ingekort) wordt de titel; is er meer, dan wordt het geheel de uitleg.
    # Alleen splitsen na een echt woord, niet na afkortingen als "m.s." of "Dr.".
    first = re.split(r"(?<=[a-zà-ÿ]{3}[.!?])\s+(?=[A-Z\"'])", beschrijving)[0] if beschrijving else ""
    if len(first) > 90:
        first = first[:90].rsplit(" ", 1)[0] + "…"
    title = first or fallback or "Nederland"
    title = title[:1].upper() + title[1:]
    uitleg = shorten(beschrijving) if len(beschrijving) > len(first) + 10 else None
    toen.append({
        "id": t["id"], "titel": title.rstrip("."),
        "onder": " · ".join(x for x in [nl_date(t["datum"]), locatie and locatie.split(",")[0]] if x),
        "uitleg": uitleg, "bron": "Nationaal Archief (CC0)", "datum": t["datum"],
        "afbeelding": t["afbeelding"], "klein": t["klein"],
    })
save("toen", toen)

save("natuur", [
    {
        "id": n["id"], "titel": n["naam"],
        "onder": " · ".join(x for x in [
            n.get("wetenschappelijk"),
            re.sub(r",\s*(Nederland|Netherlands|NL)$", "", (n.get("plaats") or "").strip()) or None,
            nl_date(n.get("datum")),
        ] if x),
        "uitleg": None,
        "bron": "Foto: " + re.sub(r"^\(c\)\s*", "", n.get("maker") or "iNaturalist").split(",")[0]
                + f" ({LICENTIES.get(n.get('licentie'), n.get('licentie') or '')}) · iNaturalist",
        "datum": n.get("datum"), "afbeelding": n["afbeelding"], "klein": n["klein"],
    }
    for n in load("data/extra/natuur.json", [])
])
