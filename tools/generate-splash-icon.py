#!/usr/bin/env python3
"""Generate the Android 12 splash icon from the 2048 px master.

The splash window is 288 dp with a 192 dp visible circle, so a bitmap drawn
from the 108 dp adaptive icon is upscaled about 2.7x. These assets keep the
art at native resolution for every density.
"""
from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs/branding/launcher-icon-master.jpg"
RES = ROOT / "app/src/main/res"

# Splash canvas per density: 288 dp.
SIZES = {"mdpi": 288, "hdpi": 432, "xhdpi": 576, "xxhdpi": 864, "xxxhdpi": 1152}
VISIBLE = 192 / 288


def keyed_art(path: Path) -> Image.Image:
    """Drop the dark surround with a flood fill from the border."""
    im = Image.open(path).convert("RGB")
    w, h = im.size

    border = []
    for x in range(w):
        border += [im.getpixel((x, 0)), im.getpixel((x, h - 1))]
    for y in range(h):
        border += [im.getpixel((0, y)), im.getpixel((w - 1, y))]
    bg = max(set(border), key=border.count)

    def diff(pixel):
        return max(abs(pixel[i] - bg[i]) for i in range(3))

    mask = bytearray(w * h)
    queue = deque()
    for x in range(w):
        for y in (0, h - 1):
            i = y * w + x
            if not mask[i] and diff(im.getpixel((x, y))) <= 12:
                mask[i] = 1
                queue.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            i = y * w + x
            if not mask[i] and diff(im.getpixel((x, y))) <= 12:
                mask[i] = 1
                queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h:
                i = ny * w + nx
                if not mask[i] and diff(im.getpixel((nx, ny))) <= 12:
                    mask[i] = 1
                    queue.append((nx, ny))

    alpha = Image.frombytes("L", (w, h), bytes(0 if m else 255 for m in mask))
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.0))
    rgba = im.convert("RGBA")
    rgba.putalpha(alpha)
    return rgba.crop(rgba.getbbox())


def disc(art: Image.Image, size: int) -> Image.Image:
    """Centre the art so its bounding box is the 192 dp visible circle."""
    target = round(VISIBLE * size)
    scale = target / max(art.size)
    w, h = round(art.width * scale), round(art.height * scale)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(art.resize((w, h), Image.Resampling.LANCZOS), ((size - w) // 2, (size - h) // 2))
    return canvas


art = keyed_art(SRC)
for density, size in SIZES.items():
    out = RES / f"drawable-{density}/splash_icon.webp"
    out.parent.mkdir(parents=True, exist_ok=True)
    disc(art, size).save(out, "WEBP", quality=95, method=6)
    print(f"{out.relative_to(ROOT)} {out.stat().st_size // 1024} KB")
