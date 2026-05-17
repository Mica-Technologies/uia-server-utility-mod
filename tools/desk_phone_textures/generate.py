"""Generate pixel-art textures for SUM's desk phone block.

The desk phone is rendered as a multi-cuboid custom model (see
``models/block/desk_phone.json``), not a full cube. We ship four textures, each
designed for a specific face/cuboid of the model:

* ``desk_phone_dial.png``    - top of the body cuboid (3x3 keypad seen from above)
* ``desk_phone_body.png``    - sides of the body cuboid (slim band, low detail)
* ``desk_phone_cradle.png``  - top + sides of the cradle (slight depression)
* ``desk_phone_handset.png`` - the handset bar (dark with ear/mouth caps)

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

BODY = (220, 215, 200, 255)
BODY_HI = (240, 236, 224, 255)
BODY_LO = (172, 168, 156, 255)
DARK = (52, 50, 48, 255)
DARKER = (28, 26, 24, 255)
SCREEN = (90, 200, 160, 255)
KEY = (200, 195, 180, 255)
KEY_TXT = (40, 38, 36, 255)
LED_RED = (200, 60, 60, 255)


def base(c=BODY):
    return Image.new("RGBA", (16, 16), c)


def rect(img, x1, y1, x2, y2, c):
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            if 0 <= x < 16 and 0 <= y < 16:
                img.putpixel((x, y), c)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def dial():
    """Top-down view of the body (dial pad). 16x16, body color, with 3x3 keypad
    centered, a small screen strip above, and an LED in one corner."""
    img = base(BODY)
    # Slim 1px frame
    rect(img, 0, 0, 15, 0, BODY_LO)
    rect(img, 0, 15, 15, 15, BODY_LO)
    rect(img, 0, 0, 0, 15, BODY_LO)
    rect(img, 15, 0, 15, 15, BODY_LO)

    # Display strip near top
    rect(img, 3, 2, 12, 3, SCREEN)
    rect(img, 3, 2, 12, 2, DARKER)

    # 3x3 keypad
    for r in range(3):
        for c in range(3):
            kx = 3 + c * 4
            ky = 6 + r * 3
            rect(img, kx, ky, kx + 2, ky + 1, KEY)
            px(img, kx + 1, ky, KEY_TXT)

    # Indicator LED
    px(img, 14, 2, LED_RED)
    return img


def body_side():
    """Side face of the body cuboid. The cuboid is only 2px tall, so we want
    the relevant content in the bottom 2 rows (UV y=14..16 in model space).
    Above that, transparent so it doesn't bleed if a model UV picks too much."""
    img = base((0, 0, 0, 0))
    # Bottom 2 rows: a slim beige band with a darker base line
    rect(img, 0, 14, 15, 14, BODY)
    rect(img, 0, 15, 15, 15, BODY_LO)
    return img


def cradle():
    """Top + sides of the elevated cradle cuboid. Shows a slight handset rest
    impression as a dark oval across the top half of the texture."""
    img = base(BODY)
    # Slim frame
    rect(img, 0, 0, 15, 0, BODY_LO)
    rect(img, 0, 15, 15, 15, BODY_LO)
    rect(img, 0, 0, 0, 15, BODY_LO)
    rect(img, 15, 0, 15, 15, BODY_LO)
    # Dark handset depression — a horizontal trough in the middle
    rect(img, 4, 6, 11, 9, DARK)
    rect(img, 3, 7, 12, 8, DARK)
    # Two slightly darker dots — ear/mouth contact points
    px(img, 4, 7, DARKER)
    px(img, 11, 7, DARKER)
    px(img, 4, 8, DARKER)
    px(img, 11, 8, DARKER)
    return img


def handset():
    """The handset bar on top: dark with lighter ear/mouth oval caps."""
    img = base((0, 0, 0, 0))
    # Whole texture is transparent except a horizontal bar across the middle.
    rect(img, 1, 5, 14, 10, DARK)
    rect(img, 1, 5, 14, 5, DARKER)   # top edge highlight
    rect(img, 1, 10, 14, 10, DARKER) # bottom edge shadow
    # Ear cap (left) + mouth cap (right) — slightly lighter ovals
    rect(img, 2, 6, 3, 9, (84, 80, 76, 255))
    rect(img, 12, 6, 13, 9, (84, 80, 76, 255))
    return img


def main():
    dial().save(OUT_DIR / "desk_phone_dial.png")
    body_side().save(OUT_DIR / "desk_phone_body.png")
    cradle().save(OUT_DIR / "desk_phone_cradle.png")
    handset().save(OUT_DIR / "desk_phone_handset.png")

    # The old textures are no longer referenced by the model. Removing them
    # keeps the texture set tidy.
    for old in ("desk_phone_top.png", "desk_phone_side.png", "desk_phone_front.png"):
        p = OUT_DIR / old
        if p.exists():
            p.unlink()
    print(f"wrote desk_phone_* textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
