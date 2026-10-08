"""Bouwt de app-bestanden voor Ruimte, Nederland van toen en Natuur uit data/extra/.

  app/src/main/assets/ruimte.json – alleen beelden met een Nederlandse vertaling in
                                    data/vertalingen/ruimte.json ({"<id>": ["titel", "uitleg"]})
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


vertalingen = load("data/vertalingen/ruimte.json", {})
save("ruimte", [
    {
        "id": r["id"], "titel": vertalingen[r["id"]][0], "onder": nl_date(r["datum"]),
        "uitleg": vertalingen[r["id"]][1], "bron": "NASA", "datum": r["datum"],
        "afbeelding": r["afbeelding"], "klein": r["klein"],
    }
    for r in load("data/extra/ruimte.json", []) if r["id"] in vertalingen
])

toen = []
for t in load("data/extra/toen.json", []):
    title = re.split(r"\s*(?:Bestanddeelnr|Bestanddeelnummer|;|\.|,)\s*", t["titel"])[0].strip()
    toen.append({
        "id": t["id"], "titel": title[:90] or "Nederland", "onder": nl_date(t["datum"]),
        "uitleg": shorten(t["uitleg"]), "bron": "Nationaal Archief / Anefo (CC0)", "datum": t["datum"],
        "afbeelding": t["afbeelding"], "klein": t["klein"],
    })
save("toen", toen)

save("natuur", [
    {
        "id": n["id"], "titel": n["naam"],
        "onder": " · ".join(x for x in [n.get("wetenschappelijk"), n.get("plaats"), nl_date(n.get("datum"))] if x),
        "uitleg": None,
        "bron": "Foto: " + re.sub(r"^\(c\)\s*", "", n.get("maker") or "iNaturalist").split(",")[0]
                + f" ({LICENTIES.get(n.get('licentie'), n.get('licentie') or '')}) · iNaturalist",
        "datum": n.get("datum"), "afbeelding": n["afbeelding"], "klein": n["klein"],
    }
    for n in load("data/extra/natuur.json", [])
])
