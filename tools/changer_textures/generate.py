"""Generate placeholder pixel-art textures for SUM's bill-changer block (C6).

The changer block is a coin-counter / cash-register style machine. Three 16x16
PNGs under ``src/main/resources/assets/sum/textures/blocks/``:

* ``bill_changer_front.png`` - keypad + screen + bill slot
* ``bill_changer_side.png``  - panel with seams
* ``bill_changer_top.png``   - hopper-style funnel

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

BODY = (110, 116, 124, 255)         # cool-gray steel body
BODY_HI = (138, 144, 152, 255)
BODY_LO = (76, 80, 88, 255)
TRIM = (44, 48, 54, 255)
SCREEN = (164, 224, 132, 255)       # green LCD
SCREEN_DIM = (108, 168, 86, 255)
SCREEN_FRAME = (24, 28, 30, 255)
KEY_LIGHT = (146, 152, 160, 255)
KEY_DARK = (60, 64, 72, 255)
SLOT_BLACK = (16, 18, 22, 255)
GOLD = (212, 178, 88, 255)


def base(color):
    return Image.new("RGBA", (16, 16), color)


def front():
    img = base(BODY)
    px = img.load()

    # Top trim (rows 0-1)
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 1] = BODY_HI

    # LCD screen frame: rows 2..5, cols 2..13
    for x in range(2, 14):
        for y in range(2, 6):
            px[x, y] = SCREEN_FRAME
    # LCD lit area: rows 3..4, cols 3..12
    for x in range(3, 13):
        for y in range(3, 5):
            px[x, y] = SCREEN
    # LCD dim row to suggest segments
    for x in range(3, 13):
        px[x, 4] = SCREEN_DIM
    # Gold "$" on LCD
    px[7, 3] = GOLD
    px[8, 3] = GOLD

    # 3x3 keypad: rows 7..9, cols 4..10 (each key is 1 pixel wide gap+1 wide)
    for ky in range(3):
        for kx in range(3):
            cx = 5 + kx * 2
            cy = 7 + ky
            px[cx, cy] = KEY_LIGHT
            px[cx + 1, cy] = KEY_DARK

    # Bill input/output slot (a thin horizontal slit) at row 12
    for x in range(3, 13):
        px[x, 12] = SLOT_BLACK
    px[2, 12] = BODY_LO
    px[13, 12] = BODY_LO

    # Bottom trim
    for x in range(16):
        px[x, 14] = BODY_LO
        px[x, 15] = TRIM
    return img


def side():
    img = base(BODY)
    px = img.load()
    # Top trim
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 1] = BODY_HI
    # Vertical seams
    for y in range(2, 14):
        px[3, y] = BODY_LO
        px[12, y] = BODY_LO
    # Highlight columns
    for y in range(2, 14):
        px[4, y] = BODY_HI
        px[11, y] = BODY_HI
    # Bottom trim
    for x in range(16):
        px[x, 14] = BODY_LO
        px[x, 15] = TRIM
    return img


def top():
    img = base(BODY)
    px = img.load()
    # Outer frame
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 15] = TRIM
    for y in range(16):
        px[0, y] = TRIM
        px[15, y] = TRIM
    # Hopper funnel - concentric rings dimming to center
    rings = [
        (1, 14, BODY_HI),
        (2, 13, BODY),
        (3, 12, BODY_LO),
        (4, 11, BODY_LO),
        (5, 10, TRIM),
        (6,  9, SLOT_BLACK),
    ]
    for lo, hi, color in rings:
        for x in range(lo, hi + 1):
            px[x, lo] = color
            px[x, hi] = color
        for y in range(lo, hi + 1):
            px[lo, y] = color
            px[hi, y] = color
    return img


def main():
    front().save(OUT_DIR / "bill_changer_front.png")
    side().save(OUT_DIR / "bill_changer_side.png")
    top().save(OUT_DIR / "bill_changer_top.png")
    print(f"wrote 3 changer textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
