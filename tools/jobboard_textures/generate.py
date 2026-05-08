"""Generate the placeholder texture for SUM's job-board block (JB).

Single 16x16 PNG. Cork-board background with three colored "papers" pinned at
slight angles to read as a bulletin board even at thumbnail size.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

CORK = (148, 102, 56, 255)
CORK_HI = (180, 132, 80, 255)
CORK_LO = (108, 72, 36, 255)
FRAME = (76, 50, 26, 255)
PAPER_A = (240, 232, 208, 255)
PAPER_B = (208, 226, 200, 255)
PAPER_C = (224, 196, 196, 255)
INK = (50, 46, 40, 255)
PIN = (188, 56, 56, 255)


def make_texture():
    img = Image.new("RGBA", (16, 16), CORK)
    px = img.load()

    # Cork speckle pattern
    for x in range(16):
        for y in range(16):
            n = (x * 7 + y * 13) % 7
            if n == 0:
                px[x, y] = CORK_HI
            elif n == 1:
                px[x, y] = CORK_LO

    # Outer frame
    for x in range(16):
        px[x, 0] = FRAME
        px[x, 15] = FRAME
    for y in range(16):
        px[0, y] = FRAME
        px[15, y] = FRAME

    # Paper A - top-left, light cream
    for x in range(2, 8):
        for y in range(2, 7):
            px[x, y] = PAPER_A
    # Ink lines on paper A
    for x in range(3, 7):
        px[x, 3] = INK
    for x in range(3, 6):
        px[x, 5] = INK
    # Pin
    px[5, 2] = PIN

    # Paper B - top-right, green
    for x in range(9, 14):
        for y in range(3, 8):
            px[x, y] = PAPER_B
    for x in range(10, 13):
        px[x, 4] = INK
    for x in range(10, 12):
        px[x, 6] = INK
    px[11, 3] = PIN

    # Paper C - bottom-center, pink
    for x in range(5, 12):
        for y in range(9, 14):
            px[x, y] = PAPER_C
    for x in range(6, 11):
        px[x, 10] = INK
    for x in range(6, 10):
        px[x, 12] = INK
    px[8, 9] = PIN

    return img


def main():
    make_texture().save(OUT_DIR / "job_board.png")
    print(f"wrote job_board.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
