"""Generate placeholder pixel-art textures for SUM's trash can block (TR).

Two 16x16 PNGs go to ``src/main/resources/assets/sum/textures/blocks/``:

* ``trash_can_side.png`` - corrugated dark-gray side with a recycle/trash badge
* ``trash_can_top.png``  - dark opening with handles on either side

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

BODY = (66, 70, 76, 255)
BODY_HI = (94, 98, 104, 255)
BODY_LO = (40, 44, 50, 255)
TRIM = (24, 26, 30, 255)
HOLE = (10, 12, 16, 255)
GREEN = (90, 168, 96, 255)
GREEN_DK = (52, 116, 60, 255)


def base(color):
    return Image.new("RGBA", (16, 16), color)


def side():
    img = base(BODY)
    px = img.load()
    # Top rim (rows 0-1)
    for x in range(16):
        px[x, 0] = TRIM
        px[x, 1] = BODY_HI
    # Bottom rim
    for x in range(16):
        px[x, 14] = BODY_LO
        px[x, 15] = TRIM
    # Vertical corrugation lines
    for x in (3, 6, 9, 12):
        for y in range(2, 14):
            px[x, y] = BODY_LO
        for y in range(2, 14):
            px[x + 1, y] = BODY_HI
    # Recycle badge in the center: a green circle-ish patch with a paler arrow
    for x in range(6, 10):
        for y in range(6, 10):
            px[x, y] = GREEN_DK
    for (cx, cy) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[cx, cy] = GREEN
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
    # Inner highlight
    for x in range(1, 15):
        px[x, 1] = BODY_HI
        px[x, 14] = BODY_LO
    for y in range(1, 15):
        px[1, y] = BODY_HI
        px[14, y] = BODY_LO
    # Opening (large hole, off-center for asymmetry)
    for x in range(3, 13):
        for y in range(3, 13):
            px[x, y] = HOLE
    # Subtle hole rim shading
    for x in range(3, 13):
        px[x, 3] = TRIM
    for y in range(3, 13):
        px[3, y] = TRIM
    # Handles: small protrusions left + right (rows 7-8 at cols 0,1 and 14,15)
    for y in (7, 8):
        px[1, y] = BODY_HI
        px[2, y] = BODY
        px[13, y] = BODY
        px[14, y] = BODY_HI
    return img


def main():
    side().save(OUT_DIR / "trash_can_side.png")
    top().save(OUT_DIR / "trash_can_top.png")
    print(f"wrote 2 trash can textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
