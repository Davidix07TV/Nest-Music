#!/usr/bin/env python3
"""Generate branded README screenshots for Nest Music.

Takes the raw app captures in docs/assets/screenN.jpg and produces
README-ready mockups in assets/screenshots/:

  * accent recolor  -> pastel lavender UI accents become Nest purple (#8A52F5),
                       pink hearts become Nest magenta (#FF2E7E)
  * synthetic status bar (clock + signal/wifi/battery)
  * phone bezel with punch-hole camera and side buttons
  * aurora backdrop in brand colors (#8A52F5 / #00E5FF / #FF2E7E on #0B0A10)
  * caption chip + Nest logo branding
  * hero.jpg banner with three devices

Run from the repository root:  python3 make_mockups.py
Requires: Pillow, numpy
"""
from __future__ import annotations

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "docs", "assets")
OUT = os.path.join(ROOT, "assets", "screenshots")
LOGO = os.path.join(ROOT, "assets", "nest-music-logo.png")

# brand palette (docs/styles.css)
BG = (11, 10, 16)
PUR = (138, 82, 245)
CY = (0, 229, 255)
MG = (255, 46, 126)
BEZEL = (11, 11, 16)
BEZEL_EDGE = (43, 37, 66)

SCREEN_W, SCREEN_H = 1080, 2340  # 9:19.5 normalized screen

FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# source, caption, crop side for the 60px lost by the 9:19.5 normalization
SCREENS = [
    ("screen1.jpg", "Home", "top"),
    ("screen2.jpg", "Artist pages", "bottom"),
    ("screen3.jpg", "Music recognition", "bottom"),
    ("screen4.jpg", "Listen Together", "top"),
    ("screen5.jpg", "Player", "top"),
    ("screen6.jpg", "Synced lyrics", "top"),
]


# ---------------------------------------------------------------- color math
def rgb_to_hsv(arr: np.ndarray) -> np.ndarray:
    arr = arr.astype(np.float32) / 255.0
    mx = arr.max(axis=-1)
    mn = arr.min(axis=-1)
    df = mx - mn
    h = np.zeros_like(mx)
    r, g, b = arr[..., 0], arr[..., 1], arr[..., 2]
    m = df > 1e-6
    idx = m & (mx == r)
    h[idx] = (60 * ((g - b) / np.where(df > 1e-6, df, 1)) % 360)[idx]
    idx = m & (mx == g)
    h[idx] = (60 * ((b - r) / np.where(df > 1e-6, df, 1)) + 120)[idx]
    idx = m & (mx == b)
    h[idx] = (60 * ((r - g) / np.where(df > 1e-6, df, 1)) + 240)[idx]
    s = np.where(mx > 1e-6, df / np.where(mx > 1e-6, mx, 1), 0)
    return np.stack([h, s, mx], axis=-1)


def hsv_to_rgb(arr: np.ndarray) -> np.ndarray:
    h, s, v = arr[..., 0], arr[..., 1], arr[..., 2]
    c = v * s
    x = c * (1 - np.abs((h / 60.0) % 2 - 1))
    mnp = v - c
    zero = np.zeros_like(c)
    seg = [
        (c, x, zero),
        (x, c, zero),
        (zero, c, x),
        (zero, x, c),
        (x, zero, c),
        (c, zero, x),
    ]
    i = (h // 60).astype(int) % 6
    rgb = np.stack([np.select([i == k for k in range(6)], comp, zero) for comp in zip(*seg)], axis=-1)
    return np.clip((rgb + mnp[..., None]) * 255.0, 0, 255).astype(np.uint8)


def rebrand(img: Image.Image) -> Image.Image:
    """Lavender accents -> Nest purple, pink -> Nest magenta."""
    hsv = rgb_to_hsv(np.asarray(img.convert("RGB")))
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]

    accent = (h >= 225) & (h <= 278) & (s >= 0.10) & (s <= 0.52) & (v >= 0.38)
    h2 = np.where(accent, 262.0, h)
    s2 = np.where(accent, np.clip(s * 3.4, 0, 0.72), s)
    v2 = np.where(accent, v * 0.98, v)

    pink = (h >= 315) & (h <= 352) & (s >= 0.35) & (v >= 0.40)
    h2 = np.where(pink, 340.0, h2)
    s2 = np.where(pink, np.clip(s * 1.08, 0, 0.82), s2)

    return Image.fromarray(hsv_to_rgb(np.stack([h2, s2, v2], axis=-1)))


# ------------------------------------------------------------------- pieces
def status_bar(img: Image.Image) -> Image.Image:
    img = img.convert("RGB")
    d = ImageDraw.Draw(img)
    white = (235, 235, 240)
    f = ImageFont.truetype(FONT_B, 46)
    d.text((88, 30), "9:41", font=f, fill=white)

    cy0 = 55
    x = SCREEN_W - 88
    # battery
    d.rounded_rectangle([x - 54, cy0 - 14, x - 6, cy0 + 14], radius=7, outline=white, width=3)
    d.rounded_rectangle([x - 48, cy0 - 8, x - 24, cy0 + 8], radius=4, fill=white)
    d.rounded_rectangle([x - 4, cy0 - 6, x, cy0 + 6], radius=2, fill=white)
    x -= 84
    # wifi
    cx = x - 16
    for r in (26, 17):
        d.arc([cx - r, cy0 - r + 6, cx + r, cy0 + r + 6], start=225, end=315, fill=white, width=5)
    d.ellipse([cx - 4, cy0 + 6, cx + 4, cy0 + 14], fill=white)
    x -= 62
    # signal
    for k, bh in enumerate((10, 16, 22, 28)):
        bx = x - 4 * 13 + k * 13
        d.rounded_rectangle([bx, cy0 + 14 - bh, bx + 8, cy0 + 14], radius=2, fill=white)
    return img


def normalize(img: Image.Image, crop: str) -> Image.Image:
    w, h = img.size
    scale = SCREEN_W / w
    img = img.resize((SCREEN_W, round(h * scale)), Image.LANCZOS)
    dh = img.size[1] - SCREEN_H
    if dh > 0:
        top = 0 if crop == "top" else (img.size[1] - SCREEN_H if crop == "bottom" else dh // 2)
        img = img.crop((0, top, SCREEN_W, top + SCREEN_H))
    elif dh < 0:
        pad = Image.new("RGB", (SCREEN_W, SCREEN_H), (0, 0, 0))
        pad.paste(img, (0, -dh // 2))
        img = pad
    return img


def phone_layer(screen: Image.Image, screen_h: int) -> Image.Image:
    screen_w = round(screen_h * SCREEN_W / SCREEN_H)
    p = max(6, round(screen_h * 0.012))
    rad = round(screen_h * 0.050)
    srad = round(screen_h * 0.040)
    W, H = screen_w + 2 * p, screen_h + 2 * p
    layer = Image.new("RGBA", (W + 8, H + 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([4, 4, W + 3, H + 3], radius=rad, fill=BEZEL + (255,), outline=BEZEL_EDGE + (255,), width=2)
    mask = Image.new("L", (screen_w, screen_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, screen_w - 1, screen_h - 1], radius=srad, fill=255)
    layer.paste(screen.resize((screen_w, screen_h), Image.LANCZOS), (p + 4, p + 4), mask)
    # punch-hole camera
    chx, chy = W // 2 + 4, p + 4 + round(screen_h * 0.034)
    chr_ = max(4, round(screen_h * 0.0105))
    d.ellipse([chx - chr_ - 2, chy - chr_ - 2, chx + chr_ + 2, chy + chr_ + 2], fill=(24, 24, 32, 255))
    d.ellipse([chx - chr_, chy - chr_, chx + chr_, chy + chr_], fill=(5, 5, 8, 255))
    # side buttons
    d.rounded_rectangle([W + 2, round(H * 0.26), W + 6, round(H * 0.33)], radius=2, fill=BEZEL_EDGE + (255,))
    d.rounded_rectangle([0, round(H * 0.21), 4, round(H * 0.30)], radius=2, fill=BEZEL_EDGE + (255,))
    return layer


def glow(size: tuple[int, int], color, alpha: int, blur: int) -> Image.Image:
    g = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.ellipse([size[0] * 0.18, size[1] * 0.18, size[0] * 0.82, size[1] * 0.82], fill=color + (alpha,))
    return g.filter(ImageFilter.GaussianBlur(blur))


def backdrop(w: int, h: int, strength: float = 1.0) -> Image.Image:
    base = Image.new("RGBA", (w, h), BG + (255,))
    blobs = [
        ((-0.18 * w, -0.22 * h, 0.72 * w, 0.62 * h), PUR, int(120 * strength)),
        ((0.62 * w, 0.16 * h, 1.24 * w, 0.74 * h), CY, int(40 * strength)),
        ((-0.10 * w, 0.58 * h, 0.66 * w, 1.26 * h), MG, int(52 * strength)),
        ((0.42 * w, 0.62 * h, 1.10 * w, 1.30 * h), PUR, int(70 * strength)),
    ]
    for box, color, alpha in blobs:
        bl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(bl).ellipse(box, fill=color + (alpha,))
        base = Image.alpha_composite(base, bl.filter(ImageFilter.GaussianBlur(round(0.09 * w))))
    # vignette
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = (xx - w / 2) / (w / 2)
    dy = (yy - h / 2) / (h / 2)
    dist = np.clip(np.sqrt(dx * dx + dy * dy) / 1.35, 0, 1)
    vig = (dist ** 2.2 * 150).astype(np.uint8)
    black = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    black.putalpha(Image.fromarray(vig, "L"))
    return Image.alpha_composite(base, black)


def chip(draw_target: Image.Image, text: str, cx: int, cy: int, fnt: ImageFont.FreeTypeFont) -> None:
    overlay = Image.new("RGBA", draw_target.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    tw = d.textlength(text, font=fnt)
    pad_x, hgt = 30, 58
    box = [cx - tw / 2 - pad_x, cy - hgt / 2, cx + tw / 2 + pad_x, cy + hgt / 2]
    d.rounded_rectangle(box, radius=hgt // 2, fill=(255, 255, 255, 16), outline=PUR + (140,), width=2)
    d.text((cx, cy), text, font=fnt, fill=(240, 238, 248, 255), anchor="mm")
    draw_target.alpha_composite(overlay)


# ------------------------------------------------------------------ outputs
def make_card(src_name: str, caption: str, crop: str, out_name: str) -> None:
    img = rebrand(normalize(Image.open(os.path.join(SRC, src_name)), crop))
    img = status_bar(img)

    W, H = 760, 1150
    card = backdrop(W, H)
    d = ImageDraw.Draw(card)
    f_eye = ImageFont.truetype(FONT_B, 24)
    d.text((46, 44), "NEST MUSIC", font=f_eye, fill=PUR)
    if os.path.exists(LOGO):
        logo = Image.open(LOGO).convert("RGBA").resize((48, 48), Image.LANCZOS)
        card.paste(logo, (W - 48 - 44, 38), logo)

    phone = phone_layer(img, 880)
    card.paste(phone, ((W - phone.size[0]) // 2, 108), phone)
    gl = glow((phone.size[0] + 260, phone.size[1] + 260), PUR, 70, 60)
    card.paste(gl, ((W - gl.size[0]) // 2, 108 - 130), gl)
    card.paste(phone, ((W - phone.size[0]) // 2, 108), phone)

    chip(card, caption, W // 2, H - 62, ImageFont.truetype(FONT_B, 30))
    card.convert("RGB").save(os.path.join(OUT, out_name), quality=90, optimize=True, progressive=True)
    print("wrote", out_name)


def make_hero(picks: list[tuple[str, str]], out_name: str) -> None:
    W, H = 1600, 900
    hero = backdrop(W, H, strength=1.25)
    sizes = [640, 780, 640]
    xs = [215, 800, 1385]
    ys = [150, 60, 150]
    layers = []
    for (src_name, crop), sh in zip(picks, sizes):
        img = rebrand(normalize(Image.open(os.path.join(SRC, src_name)), crop))
        img = status_bar(img)
        layers.append(phone_layer(img, sh))
    gl = glow((1100, 1100), PUR, 80, 90)
    hero.paste(gl, (800 - gl.size[0] // 2, 450 - gl.size[1] // 2), gl)
    for i in (0, 2, 1):
        ph = layers[i]
        hero.paste(ph, (xs[i] - ph.size[0] // 2, ys[i]), ph)
    hero.convert("RGB").save(os.path.join(OUT, out_name), quality=90, optimize=True, progressive=True)
    print("wrote", out_name)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for i, (src, caption, crop) in enumerate(SCREENS, 1):
        make_card(src, caption, crop, f"{i:02d}-{caption.lower().replace(' ', '-')}.jpg")
    make_hero([(SCREENS[0][0], SCREENS[0][2]), (SCREENS[4][0], SCREENS[4][2]), (SCREENS[5][0], SCREENS[5][2])], "hero.jpg")
