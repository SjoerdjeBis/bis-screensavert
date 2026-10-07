"""Haalt kunstwerken met toelichting op bij het Art Institute of Chicago.

Draait in GitHub Actions (vanuit de ontwikkelomgeving is de API niet bereikbaar).
Resultaat: data/aic_raw.json, de basis voor de Nederlandse collectie in de app.
"""
import json
import time
import urllib.request

API = "https://api.artic.edu/api/v1/artworks/search"
FIELDS = [
    "id", "title", "artist_title", "artist_display", "date_display", "image_id",
    "description", "short_description", "medium_display", "place_of_origin",
    "artwork_type_title", "is_boosted", "thumbnail",
]
HEADERS = {
    "Content-Type": "application/json",
    "AIC-User-Agent": "BisScreenSaver (persoonlijke screensaver)",
}


def search(must, page, limit=100):
    body = {
        "query": {"bool": {"must": must}},
        "fields": FIELDS,
        "limit": limit,
        "page": page,
    }
    req = urllib.request.Request(API, data=json.dumps(body).encode(), headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def main():
    base = [
        {"term": {"is_public_domain": True}},
        {"exists": {"field": "image_id"}},
        {"exists": {"field": "description"}},
    ]
    queries = [
        ("highlights-paintings", base + [{"term": {"is_boosted": True}}, {"term": {"artwork_type_id": 1}}]),
        ("highlights", base + [{"term": {"is_boosted": True}}]),
        ("paintings", base + [{"term": {"artwork_type_id": 1}}]),
    ]
    seen, out = set(), []
    for name, must in queries:
        for page in range(1, 4):
            try:
                data = search(must, page)
            except Exception as e:  # noqa: BLE001
                print(f"{name} p{page}: fout {e}")
                break
            items = data.get("data", [])
            print(f"{name} p{page}: {len(items)} (totaal {data.get('pagination', {}).get('total')})")
            for a in items:
                if a["id"] in seen or not a.get("image_id") or not a.get("description"):
                    continue
                seen.add(a["id"])
                a["_bron"] = name
                out.append(a)
            if len(items) < 100:
                break
            time.sleep(1)
        if len(out) >= 400:
            break
    with open("data/aic_raw.json", "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"Opgeslagen: {len(out)} werken")


if __name__ == "__main__":
    main()
