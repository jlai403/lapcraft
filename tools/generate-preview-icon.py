#!/usr/bin/env python3
"""Overlay a "PREVIEW" banner on the launcher foreground for the preview variant.

Writes app/src/preview/res/mipmap-*/ic_launcher_foreground.png from the
main foregrounds. The adaptive icon XML is inherited, so only the
foregrounds need regenerating.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android/app/src/main/res"
OUT = ROOT / "android/app/src/preview/res"
FONT = ROOT / "android/app/src/main/res/font/geist_mono_bold.ttf"
BAND = (0, 224, 198, 255)   # mint accent
TEXT = (13, 17, 20, 255)     # ink

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

for density, scale in DENSITIES.items():
    canvas = int(108 * scale)
    src = RES / f"mipmap-{density}" / "ic_launcher_foreground.png"
    im = Image.open(src).convert("RGBA")
    draw = ImageDraw.Draw(im)

    band_h = int(canvas * 0.13)
    band_y = int(canvas * 0.70)
    draw.rectangle((0, band_y, canvas, band_y + band_h), fill=BAND)

    font_size = int(canvas * 0.10)
    font = ImageFont.truetype(str(FONT), font_size)
    label = "PREVIEW"
    bbox = draw.textbbox((0, 0), label, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    x = (canvas - tw) // 2 - bbox[0]
    y = band_y + (band_h - th) // 2 - bbox[1]
    draw.text((x, y), label, font=font, fill=TEXT)

    dst = OUT / f"mipmap-{density}" / "ic_launcher_foreground.png"
    dst.parent.mkdir(parents=True, exist_ok=True)
    im.save(dst)
    print(f"wrote {dst} ({canvas}x{canvas})")
