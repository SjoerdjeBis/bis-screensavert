"""Bouwt app/src/main/assets/kunst_nl.json uit de museumdata en de Nederlandse vertalingen.

data/aic_raw.json        – opgehaald door tools/fetch_art.py
data/vertalingen/*.json  – {"<id>": ["Nederlandse titel", "Nederlandse toelichting"]}
"""
import glob
import json
import re

raw = {str(a["id"]): a for a in json.load(open("data/aic_raw.json"))}
translations = {}
for path in sorted(glob.glob("data/vertalingen/*.json")):
    translations.update(json.load(open(path)))


def dutch_date(value):
    if not value:
        return None
    value = re.sub(r"\bc\.\s*", "ca. ", value)
    value = value.replace("border added", "rand toegevoegd")
    return value


out = []
for art_id, (title, explanation) in translations.items():
    art = raw.get(art_id)
    if not art or not art.get("image_id"):
        print(f"Overgeslagen: {art_id} niet gevonden")
        continue
    out.append({
        "id": int(art_id),
        "image_id": art["image_id"],
        "titel": title,
        "kunstenaar": art.get("artist_title"),
        "datum": dutch_date(art.get("date_display")),
        "uitleg": explanation,
    })

with open("app/src/main/assets/kunst_nl.json", "w") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print(f"{len(out)} kunstwerken met Nederlandse uitleg")
