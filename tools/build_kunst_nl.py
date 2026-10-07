"""Bouwt app/src/main/assets/kunst_nl.json: kunst uit meerdere musea, Nederlands, zonder dubbelingen.

Invoer:
  data/aic_raw.json, data/cma_raw.json, data/rijks_raw.json  – opgehaald in GitHub Actions
  data/hashes.json                                            – beeld-vingerafdrukken per werk
  data/vertalingen/*.json  – {"<id>": ["titel", "toelichting"]}; een kaal getal is een AIC-id,
                             anders "cma:<id>" of "rijks:<id>" (voor Rijks is het optioneel:
                             het museum levert zelf Nederlandse tekst)

Dubbelingen worden op drie manieren herkend:
  1. hetzelfde werk (bron + id);
  2. dezelfde kunstenaar met dezelfde titel;
  3. (bijna) dezelfde afbeelding, via de vingerafdruk.
Bij een dubbeling wint de eerste in de volgorde AIC, Cleveland, Rijksmuseum.
"""
import glob
import json
import re
import unicodedata

MAX_TEKST = 450
MUSEA = {"aic": "Art Institute of Chicago", "cma": "Cleveland Museum of Art", "rijks": "Rijksmuseum"}


def load(path, default):
    try:
        return json.load(open(path))
    except FileNotFoundError:
        return default


def dutch_date(value):
    if not value:
        return None
    value = re.sub(r"\bc\.\s*", "ca. ", str(value))
    return value.replace("border added", "rand toegevoegd")


def normalize(text):
    text = unicodedata.normalize("NFKD", text or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def artist_key(name):
    """Achternaam-achtige sleutel: 'Vincent van Gogh' en 'Gogh, Vincent van' worden gelijk."""
    words = [w for w in normalize(name).split() if w not in {"van", "de", "der", "den", "ter", "le", "la", "ii", "i"}]
    return " ".join(sorted(words))


def shorten(text, limit=MAX_TEKST):
    """Kort af op een zinsgrens, zodat het op een tv-scherm past."""
    text = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", text or "")).strip()
    if len(text) <= limit:
        return text
    out = ""
    for sentence in re.split(r"(?<=[.!?])\s+", text):
        if len(out) + len(sentence) + 1 > limit:
            break
        out = (out + " " + sentence).strip()
    return out or text[:limit].rsplit(" ", 1)[0] + "…"


def hamming(a, b):
    return bin(int(a, 16) ^ int(b, 16)).count("1")


translations = {}
for path in sorted(glob.glob("data/vertalingen/*.json")):
    for key, value in json.load(open(path)).items():
        translations[key if ":" in key else f"aic:{key}"] = value

candidates = []
for a in load("data/aic_raw.json", []):
    key = f"aic:{a['id']}"
    if key in translations and a.get("image_id"):
        iiif = f"https://www.artic.edu/iiif/2/{a['image_id']}/full"
        candidates.append({
            "id": key, "kunstenaar": a.get("artist_title"), "datum": dutch_date(a.get("date_display")),
            "afbeelding": f"{iiif}/1686,/0/default.jpg", "afbeelding_reserve": f"{iiif}/843,/0/default.jpg",
            "afbeelding_klein": f"{iiif}/400,/0/default.jpg",
        })
for a in load("data/cma_raw.json", []):
    key = f"cma:{a['id']}"
    if key in translations:
        candidates.append({
            "id": key, "kunstenaar": re.sub(r"\s*\(.*$", "", a.get("kunstenaar") or "") or None,
            "datum": dutch_date(a.get("datum")),
            "afbeelding": a.get("afbeelding_groot") or a["afbeelding"], "afbeelding_reserve": a["afbeelding"],
            "afbeelding_klein": a["afbeelding"],
        })
for a in load("data/rijks_raw.json", []):
    key = f"rijks:{a['id']}"
    if not a.get("afbeelding") or not (a.get("teksten_nl") or key in translations):
        continue
    image = a["afbeelding"]
    small = image.replace("/full/max/", "/full/400,/") if "/full/max/" in image else image
    medium = image.replace("/full/max/", "/full/1920,/") if "/full/max/" in image else image
    if key not in translations:
        translations[key] = [a.get("titel") or "Zonder titel", shorten(max(a["teksten_nl"], key=len))]
    candidates.append({
        "id": key, "kunstenaar": a.get("kunstenaar"), "datum": a.get("datum"),
        "afbeelding": medium, "afbeelding_reserve": small.replace("/400,/", "/843,/"), "afbeelding_klein": small,
    })

hashes = load("data/hashes.json", {})
out, seen_titles, seen_hashes, dropped = [], {}, [], []
for c in candidates:
    title, explanation = translations[c["id"]]
    title_key = (artist_key(c["kunstenaar"]), normalize(title))
    if title_key[1] and title_key in seen_titles:
        dropped.append(f"{c['id']} = {seen_titles[title_key]} (zelfde kunstenaar en titel)")
        continue
    h = hashes.get(c["id"])
    twin = next((other for other, oh in seen_hashes if h and hamming(h, oh) <= 6), None)
    if twin:
        dropped.append(f"{c['id']} = {twin} (zelfde afbeelding)")
        continue
    seen_titles[title_key] = c["id"]
    if h:
        seen_hashes.append((c["id"], h))
    out.append({
        "id": c["id"], "bron": MUSEA[c["id"].split(":")[0]], "titel": title,
        "kunstenaar": c["kunstenaar"], "datum": c["datum"], "uitleg": explanation,
        "afbeelding": c["afbeelding"], "afbeelding_reserve": c["afbeelding_reserve"],
        "afbeelding_klein": c["afbeelding_klein"],
    })

with open("app/src/main/assets/kunst_nl.json", "w") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
per_museum = {m: sum(1 for o in out if o["bron"] == m) for m in MUSEA.values()}
print(f"{len(out)} kunstwerken met Nederlandse uitleg: {per_museum}")
for d in dropped:
    print("Dubbeling overgeslagen:", d)
