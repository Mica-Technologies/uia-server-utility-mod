"""Generate placeholder pixel-art textures for SUM's packet items (C6).

Each packet visually represents a bundle of 64 bills. The texture reuses the bill
denomination's body color so the relationship is obvious in inventory, then adds a
brown paper "wrap" band across the middle and a small stack-of-bills 3D hint at
the corner.

8 16x16 PNGs land under ``src/main/resources/assets/sum/textures/items/`` as
``packet_<denomination>.png``.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/items"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# Match bill colors so packet/bill association is visually obvious.
BILL_COLORS = {
    1:   ((144, 198, 138, 255), (52,  98,  56,  255)),
    5:   ((218, 168, 188, 255), (102, 58,  78,  255)),
    10:  ((232, 220, 132, 255), (114, 102, 32,  255)),
    20:  ((180, 222, 142, 255), (80,  118, 44,  255)),
    50:  ((230, 168, 110, 255), (132, 78,  28,  255)),
    100: ((142, 196, 224, 255), (40,  100, 130, 255)),
    200: ((196, 162, 226, 255), (84,  52,  130, 255)),
    500: ((226, 138, 138, 255), (132, 48,  48,  255)),
}

WRAP_LIGHT = (170, 130, 86, 255)
WRAP_DARK = (118, 84, 50, 255)
WRAP_HI = (200, 158, 110, 255)
DOLLAR_GOLD = (228, 198, 80, 255)


def lighten(rgba, by):
    r, g, b, a = rgba
    return (min(255, r + by), min(255, g + by), min(255, b + by), a)


def make_packet(body, border):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # Stack-of-bills outline: render the back stack offset (x+1, y-1) for a 3D feel
    # Back stack: rows 3..10, cols 2..15
    for x in range(2, 15):
        for y in range(3, 11):
            px[x, y] = border
    for x in range(3, 14):
        for y in range(4, 10):
            px[x, y] = body
    # Front stack: rows 5..12, cols 1..14
    for x in range(1, 15):
        for y in range(5, 13):
            px[x, y] = border
    for x in range(2, 14):
        for y in range(6, 12):
            px[x, y] = body
    # Subtle highlight on the front-top edge
    for x in range(3, 14):
        px[x, 6] = lighten(body, 18)
    # Brown wrap band across the middle of the front stack (rows 8..9)
    for x in range(1, 15):
        px[x, 8] = WRAP_DARK
        px[x, 9] = WRAP_LIGHT
    # Wrap highlight tick
    px[3, 8] = WRAP_HI
    # "$" mark on wrap, gold
    px[7, 9] = DOLLAR_GOLD
    px[8, 9] = DOLLAR_GOLD
    return img


def main():
    for denom, (body, border) in BILL_COLORS.items():
        make_packet(body, border).save(OUT_DIR / f"packet_{denom}.png")
    print(f"wrote {len(BILL_COLORS)} packet textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
