"""Generate the placeholder texture for SUM's bills-display block (C8).

The block model uses one 16x16 texture (top + side strips). It's a wooden tray
with a green felt inset where the bills sit when the TESR renders them. Single
PNG output.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

WOOD = (124, 92, 56, 255)
WOOD_HI = (160, 120, 76, 255)
WOOD_LO = (88, 64, 36, 255)
FELT = (40, 92, 56, 255)
FELT_HI = (62, 120, 78, 255)
FELT_LO = (28, 68, 42, 255)
GOLD = (212, 178, 88, 255)


def make_texture():
    img = Image.new("RGBA", (16, 16), WOOD)
    px = img.load()

    # Outer wood frame (rim of tray) — rows 0..2 from top, then 12..15 are sides
    # Actually the block model uses rows 0..16 horizontally, rows 0..16 for top face,
    # and rows 12..16 for the side strips. Render the texture so the top face shows
    # a felt center with a wood frame, and the bottom rows (which face the side) show
    # a wood plank.

    # Top face — full 16x16 used, but we want it to read as "tray with felt inset".
    # Felt center inset: rows 2..13, cols 2..13
    for x in range(2, 14):
        for y in range(2, 14):
            px[x, y] = FELT
    # Felt darker shadow on the inset edges (depressed look)
    for x in range(2, 14):
        px[x, 2] = FELT_LO
        px[x, 13] = FELT_HI
    for y in range(2, 14):
        px[2, y] = FELT_LO
        px[13, y] = FELT_HI

    # Wood grain hints on the surrounding frame
    for x in range(16):
        # Top wood band rows 0..1
        px[x, 0] = WOOD_HI if x % 4 != 0 else WOOD
        px[x, 1] = WOOD if x % 4 != 0 else WOOD_LO
        # Bottom wood band rows 14..15
        px[x, 14] = WOOD_LO
        px[x, 15] = WOOD if x % 4 != 0 else WOOD_HI
    for y in range(16):
        # Left/right wood band, but only if outside felt
        if y < 2 or y >= 14:
            continue
        px[0, y] = WOOD_LO
        px[1, y] = WOOD
        px[14, y] = WOOD
        px[15, y] = WOOD_HI

    # Decorative gold corner pin in each corner of the felt
    for (cx, cy) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[cx, cy] = GOLD

    return img


def main():
    make_texture().save(OUT_DIR / "bills_display.png")
    print(f"wrote bills_display.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
