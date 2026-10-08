"""Haalt vrij te gebruiken geluiden op bij Wikimedia Commons en maakt er naadloze loops van.

Draait in GitHub Actions (ffmpeg nodig). Resultaat:
  app/src/main/res/raw/geluid_<naam>.ogg   – mono Ogg Vorbis, genormaliseerd
  app/src/main/assets/geluiden.json        – bron, maker en licentie per geluid
"""
import html
import json
import os
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "BisScreensavert/1.0 (persoonlijke screensaver; github.com/SjoerdjeBis/bis-screensavert)"
API = "https://commons.wikimedia.org/w/api.php"
OK_LICENSES = ("cc0", "public domain", "pd", "cc by", "cc-by")

SOUNDS = {
    # naam: (zoektermen, soort, minimale lengte in seconden)
    "haardvuur": (["fireplace crackling", "fire crackling", "campfire crackling"], "loop", 40),
    "regen": (["rain on roof", "rain sound", "heavy rain"], "loop", 40),
    "zee": (["ocean waves beach", "sea waves", "waves breaking"], "loop", 40),
    "onweer": (["thunder rumble", "thunder", "thunderstorm"], "losse", 6),
}
LOOP_SECONDS = 90
CROSSFADE = 4


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def candidates(query):
    data = api({
        "action": "query", "generator": "search", "gsrnamespace": 6, "gsrlimit": 25,
        "gsrsearch": f"{query} filetype:audio", "prop": "imageinfo",
        "iiprop": "url|size|mime|extmetadata",
    })
    pages = sorted(data.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 99))
    for page in pages[:6]:
        info = (page.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata", {})
        license_name = meta.get("LicenseShortName", {}).get("value", "")
        if "nc" in license_name.lower().replace("cc0", "") or not any(l in license_name.lower() for l in OK_LICENSES):
            continue
        if info.get("size", 0) > 60_000_000:
            continue
        artist = re.sub(r"<[^>]+>", "", html.unescape(meta.get("Artist", {}).get("value", ""))).strip()
        yield {
            "titel": page["title"].removeprefix("File:"),
            "url": info["url"],
            "pagina": info.get("descriptionurl"),
            "licentie": license_name,
            "maker": artist or "onbekend",
        }


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True)
    try:
        return float(out.stdout.strip())
    except ValueError:
        return 0.0


def download(url, path):
    """Rustig downloaden: Wikimedia weigert te veel verzoeken achter elkaar (HTTP 429)."""
    for attempt in range(4):
        time.sleep(4 + attempt * 10)
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
                f.write(r.read())
            return
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            wait = int(e.headers.get("Retry-After") or 30)
            print(f"  429, even wachten ({wait} s)")
            time.sleep(min(wait, 120))
    raise RuntimeError("Wikimedia blijft 'te veel verzoeken' geven")


def make_loop(src, dst, length):
    """Naadloze loop: het staartje overlapt het begin, zodat je de herhaling niet hoort."""
    total = duration(src)
    start = max(0.0, min(10.0, total - length - CROSSFADE))
    t, f = length, CROSSFADE
    filt = (
        f"[0:a]atrim={start}:{start + t + f},asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,"
        f"loudnorm=I=-24:TP=-3,asplit=3[a][b][c];"
        f"[a]atrim=0:{f},asetpts=PTS-STARTPTS,afade=t=in:d={f}[head];"
        f"[b]atrim={t}:{t + f},asetpts=PTS-STARTPTS,afade=t=out:d={f}[tail];"
        f"[c]atrim={f}:{t},asetpts=PTS-STARTPTS[body];"
        f"[head][tail]amix=inputs=2:normalize=0[intro];"
        f"[intro][body]concat=n=2:v=0:a=1[out]"
    )
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src, "-filter_complex", filt, "-map", "[out]",
                    "-ar", "44100", "-c:a", "libvorbis", "-q:a", "2", dst], check=True)


def make_oneshot(src, dst):
    """Een losse rommeling van hooguit 25 seconden, met zachte in- en uitloop."""
    total = min(duration(src), 25.0)
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src, "-t", str(total), "-af",
                    f"aformat=channel_layouts=mono,loudnorm=I=-22:TP=-2,afade=t=in:d=0.3,afade=t=out:st={max(total - 2, 0)}:d=2",
                    "-ar", "44100", "-c:a", "libvorbis", "-q:a", "2", dst], check=True)


def main():
    os.makedirs("app/src/main/res/raw", exist_ok=True)
    credits = {}
    tmp = tempfile.mkdtemp()
    for name, (queries, kind, min_len) in SOUNDS.items():
        done = False
        for query in queries:
            for cand in candidates(query):
                src = os.path.join(tmp, f"{name}_bron")
                try:
                    download(cand["url"], src)
                except Exception as e:  # noqa: BLE001
                    print(f"{name}: download mislukt ({e})")
                    continue
                length = duration(src)
                if length < min_len:
                    continue
                dst = f"app/src/main/res/raw/geluid_{name}.ogg"
                try:
                    if kind == "loop":
                        make_loop(src, dst, min(LOOP_SECONDS, int(length - CROSSFADE - 1)))
                    else:
                        make_oneshot(src, dst)
                except subprocess.CalledProcessError as e:
                    print(f"{name}: ffmpeg mislukt ({e})")
                    continue
                credits[name] = cand
                print(f"{name}: {cand['titel']} ({cand['licentie']}, {length:.0f} s) -> {os.path.getsize(dst)} bytes")
                done = True
                break
            if done:
                break
        if not done:
            print(f"{name}: niets geschikts gevonden")
    with open("app/src/main/assets/geluiden.json", "w") as f:
        json.dump(credits, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
