"""Generate placeholder pixel-art textures for SUM's ATM block kit.

Produces 16x16 PNGs under
``src/main/resources/assets/sum/textures/blocks/atm_kiosk_*.png``:

* ``atm_kiosk_front.png`` - screen, card slot, keypad, cash slot
* ``atm_kiosk_side.png``  - blank side panel with vertical seams
* ``atm_kiosk_top.png``   - subtle vent/grille pattern

These are intentionally simple pixel art - the goal is "looks like an ATM in
dev, not a placeholder iron block." Polish passes can replace them later
without changing block models or registry names.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# Palette (R,G,B,A)
HOUSING       = (45, 48, 55, 255)    # dark slate
HOUSING_HI    = (62, 66, 76, 255)    # highlight rim
HOUSING_LO    = (28, 30, 36, 255)    # shadow seam
SCREEN        = (74, 158, 196, 255)  # cyan-blue lit screen
SCREEN_FRAME  = (20, 22, 28, 255)
SCREEN_GLOW   = (122, 198, 226, 255) # brighter screen highlight row
KEY_DARK      = (32, 34, 40, 255)
KEY_LIGHT     = (110, 116, 128, 255)
SLOT_BLACK    = (12, 13, 17, 255)
LOGO_GOLD     = (212, 178, 88, 255)  # accent for "$" mark


def base_canvas() -> Image.Image:
    return Image.new("RGBA", (16, 16), HOUSING)


def front() -> Image.Image:
    img = base_canvas()
    px = img.load()

    # Top highlight rim (one bright row, then the shadow seam under it)
    for x in range(16):
        px[x, 0] = HOUSING_HI
        px[x, 1] = HOUSING_LO

    # Screen frame: rows 2..6, cols 3..12 inclusive
    for x in range(3, 13):
        for y in range(2, 7):
            px[x, y] = SCREEN_FRAME
    # Screen lit area: rows 3..5, cols 4..11
    for x in range(4, 12):
        for y in range(3, 6):
            px[x, y] = SCREEN
    # One brighter "scanline" near the top of the screen
    for x in range(4, 12):
        px[x, 3] = SCREEN_GLOW
    # Tiny gold "$" pixel on the screen
    px[7, 4] = LOGO_GOLD
    px[8, 4] = LOGO_GOLD

    # Card slot - thin horizontal slot at row 8
    for x in range(4, 12):
        px[x, 8] = SLOT_BLACK
    px[3, 8] = HOUSING_LO
    px[12, 8] = HOUSING_LO

    # Keypad: 3 columns x 4 rows of keys, rows 10..13, cols 5..10
    for ky in range(4):
        for kx in range(3):
            cx = 5 + kx * 2
            cy = 10 + ky
            px[cx, cy] = KEY_LIGHT
            px[cx + 1, cy] = KEY_DARK

    # Cash slot - taller recessed slot at the bottom
    for x in range(4, 12):
        px[x, 14] = SLOT_BLACK
        px[x, 15] = SLOT_BLACK
    px[3, 14] = HOUSING_LO
    px[12, 14] = HOUSING_LO
    px[3, 15] = HOUSING_LO
    px[12, 15] = HOUSING_LO

    return img


def side() -> Image.Image:
    img = base_canvas()
    px = img.load()
    # Top highlight rim
    for x in range(16):
        px[x, 0] = HOUSING_HI
        px[x, 1] = HOUSING_LO
    # Two vertical panel seams
    for y in range(2, 16):
        px[2, y] = HOUSING_LO
        px[13, y] = HOUSING_LO
    # Subtle vertical highlight inside the seams
    for y in range(2, 16):
        px[3, y] = HOUSING_HI
    return img


def top() -> Image.Image:
    img = base_canvas()
    px = img.load()
    # Vent grille pattern in the center
    for x in range(4, 12):
        for y in range(4, 12):
            if (x + y) % 2 == 0:
                px[x, y] = HOUSING_LO
            else:
                px[x, y] = HOUSING_HI
    # Frame around the grille
    for x in range(3, 13):
        px[x, 3] = HOUSING_LO
        px[x, 12] = HOUSING_LO
    for y in range(3, 13):
        px[3, y] = HOUSING_LO
        px[12, y] = HOUSING_LO
    return img


def main() -> None:
    front().save(OUT_DIR / "atm_kiosk_front.png")
    side().save(OUT_DIR / "atm_kiosk_side.png")
    top().save(OUT_DIR / "atm_kiosk_top.png")
    print(f"wrote 3 ATM textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
