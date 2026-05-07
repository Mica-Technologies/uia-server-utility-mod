"""Generate placeholder pixel-art textures for SUM's bank-lobby block kit.

Produces 16x16 PNGs under
``src/main/resources/assets/sum/textures/blocks/`` for each bank block
SUM ships in phase A2:

* ``bank_counter_top.png`` - polished green marble counter surface
* ``bank_counter_side.png`` - dark walnut body with a marble band along the top
* ``safe_deposit_box_front.png`` - small steel hatch with hinge + handle
* ``safe_deposit_box_side.png`` - brushed steel with vertical seam
* ``vault_door_front.png`` - heavy steel slab with bolts + central wheel
* ``vault_door_side.png`` - thick steel edge profile
* ``velvet_rope_pole.png`` - polished brass stanchion

Designed to look obviously "bank" in-world while staying within SUM's
"good enough pixel art" texture budget. Polish passes can replace any
of these without touching block models or registry names.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ----- palette -----------------------------------------------------------
WALNUT_DARK   = (54, 36, 22, 255)
WALNUT_MID    = (76, 52, 32, 255)
WALNUT_LIGHT  = (98, 70, 44, 255)
MARBLE_GREEN  = (38, 76, 56, 255)
MARBLE_VEIN   = (84, 132, 100, 255)
MARBLE_HI     = (120, 168, 138, 255)
MARBLE_DEEP   = (24, 52, 38, 255)
STEEL_DARK    = (74, 78, 86, 255)
STEEL_MID     = (110, 116, 128, 255)
STEEL_HI      = (162, 170, 184, 255)
STEEL_BLACK   = (28, 30, 36, 255)
BRASS_BASE    = (170, 132, 56, 255)
BRASS_DARK    = (118, 86, 28, 255)
BRASS_HI      = (224, 188, 110, 255)
BOLT_DARK     = (40, 42, 48, 255)
BOLT_HI       = (188, 196, 208, 255)


def fill(rgba: tuple) -> Image.Image:
    return Image.new("RGBA", (16, 16), rgba)


# ----- bank counter ------------------------------------------------------
def bank_counter_top() -> Image.Image:
    img = fill(MARBLE_GREEN)
    px = img.load()
    # Diagonal vein pattern for a subtle marble look
    for x in range(16):
        for y in range(16):
            if (x * 3 + y * 5) % 17 == 0:
                px[x, y] = MARBLE_VEIN
            elif (x * 7 + y * 2) % 23 == 0:
                px[x, y] = MARBLE_HI
            elif (x + y) % 11 == 0:
                px[x, y] = MARBLE_DEEP
    # Slight rim along edges
    for i in range(16):
        px[i, 0] = MARBLE_HI
        px[i, 15] = MARBLE_DEEP
    return img


def bank_counter_side() -> Image.Image:
    img = fill(WALNUT_MID)
    px = img.load()
    # Top 3 rows are the marble band that wraps around the counter top
    for x in range(16):
        for y in range(0, 3):
            px[x, y] = MARBLE_GREEN
        px[x, 0] = MARBLE_HI
        px[x, 2] = MARBLE_DEEP
    # A subtle vertical paneling on the wood body
    for y in range(3, 16):
        px[0, y] = WALNUT_DARK
        px[15, y] = WALNUT_DARK
        px[7, y] = WALNUT_DARK
        px[8, y] = WALNUT_LIGHT
    # Wood-grain noise
    for y in range(3, 16):
        for x in range(16):
            if (x * 3 + y * 11) % 19 == 0 and px[x, y] != WALNUT_DARK:
                px[x, y] = WALNUT_LIGHT
    return img


# ----- safe deposit box --------------------------------------------------
def safe_deposit_box_front() -> Image.Image:
    img = fill(STEEL_DARK)
    px = img.load()
    # Hatch outline (rounded-ish recess)
    for x in range(2, 14):
        for y in range(2, 14):
            px[x, y] = STEEL_MID
    # Highlight rim on top + left, shadow on bottom + right (3D recess)
    for i in range(2, 14):
        px[i, 2] = STEEL_HI
        px[2, i] = STEEL_HI
        px[i, 13] = STEEL_BLACK
        px[13, i] = STEEL_BLACK
    # Center hinge line (left edge)
    for y in range(4, 12):
        px[3, y] = STEEL_BLACK
    # Pull handle (right side)
    for y in range(7, 10):
        px[11, y] = BRASS_BASE
        px[12, y] = BRASS_DARK
    # Tiny keyhole below the handle
    px[11, 11] = STEEL_BLACK
    return img


def safe_deposit_box_side() -> Image.Image:
    img = fill(STEEL_DARK)
    px = img.load()
    # Brushed-metal vertical streaks
    for y in range(16):
        px[0, y] = STEEL_HI
        px[15, y] = STEEL_BLACK
        if y % 3 == 0:
            px[5, y] = STEEL_MID
            px[10, y] = STEEL_MID
    # Top + bottom edge
    for x in range(16):
        px[x, 0] = STEEL_HI
        px[x, 15] = STEEL_BLACK
    return img


# ----- vault door --------------------------------------------------------
def vault_door_front() -> Image.Image:
    img = fill(STEEL_DARK)
    px = img.load()
    # Outer steel slab edge (highlight + shadow)
    for i in range(16):
        px[i, 0] = STEEL_HI
        px[0, i] = STEEL_HI
        px[i, 15] = STEEL_BLACK
        px[15, i] = STEEL_BLACK
    # Inner panel
    for x in range(2, 14):
        for y in range(2, 14):
            px[x, y] = STEEL_MID
    # Corner bolts
    for (bx, by) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[bx, by] = BOLT_HI
        px[bx + 1, by] = BOLT_DARK
        px[bx, by + 1] = BOLT_DARK
    # Central wheel (5x5 hub)
    for x in range(6, 10):
        for y in range(6, 10):
            px[x, y] = STEEL_HI
    # Wheel spokes
    for x in range(7, 9):
        px[x, 5] = BOLT_DARK
        px[x, 10] = BOLT_DARK
    for y in range(7, 9):
        px[5, y] = BOLT_DARK
        px[10, y] = BOLT_DARK
    # Wheel hub center
    px[7, 7] = BOLT_DARK
    px[8, 8] = BOLT_DARK
    px[8, 7] = BOLT_HI
    px[7, 8] = BOLT_HI
    return img


def vault_door_side() -> Image.Image:
    img = fill(STEEL_DARK)
    px = img.load()
    # Thick steel edge with bolt strip
    for y in range(16):
        px[0, y] = STEEL_HI
        px[15, y] = STEEL_BLACK
    for x in range(16):
        px[x, 0] = STEEL_HI
        px[x, 15] = STEEL_BLACK
    # Bolts running down the center seam
    for y in range(2, 15, 3):
        px[7, y] = BOLT_HI
        px[8, y] = BOLT_DARK
    return img


# ----- velvet rope -------------------------------------------------------
def velvet_rope_pole() -> Image.Image:
    img = fill((0, 0, 0, 0))  # transparent base; pole renders as a thin column
    px = img.load()
    # 4-pixel-wide brass pole down the center
    for y in range(16):
        for x in range(6, 10):
            px[x, y] = BRASS_BASE
        px[6, y] = BRASS_DARK
        px[9, y] = BRASS_DARK
    # Knob at the top (rounded cap)
    for x in range(5, 11):
        px[x, 0] = BRASS_HI
        px[x, 1] = BRASS_BASE
    px[4, 1] = BRASS_DARK
    px[11, 1] = BRASS_DARK
    # Base flange
    for x in range(4, 12):
        px[x, 14] = BRASS_DARK
        px[x, 15] = BOLT_DARK
    return img


def main() -> None:
    bank_counter_top().save(OUT_DIR / "bank_counter_top.png")
    bank_counter_side().save(OUT_DIR / "bank_counter_side.png")
    safe_deposit_box_front().save(OUT_DIR / "safe_deposit_box_front.png")
    safe_deposit_box_side().save(OUT_DIR / "safe_deposit_box_side.png")
    vault_door_front().save(OUT_DIR / "vault_door_front.png")
    vault_door_side().save(OUT_DIR / "vault_door_side.png")
    velvet_rope_pole().save(OUT_DIR / "velvet_rope_pole.png")
    print(f"wrote 7 bank textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
