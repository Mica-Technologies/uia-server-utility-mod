"""Generate placeholder pixel-art textures for SUM's mailbox block (MX).

Three 16x16 PNGs under ``src/main/resources/assets/sum/textures/blocks/``:

* ``mailbox_front.png`` - blue body with mail slot + flag
* ``mailbox_side.png``  - solid blue side
* ``mailbox_top.png``   - top of the mailbox

The block model trims these textures (12 wide, 13 tall) so we generate the
full 16x16 with the relevant detail in the middle and let the model crop.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

BLUE = (60, 96, 158, 255)
BLUE_HI = (88, 128, 192, 255)
BLUE_LO = (38, 70, 122, 255)
TRIM = (24, 28, 40, 255)
SLOT = (12, 14, 20, 255)
FLAG_RED = (200, 60, 60, 255)
FLAG_DARK = (140, 30, 30, 255)
LOGO = (228, 220, 188, 255)


def base():
    return Image.new("RGBA", (16, 16), BLUE)


def front():
    img = base()
    px = img.load()
    # Top trim
    for x in range(16):
        px[x, 3] = TRIM
        px[x, 4] = BLUE_HI
    # Bottom trim
    for x in range(16):
        px[x, 14] = BLUE_LO
        px[x, 15] = TRIM
    # Mail slot — horizontal cut at row 8
    for x in range(4, 12):
        px[x, 8] = SLOT
    # Slot frame
    px[3, 8] = TRIM
    px[12, 8] = TRIM
    # Letter envelope hint emerging from slot
    for x in range(5, 11):
        px[x, 9] = LOGO
    px[5, 10] = LOGO
    px[10, 10] = LOGO
    # Flag indicator on the right side, rows 5..7
    for y in range(5, 8):
        px[13, y] = FLAG_DARK
        px[14, y] = FLAG_RED
    return img


def side():
    img = base()
    px = img.load()
    # Top trim
    for x in range(16):
        px[x, 3] = TRIM
        px[x, 4] = BLUE_HI
    for x in range(16):
        px[x, 14] = BLUE_LO
        px[x, 15] = TRIM
    # Subtle vertical seams
    for y in range(5, 14):
        px[3, y] = BLUE_LO
        px[12, y] = BLUE_LO
    return img


def top():
    img = base()
    px = img.load()
    # Outer frame
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 15] = TRIM
    for y in range(16):
        px[0, y] = TRIM
        px[15, y] = TRIM
    # Inner highlight
    for x in range(1, 15):
        px[x, 1] = BLUE_HI
        px[x, 14] = BLUE_LO
    for y in range(1, 15):
        px[1, y] = BLUE_HI
        px[14, y] = BLUE_LO
    # Hinge line down the middle (mailbox lid)
    for x in range(2, 14):
        px[x, 7] = TRIM
        px[x, 8] = BLUE_HI
    return img


def main():
    front().save(OUT_DIR / "mailbox_front.png")
    side().save(OUT_DIR / "mailbox_side.png")
    top().save(OUT_DIR / "mailbox_top.png")
    print(f"wrote 3 mailbox textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
