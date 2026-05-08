"""Generate the placeholder texture for SUM's business-card item (BC).

A 16x16 PNG resembling a small business card: cream background with a thin
border, a dark "name" stripe near the top, two thinner lines for contact info,
and a tiny gold accent dot.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/items"
OUT_DIR.mkdir(parents=True, exist_ok=True)

CARD = (242, 234, 214, 255)
CARD_HI = (252, 246, 226, 255)
CARD_LO = (210, 200, 178, 255)
BORDER = (54, 50, 42, 255)
INK = (38, 36, 34, 255)
INK_LIGHT = (108, 100, 92, 255)
GOLD = (212, 178, 88, 255)


def make_business_card():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # Card body 14x9 centered
    for x in range(1, 15):
        for y in range(4, 13):
            px[x, y] = CARD
    # Top highlight
    for x in range(2, 14):
        px[x, 4] = CARD_HI
    # Bottom shadow
    for x in range(2, 14):
        px[x, 12] = CARD_LO
    # Border
    for x in range(1, 15):
        px[x, 4] = BORDER if x in (1, 14) else px[x, 4]
        px[x, 12] = BORDER if x in (1, 14) else px[x, 12]
    for y in range(4, 13):
        px[1, y] = BORDER
        px[14, y] = BORDER
    # Top + bottom edges of border
    for x in range(1, 15):
        px[x, 3] = BORDER
        px[x, 13] = BORDER
    # "Name" stripe at row 6
    for x in range(3, 13):
        px[x, 6] = INK
    # Two thinner contact lines at rows 8 and 10
    for x in range(3, 12):
        px[x, 8] = INK_LIGHT
    for x in range(3, 10):
        px[x, 10] = INK_LIGHT
    # Gold accent dot bottom-right
    px[12, 10] = GOLD
    return img


def main():
    make_business_card().save(OUT_DIR / "business_card.png")
    print(f"wrote business_card.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
