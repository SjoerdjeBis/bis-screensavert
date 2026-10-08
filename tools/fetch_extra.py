"""Haalt beelden op voor de categorieën Ruimte, Nederland van toen en Natuur, en keurt ze.

Draait in GitHub Actions (vanuit de ontwikkelomgeving zijn de bronnen niet bereikbaar).

Keuring, zodat er geen slechte beelden doorkomen:
  1. groot genoeg voor een tv (lange kant minstens 1600 pixels) en liggend;
  2. technisch: niet onscherp, niet te donker/licht, niet grauw (per bron eigen grenzen),
     en het onderste deel qua scherpte valt af;
  3. per bron: alleen beelden met een beschrijving en een vrije licentie;
  4. geen (bijna) dubbelingen, via een beeld-vingerafdruk.

Resultaat:
  data/extra/ruimte.json, toen.json, natuur.json – goedgekeurde beelden met scores
  data/extra/afgekeurd.json                        – telling per reden, om de grenzen te kunnen bijstellen
  data/debug/extra_*.json                          – een paar ruwe antwoorden
"""
import datetime
import html
import io
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict

import numpy as np
from PIL import Image

UA = "BisScreensavert/1.0 (persoonlijke screensaver; github.com/SjoerdjeBis/bis-screensavert)"
os.makedirs("data/extra", exist_ok=True)
os.makedirs("data/debug", exist_ok=True)
random.seed(42)
rejected = defaultdict(Counter)


def get(url, timeout=60, tries=4, headers=None):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 504) and attempt < tries - 1:
                time.sleep(5 * (attempt + 1))
                continue
            raise
        except Exception:
            if attempt < tries - 1:
                time.sleep(3)
                continue
            raise


def get_json(url):
    return json.loads(get(url, headers={"Accept": "application/json"}))


def debug(name, data):
    with open(f"data/debug/extra_{name}.json", "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)


def header_size(url):
    """Afmetingen uit het begin van het bestand, zonder alles te downloaden."""
    try:
        data = get(url, timeout=30, headers={"Range": "bytes=0-131071"})
        return Image.open(io.BytesIO(data)).size
    except Exception:
        return None


def measure(image_bytes):
    img = Image.open(io.BytesIO(image_bytes)).convert("L")
    img.thumbnail((512, 512))
    a = np.asarray(img, dtype=float)
    lap = 4 * a[1:-1, 1:-1] - a[:-2, 1:-1] - a[2:, 1:-1] - a[1:-1, :-2] - a[1:-1, 2:]
    small = img.resize((9, 8), Image.LANCZOS)
    px = list(small.tobytes())
    bits = 0
    for y in range(8):
        for x in range(8):
            bits = (bits << 1) | (px[y * 9 + x] > px[y * 9 + x + 1])
    return {"helder": round(a.mean(), 1), "contrast": round(a.std(), 1), "scherp": round(lap.var(), 1), "hash": f"{bits:016x}"}


def keur(source, items, limits, drop_share=0.15):
    """Technische keuring; items hebben een 'controle'-url (klein) en krijgen scores."""
    measured = []
    for i, item in enumerate(items):
        try:
            item["klein"] = item.pop("controle")
            item["scores"] = measure(get(item["klein"], timeout=40))
        except Exception:
            rejected[source]["niet te laden"] += 1
            continue
        s = item["scores"]
        if not (limits["helder"][0] <= s["helder"] <= limits["helder"][1]):
            rejected[source]["te donker of te licht"] += 1
        elif s["contrast"] < limits["contrast"]:
            rejected[source]["grauw"] += 1
        elif s["scherp"] < limits["scherp"]:
            rejected[source]["onscherp"] += 1
        else:
            measured.append(item)
        if i % 50 == 0:
            print(f"  {source}: {i}/{len(items)} gekeurd", flush=True)
        time.sleep(0.05)
    # Het minst scherpe deel valt altijd af, ook als het boven de grens zat.
    measured.sort(key=lambda it: it["scores"]["scherp"])
    cut = int(len(measured) * drop_share)
    rejected[source]["minst scherpe deel"] += cut
    measured = measured[cut:]
    kept, hashes = [], []
    for item in measured:
        h = int(item["scores"]["hash"], 16)
        if any(bin(h ^ o).count("1") <= 6 for o in hashes):
            rejected[source]["dubbeling"] += 1
            continue
        hashes.append(h)
        kept.append(item)
    print(f"{source}: {len(kept)} goedgekeurd van {len(items)}", flush=True)
    return kept


def landscape_ok(source, w, h, min_long=1600):
    if not w or not h:
        rejected[source]["afmeting onbekend"] += 1
        return False
    if max(w, h) < min_long:
        rejected[source]["te klein"] += 1
        return False
    ratio = w / h
    if ratio < 1.15:
        rejected[source]["staand of vierkant"] += 1
        return False
    if ratio > 2.6:
        rejected[source]["te smal panorama"] += 1
        return False
    return True


def plain(text):
    text = re.sub(r"<[^>]+>", " ", text or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


# ---------- Ruimte: NASA Astronomy Picture of the Day (alleen publiek domein) ----------

def fetch_ruimte():
    key = os.environ.get("NASA_KEY") or "DEMO_KEY"
    entries = []
    today = datetime.date.today()
    for years_back in range(0, 3):
        end = today - datetime.timedelta(days=365 * years_back)
        start = end - datetime.timedelta(days=364)
        url = f"https://api.nasa.gov/planetary/apod?api_key={key}&start_date={start}&end_date={end}"
        try:
            entries += get_json(url)
        except Exception as e:
            print("APOD mislukt:", e, file=sys.stderr)
        time.sleep(2)
    debug("apod", entries[:5])
    candidates = []
    for e in entries:
        if e.get("media_type") != "image":
            rejected["ruimte"]["geen foto"] += 1
            continue
        if e.get("copyright"):
            # Met copyright = van een fotograaf, niet vrij te gebruiken.
            rejected["ruimte"]["copyright"] += 1
            continue
        if len(e.get("explanation", "")) < 80:
            rejected["ruimte"]["geen uitleg"] += 1
            continue
        small, large = e.get("url"), e.get("hdurl") or e.get("url")
        size = header_size(large)
        if not size or not landscape_ok("ruimte", *size):
            continue
        candidates.append({
            "id": f"apod:{e['date']}",
            "titel_en": e.get("title", ""),
            "uitleg_en": e.get("explanation", ""),
            "datum": e["date"],
            "afbeelding": large,
            "breedte": size[0],
            "controle": small,
        })
    return keur("ruimte", candidates, {"helder": (4, 235), "contrast": 12, "scherp": 15})


# ---------- Nederland van toen: Nationaal Archief / Anefo via Wikimedia Commons ----------

def fetch_toen(per_letter=700, per_day=3):
    api = "https://commons.wikimedia.org/w/api.php"
    by_day = defaultdict(list)
    first = True
    for letter in "ABCDEFGHIJKLMNOPRSTUVWZ":
        cont, seen = {}, 0
        while seen < per_letter:
            params = {
                "action": "query", "format": "json", "maxlag": "5",
                "generator": "categorymembers", "gcmtitle": "Category:Images from Nationaal Archief",
                "gcmtype": "file", "gcmlimit": "50", "gcmstartsortkeyprefix": letter,
                "prop": "imageinfo", "iiprop": "url|size|extmetadata", "iiurlwidth": "1920",
                "iiextmetadatafilter": "DateTimeOriginal|ImageDescription|ObjectName|Artist|LicenseShortName",
                "iiextmetadatalanguage": "nl",
                **cont,
            }
            try:
                data = get_json(api + "?" + urllib.parse.urlencode(params))
            except Exception as e:
                print("Commons mislukt:", e, file=sys.stderr)
                break
            if first:
                debug("commons", data)
                first = False
            pages = (data.get("query") or {}).get("pages", {}).values()
            for page in pages:
                seen += 1
                info = (page.get("imageinfo") or [{}])[0]
                meta = info.get("extmetadata") or {}
                date = plain((meta.get("DateTimeOriginal") or {}).get("value", ""))
                m = re.search(r"(1[89]\d\d|20[01]\d)-(\d\d)-(\d\d)", date)
                if not m:
                    rejected["toen"]["geen datum"] += 1
                    continue
                desc = plain((meta.get("ImageDescription") or {}).get("value", ""))
                title = plain((meta.get("ObjectName") or {}).get("value", "")) or page.get("title", "")
                if len(desc) < 20:
                    rejected["toen"]["geen beschrijving"] += 1
                    continue
                if re.search(r"(?i)pasfoto|portret van|document|krant|affiche|handtekening|brief", desc + title):
                    rejected["toen"]["document of portret"] += 1
                    continue
                if not landscape_ok("toen", info.get("width"), info.get("height")):
                    continue
                thumb = info.get("thumburl")
                if not thumb:
                    rejected["toen"]["geen afbeelding"] += 1
                    continue
                by_day[f"{m.group(2)}-{m.group(3)}"].append({
                    "id": f"na:{page.get('pageid')}",
                    "titel": re.sub(r"\.(jpe?g|tiff?|png)$", "", re.sub(r"^File:", "", title), flags=re.I),
                    "uitleg": desc,
                    "datum": f"{m.group(1)}-{m.group(2)}-{m.group(3)}",
                    "afbeelding": thumb,
                    "controle": re.sub(r"/\d+px-", "/640px-", thumb),
                    "pagina": info.get("descriptionurl"),
                })
            if "continue" not in data:
                break
            cont = {k: v for k, v in data["continue"].items()}
            time.sleep(0.2)
        print(f"toen: letter {letter} klaar, {sum(len(v) for v in by_day.values())} kandidaten", flush=True)
    candidates = []
    for day, items in by_day.items():
        random.shuffle(items)
        candidates += items[: per_day * 2]  # wat ruimte voor de keuring
    kept = keur("toen", candidates, {"helder": (30, 225), "contrast": 35, "scherp": 40})
    # Per dag hooguit per_day over houden.
    final, count = [], Counter()
    for item in kept:
        day = item["datum"][5:]
        if count[day] < per_day:
            count[day] += 1
            final.append(item)
    return final


# ---------- Natuur: iNaturalist, waarnemingen in Nederland ----------

def fetch_natuur(per_group=120):
    groups = ["Aves", "Insecta", "Plantae", "Mammalia", "Fungi", "Amphibia", "Reptilia", "Arachnida"]
    candidates, per_taxon = [], Counter()
    for group in groups:
        for page in range(1, 3):
            params = {
                "place_id": 7506,  # Nederland
                "quality_grade": "research",
                "photos": "true",
                "photo_license": "cc0,cc-by,cc-by-sa,cc-by-nc",
                "iconic_taxa": group,
                "order_by": "votes",
                "order": "desc",
                "per_page": 200,
                "page": page,
                "locale": "nl",
            }
            try:
                data = get_json("https://api.inaturalist.org/v1/observations?" + urllib.parse.urlencode(params))
            except Exception as e:
                print("iNaturalist mislukt:", e, file=sys.stderr)
                break
            if group == groups[0] and page == 1:
                debug("inaturalist", data.get("results", [])[:3])
            results = data.get("results", [])
            for obs in results:
                if len([c for c in candidates if c["groep"] == group]) >= per_group:
                    break
                if (obs.get("faves_count") or 0) < 2:
                    rejected["natuur"]["weinig favorieten"] += 1
                    continue
                taxon = obs.get("taxon") or {}
                name = taxon.get("preferred_common_name")
                if not name:
                    rejected["natuur"]["geen Nederlandse naam"] += 1
                    continue
                if per_taxon[taxon.get("id")] >= 2:
                    rejected["natuur"]["soort al vaak"] += 1
                    continue
                photo = (obs.get("photos") or [{}])[0]
                dims = photo.get("original_dimensions") or {}
                if not landscape_ok("natuur", dims.get("width"), dims.get("height")):
                    continue
                url = photo.get("url", "")
                if "/square." not in url:
                    rejected["natuur"]["geen afbeelding"] += 1
                    continue
                per_taxon[taxon.get("id")] += 1
                candidates.append({
                    "id": f"inat:{obs['id']}",
                    "groep": group,
                    "naam": name[:1].upper() + name[1:],
                    "wetenschappelijk": taxon.get("name"),
                    "plaats": obs.get("place_guess"),
                    "datum": obs.get("observed_on"),
                    "maker": photo.get("attribution"),
                    "licentie": photo.get("license_code"),
                    "afbeelding": url.replace("/square.", "/original."),
                    "controle": url.replace("/square.", "/medium."),
                })
            time.sleep(1.2)
            if len(results) < 200:
                break
    return keur("natuur", candidates, {"helder": (20, 235), "contrast": 28, "scherp": 60})


def save(name, items):
    with open(f"data/extra/{name}.json", "w") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    which = sys.argv[1:] or ["ruimte", "natuur", "toen"]
    for name in which:
        try:
            save(name, {"ruimte": fetch_ruimte, "toen": fetch_toen, "natuur": fetch_natuur}[name]())
        except Exception as e:
            print(f"{name} mislukt: {e}", file=sys.stderr)
    with open("data/extra/afgekeurd.json", "w") as f:
        json.dump({k: dict(v) for k, v in rejected.items()}, f, ensure_ascii=False, indent=1)
