"""Generate the placeholder texture for SUM's storm-shelter sign block (ST).

Single 16x16 PNG. The block is a thin floor plaque, so the top face is what
players see most. Diagonal black/yellow hazard stripes with a centered "shelter"
arrow/triangle so the function is readable at a glance.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

YELLOW = (228, 196, 64, 255)
YELLOW_DK = (172, 144, 32, 255)
BLACK = (28, 28, 32, 255)
BLACK_HI = (60, 60, 64, 255)
WHITE = (224, 220, 208, 255)


def make_texture():
    img = Image.new("RGBA", (16, 16), YELLOW)
    px = img.load()

    # Diagonal hazard stripes — every other 2-pixel band black
    for y in range(16):
        for x in range(16):
            stripe = ((x + y) // 2) % 2
            if stripe == 0:
                px[x, y] = YELLOW
            else:
                px[x, y] = BLACK

    # Outer 1-pixel border darker
    for x in range(16):
        px[x, 0] = BLACK
        px[x, 15] = BLACK
    for y in range(16):
        px[0, y] = BLACK
        px[15, y] = BLACK

    # Centered "shelter" mark: a downward arrow into a shelter (▽ on top of __)
    # Triangle (cols 5..10, rows 3..7)
    for y in range(3, 8):
        # arrow widens row by row
        spread = y - 3
        for x in range(7 - spread, 10 + spread):
            if 0 <= x < 16:
                px[x, y] = WHITE
    # Roof line under the arrow at row 9
    for x in range(4, 12):
        px[x, 9] = WHITE
    # Walls of shelter (rows 10..12)
    for y in range(10, 13):
        px[4, y] = WHITE
        px[11, y] = WHITE

    # Slight tinting on the bordering pixels of the white shape so it doesn't look flat
    return img


def main():
    make_texture().save(OUT_DIR / "storm_shelter_sign.png")
    print(f"wrote storm_shelter_sign.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
