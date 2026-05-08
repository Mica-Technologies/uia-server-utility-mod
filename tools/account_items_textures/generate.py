"""Generate placeholder pixel-art textures for SUM's account-access items
(phone + debit card from C4).

Both items right-click to open the ATM GUI from anywhere; the textures just
need to be visually distinct in inventory. 16x16 PNGs land under
``src/main/resources/assets/sum/textures/items/``.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/items"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def make_phone() -> Image.Image:
    """Modern smartphone: tall black slab, screen, speaker, home button."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    body = (32, 34, 38, 255)        # near-black case
    body_hi = (62, 66, 72, 255)     # subtle bevel
    screen = (90, 162, 220, 255)    # cool blue screen
    screen_hi = (140, 198, 232, 255)
    speaker = (20, 22, 26, 255)
    home = (160, 168, 176, 255)

    # Body: column 4..11, row 1..14 (8 wide, 14 tall)
    for x in range(4, 12):
        for y in range(1, 15):
            px[x, y] = body
    # Bevel highlight on the left edge
    for y in range(2, 14):
        px[4, y] = body_hi
    # Screen: rows 4..11, cols 5..10 (6 wide, 8 tall)
    for x in range(5, 11):
        for y in range(4, 12):
            px[x, y] = screen
    # Screen specular: top-left corner
    px[5, 4] = screen_hi
    px[6, 4] = screen_hi
    px[5, 5] = screen_hi
    # Speaker: row 2, col 6..9
    for x in range(7, 9):
        px[x, 2] = speaker
    # Home button: row 13, col 7..8
    px[7, 13] = home
    px[8, 13] = home
    return img


def make_debit_card() -> Image.Image:
    """Classic horizontal debit card: dark blue, magnetic stripe, chip."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    body = (28, 60, 110, 255)       # navy blue
    body_hi = (48, 88, 148, 255)
    stripe = (24, 26, 30, 255)      # mag stripe
    chip = (212, 184, 80, 255)      # gold chip
    chip_hi = (240, 216, 120, 255)
    text = (200, 210, 222, 255)     # faux embossed numbers

    # Body: 14x8, rows 4..11, cols 1..14
    for x in range(1, 15):
        for y in range(4, 12):
            px[x, y] = body
    # Top bevel highlight
    for x in range(2, 14):
        px[x, 4] = body_hi
    # Magnetic stripe (top): row 5, cols 1..14
    for x in range(1, 15):
        px[x, 5] = stripe
    # Chip: 2x2, rows 7..8, cols 3..4
    px[3, 7] = chip
    px[4, 7] = chip
    px[3, 8] = chip
    px[4, 8] = chip
    px[3, 7] = chip_hi  # sparkle
    # Faux number row: row 10
    for x in (6, 8, 10, 12):
        px[x, 10] = text
    return img


def main() -> None:
    make_phone().save(OUT_DIR / "phone.png")
    make_debit_card().save(OUT_DIR / "debit_card.png")
    print(f"wrote phone.png + debit_card.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
