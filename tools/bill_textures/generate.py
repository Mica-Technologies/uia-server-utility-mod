"""Generate placeholder pixel-art textures for SUM's currency bill items.

Produces 8 16x16 PNGs, one per denomination ($1, $5, $10, $20, $50, $100, $200, $500),
under ``src/main/resources/assets/sum/textures/items/``. Each bill is a centered
rectangle in a denomination-specific color with a darker border and a small "$"
glyph - the bill's name in the tooltip carries the actual value, so the texture's
job is just to make denominations visually distinct in inventory.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/items"
OUT_DIR.mkdir(parents=True, exist_ok=True)

DOLLAR_GOLD = (228, 198, 80, 255)
DOLLAR_DARK = (164, 132, 28, 255)

# (body_color, border_color) per denomination.
# Roughly modeled after real-world note color associations where reasonable.
BILL_COLORS = {
    1:   ((144, 198, 138, 255), (52,  98,  56,  255)),  # green ($1)
    5:   ((218, 168, 188, 255), (102, 58,  78,  255)),  # pink ($5)
    10:  ((232, 220, 132, 255), (114, 102, 32,  255)),  # yellow ($10)
    20:  ((180, 222, 142, 255), (80,  118, 44,  255)),  # lime ($20)
    50:  ((230, 168, 110, 255), (132, 78,  28,  255)),  # orange ($50)
    100: ((142, 196, 224, 255), (40,  100, 130, 255)),  # cyan ($100)
    200: ((196, 162, 226, 255), (84,  52,  130, 255)),  # purple ($200)
    500: ((226, 138, 138, 255), (132, 48,  48,  255)),  # red ($500)
}


def make_bill(body: tuple, border: tuple) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # Bill body: centered 14x8 rectangle, rows 4..11, cols 1..14
    for x in range(1, 15):
        for y in range(4, 12):
            px[x, y] = body
    # Border, 1 pixel inside the rectangle
    for x in range(1, 15):
        px[x, 4]  = border
        px[x, 11] = border
    for y in range(4, 12):
        px[1,  y] = border
        px[14, y] = border
    # Subtle inner highlight on the top edge
    for x in range(2, 14):
        px[x, 5] = lighten(body, 16)
    # "$" mark, 3x4, top-left of the body
    # row 6: top of S
    px[4, 6] = DOLLAR_GOLD
    px[5, 6] = DOLLAR_GOLD
    # row 7: middle of S
    px[3, 7] = DOLLAR_DARK
    px[4, 7] = DOLLAR_GOLD
    # row 8: bottom of S
    px[5, 8] = DOLLAR_GOLD
    # row 9: tail
    px[3, 9] = DOLLAR_DARK
    px[4, 9] = DOLLAR_GOLD
    px[5, 9] = DOLLAR_GOLD
    # vertical stroke through the S
    px[4, 5]  = DOLLAR_DARK
    px[4, 10] = DOLLAR_DARK
    return img


def lighten(rgba: tuple, by: int) -> tuple:
    r, g, b, a = rgba
    return (min(255, r + by), min(255, g + by), min(255, b + by), a)


def main() -> None:
    for denom, (body, border) in BILL_COLORS.items():
        make_bill(body, border).save(OUT_DIR / f"bill_{denom}.png")
    print(f"wrote {len(BILL_COLORS)} bill textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
