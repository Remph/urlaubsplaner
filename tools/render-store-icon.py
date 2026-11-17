#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Remph
"""Renders the 512 px store icon (fastlane/metadata/android/*/images/icon.png).

The geometry is copied from app/src/main/res/drawable/ic_launcher_foreground.xml (108 dp canvas)
and the colour from values/ic_launcher_background.xml. The store icon is the visible 72 dp window
(18..90) of that canvas, full square without rounded corners: F-Droid and Play round it themselves.
Run it again whenever the launcher icon changes:  python3 tools/render-store-icon.py  (needs Pillow)
"""
import math
import pathlib

from PIL import Image, ImageDraw

TEAL = "#0E6F72"
GOLD = "#E0AC4E"
WHITE = "#FFFFFF"
SIZE = 512
SUPER = 4  # draw 4x larger, then shrink, for smooth edges
SCALE = SIZE * SUPER / 72
OUT = pathlib.Path(__file__).resolve().parent.parent / "fastlane/metadata/android"


def px(v):
    return (v - 18) * SCALE


img = Image.new("RGB", (SIZE * SUPER, SIZE * SUPER), TEAL)
d = ImageDraw.Draw(img)

# Sun: circle at (54,46) with radius 9, seven rays from r=14 to r=19.5 every 30 degrees.
d.ellipse([px(45), px(37), px(63), px(55)], fill=GOLD)
width = 4 * SCALE
for deg in range(-180, 1, 30):
    a = math.radians(deg)
    p1 = (px(54 + 14 * math.cos(a)), px(46 + 14 * math.sin(a)))
    p2 = (px(54 + 19.5 * math.cos(a)), px(46 + 19.5 * math.sin(a)))
    d.line([p1, p2], fill=GOLD, width=round(width))
    for p in (p1, p2):  # round caps
        d.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=GOLD)

# Four day tiles, 9 x 9 with radius 2.5; the second one is gold, the holiday.
for x, colour in ((30, WHITE), (43, GOLD), (56, WHITE), (69, WHITE)):
    d.rounded_rectangle([px(x), px(66), px(x + 9), px(75)], radius=2.5 * SCALE, fill=colour)

icon = img.resize((SIZE, SIZE), Image.LANCZOS)
for locale in ("de-DE", "en-US"):
    target = OUT / locale / "images"
    target.mkdir(parents=True, exist_ok=True)
    icon.save(target / "icon.png", optimize=True)
print("written for de-DE and en-US")
