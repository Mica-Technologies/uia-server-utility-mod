"""Generate placeholder pixel-art textures for SUM's player-shop block (C5).

The shop block is a vending machine: tall body with a glass front, the actual
"display item" rendered later via TESR. Three 16x16 PNGs land under
``src/main/resources/assets/sum/textures/blocks/``:

* ``shop_front.png`` - glass front with a price-tag header strip
* ``shop_side.png``  - red metal side panel with vertical seams
* ``shop_top.png``   - subtle vent pattern + edge frame

These are intentionally simple. Polish passes can replace them later without
changing block models or registry names.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# Palette
BODY      = (158, 42,  46,  255)   # rich vending-machine red
BODY_HI   = (192, 64,  68,  255)
BODY_LO   = (108, 26,  30,  255)
GLASS     = (108, 156, 188, 100)   # translucent cool-blue glass
GLASS_HI  = (172, 208, 232, 130)
TRIM      = (44,  46,  52,  255)   # near-black trim
TRIM_HI   = (98,  102, 110, 255)
LABEL     = (228, 220, 188, 255)   # cream price-tag strip
LABEL_LO  = (178, 168, 136, 255)
GOLD      = (212, 178, 88,  255)
DARK      = (20,  22,  28,  255)


def base_canvas(color) -> Image.Image:
    return Image.new("RGBA", (16, 16), color)


def front() -> Image.Image:
    """Vending machine front: top body strip, header label, glass window, base trim."""
    img = base_canvas(BODY)
    px = img.load()

    # Top trim (rows 0-1)
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 1] = TRIM_HI

    # Header price-tag strip (row 2-3): cream colored
    for x in range(2, 14):
        px[x, 2] = LABEL_LO
        px[x, 3] = LABEL
    # Gold "$" mark on the label
    px[8, 3] = GOLD

    # Trim under header (row 4)
    for x in range(16):
        px[x, 4] = TRIM

    # Glass window (rows 5-13, cols 1-14) — semi-transparent so the block reads as glass
    for x in range(1, 15):
        for y in range(5, 14):
            px[x, y] = GLASS
    # Glass highlight: top edge + left specular
    for x in range(1, 15):
        px[x, 5] = GLASS_HI
    for y in range(5, 14):
        px[1, y] = GLASS_HI
    # Glass frame (single-pixel ring around the window inside the body)
    for x in range(0, 15):
        px[x, 5] = TRIM_HI if x in (0, 14) else px[x, 5]
    for y in range(5, 14):
        px[0, y] = TRIM
        px[15, y] = TRIM
    # Base seam (row 13) and base trim (rows 14-15)
    for x in range(16):
        px[x, 13] = TRIM
        px[x, 14] = BODY_LO
        px[x, 15] = TRIM

    # Coin slot in the base
    for x in range(11, 14):
        px[x, 14] = DARK
    return img


def side() -> Image.Image:
    """Side panel: solid red body with two vertical seams."""
    img = base_canvas(BODY)
    px = img.load()
    # Top trim
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 1] = TRIM_HI
    # Vertical seams
    for y in range(2, 14):
        px[3, y] = BODY_LO
        px[12, y] = BODY_LO
    # Highlight column near each seam
    for y in range(2, 14):
        px[4, y] = BODY_HI
        px[11, y] = BODY_HI
    # Base
    for x in range(16):
        px[x, 14] = BODY_LO
        px[x, 15] = TRIM
    return img


def top() -> Image.Image:
    """Top: red panel with a centered vent grille and edge frame."""
    img = base_canvas(BODY)
    px = img.load()
    # Frame
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 15] = TRIM
    for y in range(16):
        px[0, y] = TRIM
        px[15, y] = TRIM
    # Inner highlight ring
    for x in range(1, 15):
        px[x, 1] = BODY_HI
        px[x, 14] = BODY_LO
    for y in range(1, 15):
        px[1, y] = BODY_HI
        px[14, y] = BODY_LO
    # Centered vent grille (alternating dark/light)
    for x in range(4, 12):
        for y in range(4, 12):
            px[x, y] = TRIM_HI if (x + y) % 2 == 0 else TRIM
    return img


def main() -> None:
    front().save(OUT_DIR / "shop_front.png")
    side().save(OUT_DIR / "shop_side.png")
    top().save(OUT_DIR / "shop_top.png")
    print(f"wrote 3 shop textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
