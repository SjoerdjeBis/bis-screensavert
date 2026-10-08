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
    "haardvuur": (["fireplace crackling", "campfire crackling", "fire crackling", "fireplace"], "loop", 40),
    "regen": (["rain on roof", "rain sound", "heavy rain", "rain"], "loop", 40),
    "zee": (["ocean waves beach", "sea waves", "waves breaking"], "loop", 40),
    "onweer": (["thunder rumble", "thunder", "thunderstorm"], "losse", 6),
}

# De titel moet over het juiste gaan, en mag geen bijgeluiden noemen.
MUST = {
    "haardvuur": ["fire", "campfire", "bonfire", "vuur", "feu"],
    "regen": ["rain", "regen", "pluie", "lluvia"],
    "zee": ["wave", "ocean", "sea", "surf", "beach", "zee"],
    "onweer": ["thunder", "onweer", "donner"],
}
EXCLUDE = ["market", "car", "traffic", "bone", "breaking", "voice", "people", "crowd", "speech", "talk",
           "music", "song", "bird", "dog", "train", "street", "city", "engine", "alarm", "footstep", "walk",
           "child", "kid", "bell", "church", "radio", "tv", "phone", "ice", "glass", "metal", "door", "plane"]
LOOP_SECONDS = 90
CROSSFADE = 4


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def title_ok(name, title):
    t = title.lower()
    return any(w in t for w in MUST[name]) and not any(re.search(rf"\b{w}", t) for w in EXCLUDE)


def candidates(query):
    data = api({
        "action": "query", "generator": "search", "gsrnamespace": 6, "gsrlimit": 25,
        "gsrsearch": f"{query} filetype:audio", "prop": "imageinfo",
        "iiprop": "url|size|mime|extmetadata",
    })
    pages = sorted(data.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 99))
    for page in pages:
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
    for attempt in range(2):
        time.sleep(5 + attempt * 20)
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
                f.write(r.read())
            return
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            wait = int(e.headers.get("Retry-After") or 30)
            print(f"  429, even wachten ({min(wait, 30)} s)")
            time.sleep(min(wait, 30))
    raise RuntimeError("Wikimedia blijft 'te veel verzoeken' geven")


def make_loop(src, dst, length):
    """Naadloze loop: het staartje overlapt het begin, zodat je de herhaling niet hoort."""
    import wave

    import numpy as np
    total = duration(src)
    start = max(0.0, min(10.0, total - length - CROSSFADE))
    tmp = dst + ".tmp.wav"
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-ss", str(start), "-t", str(length + CROSSFADE), "-i", src,
                    "-af", "aformat=channel_layouts=mono,loudnorm=I=-24:TP=-3,aresample=44100",
                    "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", tmp], check=True)
    with wave.open(tmp, "rb") as w:
        sr = w.getframerate()
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)
    f = int(CROSSFADE * sr)
    t = len(x) - f
    ramp = np.linspace(0.0, 1.0, f, dtype=np.float32)
    intro = x[:f] * np.sqrt(ramp) + x[t:t + f] * np.sqrt(1 - ramp)
    loop = np.concatenate([intro, x[f:t]])
    loop = np.clip(loop, -32767, 32767).astype(np.int16)
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(loop.tobytes())
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "2", dst], check=True)
    os.remove(tmp)


def make_oneshot(src, dst):
    """Een losse rommeling van hooguit 25 seconden, met zachte in- en uitloop."""
    total = min(duration(src), 25.0)
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src, "-t", str(total), "-af",
                    f"aformat=channel_layouts=mono,loudnorm=I=-22:TP=-2,afade=t=in:d=0.3,afade=t=out:st={max(total - 2, 0)}:d=2",
                    "-ar", "44100", "-c:a", "libvorbis", "-q:a", "2", dst], check=True)


# ---- Terugval: geluiden zelf maken (geen licentie nodig) ----

def synthesize(name, dst):
    """Maakt een geluid na met ruis en filters, als Wikimedia niets oplevert."""
    import numpy as np
    sr = 44100
    rng = np.random.default_rng(7)
    length = 25 if name == "onweer" else LOOP_SECONDS + CROSSFADE + 12
    n = sr * length

    def brown(size):
        x = np.cumsum(rng.standard_normal(size))
        x -= np.convolve(x, np.ones(sr // 20) / (sr // 20), mode="same")
        return x / (np.abs(x).max() + 1e-9)

    from scipy.signal import lfilter

    def lowpass(x, cutoff):
        a = np.exp(-2 * np.pi * cutoff / sr)
        return lfilter([1 - a], [1, -a], x)

    t = np.arange(n) / sr
    if name == "regen":
        noise = rng.standard_normal(n)
        hiss = noise - lowpass(noise, 500)
        hiss = lowpass(hiss, 7000)
        drops = np.zeros(n)
        idx = rng.integers(0, n - 2000, size=length * 40)
        for i in idx:
            k = np.arange(400)
            drops[i:i + 400] += rng.uniform(0.2, 1.0) * np.exp(-k / 60) * rng.standard_normal(400)
        audio = 0.8 * hiss / np.abs(hiss).max() + 0.25 * drops / (np.abs(drops).max() + 1e-9)
    elif name == "haardvuur":
        roar = lowpass(brown(n), 600)
        crackle = np.zeros(n)
        idx = rng.integers(0, n - 3000, size=length * 9)
        for i in idx:
            k = np.arange(1500)
            burst = rng.standard_normal(1500) * np.exp(-k / rng.uniform(40, 250))
            crackle[i:i + 1500] += rng.uniform(0.1, 1.0) ** 2 * burst
        crackle = crackle - lowpass(crackle, 1500)
        audio = 0.5 * roar / (np.abs(roar).max() + 1e-9) + 0.7 * crackle / (np.abs(crackle).max() + 1e-9)
    elif name == "zee":
        surf = lowpass(rng.standard_normal(n), 1200)
        env = np.zeros(n)
        pos = 0.0
        while pos < length:
            period = rng.uniform(7, 11)
            seg = (t >= pos) & (t < pos + period)
            phase = (t[seg] - pos) / period
            env[seg] = np.sin(np.pi * phase) ** 2 * (0.6 + 0.4 * rng.random())
            pos += period
        audio = surf / np.abs(surf).max() * (0.15 + 0.85 * env) + 0.3 * lowpass(brown(n), 200)
    else:  # onweer
        rumble = lowpass(lowpass(brown(n), 250), 180)
        env = np.minimum(t / 0.4, 1) * np.exp(-np.maximum(t - 0.4, 0) / 6.0)
        env *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * t) ** 2
        crack = np.zeros(n)
        crack[: sr // 2] = rng.standard_normal(sr // 2) * np.exp(-np.arange(sr // 2) / 3000)
        audio = rumble / (np.abs(rumble).max() + 1e-9) * env + 0.3 * lowpass(crack, 3000)
    audio = (audio / (np.abs(audio).max() + 1e-9) * 0.9 * 32767).astype(np.int16)
    import wave
    wav = dst + ".wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(audio.tobytes())
    if name == "onweer":
        make_oneshot(wav, dst)
    else:
        make_loop(wav, dst, LOOP_SECONDS)
    os.remove(wav)


def main():
    os.makedirs("app/src/main/res/raw", exist_ok=True)
    credits = {}
    tmp = tempfile.mkdtemp()
    for name, (queries, kind, min_len) in SOUNDS.items():
        done = False
        for query in queries:
            for cand in [c for c in candidates(query) if title_ok(name, c["titel"])][:3]:
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
            print(f"{name}: niets van Wikimedia; zelf gemaakt")
            dst = f"app/src/main/res/raw/geluid_{name}.ogg"
            synthesize(name, dst)
            credits[name] = {"titel": f"{name} (zelf gemaakt)", "maker": "Bis Screensavert",
                             "licentie": "geen licentie nodig", "pagina": None}
    with open("app/src/main/assets/geluiden.json", "w") as f:
        json.dump(credits, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
