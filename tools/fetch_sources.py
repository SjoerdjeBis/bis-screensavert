"""Haalt kunstwerken op bij extra musea en berekent beeld-vingerafdrukken tegen dubbelingen.

Draait in GitHub Actions (vanuit de ontwikkelomgeving zijn de API's niet bereikbaar).

Resultaat:
  data/cma_raw.json     – Cleveland Museum of Art (open access, CC0), Engelse teksten
  data/rijks_raw.json   – Rijksmuseum (Linked Art), Nederlandse teksten
  data/hashes.json      – "<bron>:<id>" -> dHash van de afbeelding, voor alle bronnen
  data/debug/*.json     – een paar ruwe antwoorden, om de datavorm te kunnen controleren
"""
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

UA = "BisScreensavert/1.0 (persoonlijke screensaver; github.com/SjoerdjeBis)"
os.makedirs("data/debug", exist_ok=True)


def get(url, accept="application/json", timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": accept})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def get_json(url, accept="application/json"):
    return json.loads(get(url, accept))


def debug(name, data):
    with open(f"data/debug/{name}.json", "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)


def dhash(image_bytes, size=8):
    """Verschil-hash: gelijke of bijna gelijke afbeeldingen geven (bijna) dezelfde 64 bits."""
    img = Image.open(io.BytesIO(image_bytes)).convert("L").resize((size + 1, size), Image.LANCZOS)
    px = list(img.getdata())
    bits = 0
    for y in range(size):
        for x in range(size):
            bits = (bits << 1) | (px[y * (size + 1) + x] > px[y * (size + 1) + x + 1])
    return f"{bits:016x}"


# ---------- Cleveland Museum of Art ----------

def fetch_cma(limit=1000):
    params = {
        "type": "Painting",
        "has_image": 1,
        "cc0": 1,
        "limit": limit,
        "fields": "id,accession_number,title,creation_date,creators,description,wall_description,"
                  "did_you_know,images,culture,department,type",
    }
    data = get_json("https://openaccess-api.clevelandart.org/api/artworks/?" + urllib.parse.urlencode(params))
    items = data.get("data", [])
    debug("cma_eerste", items[:2])
    out = []
    for a in items:
        text = a.get("wall_description") or a.get("description")
        web = ((a.get("images") or {}).get("web") or {}).get("url")
        if not text or not web or len(text) < 150:
            continue
        out.append({
            "id": a["id"],
            "titel_en": a.get("title"),
            "kunstenaar": ((a.get("creators") or [{}])[0] or {}).get("description"),
            "datum": a.get("creation_date"),
            "tekst_en": text,
            "afbeelding": web,
            "afbeelding_groot": ((a.get("images") or {}).get("print") or {}).get("url") or web,
            "cultuur": a.get("culture"),
        })
    print(f"CMA: {len(items)} schilderijen, {len(out)} met tekst en afbeelding")
    return out


# ---------- Rijksmuseum (Linked Art) ----------

RIJKS_MAKERS = [
    "Rembrandt van Rijn", "Johannes Vermeer", "Frans Hals", "Jan Steen", "Vincent van Gogh",
    "Jacob Isaacksz. van Ruisdael", "Meindert Hobbema", "Hendrick Avercamp", "Pieter de Hooch",
    "Pieter Jansz. Saenredam", "George Hendrik Breitner", "Isaac Israëls", "Hendrik Willem Mesdag",
    "Johan Barthold Jongkind", "Jan Hendrik Weissenbruch", "Anton Mauve", "Jacob Maris",
    "Gerard ter Borch (II)", "Jan Asselijn", "Paulus Potter", "Willem van de Velde (II)",
    "Adriaen Coorte", "Rachel Ruysch", "Judith Leyster", "Jan van Huysum", "Aelbert Cuyp",
    "Jan van Goyen", "Pieter Claesz.", "Willem Claesz. Heda", "Carel Fabritius",
    "Jan Davidsz. de Heem", "Gerard Dou", "Ferdinand Bol", "Gabriël Metsu", "Nicolaes Maes",
    "Jan van der Heyden", "Adriaen van Ostade", "Hendrick ter Brugghen", "Dirck van Baburen",
    "Jan Toorop", "Piet Mondriaan", "Floris Verster", "Suze Robertson", "Thérèse Schwartze",
    "Willem Witsen", "Jozef Israëls", "Matthijs Maris", "Willem Maris", "Charley Toorop",
]

LA = 'application/ld+json;profile="https://linked.art/ns/v1/linked-art.json"'


def rijks_search(maker, max_items=12):
    url = "https://data.rijksmuseum.nl/search/collection?" + urllib.parse.urlencode(
        {"creator": maker, "type": "painting", "imageAvailable": "true"})
    data = get_json(url, "application/ld+json")
    return [i["id"] for i in data.get("orderedItems", [])][:max_items], data


def first(lst):
    return lst[0] if lst else None


def lang_of(entry):
    labels = " ".join((l.get("_label") or l.get("id") or "") for l in entry.get("language", []))
    if "Dutch" in labels or "300388256" in labels or "nl" == labels.strip():
        return "nl"
    if "English" in labels or "300388277" in labels:
        return "en"
    return "?"


def rijks_object(obj_url):
    obj = get_json(obj_url, LA)
    names = [n for n in obj.get("identified_by", []) if n.get("type") == "Name"]
    ids = [n for n in obj.get("identified_by", []) if n.get("type") == "Identifier"]
    title_nl = next((n["content"] for n in names if lang_of(n) == "nl"), None) or (names[0]["content"] if names else None)
    texts = [t for t in obj.get("referred_to_by", []) if t.get("type") == "LinguisticObject" and t.get("content")]
    desc_nl = [t["content"] for t in texts if lang_of(t) == "nl"]
    produced = obj.get("produced_by") or {}
    maker = None
    for part in [produced] + produced.get("part", []):
        for c in part.get("carried_out_by", []):
            maker = maker or c.get("_label")
        for r in part.get("referred_to_by", []):
            if lang_of(r) == "nl" and not maker:
                maker = r.get("content")
    timespan = produced.get("timespan") or {}
    date = next((n.get("content") for n in timespan.get("identified_by", [])), None)
    image = None
    for shown in obj.get("shows", []):
        try:
            visual = get_json(shown["id"], LA)
            for d in visual.get("digitally_shown_by", []):
                digital = get_json(d["id"], LA)
                for ap in digital.get("access_point", []):
                    image = image or ap.get("id")
        except Exception as e:  # noqa: BLE001
            print("  beeld niet gevonden:", e)
    return obj, {
        "id": obj_url.rsplit("/", 1)[-1],
        "objectnummer": first([i["content"] for i in ids]),
        "titel": title_nl,
        "kunstenaar": maker,
        "datum": date,
        "teksten_nl": desc_nl,
        "afbeelding": image,
    }


def fetch_rijks():
    out, debugged = [], 0
    for maker in RIJKS_MAKERS:
        try:
            urls, raw = rijks_search(maker)
        except Exception as e:  # noqa: BLE001
            print(f"Rijks zoeken {maker}: fout {e}")
            continue
        if debugged == 0:
            debug("rijks_zoek", raw)
        print(f"Rijks {maker}: {len(urls)}")
        for u in urls:
            try:
                obj, item = rijks_object(u)
            except Exception as e:  # noqa: BLE001
                print(f"  {u}: fout {e}")
                continue
            if debugged < 3:
                debug(f"rijks_object_{debugged}", obj)
                debugged += 1
            item["zoekterm"] = maker
            out.append(item)
            time.sleep(0.2)
    with_text = [o for o in out if o["teksten_nl"] and o["afbeelding"]]
    print(f"Rijks: {len(out)} objecten, {len(with_text)} met Nederlandse tekst en afbeelding")
    return out


# ---------- vingerafdrukken ----------

def thumb_url(source, item):
    if source == "aic":
        return f"https://www.artic.edu/iiif/2/{item['image_id']}/full/200,/0/default.jpg"
    if source == "rijks":
        url = item.get("afbeelding") or ""
        return url.replace("/full/max/", "/full/200,/") if "/full/" in url else url
    return item.get("afbeelding")


def fingerprints(sources):
    try:
        hashes = json.load(open("data/hashes.json"))
    except FileNotFoundError:
        hashes = {}
    for source, items in sources.items():
        for item in items:
            key = f"{source}:{item['id']}"
            url = thumb_url(source, item)
            if key in hashes or not url:
                continue
            try:
                hashes[key] = dhash(get(url, "image/*"))
            except Exception as e:  # noqa: BLE001
                print(f"hash {key}: fout {e}")
            time.sleep(0.1)
    with open("data/hashes.json", "w") as f:
        json.dump(hashes, f, indent=0, sort_keys=True)
    print(f"{len(hashes)} vingerafdrukken")


def main():
    wanted = set(sys.argv[1:]) or {"cma", "rijks"}
    sources = {"aic": json.load(open("data/aic_raw.json"))}
    if "cma" in wanted:
        try:
            sources["cma"] = fetch_cma()
            json.dump(sources["cma"], open("data/cma_raw.json", "w"), ensure_ascii=False, indent=1)
        except Exception as e:  # noqa: BLE001
            print("CMA mislukt:", e)
    if "rijks" in wanted:
        try:
            sources["rijks"] = fetch_rijks()
            json.dump(sources["rijks"], open("data/rijks_raw.json", "w"), ensure_ascii=False, indent=1)
        except Exception as e:  # noqa: BLE001
            print("Rijks mislukt:", e)
    fingerprints(sources)


if __name__ == "__main__":
    main()
