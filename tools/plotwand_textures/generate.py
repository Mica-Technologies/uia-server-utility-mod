"""Generate placeholder texture for the SUM plot wand (D2).

A 16x16 PNG of a wooden wand with a glowing tip — visually distinct from a
fishing rod or stick so admins can spot it in inventory.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/items"
OUT_DIR.mkdir(parents=True, exist_ok=True)

WOOD_DARK = (88, 60, 30, 255)
WOOD = (134, 92, 50, 255)
WOOD_HI = (174, 124, 70, 255)
GRIP = (40, 40, 48, 255)
GLOW_HOT = (240, 220, 120, 255)
GLOW_COOL = (160, 220, 220, 255)
GEM = (80, 150, 220, 255)
GEM_HI = (140, 200, 240, 255)


def make_wand():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # Diagonal wand from bottom-left to top-right
    for i in range(12):
        x = 1 + i
        y = 14 - i
        px[x, y] = WOOD_DARK
        if 0 <= x - 1 < 16:
            px[x - 1, y] = WOOD
        if 0 <= x < 16 and 0 <= y - 1 < 16:
            px[x, y - 1] = WOOD_HI
    # Grip wrap (3 pixels along the bottom of the diagonal)
    for i in range(3):
        x = 2 + i
        y = 13 - i
        px[x, y] = GRIP
    # Tip gem at upper-right
    for (gx, gy) in ((11, 4), (12, 4), (12, 3), (13, 3), (13, 2), (12, 2)):
        if 0 <= gx < 16 and 0 <= gy < 16:
            px[gx, gy] = GEM
    px[12, 3] = GEM_HI
    # Glow halo
    glow = [(11, 3), (10, 4), (11, 5), (12, 5), (13, 4), (14, 3), (14, 2), (13, 1), (11, 2)]
    for (gx, gy) in glow:
        if 0 <= gx < 16 and 0 <= gy < 16:
            px[gx, gy] = GLOW_COOL
    px[13, 4] = GLOW_HOT
    return img


def main():
    make_wand().save(OUT_DIR / "plot_wand.png")
    print(f"wrote plot_wand.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
