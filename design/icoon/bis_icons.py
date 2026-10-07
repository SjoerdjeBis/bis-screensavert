"""Tekent Bis-iconen in de stijl van Bis Stappenteller en Bis Weekmenu: B[symbool]S op een effen vlak."""
import math
import sys
from PIL import Image, ImageDraw, ImageFont

ROOM = (244, 236, 221)
S = 1024  # tekenen op groot formaat, daarna verkleinen


def squircle_mask(size, n=4.6):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    r = size / 2
    pts = []
    for i in range(720):
        t = 2 * math.pi * i / 720
        c, s = math.cos(t), math.sin(t)
        x = r + r * math.copysign(abs(c) ** (2 / n), c)
        y = r + r * math.copysign(abs(s) ** (2 / n), s)
        pts.append((x, y))
    d.polygon(pts, fill=255)
    return mask


def letters(d, font, color, gap_w):
    """Zet B en S neer met ruimte ertussen; geeft (x_midden, baseline-y, x-hoogte, cap-hoogte)."""
    bb_b = d.textbbox((0, 0), "B", font=font)
    bb_s = d.textbbox((0, 0), "S", font=font)
    wb, ws = bb_b[2] - bb_b[0], bb_s[2] - bb_s[0]
    cap = bb_b[3] - bb_b[1]
    total = wb + gap_w + ws
    x0 = (S - total) / 2
    top = (S - cap) / 2 + S * 0.01
    d.text((x0 - bb_b[0], top - bb_b[1]), "B", font=font, fill=color)
    d.text((x0 + wb + gap_w - bb_s[0], top - bb_s[1]), "S", font=font, fill=color)
    return x0 + wb + gap_w / 2, top + cap, cap


def stem(d, cx, bottom, h, w, color):
    d.rounded_rectangle((cx - w / 2, bottom - h, cx + w / 2, bottom), radius=w / 2, fill=color)


def symbool_maan(d, cx, bottom, cap, color, bg):
    w = cap * 0.2
    stem(d, cx, bottom, cap * 0.6, w, color)
    r = cap * 0.25
    cy = bottom - cap * 0.6 - r * 1.25
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color)
    o = r * 0.62
    d.ellipse((cx - r + o, cy - r - o * 0.55, cx + r + o, cy + r - o * 0.55), fill=bg)


def symbool_scherm(d, cx, bottom, cap, color, bg):
    w = cap * 0.16
    stem(d, cx, bottom, cap * 0.58, w, color)
    sw, sh = cap * 0.56, cap * 0.4
    top = bottom - cap * 0.58 - sh - cap * 0.05
    d.rounded_rectangle((cx - sw / 2, top, cx + sw / 2, top + sh), radius=sh * 0.28, fill=color)
    m = sh * 0.2
    d.rounded_rectangle((cx - sw / 2 + m, top + m, cx + sw / 2 - m, top + sh - m), radius=sh * 0.12, fill=bg)
    # zon boven een heuvel in het schermpje
    rr = sh * 0.14
    d.ellipse((cx + sw * 0.05, top + sh * 0.3, cx + sw * 0.05 + 2 * rr, top + sh * 0.3 + 2 * rr), fill=color)


def symbool_penseel(d, cx, bottom, cap, color, bg):
    w = cap * 0.17
    stem(d, cx, bottom, cap * 0.55, w, color)
    # bus en haren: een druppelvorm met de punt omhoog
    top = bottom - cap * 0.55 - cap * 0.06
    d.rectangle((cx - w * 0.62, top - cap * 0.02, cx + w * 0.62, top + cap * 0.06), fill=color)
    tip_h = cap * 0.5
    pts = [(cx, top - tip_h)]
    for i in range(0, 181, 6):
        a = math.radians(i)
        pts.append((cx + math.cos(a) * w * 0.75 * (1 if i < 90 else 1), top - math.sin(a) * w * 0.4))
    pts = [(cx, top - tip_h)] + [(cx + w * 0.75 * math.cos(math.radians(a)), top - w * 0.35 * math.sin(math.radians(a)) - 2)
                                 for a in range(0, -181, -10)]
    d.polygon([(cx - w * 0.75, top), (cx - w * 0.6, top - tip_h * 0.55), (cx, top - tip_h),
               (cx + w * 0.6, top - tip_h * 0.55), (cx + w * 0.75, top)], fill=color)


SYMBOLEN = {"maan": symbool_maan, "scherm": symbool_scherm, "penseel": symbool_penseel}


def icon(symbool, bg, path, size=512):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    layer = Image.new("RGBA", (S, S), bg + (255,))
    d = ImageDraw.Draw(layer)
    font = ImageFont.truetype("nunito_black.ttf", int(S * 0.47))
    cx, base, cap = letters(d, font, ROOM, S * 0.19)
    SYMBOLEN[symbool](d, cx, base, cap, ROOM, bg)
    img.paste(layer, (0, 0), squircle_mask(S))
    img.resize((size, size), Image.LANCZOS).save(path)


if __name__ == "__main__":
    kleuren = {"aubergine": (74, 53, 112), "nachtpaars": (58, 54, 116), "petrol": (24, 92, 104)}
    for sym in SYMBOLEN:
        for naam, kleur in kleuren.items():
            icon(sym, kleur, f"bis_{sym}_{naam}.png")
